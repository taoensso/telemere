(ns taoensso.telemere.open-telemetry
  "OpenTelemetry handler using `opentelemetry-java`,
    Ref. <https://github.com/open-telemetry/opentelemetry-java>,
         <https://javadoc.io/doc/io.opentelemetry/opentelemetry-api/latest/index.html>"
  (:require
   [clojure.string  :as str]
   [clojure.set     :as set]
   [taoensso.encore :as enc :refer [have have?]]
   [taoensso.telemere.utils :as utils]
   [taoensso.telemere.impl  :as impl]
   [taoensso.telemere       :as tel])

  (:import
   [io.opentelemetry.context Context]
   [io.opentelemetry.api.common AttributesBuilder Attributes]
   [io.opentelemetry.api.logs  LoggerProvider Severity]
   [io.opentelemetry.api.trace TracerProvider Tracer Span]
   [java.util.concurrent CountDownLatch]))

(comment
  (remove-ns 'taoensso.telemere.open-telemetry)
  (:api (enc/interns-overview)))

;;;; TODO
;; - API for `remote-span-context`, trace state, span links?
;; - Ability to actually set (compatible) traceId, spanId?
;; - Consider actually establishing relevant OpenTelemetry Context when tracing?
;;   Would allow a simpler OpenTelemetry handler, and allow low-level
;;   manual/auto tracing *within* Telemere run forms.

;;;; Providers

(defn get-default-providers
  "Experimental, subject to change. Feedback welcome!

  Returns map with keys:
    :logger-provider - default `io.opentelemetry.api.logs.LoggerProvider`
    :tracer-provider - default `io.opentelemetry.api.trace.TracerProvider`
    :via             - ∈ #{:sdk-extension-autoconfigure :global}

  Uses `AutoConfiguredOpenTelemetrySdk` when possible, or
  `GlobalOpenTelemetry` otherwise.

  See the relevant `opentelemetry-java` docs for details."
  []
  (or
    ;; Via SDK autoconfiguration extension (when available)
    (enc/compile-when
      io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
      (enc/catching :common
        (let [builder (io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk/builder)
              sdk    (.getOpenTelemetrySdk (.build builder))]
          {:logger-provider (.getLogsBridge     sdk)
           :tracer-provider (.getTracerProvider sdk)
           :via :sdk-extension-autoconfigure})))

    ;; Via Global (generally not recommended)
    (let [g (io.opentelemetry.api.GlobalOpenTelemetry/get)]
      {:logger-provider (.getLogsBridge     g)
       :tracer-provider (.getTracerProvider g)
       :via :global})))

(def ^:no-doc default-providers_
  (delay (get-default-providers)))

(comment
  (get-default-providers)
  (let [{:keys [logger-provider tracer-provider]} (get-default-providers)]
    (def ^LoggerProvider my-lp       logger-provider)
    (def ^Tracer         my-tr (.get tracer-provider "Telemere")))

  ;; Confirm that we have a real (not noop) SpanBuilder
  (.spanBuilder my-tr "my-span"))

;;;; Attributes

(def ^:private ^String attr-name
  "Returns cached OpenTelemetry-style name: `:a.b/c-d` -> \"a.b.c_d\", etc.
  Ref. <https://opentelemetry.io/docs/specs/semconv/general/attribute-naming/>."
  (enc/fmemoize
    (fn self
      ([prefix x] (str (self prefix) "." (self x)))
      ([       x]
       (if-not (enc/named? x)
         (str/replace (str/lower-case (str x)) #"[-\s]" "_")
         (if-let [ns (namespace x)]
           (str/replace (str/lower-case (str ns "." (name x))) "-" "_")
           (str/replace (str/lower-case             (name x))  "-" "_")))))))

(comment (enc/qb 1e6 (attr-name :a.b/c-d) (attr-name :x.y/z :a.b/c-d))) ; [44.13 63.19]

;; AttributeTypes: String, Long, Double, Boolean, and arrays
(defprotocol     ^:private IAttributesBuilder (^:private -put-attr! ^AttributesBuilder [attr-val attr-name attr-builder]))
(extend-protocol           IAttributesBuilder
  ;; nil             (-put-attr! [v ^String k ^AttributesBuilder ab] (.put ab k     "nil"))  ; As pr-edn*
  nil                (-put-attr! [v ^String k ^AttributesBuilder ab]       ab             )  ; Noop
  Boolean            (-put-attr! [v ^String k ^AttributesBuilder ab] (.put ab k         v))
  String             (-put-attr! [v ^String k ^AttributesBuilder ab] (.put ab k         v))
  java.util.UUID     (-put-attr! [v ^String k ^AttributesBuilder ab] (.put ab k (str    v))) ; "d4fc65a0..."
  clojure.lang.Named (-put-attr! [v ^String k ^AttributesBuilder ab] (.put ab k (str    v))) ; ":foo/bar"

  Long               (-put-attr! [v ^String k ^AttributesBuilder ab] (.put ab k         v))
  Integer            (-put-attr! [v ^String k ^AttributesBuilder ab] (.put ab k (long   v)))
  Short              (-put-attr! [v ^String k ^AttributesBuilder ab] (.put ab k (long   v)))
  Byte               (-put-attr! [v ^String k ^AttributesBuilder ab] (.put ab k (long   v)))
  Double             (-put-attr! [v ^String k ^AttributesBuilder ab] (.put ab k         v))
  Float              (-put-attr! [v ^String k ^AttributesBuilder ab] (.put ab k (double v)))
  Number             (-put-attr! [v ^String k ^AttributesBuilder ab] (.put ab k (double v)))

  clojure.lang.IPersistentCollection
  (-put-attr! [v ^String k ^AttributesBuilder ab]
    (when-some [v1 (if (indexed? v) (nth v 0 nil) (first v))]
      (or
        (cond
          (string?  v1) (enc/catching :common (.put ab k ^"[Ljava.lang.String;" (into-array String v)))
          (int?     v1) (enc/catching :common (.put ab k                        (long-array        v)))
          (float?   v1) (enc/catching :common (.put ab k                        (double-array      v)))
          (boolean? v1) (enc/catching :common (.put ab k                        (boolean-array     v))))

        (when-let [^String s (enc/catching :common (enc/pr-edn* v))]
          (.put ab k s))))
    ab)

  Object
  (-put-attr! [v ^String k ^AttributesBuilder ab]
    (when-let [^String s (enc/catching :common (enc/pr-edn* v))]
      (.put ab k s))))

(defmacro ^:private put-attr! [attr-builder attr-name attr-val]
  `(-put-attr! ~attr-val ~attr-name ~attr-builder)) ; Fix arg order

(defn- merge-attrs!
  "If given a map, merges prefixed key/values (~like `into`).
  Otherwise just puts single named value."
  [attr-builder name-or-prefix x]
  (if (map? x)
    (enc/run-kv! (fn [k v] (put-attr! attr-builder (attr-name name-or-prefix k) v)) x)
    (do                    (put-attr! attr-builder            name-or-prefix        x))))

;;;; Spans

(defn- remote-span-context
  "Returns new remote `io.opentelemetry.api.trace.SpanContext`
  for use as `start-span` parent."
  ^io.opentelemetry.api.trace.SpanContext
  [^String trace-id ^String span-id sampled? ?trace-state]
  (io.opentelemetry.api.trace.SpanContext/createFromRemoteParent
    trace-id span-id
    (if sampled?
      (io.opentelemetry.api.trace.TraceFlags/getSampled)
      (io.opentelemetry.api.trace.TraceFlags/getDefault))

    (enc/if-not [trace-state ?trace-state]
      (io.opentelemetry.api.trace.TraceState/getDefault)
      (cond
        (map? trace-state)
        (let [tsb (io.opentelemetry.api.trace.TraceState/builder)]
          (enc/run-kv! (fn [k v] (.put tsb k v)) trace-state) ; NB only `a-zA-Z.-_` chars allowed
          (.build tsb))

        (instance? io.opentelemetry.api.trace.TraceState trace-state) trace-state
        :else
        (enc/unexpected-arg! trace-state
          :context  `remote-span-context
          :param    'trace-state
          :expected '#{nil {string string} io.opentelemetry.api.trace.TraceState})))))

(comment (enc/qb 1e6 (remote-span-context "c5b856d919f65e39a202bfb3034d65d8" "9740419096347616" false {"a" "A"}))) ; 111.13

(defn- start-span
  "Returns new `io.opentelemetry.api.trace.Span` with random `traceId` and `spanId`."
  ^Span
  [^Tracer tracer ^Context context ^String span-name ^java.time.Instant inst ?parent]
  (let [sb (.spanBuilder tracer span-name)]
    (enc/if-not [parent ?parent]
      (.setParent sb context) ; Base (callsite) context
      (cond
        ;; Local parent span, etc.
        (instance? Span parent) (.setParent sb (.with context ^Span parent))

        ;; Remote parent context, etc.
        (instance? io.opentelemetry.api.trace.SpanContext parent)
        (.setParent sb
          (.with context
            (Span/wrap ^io.opentelemetry.api.trace.SpanContext parent)))

        :else
        (enc/unexpected-arg! parent
          {:context `start-span
           :expected
           #{io.opentelemetry.api.trace.Span
             io.opentelemetry.api.trace.SpanContext}})))

    (.setStartTimestamp sb inst)
    (.startSpan         sb)))

(comment
  (let [inst (enc/now-inst)] (enc/qb 1e6                   (start-span my-tr (Context/current) "id1"          inst  nil))) ; 158.09
  (start-span my-tr (Context/current) "id1" (enc/now-inst) (start-span my-tr (Context/current) "id2" (enc/now-inst) nil))
  (start-span my-tr (Context/current) "id1" (enc/now-inst)
    (remote-span-context "c5b856d919f65e39a202bfb3034d65d8" "1111111111111111" false nil)))

(let [ak-uid  (io.opentelemetry.api.common.AttributeKey/stringKey "uid")
      ak-ns   (io.opentelemetry.api.common.AttributeKey/stringKey "ns")
      ak-line (io.opentelemetry.api.common.AttributeKey/longKey   "line")]

  (defn- span-attrs
    "Returns `io.opentelemetry.api.common.Attributes` or nil."
    [uid signal]
    (if uid
      (if-let   [ns   (get signal :ns)]
        (if-let [line (get signal :line)]
          (Attributes/of ak-uid (str uid), ak-ns ns, ak-line (long line))
          (Attributes/of ak-uid (str uid), ak-ns ns))
        (Attributes/of   ak-uid (str uid)))

      (if-let   [ns   (get signal :ns)]
        (if-let [line (get signal :line)]
          (Attributes/of ak-ns ns, ak-line (long line))
          (Attributes/of ak-ns ns))
        nil))))

(comment (enc/qb 1e6 (span-attrs "uid1" {:ns "ns1" :line 495}))) ; 101.36

(def ^:private ^String span-name
  (enc/fmemoize
    (fn [id]
      #_(if id (str          id) ":telemere/nil-id")
      (if   id (enc/as-qname id)  "telemere/nil-id"))))

(comment (enc/qb 1e6 (span-name :foo/bar))) ; 46.09

(defn- handle-tracing!
  "Experimental! Takes care of relevant signal `Span` management.
  Returns nil or `io.opentelemetry.api.trace.Span` for possible use as
  `io.opentelemetry.api.logs.LogRecordBuilder` context.

  Expect:
    - `spans_`      - latom: {<uid> <Span_>}
    - `end-buffer_` - latom: #{[<uid> <end-inst>]}
    - `gc-buffer_`  - latom: #{<uid>}"

  [tracer context spans_ end-buffer_ gc-buffer_ gc-latch_ signal]

  ;; Notes:
  ;; - Spans go to `SpanExporter` after `.end` call, ~random order okay
  ;; - Span data: t1 of self, and name + id + t0 of #{self parent trace}
  ;; - No API to directly create spans with needed data, so we ~simulate
  ;;   typical usage

  (when-let [^java.util.concurrent.CountDownLatch gc-latch (gc-latch_)]
    (try (.await gc-latch) (catch InterruptedException _)))

  (enc/when-let
    [root     (get signal :root) ; Tracing iff root
     root-uid (get root   :uid)
     :let [curr-spans (spans_)]
     root-span
     (force
       (or ; Fetch/ensure Span for root
         (get curr-spans root-uid)
         (when-let [root-inst (get root :inst)]
           (let    [root-id   (get root :id)]
             (spans_ root-uid
               (fn  [old]
                 (or old
                   (delay
                     ;; TODO Support remote-span-context parent and/or span links?
                     (start-span tracer context (span-name root-id)
                       root-inst nil)))))))))]

    (let [?parent-span ; May be identical to root-span
          (when-let   [parent     (get signal :parent)]
            (when-let [parent-uid (get parent :uid)]
              (if (= parent-uid root-uid)
                root-span
                (force
                  (or ; Fetch/ensure Span for parent
                    (get curr-spans parent-uid)
                    (let [{parent-id :id, parent-inst :inst} parent]
                      (spans_ parent-uid
                        (fn  [old]
                          (or old
                            (delay
                              (start-span tracer context (span-name parent-id)
                                parent-inst root-span)))))))))))

          {this-uid :uid, this-end-inst :end-inst} signal]

      (enc/cond
        ;; No end-inst => no run-form =>
        ;; add `Event` (rather than child `Span`) to parent
        :if-let [this-is-event? (not this-end-inst)]
        (when-let [^Span parent-span ?parent-span]
          (let [{this-id :id, this-inst :inst} signal]
            (if-let [^Attributes attrs (span-attrs this-uid signal)]
              (.addEvent parent-span (span-name this-id) attrs ^java.time.Instant this-inst)
              (.addEvent parent-span (span-name this-id)       ^java.time.Instant this-inst)))
          (do            parent-span))

        :if-let
        [^Span this-span
         (if (= this-uid root-uid)
           root-span
           (force
             (or ; Fetch/ensure Span for this (child)
               (get curr-spans this-uid)
               (let [{this-id :id, this-inst :inst} signal]
                 (spans_ this-uid
                   (fn  [old]
                     (or old
                       (delay
                         (start-span tracer context (span-name this-id)
                           this-inst (or ?parent-span root-span))))))))))]

        (do
          (if (utils/error-signal? signal)
            (.setStatus this-span io.opentelemetry.api.trace.StatusCode/ERROR)
            (.setStatus this-span io.opentelemetry.api.trace.StatusCode/OK))

          (when-let [^Attributes attrs (span-attrs this-uid signal)]
            (.setAllAttributes this-span attrs))

          ;; Error stuff
          (when-let [error (get signal :error)]
            (when (instance? Throwable error)
              (if-let [attrs
                       (when-let [ex-data (ex-data error)]
                         (when-not (empty? ex-data)
                           (let [sb (Attributes/builder)]
                             (enc/run-kv! (fn [k v] (put-attr! sb (attr-name k) v)) ex-data)
                             (.build sb))))]
                (.recordException this-span error attrs)
                (.recordException this-span error))))

          ;; (.end this-span this-end-inst) ; Ready for `SpanExporter`
          (end-buffer_ (fn [old] (conj old [this-uid this-end-inst])))
          (gc-buffer_  (fn [old] (conj old  this-uid)))

          this-span)))))

(comment
  (do
    (require '[taoensso.telemere :as t])
    (def spans_       "{<uid> <Span_>}"      (enc/latom {}))
    (def end-buffer_ "#{[<uid> <end-inst>]}" (enc/latom #{}))
    (def gc-buffer_  "#{<uid>}"              (enc/latom #{}))
    (let [[_ [s1 s2]] (t/with-signals (t/trace! ::id1 (t/trace! ::id2 "form2")))]
      (def s1 s1)
      (def s2 s2)))

  [@gc-buffer_ @end-buffer_ @spans_]
  (handle-tracing! my-tr spans_ end-buffer_ gc-buffer_ (enc/latom nil) s1))

;;;; Logging

(defn- level->severity
  ^Severity [level]
  (case      level
    :trace  Severity/TRACE
    :debug  Severity/DEBUG
    :info   Severity/INFO
    :warn   Severity/WARN
    :error  Severity/ERROR
    :fatal  Severity/FATAL
    :report Severity/INFO4
    Severity/UNDEFINED_SEVERITY_NUMBER))

(defn- level->string
  ^String [level]
  (when    level
    (case  level
      :trace  "TRACE"
      :debug  "DEBUG"
      :info   "INFO"
      :warn   "WARN"
      :error  "ERROR"
      :fatal  "FATAL"
      :report "INFO4"
      (str level))))

(defn- signal->attrs
  "Returns `io.opentelemetry.api.common.Attributes` for given signal.
  Ref. <https://opentelemetry.io/docs/specs/otel/logs/data-model/>."
  ^Attributes [signal]
  (let [ab (Attributes/builder)]
    (put-attr!    ab "error"     (utils/error-signal? signal)) ; Standard
    ;; (put-attr! ab "host.name" (utils/hostname))             ; Standard

    (when-let [{:keys [name ip]} (get signal :host)]
      (put-attr! ab "host.name" name) ; Standard
      (put-attr! ab "host.ip"   ip))

    (when-let [level (get signal :level)]
      (put-attr! ab "level" ; Standard
        (level->string level)))

    (when-let [{:keys [type msg trace data]} (enc/ex-map (get signal :error))]
      (put-attr! ab "exception.type"    type) ; Standard
      (put-attr! ab "exception.message" msg)  ; Standard
      (when trace
        (put-attr! ab "exception.stacktrace"  ; Standard
          (#'utils/format-clj-stacktrace trace)))

      (when data (merge-attrs! ab "exception.data" data)))

    (let [{:keys [ns line file, kind id uid]} signal]
      (put-attr! ab "ns"   ns)
      (put-attr! ab "line" line)
      (put-attr! ab "file" file)

      (put-attr! ab "kind" kind)
      (put-attr! ab "id"    id)
      (put-attr! ab "uid"  uid))

    (when-let [run-form (get signal :run-form)]
      (let [{:keys [run-val run-nsecs]} signal]
        (put-attr! ab "run.form"     (if (nil? run-form) "nil" (str run-form)))
        (put-attr! ab "run.val_type" (if (nil? run-val)  "nil" (.getName (class run-val))))
        (put-attr! ab "run.val"                run-val)
        (put-attr! ab "run.nsecs"    run-nsecs)))

    (put-attr! ab "sample" (get signal :sample-rate))

    (when-let [{:keys [id uid]} (get signal :parent)]
      (put-attr! ab "parent.id"  id)
      (put-attr! ab "parent.uid" uid))

    (when-let [{:keys [id uid]} (get signal :root)]
      (put-attr! ab "root.id"  id)
      (put-attr! ab "root.uid" uid))

    (when-let [ctx   (get signal :ctx)]  (merge-attrs! ab "ctx"  ctx))
    (when-let [data  (get signal :data)] (merge-attrs! ab "data" data))
    (when-let [attrs (get signal :otel/attrs)] ; Undocumented
      (cond
        (map? attrs) (enc/run-kv! (fn [k v] (put-attr! ab (attr-name k) v)) attrs) ; Unprefixed
        (instance? Attributes attrs) (.putAll ab ^Attributes attrs)                ; Unprefixed
        :else
        (enc/unexpected-arg! attrs
          {:context `signal->attrs!
           :expected #{nil map io.opentelemetry.api.common.Attributes}})))

    (.build ab)))

(comment
  (enc/qb 1e6 ; 850.93
    (signal->attrs
      {:level :info :data {:ns/kw1 :v1 :ns/kw2 :v2}
       :otel/attrs {:longs [1 1 2 3] :strs ["a" "b" "c"]}})))

(defn handler:open-telemetry-logger
  "Highly experimental, possibly buggy, and subject to change!!
  Feedback and bug reports very welcome! Please ping me (Peter) at:
    <https://www.taoensso.com/telemere> or
    <https://www.taoensso.com/telemere/slack>

  Needs `opentelemetry-java`,
    Ref. <https://github.com/open-telemetry/opentelemetry-java>.

  Returns a signal handler that:
    - Takes a Telemere signal (map).
    - Emits signal  data to configured `io.opentelemetry.api.logs.Logger`
    - Emits tracing data to configured `io.opentelemetry.api.logs.Tracer`

  Options:
    `:logger-provider` - ∈ #{nil :default <io.opentelemetry.api.logs.LoggerProvider>}  [1]
    `:tracer-provider` - ∈ #{nil :default <io.opentelemetry.api.trace.TracerProvider>} [1]
    `:max-span-msecs`  - (Advanced) Longest tracing span to support in milliseconds
                         (default 120 mins). If recorded spans exceed this max, emitted
                         data will be inaccurate. Larger values use more memory.

  [1] See `get-default-providers` for more info"

  ;; Notes:
  ;; - Multi-threaded handlers may see signals ~out of order
  ;; - Sampling means that root/parent/child signals may never be handled
  ;; - `:otel/attrs`, `:otel/context` currently undocumented

  ([] (handler:open-telemetry-logger nil))
  ([{:keys [logger-provider tracer-provider max-span-msecs]
     :or
     {logger-provider :default
      tracer-provider :default
      max-span-msecs  (enc/msecs :mins 120)}}]

   (let [min-max-span-msecs (enc/msecs :mins 15)]
    (when (< (long max-span-msecs) min-max-span-msecs)
      (throw
        (ex-info "`max-span-msecs` too small"
          {:given max-span-msecs, :min min-max-span-msecs}))))

   (let [?logger-provider (if (= logger-provider :default) (:logger-provider (force default-providers_)) logger-provider)
         ?tracer-provider (if (= tracer-provider :default) (:tracer-provider (force default-providers_)) tracer-provider)
         ?tracer
         (when-let [^io.opentelemetry.api.trace.TracerProvider p ?tracer-provider]
           (.get p "Telemere"))

         ;;; Tracing state
         spans_       (when ?tracer (enc/latom  {})) ; {<uid> <Span_>}
         end-buffer1_ (when ?tracer (enc/latom #{})) ; #{[<uid> <end-inst>]}
         sgc-buffer1_ (when ?tracer (enc/latom #{})) ; #{<uid>} ; Slow GC
         gc-latch_    (when ?tracer (enc/latom nil)) ; ?CountDownLatch

         stop-tracing!
         (if-not ?tracer
           (fn stop-tracing! []) ; Noop
           (let [end-buffer2_ (enc/latom #{})
                 sgc-buffer2_ (enc/latom #{})
                 fgc-buffer1_ (enc/latom #{})
                 fgc-buffer2_ (enc/latom #{})

                 tmax (java.util.Timer. "autoTelemereOpenTelemetryHandlerTimerMax" (boolean :daemon))
                 t2m  (java.util.Timer. "autoTelemereOpenTelemetryHandlerTimer2m"  (boolean :daemon))
                 t3s  (java.util.Timer. "autoTelemereOpenTelemetryHandlerTimer3s"  (boolean :daemon))
                 schedule!
                 (fn [^java.util.Timer timer ^long interval-msecs f]
                   (.schedule timer (proxy [java.util.TimerTask] [] (run [] (f)))
                     interval-msecs interval-msecs))

                 gc-spans!
                 (fn [uids-to-gc]
                   (when-not (empty? uids-to-gc)
                     (let [uids-to-gc (set/intersection uids-to-gc (set (keys (spans_))))]
                       (when-not (empty? uids-to-gc)
                         ;; ;; Update in small batches to minimize contention
                         ;; (doseq [batch (partition-all 16 uids-to-gc)]
                         ;;   (spans_ (fn [old] (reduce dissoc old batch))))
                         (let [gc-latch (java.util.concurrent.CountDownLatch. 1)]
                           (when (compare-and-set! gc-latch_ nil gc-latch)
                             (try
                               (spans_ (fn [old] (reduce dissoc old uids-to-gc)))
                               (finally
                                 (.countDown gc-latch)
                                 (reset!     gc-latch_ nil)))))))))

                 move-uids!
                 (fn [src_ dst_]
                   (let [drained (enc/reset-in! src_ #{})]
                     (when-not (empty? drained)
                       (dst_ (fn [old] (set/union old drained))))))]

             ;; Notes:
             ;; - Maintain local {<uid> <Span_>} state, creating spans as needed
             ;; - A timer+buffer system is used to delay calling `.end` on
             ;;   spans, allowing parents to linger in case they're handled
             ;;   before children.
             ;;
             ;; Internal buffer flow:
             ;;   1. handler->end1->end2->(end!)->fgc1->fgc2->(gc!) ; Fast GC path (span     ended)
             ;;   2. handler                    ->sgc1->sgc2->(gc!) ; Slow GC path (span not ended)
             ;;
             ;; Properties:
             ;;   - End spans 3-6   secs after trace handler ; Linger for possible out-of-order children
             ;;   - GC  spans 2-4   mins after ending        ; '', children will noop
             ;;   - GC  spans 90-92 mins after span first created
             ;;     Final catch-all for spans that may have been created but
             ;;     never ended (e.g. due to sampling or filtering).
             ;;     => Max span runtime!

             (schedule! tmax max-span-msecs ; sgc2->(gc!)
               (fn [] (gc-spans! (enc/reset-in! sgc-buffer2_ #{}))))

             (schedule! t2m (enc/msecs :mins 2)
               (fn []
                 (gc-spans! (enc/reset-in! fgc-buffer2_ #{})) ; fgc2->(gc!)
                 (move-uids! fgc-buffer1_  fgc-buffer2_)      ; fgc1->fgc2
                 (move-uids! sgc-buffer1_  sgc-buffer2_)      ; sgc1->sgc2
                 ))

             (schedule! t3s (enc/msecs :secs 3)
               (fn []
                 (let [drained (enc/reset-in! end-buffer2_ #{})]
                   (when-not (empty? drained)

                     ;; end2->(end!)
                     (let [spans (spans_)]
                       (doseq [[uid end-inst] drained]
                         (when-let [span_ (get spans uid)]
                           (.end ^Span (force span_) ^java.time.Instant end-inst))))

                     ;; (end!)->fgc1
                     (let [uids (into #{} (map (fn [[uid _]] uid)) drained)]
                       (fgc-buffer1_ (fn [old] (set/union old uids))))))

                 ;; end1->end2
                 (move-uids! end-buffer1_ end-buffer2_)))

             (fn stop-tracing! []
               (loop [] (when-not (empty? (end-buffer1_)) (recur))) ; Block to drain `end1`
               (loop [] (when-not (empty? (end-buffer2_)) (recur))) ; Block to drain `end2`
               (.cancel t3s) (.cancel t2m) (.cancel tmax))))]

     (fn a-handler:open-telemetry-logger
       ([      ] (stop-tracing!))
       ([signal]
        (let [?context
              (enc/when-let
                [^Tracer  tracer ?tracer
                 ^Context context
                 (enc/get* signal :otel/context ; Undocumented
                   :_otel-context
                   #_(io.opentelemetry.context.Context/root)
                   (io.opentelemetry.context.Context/current))

                 ^Span span
                 (handle-tracing! tracer context
                   spans_ end-buffer1_ sgc-buffer1_ gc-latch_ signal)]

                (.storeInContext span context))]

          (when-let [^io.opentelemetry.api.logs.LoggerProvider logger-provider ?logger-provider]
            (let [{:keys [ns inst level msg_]} signal
                  logger (.get logger-provider (or ns "default"))
                  lrb    (.logRecordBuilder logger)]

              (.setTimestamp     lrb inst)
              (.setSeverity      lrb (level->severity level))
              (.setAllAttributes lrb (signal->attrs   signal))

              (when-let [^Context context ?context] ; Incl. traceId, SpanId, etc.
                (.setContext lrb  context))

              (when-let [body
                         (or
                           (force msg_)
                           (when-let [error (get signal :error)]
                             (when (instance? Throwable error)
                               (str (enc/ex-type error) ": " (enc/ex-message error)))))]
                (.setBody lrb body))

              ;; Ready for `LogRecordExporter`
              (.emit lrb)))))))))

(comment
  (do
    (require '[taoensso.telemere :as t])
    (def h1 (handler:open-telemetry-logger))
    (let [[_ [s1 s2]] (t/with-signals (t/trace! ::id1 (t/trace! ::id2 "form2")))]
      (def s1 s1)
      (def s2 s2)))

  (h1 s1))
