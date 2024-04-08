(ns taoensso.telemere.timbre
  "Main Timbre macros, reimplemented on top of Telemere.
  Intended to help ease migration from Timbre to Telemere."
  (:require
   [clojure.string         :as str]
   [taoensso.encore        :as enc :refer [have have?]]
   [taoensso.telemere.impl :as impl]
   [taoensso.telemere      :as tel]))

(comment
  (remove-ns 'taoensso.telemere.timbre)
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
    (let [[v0] vargs]

      (if (enc/error? v0)
        (let [error   v0
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
              error   (get md :err)
              md      (dissoc md :err)
              vargs   (if md (enc/vrest vargs) vargs)
              pattern (if format-msg? (let [[v0] vargs] v0) nil)
              vargs   (if format-msg? (enc/vrest vargs) vargs)
              msg
              (delay
                (if format-msg?
                  (enc/format* pattern            vargs)
                  (enc/str-join " " (map arg-str) vargs)))]

          [error msg {:vargs vargs}])))))

(comment
  (parse-vargs true [                   "hello %s" "stu"])
  (parse-vargs true [(Exception. "Ex1") "hello %s" "stu"]))

(def ^:no-doc ^:const shim-id :taoensso.telemere/timbre)

#?(:clj
   (defmacro ^:no-doc log!
     "Private, don't use."
     [level format-msg? vargs]
     (enc/keep-callsite
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
  (macroexpand               '(trace "foo"))
  (tel/with-signal :force-msg (trace "foo"))
  (tel/with-signal :force-msg (infof "Hello %s" "world")))

#?(:clj
   (do
     (defmacro log     "Prefer `telemere/log!`, etc." [level & args] (enc/keep-callsite `(log! ~level  false [~@args])))
     (defmacro trace   "Prefer `telemere/log!`, etc."       [& args] (enc/keep-callsite `(log! :trace  false [~@args])))
     (defmacro debug   "Prefer `telemere/log!`, etc."       [& args] (enc/keep-callsite `(log! :debug  false [~@args])))
     (defmacro info    "Prefer `telemere/log!`, etc."       [& args] (enc/keep-callsite `(log! :info   false [~@args])))
     (defmacro warn    "Prefer `telemere/log!`, etc."       [& args] (enc/keep-callsite `(log! :warn   false [~@args])))
     (defmacro error   "Prefer `telemere/log!`, etc."       [& args] (enc/keep-callsite `(log! :error  false [~@args])))
     (defmacro fatal   "Prefer `telemere/log!`, etc."       [& args] (enc/keep-callsite `(log! :fatal  false [~@args])))
     (defmacro report  "Prefer `telemere/log!`, etc."       [& args] (enc/keep-callsite `(log! :report false [~@args])))

     (defmacro logf    "Prefer `telemere/log!`, etc." [level & args] (enc/keep-callsite `(log! ~level  true  [~@args])))
     (defmacro tracef  "Prefer `telemere/log!`, etc."       [& args] (enc/keep-callsite `(log! :trace  true  [~@args])))
     (defmacro debugf  "Prefer `telemere/log!`, etc."       [& args] (enc/keep-callsite `(log! :debug  true  [~@args])))
     (defmacro infof   "Prefer `telemere/log!`, etc."       [& args] (enc/keep-callsite `(log! :info   true  [~@args])))
     (defmacro warnf   "Prefer `telemere/log!`, etc."       [& args] (enc/keep-callsite `(log! :warn   true  [~@args])))
     (defmacro errorf  "Prefer `telemere/log!`, etc."       [& args] (enc/keep-callsite `(log! :error  true  [~@args])))
     (defmacro fatalf  "Prefer `telemere/log!`, etc."       [& args] (enc/keep-callsite `(log! :fatal  true  [~@args])))
     (defmacro reportf "Prefer `telemere/log!`, etc."       [& args] (enc/keep-callsite `(log! :report true  [~@args])))))

#?(:clj
   (defmacro spy!
     "Prefer `telemere/spy!`."
     ([                form] (enc/keep-callsite `(spy! :debug nil ~form)))
     ([level           form] (enc/keep-callsite `(spy! ~level nil ~form)))
     ([level form-name form]
      (let [msg
            (if-not form-name
              `impl/default-trace-msg
              `(fn [_form# value# error# nsecs#]
                 (impl/default-trace-msg ~form-name value# error# nsecs#)))]

        (enc/keep-callsite
          `(tel/spy!
             {:kind  :spy
              :level ~level
              :id    shim-id
              :msg   ~msg
              :catch->error true}
             ~form))))))

(comment
  (select-keys (tel/with-signal :force-msg (spy! :info "my-form-name" (+ 1 2)))                   [:level   :msg_])
  (select-keys (tel/with-signal :force-msg (spy! :info "my-form-name" (throw (Exception. "Ex")))) [:level #_:msg_]))

#?(:clj (defmacro log-errors             "Prefer `telemere/catch->error!`." [& body] (enc/keep-callsite         `(tel/catch->error! {:id shim-id, :catch-val nil} (do ~@body)))))
#?(:clj (defmacro log-and-rethrow-errors "Prefer `telemere/catch->error!`." [& body] (enc/keep-callsite         `(tel/catch->error! {:id shim-id}                 (do ~@body)))))
#?(:clj (defmacro logged-future          "Prefer `telemere/catch->error!`." [& body] (enc/keep-callsite `(future (tel/catch->error! {:id shim-id}                 (do ~@body))))))

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
