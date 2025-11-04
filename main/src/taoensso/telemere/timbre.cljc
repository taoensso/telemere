(ns taoensso.telemere.timbre
  "Main Timbre macros, reimplemented on top of Telemere.
  Intended to help ease migration from Timbre to Telemere."
  (:require
   [clojure.string         :as str]
   [taoensso.truss         :as truss]
   [taoensso.encore        :as enc]
   [taoensso.telemere.impl :as impl]
   [taoensso.telemere      :as tel]))

(comment
  (remove-ns (symbol (str *ns*)))
  (:api (enc/interns-overview)))

(let [arg-str
      (fn [x]
        (enc/cond
          (nil?    x) "nil"
          (record? x) (pr-str x)
          :else               x))]

  (defn ^:no-doc parse-vargs
    "Private, don't use. Adapted from Timbre."
    [format-msg? vargs]
    (let [[v0]   vargs]

      (if (truss/error? v0)
        (let [error     v0
              vargs   (enc/vrest vargs)
              pattern (if format-msg? (let [[v0] vargs] v0) nil)
              vargs   (if format-msg? (enc/vrest vargs) vargs)
              msg
              (delay
                (if format-msg?
                  (enc/format* pattern            vargs)
                  (enc/str-join " " (map arg-str) vargs)))]

          [error msg {:vargs vargs}])

        (let [md      (if (and (map? v0) (get (meta v0) :meta)) v0 nil)
              error   (get    md :err)
              md      (dissoc md :err)
              vargs   (if     md (enc/vrest vargs) vargs)
              pattern (if format-msg? (let [[v0]   vargs] v0) nil)
              vargs   (if format-msg? (enc/vrest   vargs) vargs)
              msg
              (delay
                (if format-msg?
                  (enc/format* pattern            vargs)
                  (enc/str-join " " (map arg-str) vargs)))]

          [error msg (when-not (empty? vargs) {:vargs vargs})])))))

(comment
  (parse-vargs true [                   "hello %s" "stu"])
  (parse-vargs true [(Exception. "Ex1") "hello %s" "stu"]))

(def ^:no-doc ^:const shim-id :taoensso.telemere/timbre)

#?(:clj
   (defmacro ^:no-doc log!
     "Private, don't use."
     [level format-msg? vargs]
     (truss/keep-callsite
       `(when (impl/signal-allowed? {:kind :log, :level ~level, :id shim-id})
          (let [[error# msg# data#] (parse-vargs ~format-msg? ~vargs)]
            (tel/log!
              {:allow? true
               :level  ~level
               :id     shim-id
               :error  error#
               :data   data#}
              msg#)
            nil)))))

(comment
  (macroexpand    '(trace "foo"))
  (tel/with-signal (trace "foo"))
  (tel/with-signal (infof "Hello %s" "world")))

#?(:clj
   (do
     (defmacro log     "Prefer `telemere/log!`, etc." [level & args] (truss/keep-callsite `(log! ~level  false [~@args])))
     (defmacro trace   "Prefer `telemere/log!`, etc."       [& args] (truss/keep-callsite `(log! :trace  false [~@args])))
     (defmacro debug   "Prefer `telemere/log!`, etc."       [& args] (truss/keep-callsite `(log! :debug  false [~@args])))
     (defmacro info    "Prefer `telemere/log!`, etc."       [& args] (truss/keep-callsite `(log! :info   false [~@args])))
     (defmacro warn    "Prefer `telemere/log!`, etc."       [& args] (truss/keep-callsite `(log! :warn   false [~@args])))
     (defmacro error   "Prefer `telemere/log!`, etc."       [& args] (truss/keep-callsite `(log! :error  false [~@args])))
     (defmacro fatal   "Prefer `telemere/log!`, etc."       [& args] (truss/keep-callsite `(log! :fatal  false [~@args])))
     (defmacro report  "Prefer `telemere/log!`, etc."       [& args] (truss/keep-callsite `(log! :report false [~@args])))

     (defmacro logf    "Prefer `telemere/log!`, etc." [level & args] (truss/keep-callsite `(log! ~level  true  [~@args])))
     (defmacro tracef  "Prefer `telemere/log!`, etc."       [& args] (truss/keep-callsite `(log! :trace  true  [~@args])))
     (defmacro debugf  "Prefer `telemere/log!`, etc."       [& args] (truss/keep-callsite `(log! :debug  true  [~@args])))
     (defmacro infof   "Prefer `telemere/log!`, etc."       [& args] (truss/keep-callsite `(log! :info   true  [~@args])))
     (defmacro warnf   "Prefer `telemere/log!`, etc."       [& args] (truss/keep-callsite `(log! :warn   true  [~@args])))
     (defmacro errorf  "Prefer `telemere/log!`, etc."       [& args] (truss/keep-callsite `(log! :error  true  [~@args])))
     (defmacro fatalf  "Prefer `telemere/log!`, etc."       [& args] (truss/keep-callsite `(log! :fatal  true  [~@args])))
     (defmacro reportf "Prefer `telemere/log!`, etc."       [& args] (truss/keep-callsite `(log! :report true  [~@args])))))

#?(:clj
   (defmacro spy
     "Prefer `telemere/spy!`.

     Note that for extra flexibility and improved interop with Open Telemetry,
     this shim intentionally handles errors (forms that throw) slightly differently
     to Timbre's original `spy`:

       When the given `form` throws, this shim may create an ADDITIONAL signal of
       `:error` kind and level. The behaviour is equivalent to:

       (telemere/spy! level             ; Creates 0/1 `:spy`   signals with given/default (`:debug`) level
         (telemere/catch->error! form)) ; Creates 0/1 `:error` signals with `:error`                 level

     The additional signal helps to separate the success and error cases for
     individual filtering and/or handling."

     ([                form] (truss/keep-callsite `(spy :debug nil ~form)))
     ([level           form] (truss/keep-callsite `(spy ~level nil ~form)))
     ([level form-name form]
      (let [ns     (str *ns*)
            coords (truss/callsite-coords &form)
            msg
            (if form-name
              `(fn [_form# value# error# nsecs#] (impl/default-trace-msg  ~form-name value# error# nsecs#))
              `(fn [_form# value# error# nsecs#] (impl/default-trace-msg '~form      value# error# nsecs#)))]

        `(tel/spy!
           {:ns     ~ns
            :coords ~coords
            :id     shim-id
            :level  ~level
            :msg    ~msg}

           (tel/catch->error!
             {:ns     ~ns
              :coords ~coords
              :id     shim-id}
             ~form))))))

(comment
  (:level      (tel/with-signal (spy (/ 1 0))))
  (select-keys (tel/with-signal (spy :info #_"my-form-name" (+ 1 2)))                   [:level :msg_])
  (select-keys (tel/with-signal (spy :info #_"my-form-name" (throw (Exception. "Ex")))) [:level :msg_]))

#?(:clj (defmacro log-errors             "Prefer `telemere/catch->error!`." [& body] (truss/keep-callsite         `(tel/catch->error! {:id shim-id, :catch-val nil} (do ~@body)))))
#?(:clj (defmacro log-and-rethrow-errors "Prefer `telemere/catch->error!`." [& body] (truss/keep-callsite         `(tel/catch->error! {:id shim-id}                 (do ~@body)))))
#?(:clj (defmacro logged-future          "Prefer `telemere/catch->error!`." [& body] (truss/keep-callsite `(future (tel/catch->error! {:id shim-id}                 (do ~@body))))))

#?(:clj
   (defmacro refer-timbre
     "(require
        '[taoensso.telemere.timbre :as timbre :refer
          [log  trace  debug  info  warn  error  fatal  report
           logf tracef debugf infof warnf errorf fatalf reportf
           spy]])"
     []
     `(require
        '~'[taoensso.telemere.timbre :as timbre :refer
            [log  trace  debug  info  warn  error  fatal  report
             logf tracef debugf infof warnf errorf fatalf reportf
             spy]])))

;;;;

(defn set-min-level! "Prefer `telemere/set-min-level!`." [min-level] (tel/set-min-level! min-level))
#?(:clj
   (defmacro with-min-level
     "Prefer `telemere/with-min-level`."
     [min-level & body]
     `(tel/with-min-level ~min-level (do ~@body))))

#?(:clj
   (defmacro set-ns-min-level!
     "Prefer `telemere/set-min-level!`."
     ([   ?min-level] `(set-ns-min-level! ~(str *ns*) ~?min-level))
     ([ns ?min-level] `(tel/set-min-level! nil ~(str ns) ~?min-level))))

#?(:clj (defmacro with-context  "Prefer `telemere/with-ctx`."  [context & body] `(tel/with-ctx  ~context (do ~@body))))
#?(:clj (defmacro with-context+ "Prefer `telemere/with-ctx+`." [context & body] `(tel/with-ctx+ ~context (do ~@body))))

(defn shutdown-appenders!
  "Prefer `telemere/stop-handlers!`."
  [] (tel/stop-handlers!))

(defn timbre->telemere-appender
  "Returns a simple Timbre appender that'll redirect logs to Telemere."
  []
  {:enabled?  true
   :min-level nil
   :fn
   (fn [data]
     (let [{:keys [instant level context ?err output_
                   ?ns-str ?file ?line ?column]} data]

       (taoensso.telemere/signal!
         {:kind :timbre
          :level level
          :inst  (taoensso.encore/as-?inst instant)
          :ctx+  context

          :ns     ?ns-str
          :file   ?file
          :line   ?line
          :column ?column

          :error  ?err
          :msg    (force output_)})))})
