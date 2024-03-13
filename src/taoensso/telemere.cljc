(ns taoensso.telemere
  "Structured telemetry for Clojure/Script applications.

  See the GitHub page (esp. Wiki) for info on motivation and design:
    <https://www.taoensso.com/telemere>"

  {:author "Peter Taoussanis (@ptaoussanis)"}
  (:refer-clojure :exclude [newline])
  (:require
   [taoensso.encore         :as enc :refer [have have?]]
   [taoensso.encore.signals :as sigs]
   [taoensso.telemere.impl  :as impl]
   #?(:clj [taoensso.telemere.streams     :as streams])
   #?(:clj [clj-commons.format.exceptions :as fmt-ex])
   #?(:clj [clj-commons.ansi              :as fmt-ansi])))

(comment
  (remove-ns 'taoensso.telemere)
  (:api (enc/interns-overview)))

(enc/assert-min-encore-version [3 91 0])

;;;; Roadmap
;; x Fundamentals
;; x Basic logging utils
;; x Interop: SLF4J
;; x Interop: `clojure.tools.logging`
;; - Core logging handlers
;; - First docs, intro video
;; - First OpenTelemetry tools
;; - Update Tufte  (signal API, config API, signal fields, etc.)
;; - Update Timbre (signal API, config API, signal fields, backport improvements)

;;;; TODO
;; - Via Timbre: core handlers, any last utils?
;;   - Cljs (.log js/console <js/Error>) better than string stacktrace (clickable, etc.)
;;
;; - Tests for utils (hostname, formatters, etc.)?
;; - Remaining docstrings and TODOs
;; - Kinds: #{:log :spy :trace :event :error :system/out :system/err <user>}
;; - General polish
;;
;; - Reading plan
;; - Recheck `ensso/telemere-draft.cljc`
;; - Cleanup `ensso/telemere-drafts.txt`
;;
;; - Decide on module/import/alias/project approach
;; - Initial README, wiki docs, etc.
;; - Explainer/demo video

;;;; Shared signal API

(sigs/def-api
  {:purpose  "signal"
   :sf-arity 4
   :*sig-handlers*  impl/*sig-handlers*
   :*rt-sig-filter* impl/*rt-sig-filter*})

(comment
  [level-aliases]
  [help:handlers get-handlers add-handler! remove-handler! with-handler with-handler+]
  [help:filtering get-filters get-min-level
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
  #?(:clj impl/with-signals)
  #?(:clj impl/with-signal)
  #?(:clj impl/signal!))

;;;; Signal help

(comment help:filters help:handlers) ; Via Encore

(impl/defhelp help:signal-handling :signal-handling)
(impl/defhelp help:signal-content  :signal-content)
(impl/defhelp help:signal-options  :signal-options)

;;;; Context

(enc/defonce default-ctx
  "Advanced feature. Default root (base) value of `*ctx*` var, controlled by:
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

;;;; Common signals
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

(comment
  (with-signal (event! ::my-id))
  (with-signal (event! ::my-id :warn))
  (with-signal
    (event! ::my-id
      {:let  [x "x"] ; Available to `:data` and `:msg`
       :data {:x x}
       :msg  ["My msg:" x]})))

#?(:clj
   (defmacro log!
     "[msg] [level-or-opts msg] => allowed?"
     {:doc      (impl/signal-docstring :log!)
      :arglists (impl/signal-arglists  :log!)}
     [& args]
     (let [opts (impl/signal-opts `log! {:kind :log, :level :info} :msg :level :asc args)]
       (enc/keep-callsite `(impl/signal! ~opts)))))

(comment
  (with-signal (log! "My msg"))
  (with-signal (log! :warn "My msg"))
  (with-signal
    (log!
      {:let  [x "x"] ; Available to `:data` and `:msg`
       :data {:x x}}
      ["My msg:" x])))

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

(comment
  (with-signal (throw (error!         (ex-info "MyEx" {}))))
  (with-signal (throw (error! ::my-id (ex-info "MyEx" {}))))
  (with-signal
    (throw
      (error!
        {:let  [x "x"] ; Available to `:data` and `:msg`
         :data {:x x}
         :msg  ["My msg:" x]}
        (ex-info "MyEx" {})))))

#?(:clj
   (defmacro trace!
     "[form] [id-or-opts form] => run result (value or throw)"
     {:doc      (impl/signal-docstring :trace!)
      :arglists (impl/signal-arglists  :trace!)}
     [& args]
     (let [opts (impl/signal-opts `trace! {:kind :trace, :level :info, :msg ::impl/spy} :run :id :asc args)]
       (enc/keep-callsite `(impl/signal! ~opts)))))

(comment
  (with-signal (trace! (+ 1 2)))
  (with-signal (trace! ::my-id (+ 1 2)))
  (with-signal
    (trace!
      {:let  [x "x"] ; Available to `:data` and `:msg`
       :data {:x x}}
      (+ 1 2))))

#?(:clj
   (defmacro spy!
     "[form] [level-or-opts form] => run result (value or throw)"
     {:doc      (impl/signal-docstring :spy!)
      :arglists (impl/signal-arglists  :spy!)}
     [& args]
     (let [opts (impl/signal-opts `spy! {:kind :spy, :level :info, :msg ::impl/spy} :run :level :asc args)]
       (enc/keep-callsite `(impl/signal! ~opts)))))

(comment
  (with-signal (spy! (+ 1 2)))
  (with-signal (spy! ::my-id (+ 1 2)))
  (with-signal
    (spy!
      {:let  [x "x"] ; Available to `:data` and `:msg`
       :data {:x x}}
      (+ 1 2))))

#?(:clj
   (defmacro catch->error!
     "[form] [id-or-opts form] => run value or ?catch-val"
     {:doc      (impl/signal-docstring :catch-to-error!)
      :arglists (impl/signal-arglists  :catch->error!)}
     [& args]
     (let [opts     (impl/signal-opts `catch->error! {:kind :error, :level :error} ::__form :id :asc args)
           rethrow? (if (contains? opts :catch-val) false (get opts :rethrow?))
           catch-val    (get       opts :catch-val)
           form         (get       opts ::__form)
           opts         (dissoc    opts ::__form :catch-val :rethrow?)]

       (enc/keep-callsite
         `(enc/try* ~form
            (catch :any ~'__caught-error
              (impl/signal! ~(assoc opts :error '__caught-error))
              (if ~rethrow? (throw ~'__caught-error) ~catch-val)))))))

(comment
  (with-signal (catch->error! (/ 1 0)))
  (with-signal (catch->error! {:id ::my-id, :catch-val "threw"} (/ 1 0)))
  (with-signal
    (catch->error!
      {:let  [x "x"] ; Available to `:data` and `:msg`
       :data {:x x}
       :msg_ ["My msg:" x __caught-error]}
     (/ 1 0))))

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

(comment (macroexpand '(uncaught->error! :id1)))

;;;; Utils

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

#?(:clj
   (defn hostname
     "Returns local cached hostname string, or `timeout-val` (default \"UnknownHost\")."
     (^String [                         ] (enc/get-hostname (enc/msecs :mins 1) 5000 "UnknownHost"))
     (        [timeout-msecs timeout-val] (enc/get-hostname (enc/msecs :mins 1) timeout-msecs timeout-val))))

(comment (enc/qb 1e6 (hostname))) ; 76.64

#?(:clj (defn thread-name ^String [] (.getName (Thread/currentThread))))
#?(:clj (defn thread-id   ^String [] (.getId   (Thread/currentThread))))

(defn format-instant
  "TODO Docstring"
  {:tag #?(:clj 'String :cljs 'string)}
  ([instant] (format-instant nil instant))

  #?(:cljs
     ([{:keys [format]} instant]
      (if format ; `goog.i18n.DateTimeFormat`
        (.format format instant)
        (.toISOString   instant)))

     :clj
     ([{:keys [formatter]
        :or   {formatter java.time.format.DateTimeFormatter/ISO_INSTANT}}
       instant]
      (.format
        ^java.time.format.DateTimeFormatter formatter
        ^java.time.Instant                  instant))))

(comment (format-instant (enc/now-inst)))

(defn format-error
  "TODO Docstring"
  {:tag #?(:clj 'String :cljs 'string)}
  ([error] (format-error nil error))

  #?(:cljs
     ([_ error]
      (let [nl newline]
        (str
          (or
            (.-stack    error) ; Incl. `ex-message` content
            (ex-message error))
          (when-let [data  (ex-data  error)] (str nl    "ex-data:"   nl "    " (pr-str       data)))
          (when-let [cause (ex-cause error)] (str nl nl "Caused by:" nl        (format-error cause))))))

     :clj
     ;; TODO Review API, esp. re: *color-enabled*, etc.
     ([{:keys [use-fonts? sort-stacktrace-by fonts]
        :or
        {use-fonts?         :auto
         sort-stacktrace-by :chronological #_:depth-first
         fonts clj-commons.format.exceptions/default-fonts}}
       error]

      (binding [fmt-ansi/*color-enabled*
                (if (enc/identical-kw? use-fonts? :auto)
                  nil ; => Guess based on environment
                  use-fonts?)

                fmt-ex/*fonts* fonts
                fmt-ex/*traditional*
                (case sort-stacktrace-by
                  :depth-first   true  ; Traditional
                  :chronological false ; Modern (default)
                  (enc/unexpected-arg! sort-stacktrace-by
                    {:context  `format-error
                     :param    'sort-stacktrace-by
                     :expected #{:depth-first :chronological}}))]

        (fmt-ex/format-exception error)))))

(comment (println (format-error (ex-info "Ex2" {:k2 :v2} (ex-info "Ex1" {:k1 :v1})))))

;;;; Interop

(enc/defaliases impl/check-interop)
#?(:clj
   (enc/defaliases
     streams/with-out->telemere
     streams/with-err->telemere
     streams/with-streams->telemere
     streams/streams->telemere!
     streams/streams->reset!))

#?(:clj
   (enc/compile-when
     (do (require '[taoensso.telemere.tools-logging :as ttl]) true)
     (enc/defaliases ttl/tools-logging->telemere!)
     (when (enc/get-env {:as :bool} :clojure.tools.logging->telemere?)
       (ttl/tools-logging->telemere!))))

#?(:clj
   (enc/compile-when
     (and org.slf4j.Logger com.taoensso.telemere.slf4j.TelemereLogger)

     (impl/signal!
       {:kind  :event
        :level :info
        :id    :taoensso.telemere/slf4j->telemere!
        :msg   "Enabling interop: SLF4J -> Telemere"})

     (require '[taoensso.telemere.slf4j :as slf4j])))

(comment (check-interop))

;;;; Flow benchmarks

(comment
  {:last-updated    "2024-02-12"
   :system          "2020 Macbook Pro M1, 16 GB memory"
   :clojure-version "1.11.1"
   :java-version    "OpenJDK 21"}

  (binding [impl/*sig-handlers* nil]

    [(enc/qb 1e6 ; [9.26 16.85 187.3 202.7]
       (signal! {:level :info, :run nil, :elide? true})
       (signal! {:level :info, :run nil, :allow? false})
       (signal! {:level :info, :run nil, :allow? true })
       (signal! {:level :info, :run nil}))

     (enc/qb 1e6 ; [8.09 15.29 677.91 278.57 688.89]
       (signal! {:level :info, :run "run", :elide? true})
       (signal! {:level :info, :run "run", :allow? false})
       (signal! {:level :info, :run "run", :allow? true })
       (signal! {:level :info, :run "run", :trace? false})
       (signal! {:level :info, :run "run"}))

     ;; For README "performance" table
     (enc/qb [8 1e6] ; [9.23 220.27 300.83 726.07]
       (signal! {:level :info, :elide? true})
       (signal! {:level :info})
       (signal! {:level :info, :run "run", :trace? false})
       (signal! {:level :info, :run "run"}))]))
