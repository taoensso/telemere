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
   #?(:default [taoensso.telemere.consoles :as consoles])
   #?(:clj     [taoensso.telemere.streams  :as streams])
   #?(:clj     [taoensso.telemere.files    :as files]))

  #?(:cljs
     (:require-macros
      [taoensso.telemere :refer
       [with-signal with-signals
        signal! event! log! trace! spy! catch->error!

        ;; Via `sigs/def-api`
        without-filters with-kind-filter with-ns-filter with-id-filter
        with-min-level with-handler with-handler+
        with-ctx with-ctx+ with-middleware]])))

(comment
  (remove-ns 'taoensso.telemere)
  (:api (enc/interns-overview)))

(enc/assert-min-encore-version [3 115 1])

;;;; TODO
;; - Solution and docs for lib authors
;; - Update Tufte  (signal API, config API, signal keys, etc.)
;; - Update Timbre (signal API, config API, signal keys, backport improvements)

;;;; Shared signal API

(sigs/def-api
  {:sf-arity 4
   :ct-sig-filter   impl/ct-sig-filter
   :*rt-sig-filter* impl/*rt-sig-filter*
   :*sig-handlers*  impl/*sig-handlers*})

;;;; Aliases

(enc/defaliases
  ;; Encore
  #?(:clj enc/set-var-root!)
  #?(:clj enc/update-var-root!)
  #?(:clj enc/get-env)
  #?(:clj enc/call-on-shutdown!)
  enc/chance
  enc/rate-limiter
  enc/newline
  enc/comp-middleware
  sigs/default-handler-dispatch-opts

  ;; Impl
  impl/msg-splice
  impl/msg-skip
  #?(:clj impl/with-signal)
  #?(:clj impl/with-signals)
  #?(:clj impl/signal!)

  ;; Utils
  utils/format-signal-fn
  utils/pr-signal-fn
  utils/error-signal?)

;;;; Help

(do
  (impl/defhelp help:signal-creators      :signal-creators)
  (impl/defhelp help:signal-options       :signal-options)
  (impl/defhelp help:signal-content       :signal-content)
  (impl/defhelp help:environmental-config :environmental-config))

;;;;

(def ^:dynamic *uid-fn*
  "Experimental, subject to change.
  (fn [root?]) used to generate signal `:uid` values when tracing.
  These are basically unique signal instance identifiers.

  `root?` argument is true iff signal is a top-level trace (i.e. form
  being traced is unnested = has no parent form).

  Root  uids typically have ~128 bits of entropy to ensure uniqueness.
  Child uids are typically used only with respect to a parent/root,
  and so can often make do with ~64 bits of entropy or less.

  Smaller uids are generally cheaper to generate, and use less space
  when serializing/transmitting/storing/etc.

  By default generates nano-style uids like
    \"r76-B8LoIPs5lBG1_Uhdy\" (root) and \"tMEYoZH0K-\" (non-root).

  For plain fixed-length UUIDs        use: (fn [_root?] (utils/uuid))
  For plain fixed-length UUID strings use: (fn [_root?] (utils/uuid-str))

  See also `utils/nano-uid-fn`, `utils/hex-id-fn`, etc."

  (utils/nano-uid-fn {:secure? false}))

(comment (enc/qb 1e6 (enc/uuid) (*uid-fn* true) (*uid-fn* false))) ; [168.83 79.02 62.95]

;;;; Signal creators
;; - event!           [id   ] [id   opts/level] ; id     + ?level => allowed? ; Sole signal with descending main arg!
;; - log!             [msg  ] [opts/level  msg] ; msg    + ?level => allowed?
;; - error!           [error] [opts/id   error] ; error  + ?id    => given error
;; - trace!           [form ] [opts/id    form] ; run    + ?id    => run result (value or throw)
;; - spy!             [form ] [opts/level form] ; run    + ?level => run result (value or throw)
;; - catch->error!    [form ] [opts/id    form] ; run    + ?id    => run value or ?return
;; - signal!          [opts ]                   ;                 => allowed? / run result (value or throw)
;; - uncaught->error! [opts/id]                 ;          ?id    => nil

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
  (with-signal (catch->error! {                  :msg ["Error:" __caught-error]} (/ 1 0)))
  (with-signal (catch->error! {:catch-sym my-err :msg ["Error:" my-err]}         (/ 1 0))))

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

;;;; Interop

#?(:clj
   (enc/defaliases
     impl/check-interop
     streams/with-out->telemere
     streams/with-err->telemere
     streams/with-streams->telemere
     streams/streams->telemere!
     streams/streams->reset!))

(comment (check-interop))

;;;; Handlers

(enc/defaliases
  #?(:default consoles/handler:console)
  #?(:cljs    consoles/handler:console-raw)
  #?(:clj     files/handler:file))

;;;; Init

(impl/on-init

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
         :data     data})))

  (add-handler! :default/console (handler:console))

  #?(:clj (enc/catching (require '[taoensso.telemere.tools-logging])))
  #?(:clj (enc/catching (require '[taoensso.telemere.slf4j]))))

;;;; Flow benchmarks

(comment
  {:last-updated    "2024-08-15"
   :system          "2020 Macbook Pro M1, 16 GB memory"
   :clojure-version "1.12.0-rc1"
   :java-version    "OpenJDK 22"}

  [(binding [impl/*sig-handlers* nil]
     (enc/qb 1e6 ; [9.31 16.76 264.12 350.43]
       (signal! {:level :info, :run nil, :elide? true }) ; 9
       (signal! {:level :info, :run nil, :allow? false}) ; 17
       (signal! {:level :info, :run nil, :allow? true }) ; 264
       (signal! {:level :info, :run nil               }) ; 350
       ))

   (binding [impl/*sig-handlers* nil]
     (enc/qb 1e6 ; [8.34 15.78 999.27 444.08 1078.83]
       (signal! {:level :info, :run "run", :elide? true }) ; 8
       (signal! {:level :info, :run "run", :allow? false}) ; 16
       (signal! {:level :info, :run "run", :allow? true }) ; 1000
       (signal! {:level :info, :run "run", :trace? false}) ; 444
       (signal! {:level :info, :run "run"               }) ; 1079
       ))

   ;; For README "performance" table
   (binding [impl/*sig-handlers* nil]
     (enc/qb [8 1e6] ; [9.34 347.7 447.71 1086.65]
       (signal! {:level :info, :elide? true             }) ; 9
       (signal! {:level :info                           }) ; 348
       (signal! {:level :info, :run "run", :trace? false}) ; 448
       (signal! {:level :info, :run "run"               }) ; 1087
       ))])

;;;;

(comment
  (with-handler :hid1 (handler:console) {} (log! "Message"))

  (let [sig
        (with-signal
          (event! ::ev-id
            {:data  {:a :A :b :b}
             :error
             (ex-info "Ex2" {:b :B}
               (ex-info "Ex1" {:a :A}))}))]

    (do      (let [hf (handler:file)]        (hf sig) (hf)))
    (do      (let [hf (handler:console)]     (hf sig) (hf)))
    #?(:cljs (let [hf (handler:console-raw)] (hf sig) (hf)))))

(comment (let [[_ [s1 s2]] (with-signals (trace! ::id1 (trace! ::id2 "form2")))] s1))
