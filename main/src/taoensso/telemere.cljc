(ns taoensso.telemere
  "Structured telemetry for Clojure/Script applications.

  See the GitHub page (esp. Wiki) for info on motivation and design:
    <https://www.taoensso.com/telemere>"

  {:author "Peter Taoussanis (@ptaoussanis)"}
  (:refer-clojure :exclude [newline])
  (:require
   [taoensso.truss          :as truss]
   [taoensso.encore         :as enc]
   [taoensso.encore.signals :as sigs]
   [taoensso.telemere.impl  :as impl]
   [taoensso.telemere.utils :as utils]
   #?(:default [taoensso.telemere.consoles :as consoles])
   #?(:clj     [taoensso.telemere.streams  :as streams])
   #?(:clj     [taoensso.telemere.files    :as files]))

  #?(:cljs
     (:require-macros
      [taoensso.telemere :refer
       [with-signal with-signals signal-allowed?
        signal! event! log! trace! spy! catch->error!

        ;; Via `sigs/def-api`
        without-filters with-kind-filter with-ns-filter with-id-filter
        with-min-level with-handler with-handler+
        with-ctx with-ctx+ with-middleware with-middleware+]])))

(comment
  (remove-ns (symbol (str *ns*)))
  (:api (enc/interns-overview)))

(enc/assert-min-encore-version [3 137 5])

;;;; Shared signal API

(sigs/def-api
  {:sf-arity 4
   :ct-call-filter   impl/ct-call-filter
   :*rt-call-filter* impl/*rt-call-filter*
   :*sig-handlers*   impl/*sig-handlers*
   :lib-dispatch-opts
   (assoc sigs/default-handler-dispatch-opts
     :convey-bindings? false ; Handled manually
     )})

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
  truss/keep-callsite

  ;; Impl
  impl/msg-splice
  impl/msg-skip
  #?(:clj impl/with-signal)
  #?(:clj impl/with-signals)

  ;; Utils
  utils/clean-signal-fn
  utils/format-signal-fn
  utils/pr-signal-fn
  utils/error-signal?)

;;;; Help

(do
  (impl/defhelp help:signal-creators      :signal-creators)
  (impl/defhelp help:signal-options       :signal-options)
  (impl/defhelp help:signal-content       :signal-content)
  (impl/defhelp help:environmental-config :environmental-config))

;;;; Unique ids

(def ^:dynamic *uid-fn*
  "Experimental, subject to change. Feedback welcome!
  (fn [root?]) used to generate signal `:uid` values (unique instance ids)
  when tracing.

  Relevant only when `otel-tracing?` is false.
  If `otel-tracing?` is true, uids are instead generated by `*otel-tracer*`.

  `root?` argument is true iff signal is a top-level trace (i.e. form being
  traced is unnested = has no parent form). Root-level uids typically need
  more entropy and so are usually longer (e.g. 32 vs 16 hex chars).

  Override default by setting one of the following:
    1.       JVM property: `taoensso.telemere.uid-kind`
    2.       Env variable: `TAOENSSO_TELEMERE_UID_KIND`
    3. Classpath resource: `taoensso.telemere.uid-kind`

    Possible (compile-time) values include:
      `:uuid`          - UUID string (Cljs) or `java.util.UUID` (Clj)
      `:uuid-str`      - UUID string       (36/36 chars)
      `:nano/secure`   - nano-style string (21/10 chars) w/ strong RNG
      `:nano/insecure` - nano-style string (21/10 chars) w/ fast   RNG (default)
      `:hex/insecure`  - hex-style  string (32/16 chars) w/ strong RNG
      `:hex/secure`    - hex-style  string (32/16 chars) w/ fast   RNG"

  (utils/parse-uid-fn impl/uid-kind))

(comment (enc/qb 1e6 (*uid-fn* true) (*uid-fn* false))) ; [79.4 63.53]

;;;; OpenTelemetry

#?(:clj
   (def otel-tracing?
     "Experimental, subject to change. Feedback welcome!

     Should Telemere's tracing signal creators (`trace!`, `spy!`, etc.)
     interop with OpenTelemetry Java [1]? This will affect relevant
     Telemere macro expansions.

     Defaults to `true` iff OpenTelemetry Java is present when this
     namespace is evaluated/compiled.

     If `false`:
       1. Telemere's   OpenTelemetry handler will NOT emit to `SpanExporter`s.
       2. Telemere and OpenTelemetry will NOT recognize each other's spans.

     If `true`:
       1. Telemere's   OpenTelemetry handler WILL emit to `SpanExporter`s.
       2. Telemere and OpenTelemetry WILL recognize each other's spans.

     Override default by setting one of the following to \"true\" or \"false\":
       1.       JVM property: `taoensso.telemere.otel-tracing`
       2.       Env variable: `TAOENSSO_TELEMERE_OTEL_TRACING`
       3. Classpath resource: `taoensso.telemere.otel-tracing`

     See also: `otel-default-providers_`, `*otel-tracer*`,
       `taoensso.telemere.open-telemere/handler:open-telemetry`.

     [1] Ref. <https://github.com/open-telemetry/opentelemetry-java>"
     impl/enabled:otel-tracing?))

#?(:clj
   (def otel-default-providers_
     "Experimental, subject to change. Feedback welcome!

     When OpenTelemetry Java API [1] is present, value will be a delayed map
     with keys:
       :logger-provider     - default `io.opentelemetry.api.logs.LoggerProvider`
       :tracer-provider     - default `io.opentelemetry.api.trace.TracerProvider`
       :via                 - ∈ #{:sdk-extension-autoconfigure :global}
       :auto-configured-sdk - `io.opentelemetry.sdk.OpenTelemetrySdk` or nil

     Uses `AutoConfiguredOpenTelemetrySdk` when possible, or
     `GlobalOpenTelemetry` otherwise.

     See the relevant OpenTelemetry Java docs for details.

     [1] Ref. <https://github.com/open-telemetry/opentelemetry-java>"
     (enc/compile-when impl/present:otel?
       (delay
         (or
           ;; Via SDK autoconfiguration extension (when available)
           (enc/compile-when
             io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
             (truss/catching :common
               (let [builder (io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk/builder)
                     sdk    (.getOpenTelemetrySdk (.build builder))]
                 {:logger-provider (.getLogsBridge     sdk)
                  :tracer-provider (.getTracerProvider sdk)
                  :via :sdk-extension-autoconfigure
                  :auto-configured-sdk sdk})))

           ;; Via Global (generally not recommended)
           (let [g (io.opentelemetry.api.GlobalOpenTelemetry/get)]
             {:logger-provider (.getLogsBridge     g)
              :tracer-provider (.getTracerProvider g)
              :via :global}))))))

#?(:clj
   (def ^:dynamic *otel-tracer*
     "Experimental, subject to change. Feedback welcome!

     OpenTelemetry `Tracer` to use for Telemere's tracing signal creators
     (`trace!`, `span!`, etc.), ∈ #{nil io.opentelemetry.api.trace.Tracer Delay}.

     Defaults to the provider in `otel-default-providers_`.
     See also `otel-tracing?`."
     (enc/compile-when impl/enabled:otel-tracing?
       (delay
         (when-let [^io.opentelemetry.api.trace.TracerProvider p
                    (get (force otel-default-providers_) :tracer-provider)]
           (do #_impl/viable-tracer (.get p "Telemere")))))))

(comment (enc/qb 1e6 (force *otel-tracer*))) ; 51.23

;;;; Signal creators
;; - log! ------------- ?level + msg    => nil
;; - event! ----------- id     + ?level => nil
;; - trace! ----------- ?id    + run    => run result (value or throw)
;; - spy! ------------- ?level + run    => run result (value or throw)
;; - error! ----------- ?id    + error  => given error
;; - catch->error! ---- ?id    + run    => run value or ?catch-val
;; - uncaught->error! - ?id             => nil
;; - signal! ---------- opts            => allowed? / run result (value or throw)

#?(:clj
   (defn- args->opts [args]
     (case     (count args)
       0 {}
       1 (impl/valid-opts! (first args))
       (apply hash-map args))))

#?(:clj
   (defmacro signal-allowed?
     "Returns true iff signal with given opts would meet filtering conditions:
       (when (signal-allowed? {:level :warn, <...>}) (my-custom-code))

      Allows you to use Telemere's rich filtering system for conditionally
      executing arbitrary code. Also handy for batching multiple signals
      under a single set of conditions (incl. rate-limiting, sampling, etc.):

        ;; Logs exactly 2 or 0 messages (never 1):
        (when (signal-allowed? {:level :info, :sample-rate 0.5})
          (log! {:allow? true} \"Message 1\")
          (log! {:allow? true} \"Message 2\"))"

     ;; Used also for interop (tools.logging, SLF4J), etc.
     {:arglists (impl/signal-arglists :signal-allowed?)}
     [& args] `(impl/signal-allowed? ~(args->opts args))))

(comment (macroexpand '(signal-allowed? {:ns "my-ns"})))

#?(:clj
   (defmacro signal!
     "opts => allowed? / unconditional run result (value or throw)."
     {:doc      (impl/signal-docstring :signal!)
      :arglists (impl/signal-arglists  :signal!)}
     [& args]
     (truss/keep-callsite
       `(impl/signal! ~(args->opts args)))))

(comment (:coords (macroexpand '(with-signal (signal!)))))

#?(:clj
   (defn- merge-or-assoc-opts [m macro-form k v]
     (let [m (assoc m :coords (truss/callsite-coords macro-form))]
       (if  (map? v)
         (merge m v)
         (assoc m k v)))))

#?(:clj
   (let [base-opts {:kind :log, :level :info}]
     (defmacro log!?
       "?level + msg => allowed?"
       {:doc      (impl/signal-docstring :log!)
        :arglists (impl/signal-arglists  :log!)}
       ([opts-or-msg      ] `(impl/signal!        ~(merge-or-assoc-opts base-opts &form :msg   opts-or-msg)))
       ([opts-or-level msg] `(impl/signal! ~(assoc (merge-or-assoc-opts base-opts &form :level opts-or-level) :msg msg))))))

(comment (:coords (with-signal (log!? :info "My msg"))))

#?(:clj
   (defmacro log!
     "Like `log!?` but always returns nil."
     {:doc      (impl/signal-docstring :log!)
      :arglists (impl/signal-arglists  :log!)}
     [& args] `(do ~(truss/keep-callsite `(log!? ~@args)) nil)))

(comment (:coords (with-signal (log! :info "My msg"))))

#?(:clj
   (let [base-opts {:kind :event, :level :info}]
     (defmacro event!?
       "id + ?level => allowed? Note unique arg order: [x opts] rather than [opts x]!"
       {:doc      (impl/signal-docstring :event!)
        :arglists (impl/signal-arglists  :event!)}
       ([   opts-or-id]    `(impl/signal!        ~(merge-or-assoc-opts base-opts &form :id    opts-or-id)))
       ([id opts-or-level] `(impl/signal! ~(assoc (merge-or-assoc-opts base-opts &form :level opts-or-level) :id id))))))

(comment (:coords (with-signal (event!? ::my-id :info))))

#?(:clj
   (defmacro event!
     "Like `event!?` but always returns nil."
     {:doc      (impl/signal-docstring :event!)
      :arglists (impl/signal-arglists  :event!)}
     [& args] `(do ~(truss/keep-callsite `(event!? ~@args)) nil)))

(comment (:coords (with-signal (event! ::my-id :info))))

#?(:clj
   (let [base-opts {:kind :trace, :level :info, :msg `impl/default-trace-msg}]
     (defmacro trace!
       "?id + run => unconditional run result (value or throw)."
       {:doc      (impl/signal-docstring :trace!)
        :arglists (impl/signal-arglists  :trace!)}
       ([opts-or-run]    `(impl/signal!        ~(merge-or-assoc-opts base-opts &form :run opts-or-run)))
       ([opts-or-id run] `(impl/signal! ~(assoc (merge-or-assoc-opts base-opts &form :id  opts-or-id) :run run))))))

(comment (:coords (with-signal (trace! ::my-id (+ 1 2)))))

#?(:clj
   (let [base-opts {:kind :spy, :level :info, :msg `impl/default-trace-msg}]
     (defmacro spy!
       "?level + run => unconditional run result (value or throw)."
       {:doc      (impl/signal-docstring :spy!)
        :arglists (impl/signal-arglists  :spy!)}
       ([opts-or-run]       `(impl/signal!        ~(merge-or-assoc-opts base-opts &form :run   opts-or-run)))
       ([opts-or-level run] `(impl/signal! ~(assoc (merge-or-assoc-opts base-opts &form :level opts-or-level) :run run))))))

(comment (with-signals (spy! :info (+ 1 2))))

#?(:clj
   (let [base-opts {:kind :error, :level :error}]
     (defmacro error!
       "?id + error => unconditional given error."
       {:doc      (impl/signal-docstring :error!)
        :arglists (impl/signal-arglists  :error!)}
       ([opts-or-id error] `(error! ~(assoc (merge-or-assoc-opts base-opts &form :id opts-or-id) :error error)))
       ([opts-or-error]
        (let [opts     (merge-or-assoc-opts base-opts &form :error opts-or-error)
              gs-error (gensym "error")]

          `(let [~gs-error ~(get opts :error)]
             (impl/signal! ~(assoc opts :error gs-error))
             ~gs-error))))))

(comment (:coords (with-signal (throw (error! ::my-id (truss/ex-info "MyEx" {}))))))

#?(:clj
   (let [base-opts {:kind :error, :level :error}]
     (defmacro catch->error!
       "?id + run => unconditional run value or ?catch-val."
       {:doc      (impl/signal-docstring :catch->error!)
        :arglists (impl/signal-arglists  :catch->error!)}
       ([opts-or-id run] `(catch->error! ~(assoc (merge-or-assoc-opts base-opts &form :id opts-or-id) :run run)))
       ([opts-or-run]
        (let [opts      (merge-or-assoc-opts base-opts &form :run opts-or-run)
              rethrow?  (not (contains? opts :catch-val))
              catch-val      (get       opts :catch-val)
              run-form       (get       opts :run)
              opts           (dissoc    opts :run :catch-val)
              gs-caught      (gensym "caught")]

          `(truss/try* ~run-form
             (catch :all ~gs-caught
               (impl/signal! ~(assoc opts :error gs-caught))
               (if ~rethrow? (throw ~gs-caught) ~catch-val))))))))

(comment (:coords (with-signal (catch->error! ::my-id (/ 1 0)))))

#?(:clj
   (defn uncaught->handler!
     "Sets JVM's global `DefaultUncaughtExceptionHandler` to given
       (fn handler [`<java.lang.Thread>` `<java.lang.Throwable>`]).

     See also `uncaught->error!`."
     [handler]
     (Thread/setDefaultUncaughtExceptionHandler
       (when handler ; falsey to remove
         (reify   Thread$UncaughtExceptionHandler
           (uncaughtException [_ thread throwable]
             (handler            thread throwable)))))
     nil))

#?(:clj
   (let [base-opts
         {:kind :error, :level :error,
          :msg   `["Uncaught Throwable on thread:" (.getName ~(with-meta '__thread-arg {:tag 'java.lang.Thread}))]
          :error '__throwable-arg}]

     (defmacro uncaught->error!
       "Uses `uncaught->handler!` so that `error!` will be called for
       uncaught JVM errors.

       See `uncaught->handler!` and `error!` for details."
       {:arglists  (impl/signal-arglists :uncaught->error!)}
       ([          ] (truss/keep-callsite `(uncaught->error! {})))
       ([opts-or-id]
        (let [opts (merge-or-assoc-opts base-opts &form :id opts-or-id)]
          `(uncaught->handler!
             (fn [~'__thread-arg ~'__throwable-arg]
               (impl/signal! ~opts))))))))

(comment
  (macroexpand '(uncaught->error! ::uncaught))
  (do
    (uncaught->error! ::uncaught)
    (enc/threaded :user (/ 1 0))))

;;;;

(defn dispatch-signal!
  "Dispatches given signal to registered handlers, supports `with-signal/s`.
  Normally called automatically (internally) by signal creators, this util
  is provided publicly since it's also handy for manually re/dispatching
  custom/modified signals, etc.:

    (let [original-signal (with-signal :trap (event! ::my-id1))
          modified-signal (assoc original-signal :id ::my-id2)]
      (dispatch-signal! modified-signal))"

  [signal]
  (when-let [wrapped-signal (impl/wrap-signal signal)]
    (impl/dispatch-signal! wrapped-signal)))

(comment (dispatch-signal! (assoc (with-signal :trap (log! "hello")) :level :warn)))

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
  #?(:clj        files/handler:file))

;;;; Init

(impl/on-init

  (enc/set-var-root! sigs/*default-handler-error-fn*
    (fn [{:keys [error] :as m}]
      (impl/signal!
        {:kind  :error
         :level :error
         :error  error
         :ns    "taoensso.encore.signals"
         :id    :taoensso.encore.signals/handler-error
         :msg   "Error executing wrapped handler fn"
         :data  (dissoc m :error)})))

  (enc/set-var-root! sigs/*default-handler-backp-fn*
    (fn [data]
      (impl/signal!
        {:kind  :event
         :level :warn
         :ns    "taoensso.encore.signals"
         :id    :taoensso.encore.signals/handler-back-pressure
         :msg   "Back pressure on wrapped handler fn"
         :data  data})))

  (add-handler! :default/console (handler:console) {:async nil})

  #?(:clj (truss/catching (require '[taoensso.telemere.tools-logging])))  ;    TL->Telemere
  #?(:clj (truss/catching (require '[taoensso.telemere.slf4j])))          ; SLF4J->Telemere
  #?(:clj (truss/catching (require '[taoensso.telemere.open-telemetry]))) ; Telemere->OTel
  )

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
       ))

   ;; Full bench to handled signals
   ;;   Sync           => 4240.6846 (~4.2m/sec)
   ;;   Async dropping => 2421.9176 (~2.4m/sec)
   (let [runtime-msecs 5000
         n-procs (.availableProcessors (Runtime/getRuntime))
         fp (enc/future-pool n-procs)
         c  (java.util.concurrent.atomic.AtomicLong. 0)
         p  (promise)]

     (with-handler ::bench (fn [_] (.incrementAndGet c))
       {:async nil} ; Sync
       #_{:async {:mode :dropping, :n-threads n-procs}}
       (let [t (enc/after-timeout runtime-msecs (deliver p (.get c)))]
         (dotimes [_ n-procs]
           (fp (fn [] (dotimes [_ 6e6] (signal! {:level :info})))))

         (/ (double @p) (double runtime-msecs)))))])

;;;;

(comment
  (with-handler :hid1 (handler:console) {} (log! "Message"))

  (let [sig
        (with-signal
          (event! ::ev-id
            {:data  {:a :A :b :b}
             :error
             (truss/ex-info   "Ex2" {:b :B}
               (truss/ex-info "Ex1" {:a :A}))}))]

    (do      (let [hf (handler:file)]        (hf sig) (hf)))
    (do      (let [hf (handler:console)]     (hf sig) (hf)))
    #?(:cljs (let [hf (handler:console-raw)] (hf sig) (hf)))))

(comment (let [{[s1 s2] :signals} (with-signals (trace! ::id1 (trace! ::id2 "form2")))] s1))
