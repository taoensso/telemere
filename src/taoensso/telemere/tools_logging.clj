(ns ^:no-doc taoensso.telemere.tools-logging
  "Private ns, implementation detail.
  Interop support: `clojure.tools.logging` -> Telemere."
  (:require
   [taoensso.encore        :as enc :refer [have have?]]
   [taoensso.telemere.impl :as impl]
   [clojure.tools.logging  :as ctl]))

(defmacro ^:private when-debug [& body] (when #_true false `(do ~@body)))

(deftype TelemereLogger [logger-ns]

  clojure.tools.logging.impl/Logger
  (enabled? [_ level]
    (when-debug (println [:tools.logger/enabled? logger-ns level]))
    (impl/signal-allowed?
      {:location nil
       :ns       nil
       :kind     :log
       :id       :taoensso.telemere/tools-logging
       :level    level}))

  (write! [_ level throwable message]
    (when-debug (println [:tools.logger/write! logger-ns level]))
    (impl/signal!
      {:allow?   true ; Pre-filtered by `enabled?` call
       :location nil
       :ns       nil
       :kind     :log
       :id       :taoensso.telemere/tools-logging
       :level    level
       :instant  :auto
       :error    throwable
       :msg      message})
    nil))

(deftype TelemereLoggerFactory []
  clojure.tools.logging.impl/LoggerFactory
  (name       [_          ] "taoensso.telemere")
  (get-logger [_ logger-ns] (TelemereLogger. (str logger-ns))))

(defn ^:public tools-logging->telemere!
  "Configures `clojure.tools.logging` to use Telemere as its logging implementation."
  []

  (impl/signal!
    {:kind  :event
     :level :info
     :id    :taoensso.telemere/clojure.tools.logging->telemere!
     :msg  "Enabling interop: `clojure.tools.logging` -> Telemere"})

  (alter-var-root #'clojure.tools.logging/*logger-factory*
    (fn [_] (TelemereLoggerFactory.))))

(defn tools-logging-factory    [] (TelemereLoggerFactory.))
(defn tools-logging->telemere? []
  (when-let [lf clojure.tools.logging/*logger-factory*]
    (instance? TelemereLoggerFactory lf)))

(impl/add-interop-check! :tools-logging
  (fn []
    (let [sending? (tools-logging->telemere?)
          receiving?
          (and sending?
            (impl/test-interop! "`clojure.tools.logging` -> Telemere"
              #(clojure.tools.logging/info %)))]

      {:present?            true
       :sending->telemere?  sending?
       :telemere-receiving? receiving?})))
