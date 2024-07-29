(ns taoensso.telemere.open-telemetry
  "OpenTelemetry handler using `opentelemetry-java`,
    Ref. <https://github.com/open-telemetry/opentelemetry-java>."
  (:require
   [clojure.string  :as str]
   [taoensso.encore :as enc :refer [have have?]]
   [taoensso.telemere.utils :as utils]
   [taoensso.telemere.impl  :as impl]
   [taoensso.telemere       :as tel])

  (:import
   [io.opentelemetry.api.logs LoggerProvider Severity]
   [io.opentelemetry.api.common Attributes AttributesBuilder]
   [io.opentelemetry.api GlobalOpenTelemetry]))

(comment
  (remove-ns 'taoensso.telemere.open-telemetry)
  (:api (enc/interns-overview)))

;;;; Implementation

(defn- level->severity
  ^Severity [level]
  (case level
    :trace  Severity/TRACE
    :debug  Severity/DEBUG
    :info   Severity/INFO
    :warn   Severity/WARN
    :error  Severity/ERROR
    :fatal  Severity/FATAL
    :report Severity/INFO4
    Severity/UNDEFINED_SEVERITY_NUMBER))

(def ^:private ^String attr-name
  "Returns cached OpenTelemetry-style name: `:foo/bar-baz` -> \"foo_bar_baz\", etc.
  Ref. <https://opentelemetry.io/docs/specs/semconv/general/attribute-naming/>."
  (enc/fmemoize
    (fn
      ([prefix x] (str (attr-name prefix) "." (attr-name x))) ; For `merge-prefix-map`, etc.
      ([       x]
       (if-not (enc/named? x)
         (str/replace (str/lower-case (str x)) #"[-\s]" "_")
         (if-let [ns (namespace x)]
           (str/replace (str/lower-case (str ns "." (name x))) "-" "_")
           (str/replace (str/lower-case             (name x))  "-" "_")))))))

(comment (enc/qb 1e6 (attr-name :x1.x2/x3-x4 :Foo/Bar-BAZ))) ; 63.6

;; AttributeTypes: String, Long, Double, Boolean, and arrays
(defprotocol     IAttr+ (^:private attr+ [_aval akey builder]))
(extend-protocol IAttr+
  nil                (attr+ [v k ^AttributesBuilder b] (.put b (attr-name k) "nil")) ; Like pr-edn*
  Boolean            (attr+ [v k ^AttributesBuilder b] (.put b (attr-name k)         v))
  String             (attr+ [v k ^AttributesBuilder b] (.put b (attr-name k)         v))
  java.util.UUID     (attr+ [v k ^AttributesBuilder b] (.put b (attr-name k) (str    v))) ; "d4fc65a0..."

  Long               (attr+ [v k ^AttributesBuilder b] (.put b (attr-name k)         v))
  Integer            (attr+ [v k ^AttributesBuilder b] (.put b (attr-name k) (long   v)))
  Short              (attr+ [v k ^AttributesBuilder b] (.put b (attr-name k) (long   v)))
  Byte               (attr+ [v k ^AttributesBuilder b] (.put b (attr-name k) (long   v)))
  Double             (attr+ [v k ^AttributesBuilder b] (.put b (attr-name k)         v))
  Float              (attr+ [v k ^AttributesBuilder b] (.put b (attr-name k) (double v)))
  Number             (attr+ [v k ^AttributesBuilder b] (.put b (attr-name k) (double v)))

  clojure.lang.Named
  (attr+ [v k ^AttributesBuilder b]
    (.put b (attr-name k)
      #_(str v)                                                       ; ":foo/bar", etc.
      (let [n (name v)] (if-let [ns (namespace v)] (str ns "/" n) n)) ;  "foo/bar", etc.
      ))

  clojure.lang.IPersistentCollection
  (attr+ [v k ^AttributesBuilder b]
    (let [v1 (first v)]
      (or
        (cond
          (boolean? v1) (enc/catching :common (.put b (attr-name k)                        (boolean-array     (mapv boolean     v))))
          (int?     v1) (enc/catching :common (.put b (attr-name k)                        (long-array        (mapv long        v))))
          (float?   v1) (enc/catching :common (.put b (attr-name k)                        (double-array      (mapv double      v)))))
        (do                                   (.put b (attr-name k) ^"[Ljava.lang.String;" (into-array String (mapv enc/pr-edn* v)))))))

  Object (attr+ [v k ^AttributesBuilder b] (.put b (attr-name k) (enc/pr-edn* v))))

(defn- as-attrs
  "Returns `io.opentelemetry.api.common.Attributes` for given map."
  ^Attributes [m]
  (if (empty?  m)
    (Attributes/empty)
    (let [builder (Attributes/builder)]
      (enc/run-kv! (fn [k v] (attr+ v k builder)) m)
      (.build builder))))

(comment (str (as-attrs {:s "s", :kw :foo/bar, :long 5, :double 5.0, :longs [5 5 5] :nil nil})))

(defn- merge-prefix-map
  "Merges prefixed `from` into `to`."
  [to prefix from]
  (enc/cond
    (map? from)
    (reduce-kv
      (fn [acc k v] (assoc acc (attr-name prefix k) v))
      to from)

    from (assoc to prefix from)
    :else       to))

(comment (merge-prefix-map {} "data" {:a/b1 "v1" :a/b2 "v2" :nil nil}))

(defn- signal->attrs-map
  "Returns attributes map for given signal,
  Ref. <https://opentelemetry.io/docs/specs/otel/logs/data-model/>."
  [attrs-key signal]
  (let [attrs-map
        (let [{:keys [ns line file, kind level id uid parent,
                      run-form run-val run-nsecs, sample-rate]}
              signal]

          (enc/assoc-some nil
            {"ns"    ns
             "line"  line
             "file"  file

             "error" (utils/error-signal? signal) ; Standard key
             "kind"  kind
             "level" level
             "id"    id
             "uid"   uid

             "run.form"     run-form
             "run.val_type" (enc/class-sym run-val)
             "run.val"      run-val
             "run.nsecs"    run-nsecs
             "sample"       sample-rate

             "parent.id"  (get parent  :id)
             "parent.uid" (get parent :uid)}))

        attrs-map
        (enc/if-not [{:keys [type msg data trace]} (enc/ex-map (get signal :error))]
          attrs-map
          (merge-prefix-map
            (enc/assoc-some attrs-map
              ;; 3x standard keys
              "exception.type"       type
              "exception.message"    msg
              "exception.stacktrace" (when trace (#'utils/format-clj-stacktrace trace)))
            "exception.data" data))

        kvs (get signal :kvs)
        attr-kvs
        (when attrs-key
          (when-let [kvs (get signal attrs-key)]
            (not-empty kvs)))

        kvs
        (if attr-kvs
          (dissoc kvs attrs-key)
          (do     kvs))

        attrs-map
        (-> attrs-map
          (merge-prefix-map "ctx"  (get signal :ctx))
          (merge-prefix-map "data" (get signal :data))
          (merge-prefix-map "kvs"  (get signal :kvs))
          (enc/merge attr-kvs) ; Unprefixed, undocumented
          )]

    attrs-map))

(defn default-logger-provider
  "Experimental, subject to change. Feedback welcome!

  Returns `io.opentelemetry.api.logs.LoggerProvider` via:
    `AutoConfiguredOpenTelemetrySdk` when possible, or
    `GlobalOpenTelemetry` otherwise.

  See the relevant `opentelemetry-java` docs for details."
  ^LoggerProvider []
  (or
    ;; Without Java agent
    (enc/compile-when
      io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
      (enc/catching :common
        (let [builder (io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk/builder)]
          (.getSdkLoggerProvider (.getOpenTelemetrySdk (.build builder))))))

    ;; With Java agent
    (.getLogsBridge (GlobalOpenTelemetry/get))))

;;;; Handler

(defn handler:open-telemetry-logger
  "Experimental, subject to change. Feedback welcome!

  Needs `opentelemetry-java`,
    Ref. <https://github.com/open-telemetry/opentelemetry-java>.

  Returns a (fn handler [signal]) that:
    - Takes a Telemere signal (map).
    - Emits the signal to `io.opentelemetry.api.logs.Logger` returned
      by given `io.opentelemetry.api.logs.LoggerProvider`.

  Options:
    `:logger-provider` - `io.opentelemetry.api.logs.LoggerProvider`
      Defaults to the LoggerProvider returned by (default-logger-provider),
      see that docstring for details."

  ([] (handler:open-telemetry-logger nil))
  ([{:keys
     [^LoggerProvider logger-provider
      attrs-signal-key ; Advanced, undocumented
      ]

     :or
     {logger-provider (default-logger-provider)
      attrs-signal-key :open-telemetry/attrs}}]

   (let []
     (fn a-handler:open-telemetry-logger
       ([      ]) ; Stop => noop
       ([signal]
        (let [{:keys [ns inst level msg_]} signal
              logger    (.get logger-provider (or ns "default"))
              severity  (level->severity level)
              msg       (force msg_)
              attrs-map (signal->attrs-map attrs-signal-key signal)
              attrs     (as-attrs attrs-map)

              b (.logRecordBuilder logger)]

          (.setTimestamp     b inst)
          (.setSeverity      b severity)
          (.setAllAttributes b attrs)
          (when-let [body
                     (or msg
                       (when-let [error (get signal :error)]
                         (str (enc/ex-type error) ": " (enc/ex-message error))))]
            (.setBody b body))

          (.emit b)))))))

(comment
  (as-attrs
    (signal->attrs-map :my-attrs
      {:level :info :data {:ns/kw1 :v1 :ns/kw2 :v2}
       :my-attrs {:longs [1 1 2 3] :strs ["a" "b" "c"]}})))
