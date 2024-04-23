(ns taoensso.telemere
  "Structured telemetry for Clojure/Script applications.

  See the GitHub page (esp. Wiki) for info on motivation and design:
    <https://www.taoensso.com/telemere>"

  {:author "Peter Taoussanis (@ptaoussanis)"}
  (:refer-clojure :exclude [binding newline])
  (:require
   [taoensso.encore         :as enc :refer [binding have have?]]
   [taoensso.encore.signals :as sigs]
   [taoensso.telemere.impl  :as impl]
   [taoensso.telemere.utils :as utils]
   #?(:clj [taoensso.telemere.streams :as streams])

   #?(:default [taoensso.telemere.handlers.console :as handlers:console])
   #?(:clj     [taoensso.telemere.handlers.file    :as handlers:file]))

  #?(:cljs
     (:require-macros
      [taoensso.telemere :refer
       [set-ctx! with-ctx with-ctx+
        set-middleware! with-middleware

        with-signal with-signals
        signal! event! log! trace! spy! catch->error!

        ;; Via `sigs/def-api`
        without-filters with-kind-filter with-ns-filter with-id-filter
        with-min-level with-handler with-handler+]])))

(comment
  (remove-ns 'taoensso.telemere)
  (:api (enc/interns-overview)))

(enc/assert-min-encore-version [3 103 0])

;;;; TODO
;; - Add email handler
;; - Native OpenTelemetry traces and spans
;; - Update Tufte  (signal API, config API, signal keys, etc.)
;; - Update Timbre (signal API, config API, signal keys, backport improvements)

;;;; Shared signal API

(sigs/def-api
  {:purpose  "signal"
   :sf-arity 4
   :ct-sig-filter   impl/ct-sig-filter
   :*rt-sig-filter* impl/*rt-sig-filter*
   :*sig-handlers*  impl/*sig-handlers*
   :sig-filter-system-vals-info
   "These include:

    Compile-time:

      ns-filter: (get-env {:as :edn} :taoensso.telemere/ct-ns-filter<.platform><.edn>)
      id-filter: (get-env {:as :edn} :taoensso.telemere/ct-id-filter<.platform><.edn>)
      min-level: (get-env {:as :edn} :taoensso.telemere/ct-min-level<.platform><.edn>)

    Runtime:

      ns-filter: (get-env {:as :edn}                 :taoensso.telemere/rt-ns-filter<.platform><.edn>)
      id-filter: (get-env {:as :edn}                 :taoensso.telemere/rt-id-filter<.platform><.edn>)
      min-level: (get-env {:as :edn, :default :info} :taoensso.telemere/rt-min-level<.platform><.edn>)

    See `get-env` for details."})

(comment help:filters)

(comment
  [level-aliases

   help:handlers get-handlers add-handler! remove-handler!
   with-handler with-handler+ shut-down-handlers!

   help:filtering get-filters get-min-level
   set-kind-filter! set-ns-filter! set-id-filter! set-min-level!
   with-kind-filter with-ns-filter with-id-filter with-min-level])

;;;; Aliases

(enc/defaliases
  #?(:clj enc/set-var-root!)
  #?(:clj enc/update-var-root!)
  #?(:clj enc/get-env)
  enc/chance
  enc/rate-limiter
  enc/newline

  impl/msg-splice
  impl/msg-skip
  #?(:clj impl/with-signal)
  #?(:clj impl/with-signals)
  #?(:clj impl/signal!)
  utils/error-signal?)

;;;; Help

(impl/defhelp help:signal-creators   :signal-creators)
(impl/defhelp help:signal-options    :signal-options)
(impl/defhelp help:signal-flow       :signal-flow)
(impl/defhelp help:signal-content    :signal-content)
(enc/defalias help:signal-filters    help:filters)  ; Via Encore
(enc/defalias help:signal-handlers   help:handlers) ; Via Encore
(impl/defhelp help:signal-formatters :signal-formatters)

;;;; Context

(enc/defonce default-ctx
  "Default root (base) value of `*ctx*` var.
  Defaults to `nil`, controlled by:
    (get-env {:as :edn} :taoensso.telemere/default-ctx<.platform><.edn>)

  See `get-env` for details."
  (enc/get-env {:as :edn} :taoensso.telemere/default-ctx<.platform><.edn>))

(enc/def* ^:dynamic *ctx*
  "Dynamic context: arbitrary user-level state attached as `:ctx` to all signals.
  Value may be any type, but is usually nil or a map.

  Re/bind dynamic     value using `with-ctx`, `with-ctx+`, or `binding`.
  Modify  root (base) value using `set-ctx!`.
  Default root (base) value is    `default-ctx`.

  Note that as with all dynamic Clojure vars, \"binding conveyance\" applies
  when using futures, agents, etc.

  Tips:
    - Value may be (or may contain) an atom if you want mutable semantics
    - Value may be of form {<scope-id> <data>} for custom scoping, etc."
  default-ctx)

#?(:clj
   (defmacro set-ctx!
     "Set `*ctx*` var's root (base) value. See `*ctx*` for details."
     [root-val] `(enc/set-var-root! *ctx* ~root-val)))

#?(:clj
   (defmacro with-ctx
     "Evaluates given form with given `*ctx*` value. See `*ctx*` for details."
     [init-val form] `(binding [*ctx* ~init-val] ~form)))

(comment (with-ctx "my-ctx" *ctx*))

#?(:clj
   (defmacro with-ctx+
     "Evaluates given form with updated `*ctx*` value.

     `update-map-or-fn` may be:
       - A map to merge with    current `*ctx*` value, or
       - A unary fn to apply to current `*ctx*` value

     See `*ctx*` for details."
     [update-map-or-fn form]
     `(binding [*ctx* (impl/update-ctx *ctx* ~update-map-or-fn)]
        ~form)))

(comment (with-ctx {:a :A1 :b :B1} (with-ctx+ {:a :A2} *ctx*)))

;;;; Middleware

(enc/defonce ^:dynamic *middleware*
  "Optional vector of unary middleware fns to apply (sequentially/left-to-right)
  to each signal before passing it to handlers. If any middleware fn returns nil,
  aborts immediately without calling handlers.

  Useful for transforming each signal before handling.

  Re/bind dynamic     value using `with-middleware`, `binding`.
  Modify  root (base) value using `set-middleware!`."
  nil)

#?(:clj
   (defmacro set-middleware!
     "Set `*middleware*` var's root (base) value. See `*middleware*` for details."
     [root-val] `(enc/set-var-root! *middleware* ~root-val)))

#?(:clj
   (defmacro with-middleware
     "Evaluates given form with given `*middleware*` value.
     See `*middleware*` for details."
     [init-val form] `(binding [*middleware* ~init-val] ~form)))

;;;; Encore integration

(do
  (enc/set-var-root! sigs/*default-handler-error-fn*
    (fn [{:keys [error] :as m}]
      (impl/signal!
        {:kind     :error
         :level    :error
         :error     error
         :location {:ns "taoensso.encore.signals"}
         :id            :taoensso.encore.signals/handler-error
         :msg      "Error executing wrapped handler fn"
         :data     (dissoc m :error)})))

  (enc/set-var-root! sigs/*default-handler-backp-fn*
    (fn [data]
      (impl/signal!
        {:kind     :event
         :level    :warn
         :location {:ns "taoensso.encore.signals"}
         :id            :taoensso.encore.signals/handler-back-pressure
         :msg      "Back pressure on wrapped handler fn"
         :data     data}))))

;;;; Signal creators
;; - signal!                  [              opts] ;                 => allowed? / run result (value or throw)
;; - event!           [id   ] [id   level-or-opts] ; id     + ?level => allowed? ; Sole signal with descending main arg!
;; - log!             [msg  ] [level-or-opts  msg] ; msg    + ?level => allowed?
;; - error!           [error] [id-or-opts   error] ; error  + ?id    => given error
;; - trace!           [form ] [id-or-opts    form] ; run    + ?id    => run result (value or throw)
;; - spy!             [form ] [level-or-opts form] ; run    + ?level => run result (value or throw)
;; - catch->error!    [form ] [id-or-opts    form] ; run    + ?id    => run value or ?return
;; - uncaught->error! [     ] [id-or-opts        ] ;          ?id    => nil

#?(:clj
   (defmacro event!
     "[id] [id level-or-opts] => allowed?"
     {:doc      (impl/signal-docstring :event!)
      :arglists (impl/signal-arglists  :event!)}
     [& args]
     (let [opts (impl/signal-opts `event! {:kind :event, :level :info} :id :level :dsc args)]
       (enc/keep-callsite `(impl/signal! ~opts)))))

(comment (with-signal (event! ::my-id :info)))

#?(:clj
   (defmacro log!
     "[msg] [level-or-opts msg] => allowed?"
     {:doc      (impl/signal-docstring :log!)
      :arglists (impl/signal-arglists  :log!)}
     [& args]
     (let [opts (impl/signal-opts `log! {:kind :log, :level :info} :msg :level :asc args)]
       (enc/keep-callsite `(impl/signal! ~opts)))))

(comment (with-signal (log! :info "My msg")))

#?(:clj
   (defmacro error!
     "[error] [error id-or-opts] => error"
     {:doc      (impl/signal-docstring :error!)
      :arglists (impl/signal-arglists  :error!)}
     [& args]
     (let [opts (impl/signal-opts `error! {:kind :error, :level :error} :error :id :asc args)
           error-form (get opts :error)]

       (enc/keep-callsite
         `(let [~'__error ~error-form]
            (impl/signal! ~(assoc opts :error '__error))
            ~'__error ; Unconditional!
            )))))

(comment (with-signal (throw (error! ::my-id (ex-info "MyEx" {})))))

#?(:clj
   (defmacro catch->error!
     "[form] [id-or-opts form] => run value or ?catch-val"
     {:doc      (impl/signal-docstring :catch-to-error!)
      :arglists (impl/signal-arglists  :catch->error!)}
     [& args]
     (let [opts     (impl/signal-opts `catch->error! {:kind :error, :level :error} ::__form :id :asc args)
           rethrow? (if (contains? opts :catch-val) false (get opts :rethrow? true))
           catch-val    (get       opts :catch-val)
           catch-sym    (get       opts :catch-sym '__caught-error) ; Undocumented
           form         (get       opts ::__form)
           opts         (dissoc    opts ::__form :catch-val :catch-sym :rethrow?)]

       (enc/keep-callsite
         `(enc/try* ~form
            (catch :all ~catch-sym
              (impl/signal! ~(assoc opts :error catch-sym))
              (if ~rethrow? (throw ~catch-sym) ~catch-val)))))))

(comment
  (with-signal (catch->error! ::my-id (/ 1 0)))
  (with-signal (catch->error! {                  :msg_ ["Error:" __caught-error]} (/ 1 0)))
  (with-signal (catch->error! {:catch-sym my-err :msg_ ["Error:" my-err]}         (/ 1 0))))

#?(:clj
   (defmacro trace!
     "[form] [id-or-opts form] => run result (value or throw)"
     {:doc      (impl/signal-docstring :trace!)
      :arglists (impl/signal-arglists  :trace!)}
     [& args]
     (let [opts
           (impl/signal-opts `trace!
             {:location (enc/get-source &form &env) ; For catch-opts
              :kind :trace, :level :info, :msg `impl/default-trace-msg}
             :run :id :asc args)

           ;; :catch->error <id-or-opts> currently undocumented
           [opts catch-opts] (impl/signal-catch-opts opts)]

       (if catch-opts
         (enc/keep-callsite `(catch->error! ~catch-opts (impl/signal! ~opts)))
         (enc/keep-callsite                            `(impl/signal! ~opts))))))

(comment
  (with-signal (trace! ::my-id (+ 1 2)))
  (let [[_ [s1 s2]]
        (with-signals
          (trace! {:id :id1, :catch->error :id2}
            (throw (ex-info "Ex1" {}))))]
    [s2]))

#?(:clj
   (defmacro spy!
     "[form] [level-or-opts form] => run result (value or throw)"
     {:doc      (impl/signal-docstring :spy!)
      :arglists (impl/signal-arglists  :spy!)}
     [& args]
     (let [opts
           (impl/signal-opts `spy!
             {:location (enc/get-source &form &env) ; For catch-opts
              :kind :spy, :level :info, :msg `impl/default-trace-msg}
             :run :level :asc args)

           ;; :catch->error <id-or-opts> currently undocumented
           [opts catch-opts] (impl/signal-catch-opts opts)]

       (if catch-opts
         (enc/keep-callsite `(catch->error! ~catch-opts (impl/signal! ~opts)))
         (enc/keep-callsite                            `(impl/signal! ~opts))))))

(comment (with-signal :force (spy! :info (+ 1 2))))

#?(:clj
   (defmacro uncaught->error!
     "Uses `uncaught->handler!` so that `error!` will be called for
     uncaught JVM errors.

     See `uncaught->handler!` and `error!` for details."
     {:arglists (impl/signal-arglists :uncaught->error!)}
     [& args]
     (let [msg-form ["Uncaught Throwable on thread: " `(.getName ~(with-meta '__thread {:tag 'java.lang.Thread}))]
           opts
           (impl/signal-opts `uncaught->error!
             {:kind :error, :level :error, :msg msg-form}
             :error :id :dsc (into ['__throwable] args))]

       (enc/keep-callsite
         `(uncaught->handler!
            (fn [~'__thread ~'__throwable]
              (impl/signal! ~opts)))))))

(comment (macroexpand '(uncaught->error! ::my-id)))

#?(:clj
   (defn uncaught->handler!
     "Sets JVM's global `DefaultUncaughtExceptionHandler` to given
       (fn handler [`<java.lang.Thread>` `<java.lang.Throwable>`]).

     See also `uncaught->error!`."
     [handler]
     (Thread/setDefaultUncaughtExceptionHandler
       (reify   Thread$UncaughtExceptionHandler
         (uncaughtException [_ thread throwable]
           (handler            thread throwable))))
     nil))

;;;; Intake

#?(:clj
   (enc/defaliases
     impl/check-intakes
     streams/with-out->telemere
     streams/with-err->telemere
     streams/with-streams->telemere
     streams/streams->telemere!
     streams/streams->reset!))

(comment (check-intakes))

;;;; Handlers

(enc/defaliases
  #?(:default handlers:console/handler:console)
  #?(:cljs    handlers:console/handler:console-raw)
  #?(:clj        handlers:file/handler:file))

;;;; Flow benchmarks

(comment
  {:last-updated    "2024-02-12"
   :system          "2020 Macbook Pro M1, 16 GB memory"
   :clojure-version "1.11.1"
   :java-version    "OpenJDK 21"}

  [(binding [impl/*sig-handlers* nil]
     (enc/qb 1e6 ; [10.4 17.06 195.42 200.34]
       (signal! {:level :info, :run nil, :elide? true})
       (signal! {:level :info, :run nil, :allow? false})
       (signal! {:level :info, :run nil, :allow? true })
       (signal! {:level :info, :run nil})))

   (binding [impl/*sig-handlers* nil]
     (enc/qb 1e6 ; [8.1 15.35 647.82 279.67 682.1]
       (signal! {:level :info, :run "run", :elide? true})
       (signal! {:level :info, :run "run", :allow? false})
       (signal! {:level :info, :run "run", :allow? true })
       (signal! {:level :info, :run "run", :trace? false})
       (signal! {:level :info, :run "run"})))

   ;; For README "performance" table
   (binding [impl/*sig-handlers* nil]
     (enc/qb [8 1e6] ; [9.23 197.2 277.55 649.32]
       (signal! {:level :info, :elide? true})
       (signal! {:level :info})
       (signal! {:level :info, :run "run", :trace? false})
       (signal! {:level :info, :run "run"})))])

;;;;

(impl/on-init
  (when impl/auto-handlers?
    (add-handler! :default/console (handler:console)))

  #?(:clj (enc/catching (require '[taoensso.telemere.tools-logging])))
  #?(:clj (enc/catching (require '[taoensso.telemere.slf4j])))
  #?(:clj (enc/catching (require '[taoensso.telemere.handlers.open-telemetry]))))

;;;;

(comment
  (with-handler :hid1 (handlers/console-handler) {} (log! "Message"))

  (let [sig
        (with-signal
          (event! ::ev-id
            {:data  {:a :A :b :b}
             :error
             (ex-info "Ex2" {:b :B}
               (ex-info "Ex1" {:a :A}))}))]

    (do      (let [hf (handlers/file-handler)]        (hf sig) (hf)))
    (do      (let [hf (handlers/console-handler)]     (hf sig) (hf)))
    #?(:cljs (let [hf (handlers/raw-console-handler)] (hf sig) (hf)))))
