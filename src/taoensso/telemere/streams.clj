(ns ^:no-doc taoensso.telemere.streams
  "Private ns, implementation detail.
  Intake support: standard stream/s -> Telemere."
  (:refer-clojure :exclude [binding])
  (:require
   [taoensso.encore        :as enc :refer [binding have have?]]
   [taoensso.telemere.impl :as impl]))

(enc/defonce           orig-*out* "Original `*out*` on ns load" *out*)
(enc/defonce           orig-*err* "Original `*err*` on ns load" *err*)
(enc/defonce ^:dynamic prev-*out* "Previous `*out*` (prior to any Telemere binds)" nil)
(enc/defonce ^:dynamic prev-*err* "Previous `*err*` (prior to any Telemere binds)" nil)

(def ^:private ^:const default-out-opts {:kind :system/out, :level :info})
(def ^:private ^:const default-err-opts {:kind :system/err, :level :error})

(defn osw ^java.io.OutputStreamWriter [x] (java.io.OutputStreamWriter. x))

(defn telemere-print-stream
  "Returns a `java.io.PrintStream` that will flush to Telemere signals with given opts."
  ^java.io.PrintStream [{:as sig-opts :keys [kind level id]}]
  (let [baos
        (proxy [java.io.ByteArrayOutputStream] []
          (flush []
            (let [^java.io.ByteArrayOutputStream this this]
              (proxy-super flush)
              (let [msg (.trim (.toString this))]
                (proxy-super reset)

                (when-not (.isEmpty msg)
                  (binding [*out* (or prev-*out* orig-*out*)
                            *err* (or prev-*err* orig-*err*)]

                    (impl/signal!
                      {:location nil
                       :ns       nil
                       :kind     kind
                       :level    level
                       :id       id
                       :msg      msg})))))))]

    (java.io.PrintStream. baos true ; Auto flush
      java.nio.charset.StandardCharsets/UTF_8)))

(defmacro ^:public with-out->telemere
  "Executes form with `*out*` bound to flush to Telemere signals with given opts."
  ([     form] `(with-out->telemere nil ~form))
  ([opts form]
   `(binding [prev-*out* (or prev-*out* *out*)
              *out* (osw (telemere-print-stream ~(conj default-out-opts opts)))]
      ~form)))

(defmacro ^:public with-err->telemere
  "Executes form with `*err*` bound to flush to Telemere signals with given opts."
  ([     form] `(with-err->telemere nil ~form))
  ([opts form]
   `(binding [prev-*err* (or prev-*err* *err*)
              *err* (osw (telemere-print-stream ~(conj default-err-opts opts)))]
      ~form)))

(defmacro ^:public with-streams->telemere
  "Executes form with `*out*` and/or `*err*` bound to flush to Telemere signals
  with given opts."
  ([form] `(with-streams->telemere nil ~form))
  ([{:keys [out err]
     :or   {out default-out-opts
            err default-err-opts}} form]

   `(binding [prev-*out* (or prev-*out* *out*)
              prev-*err* (or prev-*err* *err*)
              *out* (if-let [out# ~out] (osw (telemere-print-stream out#)) *out*)
              *err* (if-let [err# ~err] (osw (telemere-print-stream err#)) *err*)]
      ~form)))

(comment (impl/with-signal (with-out->telemere (println "hello"))))

(enc/defonce ^:private orig-out_ "Original `System/out`, or nil" (atom nil))
(enc/defonce ^:private orig-err_ "Original `System/err`, or nil" (atom nil))

(let [monitor (Object.)]

  (defn ^:public streams->reset!
    "Experimental, subject to change without notice!
    Resets `System/out` and `System/err` to their original value (prior to any
    `streams->telemere!` call)."
    []
    (let [[orig-out _] (reset-vals! orig-out_ nil)
          [orig-err _] (reset-vals! orig-err_ nil)]

      (impl/signal!
        {:kind  :event
         :level :info
         :id    :taoensso.telemere/streams->telemere!
         :msg   "Disabling intake: standard stream/s -> Telemere"
         :data  {:system/out? (boolean orig-out)
                 :system/err? (boolean orig-err)}})

      (locking monitor
        (when orig-out (System/setOut orig-out))
        (when orig-err (System/setErr orig-err)))

      (boolean (or orig-out orig-err))))

  (defn ^:public streams->telemere!
    "Experimental, subject to change without notice!

    When given `out`, sets JVM's `System/out` to flush to Telemere signals with those opts.
    When given `err`, sets JVM's `System/err` to flush to Telemere signals with those opts.

    Note that setting `System/out` won't necessarily affect Clojure's `*out*`,
    and       setting `System/err` won't necessarily affect Clojure's `*err*`.

    See also:
      `with-out->telemere`,
      `with-err->telemere`,
      `with-streams->telemere`."

    ([] (streams->telemere! nil))
    ([{:keys [out err]
       :or   {out default-out-opts
              err default-err-opts}}]

     (when (or out err)
       (let [out (when out (telemere-print-stream out))
             err (when err (telemere-print-stream err))]

         (impl/signal!
           {:kind  :event
            :level :info
            :id    :taoensso.telemere/streams->telemere!
            :msg   "Enabling intake: standard stream/s -> Telemere"
            :data  {:system/out? (boolean out)
                    :system/err? (boolean err)}})

         (locking monitor
           (when out (compare-and-set! orig-out_ nil System/out) (System/setOut out))
           (when err (compare-and-set! orig-err_ nil System/err) (System/setErr err)))

         true)))))

(comment
  (streams->telemere?)
  (streams->telemere! {})
  (streams->reset!))

(impl/add-intake-check! :system/out
  (fn []
    (let [sending?   (boolean @orig-out_)
          receiving? (and  sending? (impl/test-intake! "`System/out` -> Telemere" #(.println System/out %)))]
      {:sending->telemere? sending?, :telemere-receiving? receiving?})))

(impl/add-intake-check! :system/err
  (fn []
    (let [sending?   (boolean @orig-err_)
          receiving? (and  sending? (impl/test-intake! "`System/err` -> Telemere" #(.println System/err %)))]
      {:sending->telemere? sending?, :telemere-receiving? receiving?})))
