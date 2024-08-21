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
   [io.opentelemetry.api.common AttributesBuilder Attributes]
   [io.opentelemetry.api.logs  LoggerProvider Severity]
   [io.opentelemetry.api.trace TracerProvider]))

(comment
  (remove-ns 'taoensso.telemere.open-telemetry)
  (:api (enc/interns-overview)))

(enc/declare-remote
  ^:dynamic taoensso.telemere/*otel-tracer*
  taoensso.telemere/otel-default-providers_
  taoensso.telemere/add-handler!)

;;;; TODO
;; - API for remote span context and trace state? (Ref. beta19)
;; - API for span links?

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

;;;; Handler

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
  "Returns `Attributes` for given signal.
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
  (enc/qb 1e6 ; 808.56
    (signal->attrs
      {:level :info :data {:ns/kw1 :v1 :ns/kw2 :v2}
       :otel/attrs {:longs [1 1 2 3] :strs ["a" "b" "c"]}})))

(let [ak-ns   (io.opentelemetry.api.common.AttributeKey/stringKey "ns")
      ak-line (io.opentelemetry.api.common.AttributeKey/longKey   "line")]

  (defn- basic-span-attrs
    "Returns `?Attributes`."
    [signal]
    (when-let [ns   (get signal :ns)]
      (if-let [line (get signal :line)]
        (Attributes/of ak-ns ns, ak-line (long line))
        (Attributes/of ak-ns ns)))))

(comment (enc/qb 1e6 (basic-span-attrs {:ns "ns1" :line 495}))) ; 52.4

(defn handler:open-telemetry
  "Highly experimental, possibly buggy, and subject to change!!
  Feedback and bug reports very welcome! Please ping me (Peter) at:
    <https://www.taoensso.com/telemere> or
    <https://www.taoensso.com/telemere/slack>

  Needs `opentelemetry-java`,
    Ref. <https://github.com/open-telemetry/opentelemetry-java>.

  Returns a signal handler that:
    - Takes a Telemere signal (map).
    - Emits signal  data to configured `LogExporter`
    - Emits tracing data to configured `SpanExporter`
      iff `telemere/otel-tracing?` is true.

  Options:
    `:logger-provider` - nil or `io.opentelemetry.api.logs.LoggerProvider`,
      (see `telemere/get-default-providers` for default)."

  ;; Notes:
  ;; - Multi-threaded handlers may see signals ~out of order
  ;; - Sampling means that root/parent/child signals might not be handled
  ;; - `:otel/attrs`, `:otel/context` currently undocumented

  ([] (handler:open-telemetry nil))
  ([{:keys [emit-tracing? logger-provider]
     :or   {emit-tracing? true}}]

   (let [?logger-provider
         (if (not= logger-provider :default)
           logger-provider
           (:logger-provider (force taoensso.telemere/otel-default-providers_)))

         ;; Mechanism to end spans 3-6 secs *after* signal handling. The delay
         ;; helps support out-of-order signals due to >1 handler threads, etc.
         span-buffer1_ (enc/latom #{}) ; #{[<Span> <end-inst>]}
         span-buffer2_ (enc/latom #{})
         timer_
         (delay
           (let [t3s (java.util.Timer. "autoTelemereOpenTelemetryHandlerTimer3s" (boolean :daemon))]
             (.schedule t3s
               (proxy [java.util.TimerTask] []
                 (run []
                   ;; span2->end!
                   (when-let [drained (enc/reset-in! span-buffer2_ #{})]
                     (doseq [[span end-inst] drained]
                       (.end
                         ^io.opentelemetry.api.trace.Span span
                         ^java.time.Instant end-inst)))

                   ;; span1->span2
                   (when-let [drained (enc/reset-in! span-buffer1_ #{})]
                     (when-not (empty? drained)
                       (span-buffer2_ (fn [old] (set/union old drained)))))))

               3000 3000)))

         stop-tracing!
         (fn stop-tracing! []
           (when (realized? timer_)
             (loop [] (when-not (empty? (span-buffer1_)) (recur))) ; Block to drain `span1`
             (loop [] (when-not (empty? (span-buffer2_)) (recur))) ; Block to drain `span2`
             (.cancel ^java.util.Timer @timer_)))]

     (fn a-handler:open-telemetry
       ([      ] (stop-tracing!))
       ([signal]
        (let [?tracing-context
              (when emit-tracing?
                (when-let [context (enc/get* signal :otel/context :_otel-context nil)]
                  (let    [span (io.opentelemetry.api.trace.Span/fromContext context)]
                    (when (.isRecording span)
                      (enc/if-not [end-inst (get signal :end-inst)]
                        ;; No end-inst => no run-form => add `Event` to span (parent)
                        (let [{:keys [id ^java.time.Instant inst]} signal]
                          (if-let [^Attributes attrs (basic-span-attrs signal)]
                            (.addEvent span (impl/otel-name id) attrs inst)
                            (.addEvent span (impl/otel-name id)       inst)))

                        ;; Real span
                        (do
                          (if (utils/error-signal? signal)
                            (.setStatus span io.opentelemetry.api.trace.StatusCode/ERROR)
                            (.setStatus span io.opentelemetry.api.trace.StatusCode/OK))

                          (when-let [^Attributes attrs (basic-span-attrs signal)]
                            (.setAllAttributes span attrs))

                          ;; Error stuff
                          (when-let [error (get signal :error)]
                            (when (instance? Throwable error)
                              (if-let [attrs
                                       (when-let [ex-data (ex-data error)]
                                         (when-not (empty? ex-data)
                                           (let [sb (Attributes/builder)]
                                             (enc/run-kv! (fn [k v] (put-attr! sb (attr-name k) v)) ex-data)
                                             (.build sb))))]
                                (.recordException span error attrs)
                                (.recordException span error))))

                          ;; (.end span end-inst) ; Emit to `SpanExporter` now
                          ;; Emit to `SpanExporter` after delay:
                          (span-buffer1_ (fn [old] (conj old [span end-inst])))
                          (.deref timer_) ; Ensure timer is running
                          ))

                      context))))]

          (when-let [^io.opentelemetry.api.logs.LoggerProvider logger-provider ?logger-provider]
            (let [{:keys [ns inst level msg_]} signal
                  logger (.get logger-provider (or ns "default"))
                  lrb    (.logRecordBuilder logger)]

              (.setTimestamp     lrb inst)
              (.setSeverity      lrb (level->severity level))
              (.setAllAttributes lrb (signal->attrs   signal))

              (when-let [^io.opentelemetry.context.Context tracing-context ?tracing-context]
                (.setContext lrb tracing-context)) ; Incl. traceId, spanId, etc.

              (when-let [body
                         (or
                           (force msg_)
                           (when-let [error (get signal :error)]
                             (when (instance? Throwable error)
                               (str (enc/ex-type error) ": " (enc/ex-message error)))))]
                (.setBody lrb body))

              ;; Emit to `LogRecordExporter`
              (.emit lrb)))))))))

(enc/deprecated
  (def ^:no-doc ^:deprecated handler:open-telemetry-logger
    "Prefer `handler:open-telemetry`."
    handler:open-telemetry))

(comment
  (do
    (require '[taoensso.telemere :as t])
    (def h1 (handler:open-telemetry))
    (let [[_ [s1 s2]] (t/with-signals (t/trace! ::id1 (t/trace! ::id2 "form2")))]
      (def s1 s1)
      (def s2 s2)))

  (h1 s1))

(defn check-interop
  "Returns interop debug info map."
  []
  {:present?       true
   :use-tracer?    impl/enabled:otel-tracing?
   :viable-tracer? (boolean (impl/viable-tracer (force taoensso.telemere/*otel-tracer*)))})

(impl/add-interop-check! :open-telemetry check-interop)

(impl/on-init
  (when impl/enabled:otel-tracing?
    ;; (taoensso.telemere/add-handler! :default/open-telemetry (handler:open-telemetry))
    (impl/signal!
      {:kind  :event
       :level :debug ; < :info since runs on init
       :id    :taoensso.telemere/open-telemetry-tracing!
       :msg   "Enabling interop: OpenTelemetry tracing"})))
