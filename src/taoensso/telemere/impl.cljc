(ns ^:no-doc taoensso.telemere.impl
  "Private ns, implementation detail.
  Signal design shared by: Telemere, Tufte, Timbre."
  (:require
   [taoensso.encore         :as enc :refer [have have?]]
   [taoensso.encore.signals :as sigs]))

(comment
  (remove-ns 'taoensso.telemere.impl)
  (:api (enc/interns-overview)))

;;;; Utils

#?(:clj (defmacro threaded [& body] `(let [t# (Thread. (fn [] ~@body))] (.start t#) t#)))

;;;; Config

#?(:clj
   (let [base      (enc/get-env {:as :edn} :taoensso.telemere/ct-filters<.platform><.edn>)
         ns-filter (enc/get-env {:as :edn} :taoensso.telemere/ct-ns-filter<.platform><.edn>)
         id-filter (enc/get-env {:as :edn} :taoensso.telemere/ct-id-filter<.platform><.edn>)
         min-level (enc/get-env {:as :edn} :taoensso.telemere/ct-min-level<.platform><.edn>)]

     (enc/defonce ct-sig-filter
       "`SigFilter` used for compile-time elision, or nil."
       (sigs/sig-filter
         {:ns-filter (or ns-filter (get base :ns-filter))
          :id-filter (or id-filter (get base :id-filter))
          :min-level (or min-level (get base :min-level))}))))

(let [base      (enc/get-env {:as :edn} :taoensso.telemere/rt-filters<.platform><.edn>)
      ns-filter (enc/get-env {:as :edn} :taoensso.telemere/rt-ns-filter<.platform><.edn>)
      id-filter (enc/get-env {:as :edn} :taoensso.telemere/rt-id-filter<.platform><.edn>)
      min-level (enc/get-env {:as :edn} :taoensso.telemere/rt-min-level<.platform><.edn>)]

  (enc/defonce ^:dynamic *rt-sig-filter*
    "`SigFilter` used for runtime filtering, or nil."
    (sigs/sig-filter
      {:ns-filter (or ns-filter (get base :ns-filter))
       :id-filter (or id-filter (get base :id-filter))
       :min-level (or min-level (get base :min-level))})))

;;;; Context (optional arb app-level state)
;; taoensso.telemere/*ctx*

(defn update-ctx
  "Returns `new-ctx` given `old-ctx` and an update map or fn."
  [old-ctx update-map-or-fn]
  (enc/cond
    (nil? update-map-or-fn)                   old-ctx
    (map? update-map-or-fn) (enc/fast-merge   old-ctx update-map-or-fn) ; Before ifn
    (ifn? update-map-or-fn) (update-map-or-fn old-ctx)
    :else
    (enc/unexpected-arg! update-map-or-fn
      {:context  `update-ctx
       :param    'update-map-or-fn
       :expected '#{nil map fn}})))

;;;; Unique IDs (UIDs)

(enc/def* nanoid-readable (enc/rand-id-fn {:chars :nanoid-readable, :len 23}))

#?(:clj
   (defn- parse-uid-form [uid-form]
     (when   uid-form
       (case uid-form
         :auto/uuid            `(enc/uuid)
         :auto/uuid-str        `(enc/uuid-str)
         :auto/nanoid          `(enc/nanoid)
         :auto/nanoid-readable `(nanoid-readable)
         uid-form))))

(comment
  (enc/qb 1e6 ; [164.72 184.51 301.8 539.49]
    (enc/uuid)
    (enc/uuid-str)
    (enc/nanoid)
    (nanoid-readable)))

;;;; Messages

(deftype MsgSplice [args])
(deftype MsgSkip   [])

(defn ^:public msg-splice "TODO Docstring" [args] (MsgSplice. args))
(defn ^:public msg-skip   "TODO Docstring" []     (MsgSkip.))

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
    "Returns string formed by joining all args without separator,
    rendering nils as \"nil\"."
    {:tag #?(:clj 'String :cljs 'string)}
    [args] (enc/str-join nil xform args)))

(comment
  (enc/qb 2e6 ; [280.29 408.3]
    (str         "a" "b" "c" nil :kw)
    (signal-msg ["a" "b" "c" nil :kw (msg-splice ["d" "e"])])))

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

;;;; Tracing (optional flow tracking)

(enc/def* ^:dynamic *trace-parent* "?TraceParent" nil)
(defrecord TraceParent [id uid])

#?(:clj
   (defmacro with-tracing
     "Wraps `form` with tracing iff const boolean `trace?` is true."
     [trace? id uid form]

     ;; Not much motivation to support runtime `trace?` form, but easy
     ;; to add support later if desired
     (when-not (enc/const-form? trace?)
       (enc/unexpected-arg!     trace?
         {:msg     "Expected constant (compile-time) `:trace?` value"
          :context `with-tracing}))

     (if trace?
       `(binding [*trace-parent* (TraceParent. ~id ~uid)] ~form)
       (do                                                 form))))

(comment
  [(macroexpand '(with-tracing false :id1 :uid1 "form"))
   (macroexpand '(with-tracing true  :id1 :uid1 "form"))])

;;;; Main types

(defrecord Signal
  ;; Telemere's main public data type, we avoid field nesting and duplication
  [^long schema-version instant uid,
   callsite-id location ns line column file,
   sample-rate, kind id level, ctx parent,
   data msg_ error run-form run-value,
   end-instant runtime-nsecs])

(deftype #_defrecord WrappedSignal
  ;; Internal type to implement `sigs/IFilterableSignal`,
  ;; incl. lazy + cached `signal-value_` field.
  [ns kind id level signal-value_]
  sigs/IFilterableSignal
  (allow-signal? [_ sig-filter] (sig-filter ns kind id level))
  (signal-value  [_ handler-context]
    (let [sig-val @signal-value_]
      (or
        (when-let [handler-sample-rate
                   (when-let [^taoensso.encore.signals.HandlerContext hc handler-context]
                     (.-sample-rate hc))]

          (when (map? sig-val)
            ;; Replace call sample rate with combined (call * handler) sample rate
            (assoc    sig-val :sample-rate
              (*
                (double handler-sample-rate)
                (double (or (get sig-val :sample-rate) 1.0))))))
        sig-val))))

;;;; Handlers

(enc/defonce ^:dynamic *sig-spy*      "To support `with-signal`" nil)
(enc/defonce ^:dynamic *sig-handlers* "?[<wrapped-handler-fn>]"  nil)

(defn -with-signal
  "Private util to support `with-signal` macro."
  [form-fn
   {:keys [return trap-errors? stop-propagation? force-msg?]
    :or   {return :vec}}]

  (let [sv_ (volatile! nil)]
    (binding [*sig-spy* [sv_ stop-propagation?]]
      (let [form-result
            (if trap-errors?
              (enc/try* {:okay (form-fn)} (catch :any t t {:error t}))
              (do              (form-fn)))

            signal
            (when-let [sv @sv_]
              (if-not force-msg?
                sv
                (if-let [e (find sv :msg_)]
                  (assoc sv :msg_ (force (val e)))
                  (do    sv))))]

        (case return
          :vec         [form-result signal]
          :form-result  form-result
          :signal                   signal
          (enc/unexpected-arg!
            {:context  `with-signal
             :param    'return
             :expected #{:vec :form-result :signal}}))))))

#?(:clj
   (defmacro ^:public with-signal
     "Util for tests/debugging.
     Executes given form and returns [<form-result> <last-signal-dispatched-by-form>].
     If `trap-errors?` is true, form result will be wrapped by {:keys [okay error]}."

     {:arglists
      '([form]
        [{:keys [return trap-errors? stop-propagation? force-msg?]}
         form])}

     ([     form] `(-with-signal (fn [] ~form) nil))
     ([opts form] `(-with-signal (fn [] ~form) ~opts))))

(defn dispatch-signal!
  "Dispatches given signal to registered handlers, supports `with-signal`."
  [signal]
  (or
    (when-let [[v stop-propagation?] *sig-spy*]
      (vreset! v (sigs/signal-value signal nil))
      stop-propagation?)

    (sigs/call-handlers! *sig-handlers* signal)))

;;;; Signal constructor

(deftype RunResult [value error ^long runtime-nsecs]
  #?(:clj clojure.lang.IFn :cljs IFn)
  (#?(:clj invoke :cljs -invoke) [_] (if error (throw error) value)))

(defn new-signal
  "Returns a new `Signal` with given opts."
  ^Signal
  ;; Note all dynamic vals passed as explicit args for better control
  [instant uid,
   callsite-id location ns line column file,
   sample-rate, kind id level, ctx parent,
   user-opts data msg_,
   run-form run-result error]

  (let [signal
        (if-let [^RunResult run-result run-result]
          (let  [runtime-nsecs (.-runtime-nsecs run-result)
                 end-instant
                 #?(:clj  (.plusNanos ^java.time.Instant instant runtime-nsecs)
                    :cljs (js/Date. (+ (.getTime instant) (/ runtime-nsecs 1e6))))

                 run-error (.-error run-result)
                 run-value (.-value run-result)
                 msg_
                 (if (enc/identical-kw? msg_ ::spy)
                   (delay (str run-form " => " (or run-error run-value)))
                   msg_)]

            (Signal. 1 instant uid,
              callsite-id location ns line column file,
              sample-rate, kind id level, ctx parent,
              data msg_,
              run-error run-form run-value,
              end-instant runtime-nsecs))

          (Signal. 1 instant uid,
            callsite-id location ns line column file,
            sample-rate, kind id level, ctx parent,
            data msg_, error nil nil instant nil))]

    (if user-opts
      (reduce-kv assoc signal user-opts)
      (do              signal))))

(comment
  (enc/qb 1e6 ; 55.67
    (new-signal
      nil nil nil nil nil nil nil nil nil nil
      nil nil nil nil nil nil nil nil nil nil)))

;;;; Signal API helpers

#?(:clj
   (defn signal-arglists [macro-id]
     (case macro-id

       :signal! ; [opts] => <run result> or <allowed?>
       '([{:as opts :keys
           [#_defaults #_elide? #_allow? #_callsite-id,
            elidable? location instant uid middleware,
            sample-rate ns kind id level filter when rate-limit,
            ctx parent trace?, let data msg error run & user-opts]}])

       :log! ; [msg] [level-or-opts msg] => <allowed?>
       '([      msg]
         [level msg]
         [{:as opts :keys
           [#_defaults #_elide? #_allow? #_callsite-id,
            elidable? location instant uid middleware,
            sample-rate ns kind id level filter when rate-limit,
            ctx parent trace?, let data msg error #_run & user-opts]}
          msg])

       :event! ; [id] [level-or-opts id] => <allowed?>
       '([      id]
         [level id]
         [{:as opts :keys
           [#_defaults #_elide? #_allow? #_callsite-id,
            elidable? location instant uid middleware,
            sample-rate ns kind id level filter when rate-limit,
            ctx parent trace?, let data msg error #_run & user-opts]}
          id])

       :error! ; [error] [id-or-opts error] => <error>
       '([   error]
         [id error]
         [{:as opts :keys
           [#_defaults #_elide? #_allow? #_callsite-id,
            elidable? location instant uid middleware,
            sample-rate ns kind id level filter when rate-limit,
            ctx parent trace?, let data msg error #_run & user-opts]}
          error])

       (:trace! :spy!) ; [form] [id-or-opts form] => <run result> (value or throw)
       '([   form]
         [id form]
         [{:as opts :keys
           [#_defaults #_elide? #_allow? #_callsite-id,
            elidable? location instant uid middleware,
            sample-rate ns kind id level filter when rate-limit,
            ctx parent trace?, let data msg error run & user-opts]}
          form])

       :catch->error! ; [form] [level-or-opts form] => <run result> (value or throw)
       '([      form]
         [level form]
         [{:as opts :keys
           [#_defaults #_elide? #_allow? #_callsite-id, rethrow? catch-val,
            elidable? location instant uid middleware,
            sample-rate ns kind id level filter when rate-limit,
            ctx parent trace?, let data msg error #_run & user-opts]}
          form])

       :uncaught->error! ; [] [id-or-opts] => nil
       '([  ]
         [id]
         [{:as opts :keys
           [#_defaults #_elide? #_allow? #_callsite-id,
            elidable? location instant uid middleware,
            sample-rate ns kind id level filter when rate-limit,
            ctx parent trace?, let data msg error #_run & user-opts]}])

       (enc/unexpected-arg! macro-id))))

#?(:clj
   (defn signal-opts
     "Util to help write common signal wrapper macros:
       [[<config>]               val-y] => signal-opts
       [[<config>] opts-or-val-x val-y] => signal-opts"
     ([arg-key or-key defaults             arg-val] {:defaults defaults, arg-key arg-val})
     ([arg-key or-key defaults opts-or-key arg-val]
      (if    (map?   opts-or-key)
        (let [opts   opts-or-key] (conj {:defaults defaults, arg-key arg-val} opts))
        (let [or-val opts-or-key]       {:defaults defaults, arg-key arg-val, or-key or-val})))))

(comment
  [(signal-opts :msg :level {:level :info}                "foo")
   (signal-opts :msg :level {:level :info} {:level :warn} "foo")
   (signal-opts :msg :level {:level :info}         :warn  "foo")])

;;;; Signal macro

#?(:clj
   (defmacro ^:public signal!
     "Expands to a low-level signal call.

     TODO Docstring
      - How low-level is this? Should location, ctx, etc. be in public arglists?
      - Describe
      - Reference diagram link [1]
      - Mention ability to delay-wrap :data
      - Mention combo `:sample-rate` stuff (call * handler)

     - If :run => returns body run-result (re-throwing)
       Otherwise returns true iff call allowed

     [1] Ref. <https://github.com/taoensso/telemere/blob/master/signal-flow.svg>"
     {:arglists (signal-arglists :signal!)}
     [opts]
     (have? map? opts) ; We require const map keys, but vals may require eval
     (let [defaults             (get    opts :defaults)
           opts (merge defaults (dissoc opts :defaults))
           {run-form :run} opts

           {:keys [callsite-id location elide? allow?]}
           (sigs/filterable-expansion
             {:macro-form &form
              :macro-env  &env
              :sf-arity   4
              :ct-sig-filter   ct-sig-filter
              :rt-sig-filter `*rt-sig-filter*}
             opts)]

       (if elide?
         run-form
         (let [{:keys [ns line column file]} location
               {instant-form :instant
                kind-form    :kind
                id-form      :id
                level-form   :level} opts

               trace?         (get opts :trace? (boolean run-form))
               uid-form       (get opts :uid    (when trace? :auto/uuid-str))
               ctx-form       (get opts :ctx                 `taoensso.telemere/*ctx*)
               parent-form    (get opts :parent (when trace? `taoensso.telemere.impl/*trace-parent*))
               instant-form   (get opts :instant  :auto)
               instant-form   (if (= instant-form :auto) `(enc/now-inst*) instant-form)
               uid-form       (parse-uid-form uid-form)
               ;; run-fn-form (when run-form `(fn [] ~run-form))
               run-result-form
               (when run-form
                 `(let [~'__t0 (enc/now-nano*)]
                    (with-tracing ~trace? ~'__id ~'__uid
                      (enc/try*
                        (do               (RunResult. ~run-form nil   (- (enc/now-nano*) ~'__t0)))
                        (catch :any ~'__t (RunResult. nil       ~'__t (- (enc/now-nano*) ~'__t0)))))))

               signal-form
               (let [{let-form         :let
                      data-form        :data
                      msg-form         :msg
                      error-form       :error
                      sample-rate-form :sample-rate}
                     opts

                     let-form  (or let-form '[])
                     msg-form  (parse-msg-form msg-form)

                     ;; No, better leave it to user re: whether or not to delay-wrap
                     ;; data-form
                     ;; (when data-form
                     ;;   (if (enc/call-in-form? data-form)
                     ;;     `(delay ~data-form)
                     ;;     (do      data-form)))

                     user-opts-form
                     (not-empty
                       (dissoc opts
                         :elidable? :location :instant :uid :middleware,
                         :sample-rate :ns :kind :id :level :filter :when #_:rate-limit,
                         :ctx :parent #_:trace?, :let :data :msg :error :run
                         :elide? :allow? :callsite-id))]

                 ;; Eval let bindings AFTER call filtering but BEFORE data, msg
                 `(let ~let-form ; Allow to throw during `signal-value_` deref
                    (new-signal ~'__instant ~'__uid
                      ~callsite-id ~location ~ns ~line ~column ~file,
                      ~sample-rate-form, ~kind-form ~'__id ~level-form, ~ctx-form ~parent-form,
                      ~user-opts-form ~data-form ~msg-form,
                      '~run-form ~'__run-result ~error-form)))]

           #_ ; Sacrifice some perf to de-dupe (possibly large) `run-form`
           (let [~'__run-fn ~run-fn-form]
             (if-not ~allow?
               (when ~'__run-fn (~'__run-fn))
               (let [])))

           `(enc/if-not ~allow? ; Allow to throw at call
              ~run-form
              (let [~'__instant    ~instant-form    ; Allow to throw at call
                    ~'__id         ~id-form         ; ''
                    ~'__uid        ~uid-form        ; ''
                    ~'__run-result ~run-result-form ; Non-throwing (traps)

                    ~'__call-middleware
                    ~(get opts :middleware
                       `taoensso.telemere/*middleware*)]

                (dispatch-signal!
                  (WrappedSignal. ; Same internal value sent (conditionally) to all handlers
                    ~ns ~kind-form ~'__id ~level-form

                    ;; Cache shared by all handlers. Covers signal `:let` eval, signal construction,
                    ;; middleware (possibly expensive), etc.
                    (delay

                      ;; The unwrapped signal value actually visible to users/handler-fns, realized only
                      ;; AFTER handler filtering. Allowed to throw on deref (handler will catch).
                      (let [~'__signal ~signal-form] ; Can throw
                        (if ~'__call-middleware
                          ((sigs/get-middleware-fn ~'__call-middleware) ~'__signal) ; Can throw
                          (do                                           ~'__signal))))))

                (if    ~'__run-result
                  (do (~'__run-result))
                  true))))))))

(comment
  (with-signal  (signal! {:level :warn :let [x :x] :msg ["Test" "message" x] :data {:a :A :x x} :run (+ 1 2)}))
  (macroexpand '(signal! {:level :warn :let [x :x] :msg ["Test" "message" x] :data {:a :A :x x} :run (+ 1 2)}))

  (do
    (println "---")
    (sigs/with-handler *sig-handlers* "hf1" (fn hf1 [x] (println x)) {}
      (signal! {:level :info, :run "run"}))))

#?(:clj
   (defmacro signal-allowed?
     "Used only for interop (SLF4J, `clojure.tools.logging`, etc.)."
     {:arglists (signal-arglists :signal!)}
     [opts]
     (let [{:keys [#_callsite-id #_location elide? allow?]}
           (sigs/filterable-expansion
             {:macro-form &form
              :macro-env  &env
              :sf-arity   4
              :ct-sig-filter   ct-sig-filter
              :rt-sig-filter `*rt-sig-filter*}
             opts)]

       (and (not elide?) allow?))))
