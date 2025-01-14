(ns taoensso.telemere.tools-logging
  "Interop support for tools.logging -> Telemere.
  Telemere will attempt to load this ns automatically when possible.

  Naming conventions:
    `tools.logging`         - For referring to the library.
    `tools-logging`         - For symbols, keywords, and this namespace.
    `clojure.tools.logging` - For env config to match library's conventions."

  (:require
   [taoensso.encore        :as enc :refer [have have?]]
   [taoensso.telemere.impl :as impl]
   [clojure.tools.logging  :as ctl]))

(defmacro ^:private when-debug [& body] (when #_true false `(do ~@body)))

(deftype TelemereLogger [logger-name]
  ;; `logger-name` is typically ns string
  clojure.tools.logging.impl/Logger
  (enabled? [_ level]
    (when-debug (println [:tools-logging/enabled? level logger-name]))
    (impl/signal-allowed?
      {:location {:ns logger-name}
       :kind     :tools-logging
       :level    level}))

  (write! [_ level throwable message]
    (when-debug (println [:tools-logging/write! level logger-name]))
    (impl/signal!
      {:allow?   true ; Pre-filtered by `enabled?` call
       :location {:ns logger-name}
       :kind     :tools-logging
       :level    level
       :error    throwable
       :msg      message})
    nil))

(deftype TelemereLoggerFactory []
  clojure.tools.logging.impl/LoggerFactory
  (name       [_            ] "taoensso.telemere")
  (get-logger [_ logger-name] (TelemereLogger. (str logger-name))))

(defn tools-logging->telemere!
  "Configures tools.logging to use Telemere as its logging
  implementation (backend).

  Called automatically if one of the following is \"true\":
    1.       JVM property: `clojure.tools.logging.to-telemere`
    2.       Env variable: `CLOJURE_TOOLS_LOGGING_TO_TELEMERE`
    3. Classpath resource: `clojure.tools.logging.to-telemere`"
  []
  (impl/signal!
    {:kind  :event
     :level :debug ; < :info since runs on init
     :id    :taoensso.telemere/tools-logging->telemere!
     :msg   "Enabling interop: tools.logging -> Telemere"})

  (alter-var-root #'clojure.tools.logging/*logger-factory*
    (fn [_] (TelemereLoggerFactory.))))

(defn tools-logging->telemere?
  "Returns true iff tools.logging is configured to use Telemere
  as its logging implementation (backend)."
  []
  (when-let [lf clojure.tools.logging/*logger-factory*]
    (instance? TelemereLoggerFactory lf)))

;;;;

(defn check-interop
  "Returns interop debug info map."
  []
  (let [sending? (tools-logging->telemere?)
        receiving?
        (and sending?
          (impl/test-interop! "tools.logging -> Telemere"
            #(clojure.tools.logging/info %)))]

    {:present?            true
     :enabled-by-env?     impl/enabled:tools-logging?
     :sending->telemere?  sending?
     :telemere-receiving? receiving?}))

(impl/add-interop-check! :tools-logging check-interop)

(impl/on-init
  (when impl/enabled:tools-logging?
    (tools-logging->telemere!)))
