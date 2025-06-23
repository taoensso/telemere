(ns taoensso.telemere.slf4j
  "SLF4Jv2 -> Telemere interop.
  Telemere will attempt to load this ns automatically when possible.

  To use Telemere as your SLF4J backend/provider, just include the
  `com.taoensso/telemere-slf4j` dependency on your classpath.

  Implementation details,
  Ref. <https://www.slf4j.org/faq.html#slf4j_compatible>:

    - Libs  must include `org.slf4j/slf4j-api` dependency, but NO backend.

    - Users must include a single backend dependency of their choice
      (e.g. `com.taoensso/telemere-slf4j` or `org.slf4j/slf4j-simple`).

    - SLF4J uses standard `ServiceLoader` mechanism to find its logging backend,
      searches for `SLF4JServiceProvider` provider on classpath."

  {:author "Peter Taoussanis (@ptaoussanis)"}
  (:require
   [taoensso.truss         :as truss]
   [taoensso.encore        :as enc]
   [taoensso.telemere.impl :as impl])

  (:import
   [org.slf4j Logger]
   [com.taoensso.telemere.slf4j TelemereLogger]))

(comment (remove-ns (symbol (str *ns*))))

;;;; Utils

(defmacro ^:private when-debug [& body] (when #_true false `(do ~@body)))

(defn- sig-level
  "Returns `taoensso.encore.signals` level for given `org.slf4j.event.Level`."
  ;; Faster than switching on `org.slf4j.event.EventConstants` directly
  [^org.slf4j.event.Level level]
  (enc/case-eval  (.toInt level)
    org.slf4j.event.EventConstants/TRACE_INT :trace
    org.slf4j.event.EventConstants/DEBUG_INT :debug
    org.slf4j.event.EventConstants/INFO_INT  :info
    org.slf4j.event.EventConstants/WARN_INT  :warn
    org.slf4j.event.EventConstants/ERROR_INT :error
    (throw
      (ex-info "Unexpected `org.slf4j.event.Level`"
        {:level (enc/typed-val level)}))))

(comment (enc/qb 1e6 (sig-level org.slf4j.event.Level/INFO))) ; 36.47

(defn- get-marker "Private util for tests, etc."
  ^org.slf4j.Marker [n] (org.slf4j.MarkerFactory/getMarker n))

(defn- est-marker!
  "Private util for tests, etc.
  Globally establishes (compound) `org.slf4j.Marker` with name `n` and mutates it
  (all occurences!) to have exactly the given references. Returns the (compound) marker."
  ^org.slf4j.Marker [n & refs]
  (let [m (get-marker n)]
    (enc/reduce-iterator! (fn [_ in] (.remove m in)) nil (.iterator m))
    (doseq [n refs] (.add m (get-marker n)))
    m))

(comment [(est-marker! "a1" "a2") (get-marker  "a1") (= (get-marker "a1") (get-marker "a1"))])

(def ^:private marker-names
  "Returns #{<MarkerName>}. Cached => assumes markers NOT modified after creation."
  ;; We use `BasicMarkerFactory` so:
  ;;   1. Our markers are just labels (no other content besides their name).
  ;;   2. Markers with the same name are identical (enabling caching).
  (enc/fmemoize
    (fn marker-names [marker-or-markers]
      (if (instance? org.slf4j.Marker marker-or-markers)

        ;; Single marker
        (let [^org.slf4j.Marker m marker-or-markers
              acc #{(.getName m)}]

          (if-not (.hasReferences m)
            acc
            (enc/reduce-iterator!
              (fn [acc  ^org.slf4j.Marker in]
                (if-not   (.hasReferences in)
                  (conj acc (.getName     in))
                  (into acc (marker-names in))))
              acc (.iterator m))))

        ;; Vector of markers
        (reduce
          (fn [acc in] (into acc (marker-names in)))
          #{} (truss/have vector? marker-or-markers))))))

(comment
  (let [m1 (est-marker! "M1")
        m2 (est-marker! "M1")
        cm (est-marker! "Compound" "M1" "M2")
        ms [m1 m2]]

    (enc/qb 1e6 ; [45.52 47.48 44.85]
      (marker-names m1)
      (marker-names cm)
      (marker-names ms))))

;;;; Interop fns (called by `TelemereLogger`)

(defn- allowed?
  "Called by `com.taoensso.telemere.slf4j.TelemereLogger`."
  [logger-name level]
  (when-debug (println [:slf4j/allowed? (sig-level level) logger-name]))
  (impl/signal-allowed?
    {:ns    logger-name ; Typically source class name
     :kind  :slf4j
     :level (sig-level level)}))

(defn- normalized-log!
  [logger-name level inst error msg-pattern args marker-names kvs]
  (when-debug (println [:slf4j/normalized-log! (sig-level level) logger-name]))
  (impl/signal!
    {:allow? true ; Pre-filtered by `allowed?` call
     :ns     logger-name ; Typically source class name
     :kind   :slf4j
     :level  (sig-level level)
     :inst   inst
     :error  error

     :ctx+
     (when-let [hmap (org.slf4j.MDC/getCopyOfContextMap)]
       (clojure.lang.PersistentHashMap/create hmap))

     :msg
     (delay
       (org.slf4j.helpers.MessageFormatter/basicArrayFormat
         msg-pattern args))

     :data
     (enc/assoc-some nil
       :slf4j/marker-names marker-names
       :slf4j/args (when args (vec args))
       :slf4j/kvs  kvs)})
  nil)

(defn- log!
  "Called by `com.taoensso.telemere.slf4j.TelemereLogger`."

  ;; Modern "fluent" API calls
  ([logger-name ^org.slf4j.event.LoggingEvent event]
   (let [inst        (or (when-let [ts (.getTimeStamp event)] (java.time.Instant/ofEpochMilli ts)) (enc/now-inst*))
         level       (.getLevel     event)
         error       (.getThrowable event)
         msg-pattern (.getMessage   event)
         args        (when-let [args    (.getArgumentArray event)] args)
         markers     (when-let [markers (.getMarkers       event)] (marker-names (vec markers)))
         kvs         (when-let [kvps    (.getKeyValuePairs event)]
                       (reduce
                         (fn [acc ^org.slf4j.event.KeyValuePair kvp]
                           (assoc acc (.-key kvp) (.-value kvp)))
                         nil kvps))]

     (when-debug (println [:slf4j/fluent-log-call (sig-level level) logger-name]))
     (normalized-log! logger-name level inst error msg-pattern args markers kvs)))

  ;; Legacy API calls
  ([logger-name ^org.slf4j.event.Level level error msg-pattern args marker]
   (let [marker-names (when marker (marker-names marker))]
     (when-debug (println [:slf4j/legacy-log-call (sig-level level) logger-name]))
     (normalized-log! logger-name level (enc/now-inst*) error msg-pattern args marker-names nil))))

(comment
  (def ^org.slf4j.Logger sl (org.slf4j.LoggerFactory/getLogger "my.class"))
  (impl/with-signal (->          sl  (.info "Hello {}" "x")))
  (impl/with-signal (-> (.atInfo sl) (.log  "Hello {}" "x")))

  (do ; Will noop with `NOPMDCAdapter`
    (org.slf4j.MDC/put "key" "val")
    (org.slf4j.MDC/get "key")
    (org.slf4j.MDC/getCopyOfContextMap)
    (org.slf4j.MDC/clear)))

;;;;

(defn check-interop
  "Returns interop debug info map."
  []
  (let [^org.slf4j.Logger sl
        (org.slf4j.LoggerFactory/getLogger  "InteropTestTelemereLogger")
        sending? (instance? com.taoensso.telemere.slf4j.TelemereLogger sl)
        receiving?
        (and sending?
          (impl/test-interop! "SLF4J -> Telemere" #(.info sl %)))]

    {:present?                   true
     :telemere-provider-present? true
     :sending->telemere?  sending?
     :telemere-receiving? receiving?}))

(impl/add-interop-check! :slf4j check-interop)

(impl/on-init
  (impl/signal!
    {:kind  :event
     :level :debug ; < :info since runs on init
     :id    :taoensso.telemere/slf4j->telemere!
     :msg   "Enabling interop: SLF4J -> Telemere"}))
