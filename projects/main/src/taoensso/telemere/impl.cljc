(ns ^:no-doc taoensso.telemere.impl
  "Private ns, implementation detail.
  Signal design shared by: Telemere, Tufte, Timbre."
  (:refer-clojure :exclude [binding])
  (:require
   [clojure.set             :as set]
   [taoensso.encore         :as enc :refer [binding have have?]]
   [taoensso.encore.signals :as sigs])

  #?(:cljs
     (:require-macros
      [taoensso.telemere.impl :refer [with-signal]])))

(comment
  (remove-ns 'taoensso.telemere.impl)
  (:api (enc/interns-overview)))

#?(:clj
   (enc/declare-remote
     ^:dynamic taoensso.telemere/*ctx*
     ^:dynamic taoensso.telemere/*middleware*
     ^:dynamic taoensso.telemere/*uid-fn*
     ^:dynamic taoensso.telemere/*otel-tracer*))

;;;; Config

#?(:clj
   (do
     (def present:tools-logging?  (enc/have-resource? "clojure/tools/logging.clj"))
     (def present:slf4j?          (enc/compile-if org.slf4j.Logger                           true false))
     (def present:telemere-slf4j? (enc/compile-if com.taoensso.telemere.slf4j.TelemereLogger true false))
     (def present:otel?           (enc/compile-if io.opentelemetry.context.Context           true false))

     (def enabled:tools-logging?
       "Documented at `taoensso.telemere.tools-logging/tools-logging->telemere!`."
       (enc/get-env {:as :bool, :default false} :clojure.tools.logging/to-telemere))

     (def enabled:otel-tracing?
       "Documented at `taoensso.telemere/otel-tracing?`."
       (enc/get-env {:as :bool, :default present:otel?}
         :taoensso.telemere/otel-tracing<.platform>))))

(def uid-kind
  "Documented at `taoensso.telemere/*uid-fn*`."
  (enc/get-env {:as :edn, :default :default}
    :taoensso.telemere/uid-kind<.platform><.edn>))

#?(:clj
   (let [base        (enc/get-env {:as :edn} :taoensso.telemere/ct-filters<.platform><.edn>)
         kind-filter (enc/get-env {:as :edn} :taoensso.telemere/ct-kind-filter<.platform><.edn>)
         ns-filter   (enc/get-env {:as :edn} :taoensso.telemere/ct-ns-filter<.platform><.edn>)
         id-filter   (enc/get-env {:as :edn} :taoensso.telemere/ct-id-filter<.platform><.edn>)
         min-level   (enc/get-env {:as :edn} :taoensso.telemere/ct-min-level<.platform><.edn>)]

     (enc/defonce ct-sig-filter
       "`SigFilter` used for compile-time elision, or nil."
       (sigs/sig-filter
         {:kind-filter (or kind-filter (get base :kind-filter))
          :ns-filter   (or ns-filter   (get base :ns-filter))
          :id-filter   (or id-filter   (get base :id-filter))
          :min-level   (or min-level   (get base :min-level))}))))

(let [base        (enc/get-env {:as :edn}                 :taoensso.telemere/rt-filters<.platform><.edn>)
      kind-filter (enc/get-env {:as :edn}                 :taoensso.telemere/rt-kind-filter<.platform><.edn>)
      ns-filter   (enc/get-env {:as :edn}                 :taoensso.telemere/rt-ns-filter<.platform><.edn>)
      id-filter   (enc/get-env {:as :edn}                 :taoensso.telemere/rt-id-filter<.platform><.edn>)
      min-level   (enc/get-env {:as :edn, :default :info} :taoensso.telemere/rt-min-level<.platform><.edn>)]

  (enc/defonce ^:dynamic *rt-sig-filter*
    "`SigFilter` used for runtime filtering, or nil."
    (sigs/sig-filter
      {:kind-filter (or kind-filter (get base :kind-filter))
       :ns-filter   (or ns-filter   (get base :ns-filter))
       :id-filter   (or id-filter   (get base :id-filter))
       :min-level   (or min-level   (get base :min-level))})))

(comment (enc/get-env {:as :edn, :return :explain} :taoensso.telemere/rt-filters<.platform><.edn>))

;;;; Utils

#?(:clj
   (defmacro on-init [& body]
     (let [sym        (with-meta '__on-init {:private true})
           compiling? (if (:ns &env) false `*compile-files*)]
       `(defonce ~sym (when-not ~compiling? ~@body nil)))))

(comment (macroexpand-1 '(on-init (println "foo"))))

;;;; Messages

(deftype MsgSkip   [])
(deftype MsgSplice [args])

(def ^:public msg-skip
  "For use within signal message vectors.
  Special value that will be ignored (noop) when creating message.
  Useful for conditionally skipping parts of message content, etc.:

    (signal! {:msg [\"Hello\" (if <cond> <then> msg-skip) \"world\"] <...>}) or
    (log!          [\"Hello\" (if <cond> <then> msg-skip) \"world\"]), etc.

      %> {:msg_ \"Hello world\" <...>}"

  (MsgSkip.))

(defn ^:public msg-splice
  "For use within signal message vectors.
  Wraps given arguments so that they're spliced when creating message.
  Useful for conditionally splicing in extra message content, etc.:

    (signal! {:msg [(when <cond> (msg-splice [\"Username:\" \"Steve\"])) <...>]}) or
    (log!          [(when <cond> (msg-splice [\"Username:\" \"Steve\"]))])

      %> {:msg_ \"Username: Steve\"}"

  [args] (MsgSplice. args))

(let [;; xform (map #(if (nil? %) "nil" %))
      xform
      (fn [rf]
        (let [;; Protocol-based impln (extensible but ~20% slower)
              ;; rf* (fn rf* [acc in] (reduce-msg-arg in acc rf))
              rf*
              (fn rf* [acc in]
                (enc/cond
                  (instance? MsgSplice in) (reduce rf* acc (.-args ^MsgSplice in))
                  (instance? MsgSkip   in)             acc
                  (nil?                in)         (rf acc "nil")
                  :else                            (rf acc in)))]
          (fn
            ([      ] (rf))
            ([acc   ] (rf  acc))
            ([acc in] (rf* acc in)))))]

  (defn signal-msg
    "Returns string formed by joining all args with \" \" separator,
    rendering nils as \"nil\". Supports `msg-skip`, `msg-splice`.

    API intended to be usefully different to `str`:
      -        `str`: no   spacers, skip nils, no     splicing
      - `signal-msg`: auto spacers, show nils, opt-in splicing"

    {:tag #?(:clj 'String :cljs 'string)}
    [args] (enc/str-join " " xform args)))

(comment
  (enc/qb 2e6 ; [305.61 625.35]
    (str         "a" "b" "c" nil :kw)                         ; "abc:kw"
    (signal-msg ["a" "b" "c" nil :kw (msg-splice ["d" "e"])]) ; "a b c nil :kw d e"
    ))

#?(:clj
   (defn- parse-msg-form [msg-form]
     (when msg-form
       (enc/cond
         (string? msg-form) msg-form
         (vector? msg-form)
         (enc/cond
           (empty?           msg-form) nil
           :let [[m1 & more] msg-form]
           (and (string? m1) (nil? more)) m1
           :else `(delay (signal-msg ~msg-form)))

         ;; Auto delay-wrap (user should never delay-wrap!)
         ;; :else `(delay ~msg-form)

         ;; Leave user to delay-wrap when appropriate (document)
         :else msg-form))))

(defn default-trace-msg
  [form value error nsecs]
  (if error
    (str form " !> " (enc/ex-type error))
    (str form " => " value)))

(comment
  (default-trace-msg "(+ 1 2)" 3   nil               12345)
  (default-trace-msg "(+ 1 2)" nil (Exception. "Ex") 12345))

;;;; Tracing

(enc/def* ^:dynamic *trace-root*   "?{:keys [id uid]}" nil) ; Fixed once bound
(enc/def* ^:dynamic *trace-parent* "?{:keys [id uid]}" nil) ; Changes each nesting level

;; Root Telemere ids: {:parent nil, :id id1, :uid uid1  :root {:id id1, :uid uid1}}
;; Root     OTel ids: {:parent nil, :id id1, :uid span1,:root {:id id1, :uid trace1}}

;;;; OpenTelemetry

#?(:clj
   (enc/compile-when present:otel?
     (do
       (enc/def*            ^:dynamic *otel-context* "`?Context`" nil)
       (defmacro otel-context [] `(or *otel-context* (io.opentelemetry.context.Context/current)))

       (defn otel-trace-id
         "Returns valid `traceId` or nil."
         [^io.opentelemetry.context.Context context]
         (let [sc (.getSpanContext (io.opentelemetry.api.trace.Span/fromContext context))]
           (when (.isValid sc) (.getTraceId sc))))

       (defn otel-span-id
         "Returns valid `spanId` or nil."
         [^io.opentelemetry.context.Context context]
         (let [sc (.getSpanContext (io.opentelemetry.api.trace.Span/fromContext context))]
           (when (.isValid sc) (.getSpanId sc))))

       (defn viable-tracer
         "Returns viable `Tracer`, or nil."
         [tracer]
         (when-let [tracer ^io.opentelemetry.api.trace.Tracer tracer]
           (let [sb   (.spanBuilder tracer "test-span")
                 span (.startSpan sb)]
             (when (.isValid (.getSpanContext span))
               tracer))))

       (def ^String otel-name (enc/fmemoize (fn [id] (if id (enc/as-qname id) "telemere/no-id"))))
       (defn otel-context+span
         "Returns new `Context` that includes minimal `Span` in given parent `Context`.
         We leave the (expensive) population of attributes, etc. for signal handler.
         Interop needs only the basics (t0, traceId, spanId, spanName) right away."
         ^io.opentelemetry.context.Context
         [id inst ?parent-context]
         (let [parent-context (or ?parent-context (otel-context))]
           (enc/if-not [tracer (force taoensso.telemere/*otel-tracer*)]
             parent-context ; Can't add Span without Tracer
             (let [sb (.spanBuilder ^io.opentelemetry.api.trace.Tracer tracer (otel-name id))]
               (.setStartTimestamp sb ^java.time.Instant inst)
               (.with ^io.opentelemetry.context.Context parent-context
                 (.startSpan sb)))))))))

(comment
  (enc/qb 1e6 (otel-context) (otel-context+span ::id1 (enc/now-inst) nil)) ; [46.42 186.89]
  (viable-tracer (force taoensso.telemere/*otel-tracer*))
  (otel-trace-id (otel-context)))

;;;; Main types

(defrecord Signal
  ;; Telemere's main public data type, we avoid nesting and duplication
  [schema inst uid,
   location ns line column file, #?@(:clj [host thread _otel-context]),
   sample-rate, kind id level, ctx parent root, data kvs msg_,
   error run-form run-val end-inst run-nsecs]
 
  Object (toString [sig] (str "#" `Signal (into {} sig))))

(do     (enc/def-print-impl [sig Signal] (str "#" `Signal (pr-str (into {} sig)))))
#?(:clj (enc/def-print-dup  [sig Signal] (str "#" `Signal (pr-str (into {} sig))))) ; NB intentionally verbose, to support extra keys

(def     impl-signal-keys #{:_otel-context})
(def standard-signal-keys
  (set/difference (set (keys (map->Signal {:schema 0})))
    impl-signal-keys))

(comment
  (def s1 (with-signal (signal! {:level :info, :my-k1 :my-v1})))
  (read-string (str    (assoc s1 :my-k2 :my-v2)))
  (read-string (pr-str (assoc s1 :my-k2 :my-v2)))
  (read-string (binding [*print-dup* true] (pr-str (assoc s1 :my-k2 :my-v2))))

  (defrecord MyRec [x])
  (read-string ; Non-verbose will fail on any extra keys
    (binding [*print-dup* true, *verbose-defrecords* false]
      (pr-str (assoc (MyRec. :x) :y :y)))))

(deftype #_defrecord WrappedSignal
  ;; Internal type to implement `sigs/IFilterableSignal`,
  ;; incl. lazy + cached `signal-value_` field.
  [ns kind id level signal-value_]
  sigs/IFilterableSignal
  (allow-signal? [_ sig-filter] (sig-filter kind ns id level))
  (signal-value  [_ handler-sample-rate]
    (let [sig-val (force signal-value_)]
      (or
        (when handler-sample-rate
          (when (map? sig-val)
            ;; Replace call sample rate with combined (call * handler) sample rate
            (assoc    sig-val :sample-rate
              (*
                (double handler-sample-rate)
                (double (or (get sig-val :sample-rate) 1.0))))))
        sig-val))))

(defn wrap-signal
  "Used by `taoensso.telemere/dispatch-signal!`."
  [signal]
  (when (map? signal)
    (let [{:keys     [ns kind id level]} signal]
      (WrappedSignal. ns kind id level   signal))))

;;;; Handlers

(enc/defonce ^:dynamic *sig-handlers* "?[<wrapped-handler-fn>]" nil)

(defrecord SpyOpts [vol_ last-only? trap?])
(def ^:dynamic *sig-spy* "?SpyOpts" nil)

(defn force-msg-in-sig [sig]
  (if-not (map? sig)
    sig
    (if-let [e (find sig :msg_)]
      (assoc sig :msg_ (force (val e)))
      (do    sig))))

#?(:clj
   (defmacro ^:public with-signal
     "Experimental, subject to change.
     Executes given form, trapping errors. Returns the LAST signal created by form.
     Useful for tests/debugging.

     Options:
       `trap-signals?` (default false)
         Should ALL signals created by form be trapped to prevent normal dispatch
         to registered handlers?

       `raw-msg?` (default false)
         Should delayed `:msg_` in returned signal be retained as-is?
         Delay is otherwise replaced by realized string.

     See also `with-signals`."
     ([                       form] `(with-signal false false          ~form))
     ([         trap-signals? form] `(with-signal false ~trap-signals? ~form))
     ([raw-msg? trap-signals? form]
      `(let [sig_# (volatile! nil)]
         (binding [*sig-spy* (SpyOpts. sig_# true ~trap-signals?)]
           (enc/try* ~form (catch :all _#)))

         (if ~raw-msg?
           (do               @sig_#)
           (force-msg-in-sig @sig_#))))))

#?(:clj
   (defmacro ^:public with-signals
     "Experimental, subject to change.
     Like `with-signal` but returns [[<form-value> <form-error>] [<signal1> ...]].
     Useful for tests/debugging."
     ([                        form] `(with-signals false false          ~form))
     ([          trap-signals? form] `(with-signals false ~trap-signals? ~form))
     ([raw-msgs? trap-signals? form]
      `(let [sigs_# (volatile! nil)
             form-result#
             (binding [*sig-spy* (SpyOpts. sigs_# false ~trap-signals?)]
               (enc/try*
                 (do            [~form nil])
                 (catch :all t# [nil    t#])))

             sigs#
             (if ~raw-msgs?
               (do                    @sigs_#)
               (mapv force-msg-in-sig @sigs_#))]

         [form-result# (not-empty sigs#)]))))

#?(:clj (def ^:dynamic *sig-spy-off-thread?* false))
(defn dispatch-signal!
  "Dispatches given signal to registered handlers, supports `with-signal/s`."
  [signal]
  (or
    (when-let [{:keys [vol_ last-only? trap?]} *sig-spy*]
      (let [sv
            #?(:cljs (sigs/signal-value signal nil)
               :clj
               (if *sig-spy-off-thread?* ; Simulate async handler
                 (deref (enc/promised :user (sigs/signal-value signal nil)))
                 (do                        (sigs/signal-value signal nil))))]

        (if last-only?
          (vreset! vol_                  sv)
          (vswap!  vol_ #(conj (or % []) sv))))
      (when trap? :trapped))

    (sigs/call-handlers! *sig-handlers* signal)
    :dispatched))

;;;; Signal API helpers

#?(:clj (defmacro signal-docstring [    rname] (enc/slurp-resource (str "signal-docstrings/" (name rname) ".txt"))))
#?(:clj (defmacro defhelp          [sym rname] `(enc/def* ~sym {:doc ~(eval `(signal-docstring ~rname))} "See docstring")))

#?(:clj
   (defn signal-arglists [macro-id]
     (case macro-id

       :signal! ; [opts] => allowed? / run result (value or throw)
       '([{:as opts :keys
           [#_defaults #_elide? #_allow? #_expansion-id, ; Undocumented
            elidable? location #_location* inst uid middleware middleware+,
            sample-rate kind ns id level when rate-limit rate-limit-by,
            ctx ctx+ parent root trace?, do let data msg error run & kvs]}])

       :signal-allowed?
       '([{:as opts :keys
           [#_defaults #_elide? #_allow? #_expansion-id, ; Undocumented
            elidable? location #_location* #_inst #_uid #_middleware #_middleware+,
            sample-rate kind ns id level when rate-limit rate-limit-by,
            #_ctx #_ctx+ #_parent #_root #_trace?, #_do #_let #_data #_msg #_error #_run #_& #_kvs]}])

       :event! ; [id] [id level-or-opts] => allowed?
       '([id      ]
         [id level]
         [id
          {:as opts :keys
           [#_defaults #_elide? #_allow? #_expansion-id,
            elidable? location #_location* inst uid middleware middleware+,
            sample-rate kind ns id level when rate-limit rate-limit-by,
            ctx ctx+ parent root trace?, do let data msg error #_run & kvs]}])

       :log! ; [msg] [level-or-opts msg] => allowed?
       '([      msg]
         [level msg]
         [{:as opts :keys
           [#_defaults #_elide? #_allow? #_expansion-id,
            elidable? location #_location* inst uid middleware middleware+,
            sample-rate kind ns id level when rate-limit rate-limit-by,
            ctx ctx+ parent root trace?, do let data msg error #_run & kvs]}
          msg])

       :error! ; [error] [id-or-opts error] => given error
       '([   error]
         [id error]
         [{:as opts :keys
           [#_defaults #_elide? #_allow? #_expansion-id,
            elidable? location #_location* inst uid middleware middleware+,
            sample-rate kind ns id level when rate-limit rate-limit-by,
            ctx ctx+ parent root trace?, do let data msg error #_run & kvs]}
          error])

       :trace! ; [form] [id-or-opts form] => run result (value or throw)
       '([   form]
         [id form]
         [{:as opts :keys
           [#_defaults #_elide? #_allow? #_expansion-id,
            elidable? location #_location* inst uid middleware middleware+,
            sample-rate kind ns id level when rate-limit rate-limit-by,
            ctx ctx+ parent root trace?, do let data msg error run & kvs]}
          form])

       :spy! ; [form] [level-or-opts form] => run result (value or throw)
       '([      form]
         [level form]
         [{:as opts :keys
           [#_defaults #_elide? #_allow? #_expansion-id,
            elidable? location #_location* inst uid middleware middleware+,
            sample-rate kind ns id level when rate-limit rate-limit-by,
            ctx ctx+ parent root trace?, do let data msg error run & kvs]}
          form])

       :catch->error! ; [form] [id-or-opts form] => run result (value or throw)
       '([   form]
         [id form]
         [{:as opts :keys
           [#_defaults #_elide? #_allow? #_expansion-id, rethrow? catch-val,
            elidable? location #_location* inst uid middleware middleware+,
            sample-rate kind ns id level when rate-limit rate-limit-by,
            ctx ctx+ parent root trace?, do let data msg error #_run & kvs]}
          form])

       :uncaught->error! ; [] [id-or-opts] => nil
       '([  ]
         [id]
         [{:as opts :keys
           [#_defaults #_elide? #_allow? #_expansion-id,
            elidable? location #_location* inst uid middleware middleware+,
            sample-rate kind ns id level when rate-limit rate-limit-by,
            ctx ctx+ parent root trace?, do let data msg error #_run & kvs]}])

       (enc/unexpected-arg! macro-id))))

#?(:clj
   (defn signal-opts
     "Util to help write common signal wrapper macros."
     [context location* defaults main-key extra-key arg-order args]
     (enc/cond
       :let [context-name (str "`" (name context) "`")
             num-args (count args)
             bad-args!
             (fn [msg data]
               (throw
                 (ex-info (str "Invalid " context-name " args: " msg)
                   (conj
                     {:context context
                      :args    args}
                     data))))]

       (not (#{1 2} num-args))
       (bad-args! (str "wrong number of args (" num-args ")")
         {:actual num-args, :expected #{1 2}})

       :let [[main-arg extra-arg]
             (case arg-order
               :dsc          args  ; [main ...]
               :asc (reverse args) ; [... main]
               (enc/unexpected-arg!
                 arg-order))

             extra-arg?  (= num-args 2)
             extra-opts? (and extra-arg? (map? extra-arg))]

       :do
       (enc/cond
         (and (map? main-arg) (not extra-arg?))
         (bad-args! "single map arg is USUALLY a mistake, so isn't allowed. Please use 2 arg arity instead, or `signal!`." {})

         (and extra-opts? (contains? extra-arg main-key))
         (bad-args! (str "given opts should not contain `" main-key "`.") {}))

       :let [base  (merge defaults {:location* location*, main-key main-arg})]
       extra-opts? (merge base            extra-arg)
       extra-arg?  (merge base {extra-key extra-arg})
       :else              base)))

(comment (signal-opts `foo! :loc* {:level :info} :id :level :dsc [::my-id {:level :warn}]))

#?(:clj
   (defn signal-catch-opts
     "For use within `trace!` and `spy!`, etc."
     [main-opts]
     (let [catch-id-or-opts (get    main-opts :catch->error)
           main-opts        (dissoc main-opts :catch->error)
           catch-opts
           (when catch-id-or-opts
             (let [base ; Inherit some opts from main
                   (enc/assoc-some {}
                     :location* (get main-opts :location*)
                     :id        (get main-opts :id))]
               (cond
                 (true? catch-id-or-opts) (do   base)
                 (map?  catch-id-or-opts) (conj base catch-id-or-opts)
                 :else                    (conj base {:id catch-id-or-opts}))))]

       [main-opts catch-opts])))

(comment
  (signal-catch-opts {:id :main-id, :catch->error           true})
  (signal-catch-opts {:id :main-id, :catch->error      :error-id})
  (signal-catch-opts {:id :main-id, :catch->error {:id :error-id}}))

;;;; Signal macro

(deftype RunResult [value error ^long run-nsecs]
  #?(:clj clojure.lang.IFn :cljs IFn)
  (#?(:clj invoke :cljs -invoke) [_] (if error (throw error) value))
  (#?(:clj invoke :cljs -invoke) [_ signal_]
    (if error
      (throw
        (ex-info "Signal `:run` form error"
          (enc/try*
            (do           {:taoensso.telemere/signal (force signal_)})
            (catch :all t {:taoensso.telemere/signal-error t}))
          error))
      value)))

(defn inst+nsecs
  "Returns given platform instant plus given number of nanosecs."
  [inst run-nsecs]
  #?(:clj  (.plusNanos ^java.time.Instant inst run-nsecs)
     :cljs (js/Date. (+ (.getTime inst) (/ run-nsecs 1e6)))))

(comment (enc/qb 1e6 (inst+nsecs (enc/now-inst) 1e9)))

#?(:clj (defn- auto-> [form auto-form] (if (= form :auto) auto-form form)))
#?(:clj
   (defmacro ^:public signal!
     "Generic low-level signal call, also aliased in Encore."
     {:doc      (signal-docstring :signal!)
      :arglists (signal-arglists  :signal!)}
     [opts]
     (have? map? opts) ; We require const map keys, but vals may require eval
     (let [defaults              (get    opts :defaults)
           opts  (merge defaults (dissoc opts :defaults))
           cljs? (boolean (:ns &env))
           clj?  (not cljs?)
           {run-form :run} opts

           show-run-form
           (when run-form
             (get opts :run-form
               (if (and
                     (enc/list-form? run-form)
                     (> (count       run-form)  1)
                     (> (count (str  run-form)) 32))
                 (list (first run-form) '...)
                 (do          run-form))))

           {:keys [#_expansion-id location elide? allow?]}
           (sigs/filterable-expansion
             {:sf-arity 4
              :ct-sig-filter     ct-sig-filter
              :*rt-sig-filter* `*rt-sig-filter*}

             (assoc opts
               :location* (get opts :location* (enc/get-source &form &env))
               :bound-forms
               {:kind  '__kind
                :ns    '__ns
                :id    '__id
                :level '__level}))]

       (if elide?
         run-form
         (let [{ns-form     :ns
                line-form   :line
                column-form :column
                file-form   :file} location

               {inst-form  :inst
                level-form :level
                kind-form  :kind
                id-form    :id} opts

               trace? (get opts :trace? (boolean run-form))
               _
               (when-not (contains? #{true false nil} trace?)
                 (enc/unexpected-arg! trace?
                   {:msg "Expected constant (compile-time) `:trace?` boolean"
                    :context `signal!}))

               thread-form (when clj? `(enc/thread-info))

               inst-form   (get opts :inst :auto)
               inst-form   (auto-> inst-form `(enc/now-inst*))

               parent-form (get opts :parent `*trace-parent*)
               root-form0  (get opts :root   `*trace-root*)

               uid-form    (get opts :uid (when trace? :auto))

               signal-delay-form
               (let [{do-form          :do
                      let-form         :let
                      msg-form         :msg
                      data-form        :data
                      error-form       :error
                      sample-rate-form :sample-rate} opts

                     let-form (or let-form '[])
                     msg-form (parse-msg-form msg-form)

                     ctx-form
                     (if-let [ctx+ (get opts :ctx+)]
                       `(taoensso.encore.signals/update-ctx taoensso.telemere/*ctx* ~ctx+)
                       (get opts :ctx                      `taoensso.telemere/*ctx*))

                     middleware-form
                     (if-let [middleware+ (get opts :middleware+)]
                       `(taoensso.telemere/comp-middleware taoensso.telemere/*middleware* ~middleware+)
                       (get opts :middleware              `taoensso.telemere/*middleware*))

                     kvs-form
                     (not-empty
                       (dissoc opts
                         :elidable? :location :location* :inst :uid :middleware :middleware+,
                         :sample-rate :ns :kind :id :level :filter :when #_:rate-limit #_:rate-limit-by,
                         :ctx :ctx+ :parent #_:trace?, :do :let :data :msg :error :run,
                         :elide? :allow? #_:expansion-id :otel/context))

                     _ ; Compile-time validation
                     (do
                       (when (and run-form error-form) ; Ambiguous source of error
                         (throw
                           (ex-info "Signals cannot have both `:run` and `:error` opts at the same time"
                             {:run-form   run-form
                              :error-form error-form
                              :location   location
                              :other-opts (dissoc opts :run :error)})))

                       (when-let [e (find opts :msg_)] ; Common typo/confusion
                         (throw
                           (ex-info "Signals cannot have `:msg_` opt (did you mean `:msg`?))"
                             {:msg_ (enc/typed-val (val e))}))))

                     signal-form
                     (let [record-form
                           (let   [clause [(if run-form :run :no-run) (if clj? :clj :cljs)]]
                             (case clause
                               [:run    :clj ]  `(Signal. 1 ~'__inst ~'__uid, ~location ~'__ns ~line-form ~column-form ~file-form, (enc/host-info) ~'__thread ~'__otel-context1, ~sample-rate-form, ~'__kind ~'__id ~'__level, ~ctx-form ~parent-form ~'__root1, ~data-form ~kvs-form ~'_msg_,   ~'_run-err  '~show-run-form ~'_run-val ~'_end-inst ~'_run-nsecs)
                               [:run    :cljs]  `(Signal. 1 ~'__inst ~'__uid, ~location ~'__ns ~line-form ~column-form ~file-form,                                               ~sample-rate-form, ~'__kind ~'__id ~'__level, ~ctx-form ~parent-form ~'__root1, ~data-form ~kvs-form ~'_msg_,   ~'_run-err  '~show-run-form ~'_run-val ~'_end-inst ~'_run-nsecs)
                               [:no-run :clj ]  `(Signal. 1 ~'__inst ~'__uid, ~location ~'__ns ~line-form ~column-form ~file-form, (enc/host-info) ~'__thread ~'__otel-context1, ~sample-rate-form, ~'__kind ~'__id ~'__level, ~ctx-form ~parent-form ~'__root1, ~data-form ~kvs-form ~msg-form, ~error-form nil             nil        nil         nil)
                               [:no-run :cljs]  `(Signal. 1 ~'__inst ~'__uid, ~location ~'__ns ~line-form ~column-form ~file-form,                                               ~sample-rate-form, ~'__kind ~'__id ~'__level, ~ctx-form ~parent-form ~'__root1, ~data-form ~kvs-form ~msg-form, ~error-form nil             nil        nil         nil)
                               (enc/unexpected-arg! clause {:context :signal-constructor-args})))

                           record-form
                           (if-not run-form
                             record-form
                             `(let [~(with-meta '_run-result {:tag `RunResult}) ~'__run-result
                                    ~'_run-nsecs (.-run-nsecs    ~'_run-result)
                                    ~'_run-val   (.-value        ~'_run-result)
                                    ~'_run-err   (.-error        ~'_run-result)
                                    ~'_end-inst  (inst+nsecs ~'__inst ~'_run-nsecs)
                                    ~'_msg_
                                    (let [mf# ~msg-form]
                                      (if (fn? mf#) ; Undocumented, handy for `trace!`/`spy!`, etc.
                                        (delay (mf# '~show-run-form ~'_run-val ~'_run-err ~'_run-nsecs))
                                        mf#))]
                                ~record-form))]

                       (if-not kvs-form
                         record-form
                         `(let [signal# ~record-form]
                            (reduce-kv assoc signal# (.-kvs signal#)))))]

                 `(enc/bound-delay
                    ;; Delay (cache) shared by all handlers, incl. `:let` eval,
                    ;; signal construction, middleware, etc. Throws caught by handler.
                    ~do-form
                    (let [~@let-form ; Allow to throw, eval BEFORE data, msg, etc.
                          signal# ~signal-form]

                      ;; Final unwrapped signal value visible to users/handler-fns, allow to throw
                      (if-let [sig-middleware# ~middleware-form]
                        (sig-middleware# signal#) ; Apply signal middleware, can throw
                        (do              signal#)))))

               ;; Trade-off: avoid double `run-form` expansion
               run-fn-form (when run-form `(fn [] ~run-form))
               run-form*   (when run-form `(~'__run-fn-form))

               into-let-form
               (enc/cond!
                 (not trace?) ; Don't trace
                 `[~'__otel-context1 nil
                   ~'__uid   ~(auto-> uid-form `(taoensso.telemere/*uid-fn* (if ~'__root0 false true)))
                   ~'__root1 ~'__root0 ; Retain, but don't establish
                   ~'__run-result
                   ~(when run-form
                      `(let [t0# (enc/now-nano*)]
                         (enc/try*
                           (do            (RunResult. ~run-form* nil (- (enc/now-nano*) t0#)))
                           (catch :all t# (RunResult. nil        t#  (- (enc/now-nano*) t0#))))))]

                 ;; Trace without OpenTelemetry
                 (or cljs? (not enabled:otel-tracing?))
                 `[~'__otel-context1 nil
                   ~'__uid  ~(auto-> uid-form `(taoensso.telemere/*uid-fn* (if ~'__root0 false true)))
                   ~'__root1 (or ~'__root0 ~(when trace? `{:id ~'__id, :uid ~'__uid}))
                   ~'__run-result
                   ~(when run-form
                      `(binding [*trace-root*   ~'__root1
                                 *trace-parent* {:id ~'__id, :uid ~'__uid}]
                         (let [t0# (enc/now-nano*)]
                           (enc/try*
                             (do            (RunResult. ~run-form* nil (- (enc/now-nano*) t0#)))
                             (catch :all t# (RunResult. nil        t#  (- (enc/now-nano*) t0#)))))))]

                 ;; Trace with OpenTelemetry
                 (and clj? enabled:otel-tracing?)
                 `[~'__otel-context0 ~(get opts :otel/context `(otel-context)) ; Context
                   ~'__otel-context1 ~(if run-form `(otel-context+span ~'__id ~'__inst ~'__otel-context0) ~'__otel-context0)
                   ~'__uid           ~(auto-> uid-form `(or (otel-span-id ~'__otel-context1) (com.taoensso.encore.Ids/genHexId16)))
                   ~'__root1
                   (or ~'__root0
                     ~(when trace?
                        `{:id ~'__id, :uid (or (otel-trace-id ~'__otel-context1) (com.taoensso.encore.Ids/genHexId32))}))

                   ~'__run-result
                   ~(when run-form
                      `(binding [*otel-context* ~'__otel-context1
                                 *trace-root*   ~'__root1
                                 *trace-parent* {:id ~'__id, :uid ~'__uid}]
                         (let [otel-scope# (.makeCurrent ~'__otel-context1)
                               t0#         (enc/now-nano*)]
                           (enc/try*
                             (do            (RunResult. ~run-form* nil (- (enc/now-nano*) t0#)))
                             (catch :all t# (RunResult. nil        t#  (- (enc/now-nano*) t0#)))
                             (finally (.close otel-scope#))))))])

               final-form
               ;; Unless otherwise specified, allow errors to throw on call
               `(let [~'__run-fn-form ~run-fn-form
                      ~'__kind        ~kind-form
                      ~'__ns          ~ns-form
                      ~'__id          ~id-form
                      ~'__level       ~level-form]

                  (enc/if-not ~allow?
                    ~run-form*
                    (let [~'__inst   ~inst-form
                          ~'__thread ~thread-form
                          ~'__root0  ~root-form0 ; ?{:keys [id uid]}

                          ~@into-let-form ; Inject conditional bindings
                          signal# ~signal-delay-form]

                      (dispatch-signal!
                        ;; Unconditionally send same wrapped signal to all handlers.
                        ;; Each handler will use wrapper for handler filtering,
                        ;; unwrapping (realizing) only allowed signals.
                        (WrappedSignal. ~'__ns ~'__kind ~'__id ~'__level signal#))

                      (if ~'__run-result
                        ( ~'__run-result signal#)
                        true))))]

           (if-let [iife-wrap? true #_cljs?]
             ;; Small perf hit to improve compatibility within `go` and other IOC-style bodies
             `((fn [] ~final-form))
             (do       final-form)))))))

(comment
  (with-signal  (signal! {:level :warn :let [x :x] :msg ["Test" "message" x] :data {:a :A :x x} :run (+ 1 2)}))
  (macroexpand '(signal! {:level :warn :let [x :x] :msg ["Test" "message" x] :data {:a :A :x x} :run (+ 1 2)}))
  (macroexpand '(signal! {:level :info}))

  (do
    (println "---")
    (sigs/with-handler *sig-handlers* "hf1" (fn hf1 [x] (println x)) {}
      (signal! {:level :info, :run "run"}))))

#?(:clj
   (defmacro ^:public signal-allowed?
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
     {:arglists (signal-arglists :signal-allowed?)}
     [opts]
     (have? map? opts)
     (let [defaults             (get    opts :defaults)
           opts (merge defaults (dissoc opts :defaults))

           {:keys [#_expansion-id #_location elide? allow?]}
           (sigs/filterable-expansion
             {:sf-arity 4
              :ct-sig-filter     ct-sig-filter
              :*rt-sig-filter* `*rt-sig-filter*}
             (assoc opts :location*
               (get opts :location* (enc/get-source &form &env))))]

       (if elide? false `(if ~allow? true false)))))

(comment (macroexpand '(signal-allowed? {:level :info})))

;;;; Interop

#?(:clj
   (do
     (enc/defonce ^:private interop-checks_
       "{<source-id> (fn check [])}"
       (atom
         {:tools-logging  (fn [] {:present? present:tools-logging?, :enabled-by-env? enabled:tools-logging?})
          :slf4j          (fn [] {:present? present:slf4j?, :telemere-provider-present? present:telemere-slf4j?})
          :open-telemetry (fn [] {:present? present:otel?, :use-tracer? enabled:otel-tracing?})}))

     (defn add-interop-check! [source-id check-fn] (swap! interop-checks_ assoc source-id check-fn))

     (defn ^:public check-interop
       "Experimental, subject to change.
       Runs Telemere's registered interop checks and returns info useful
       for tests/debugging, e.g.:

         {:open-telemetry {:present? false}
          :tools-logging  {:present? false}
          :slf4j          {:present? true
                           :sending->telemere?  true
                           :telemere-receiving? true}
          ...}"
       []
       (enc/map-vals (fn [check-fn] (check-fn))
         @interop-checks_))

     (defn test-interop! [msg test-fn]
       (let [msg (str "Interop test: " msg " (" (enc/uuid-str) ")")
             signal
             (binding [*rt-sig-filter* nil] ; Without runtime filters
               (with-signal :raw :trap (test-fn msg)))]

         (= (force (get signal :msg_)) msg)))))
