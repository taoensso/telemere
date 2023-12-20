(ns ^:no-doc taoensso.telemere.impl
  "Private ns, implementation detail.
  Signal design shared by: Telemere, Tufte, Timbre."
  (:require
   [taoensso.encore             :as enc :refer [have have?]]
   [taoensso.encore.signals     :as sigs]
   [taoensso.encore.signals.api :as sigs-api]))

(comment
  (remove-ns 'taoensso.telemere.impl)
  (:api (enc/interns-overview)))

;;;; Config

#?(:clj
   (let [base      (enc/get-sys-value {:as :edn} :taoensso.telemere/ct-filters)
         ns-filter (enc/get-sys-value {:as :edn} :taoensso.telemere.ct-ns-filter)
         id-filter (enc/get-sys-value {:as :edn} :taoensso.telemere.ct-id-filter)
         min-level (enc/get-sys-value {:as :edn} :taoensso.telemere.ct-min-level)]

     (enc/defonce ct-sig-filter
       "`SigFilter` used for compile-time elision, or nil."
       (sigs/sig-filter
         {:ns-filter (or ns-filter (get base :ns-filter))
          :id-filter (or id-filter (get base :id-filter))
          :min-level (or min-level (get base :min-level))}))))

(let [base      (enc/get-sys-value {:as :edn} :taoensso.telemere/rt-filters)
      ns-filter (enc/get-sys-value {:as :edn} :taoensso.telemere.rt-ns-filter)
      id-filter (enc/get-sys-value {:as :edn} :taoensso.telemere.rt-id-filter)
      min-level (enc/get-sys-value {:as :edn} :taoensso.telemere.rt-min-level)]

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
     (when uid-form
       (case uid-form
         :auto/uuid            `(enc/uuid)
         :auto/uuid-str        `(enc/uuid-str)
         :auto/nanoid          `(enc/nanoid)
         :auto/nanoid-readable `(nanoid-readable)
         uid-form))))

(comment
  (enc/qb 1e6 ; [197.4 218.91 330.26 555.47]
    (enc/uuid)
    (enc/uuid-str)
    (enc/nanoid)
    (nanoid-readable)))

;;;; Messages

(deftype MsgSplice [args])
(deftype MsgSkip   [])

(defn ^:public msg-splice [args] (MsgSplice. args))
(defn ^:public msg-skip   []     (MsgSkip.))

(let [;; xform (map #(if (nil? %) "nil" %))
      xform
      (fn [rf]
        (let [;; Protocol-based impln (extensible but ~20% slower)
              ;; rf* (fn rf* [acc in] (reduce-msg-arg in acc rf))
              rf*
              (fn rf* [acc in]
                (enc/cond
                  (instance? MsgSplice in) (let [args (.args ^MsgSplice in)] (if (empty? args) acc (reduce rf* acc args)))
                  (instance? MsgSkip   in)     acc
                  (nil?                in) (rf acc "nil")
                  :else                    (rf acc in)))]
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
  (enc/qb 2e6 ; [317.72 248.44]
    (str         "x" "y" "z" nil :kw)
    (signal-msg ["x" "y" "z" nil :kw])))

#?(:clj
   (defn- parse-msg-form [msg-form]
     (when msg-form
       (enc/cond
         (string? msg-form) msg-form
         (vector? msg-form)
         (enc/cond
           (empty? msg-form) nil
           :let [[m1 & more] msg-form]

           (and (string? m1) (nil? more)) m1

           :else `(delay (signal-msg ~msg-form)))

         :else
         (if (enc/call-in-form? msg-form)
           `(delay             ~msg-form)
           (do                  msg-form))))))

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

(deftype #_defrecord WrappedSignal [ns kind id level signal-value_]
  sigs/IFilterableSignal
  (allow-signal? [_ sig-filter] (sig-filter ns kind id level))
  (signal-value  [_] (force signal-value_)))

(defrecord Signal
  ;; Telemere's main public data type.
  ;; Wwe avoid nesting and duplication.
  [^long schema-version timestamp uid,
   callsite-id location ns line column file,
   kind id level, ctx parent,
   data msg_ error run-form run-value,
   end-timestamp ^long runtime-nsecs]

  sigs/IFilterableSignal
  (allow-signal? [_ sig-filter] (sig-filter ns id level))
  (signal-value  [this] this))

;;;; Handlers

(enc/defonce ^:dynamic *sig-spy* "To support `with-signal`" nil)
(enc/defonce ^:dynamic *sig-handlers*
  "{<handler-id> (fn wrapped-handler-fn [^Signal signal])}"
  nil)

#?(:clj
   (defmacro ^:public with-signal
     "Executes given form and returns [<form result> <last signal dispatched by form>].
     Useful for tests/debugging."
     ([                            form] `(with-signal nil ~form))
     ([{:keys [stop-propagation?]} form]
      `(let [v# (volatile! nil)]
         (binding [*sig-spy* [v# ~stop-propagation?]]
           [~form @v#])))))

(defn dispatch-signal!
  "Dispatches given signal to registered handlers, supports `with-signal`."
  [signal]
  (or
    (when-let [[v stop-propagation?] *sig-spy*]
      (vreset! v (sigs/signal-value signal))
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
  [#_schema-version timestamp uid,
   callsite-id location ns line column file,
   kind id level, ctx parent,
   user-opts data msg_,
   run-form run-result error]

  (let [signal
        (if-let [^RunResult run-result run-result]
          (let  [runtime-nsecs (.-runtime-nsecs run-result)
                 end-timestamp
                 #?(:clj  (.plusNanos ^java.time.Instant timestamp runtime-nsecs)
                    :cljs (js/Date. (+ (.getTime timestamp) (/ runtime-nsecs 1e6))))]

            (Signal. 1 timestamp uid,
             callsite-id location ns line column file,
             kind id level, ctx parent,
             data msg_,
             (.-error run-result)
             (do      run-form)
             (.-value run-result)
             end-timestamp
             runtime-nsecs))

          (Signal. 1 timestamp uid,
            callsite-id location ns line column file,
            kind id level, ctx parent,
            data msg_, error nil nil timestamp 0))]

    (if user-opts
      (reduce-kv assoc signal user-opts)
      (do              signal))))

(comment (enc/qb 1e6 (new-signal nil nil nil nil nil nil nil nil nil nil nil nil nil nil nil nil nil nil nil))) ; 54.8

;;;; Signal macro

#?(:clj
   (defmacro ^:public signal!
     "Expands to a low-level signal call.

     TODO
      - Describe
      - Reference diagram link [1]
      - Mention option to delay-wrap data

     [1] Ref. <https://github.com/taoensso/telemere/blob/master/signal-flow.svg>"
     {:arglists
      '([{:keys
          [elidable? location timestamp uid middleware,
           sample ns kind id level filter when rate-limit,
           ctx parent trace?, let data msg error run & user-opts]}])}

     [{:keys [elidable? location]
       :or   {elidable? true}
       :as   opts}]

     (have? map? opts) ; We require const map keys, but vals may require eval
     (let [location (or location (enc/get-source &form &env))
           {run-form :run} opts

           {:keys [callsite-id elide? allow?]}
           (sigs/filterable-expansion ; Filter call
             {:location location
              :opts-arg opts ; {:keys [sample ns kind id level filter/when rate-limit]}
              :sf-arity 4
              :ct-sig-filter   ct-sig-filter
              :rt-sig-filter `*rt-sig-filter*})

           callsite-id (get opts :callsite-id callsite-id)
           elide?      (get opts :elide?      elide?)
           allow?      (get opts :allow?      allow?)]

       (if (and elide? elidable?)
         run-form
         (let [{:keys [ns line column file]} location
               {timestamp-form :timestamp
                kind-form      :kind
                id-form        :id
                level-form     :level} opts

               trace?         (get opts :trace? (boolean run-form))
               uid-form       (get opts :uid    (when trace? :auto/uuid-str))
               ctx-form       (get opts :ctx                 `taoensso.telemere/*ctx*)
               parent-form    (get opts :parent (when trace? `taoensso.telemere.impl/*trace-parent*))
               timestamp-form (or timestamp-form `(enc/now-inst*))

               uid-form       (parse-uid-form uid-form)
               ;; run-fn-form (when run-form `(fn [] ~run-form))
               run-result-form
               (when run-form
                 `(let [~'t0 (enc/now-nano*)]
                    (with-tracing ~trace? ~'__id ~'__uid
                      (enc/try*
                        (do             (RunResult. ~run-form nil (- (enc/now-nano*) ~'t0)))
                        (catch :any ~'t (RunResult. nil       ~'t (- (enc/now-nano*) ~'t0)))))))

               signal-form
               (let [{let-form   :let
                      data-form  :data
                      msg-form   :msg
                      error-form :error} opts

                     let-form  (or let-form '[])
                     msg-form  (parse-msg-form msg-form)

                     ;; No, leave it to user whether or not to delay-wrap
                     ;; data-form
                     ;; (when data-form
                     ;;   (if (enc/call-in-form? data-form)
                     ;;     `(delay ~data-form)
                     ;;     (do      data-form)))

                     user-opts-form
                     (not-empty
                       (dissoc opts
                         :elidable? :location :timestamp :uid :middleware,
                         :sample :ns :kind :id :level :filter :when #_:rate-limit,
                         :ctx :parent #_:trace?, :let :data :msg :error :run))]

                 ;; Eval let bindings AFTER call filtering but BEFORE data, msg
                 `(let ~let-form ; Allow to throw during @signal-value_
                    (new-signal ~'__timestamp ~'__uid
                      ~callsite-id ~location ~ns ~line ~column ~file,
                      ~kind-form ~'__id ~level-form, ~ctx-form ~parent-form,
                      ~user-opts-form ~data-form ~msg-form,
                      '~run-form ~'__run-result ~error-form)))]

           #_ ; Sacrifice some perf to de-dupe (possibly large) `run-form`
           (let [~'__run-fn ~run-fn-form]
             (if-not ~allow?
               (when ~'__run-fn (~'__run-fn))
               (let [])))

           `(enc/if-not ~allow? ; Allow to throw at call
              ~run-form
              (let [~'__timestamp  ~timestamp-form  ; Allow to throw at call
                    ~'__id         ~id-form         ; ''
                    ~'__uid        ~uid-form        ; ''

                    ~'__run-result ~run-result-form ; Non-throwing (traps)
                    ~'__call-middleware ~(get opts :middleware `taoensso.telemere/*middleware*)]

                (dispatch-signal!
                  (WrappedSignal.
                    ~ns ~kind-form ~'__id ~level-form
                    (delay
                      ;; Unwrapped signal value given to handler fns, realized only AFTER
                      ;; handler filtering. Can throw on deref (handler will catch).
                      (let [~'signal ~signal-form] ; Can throw (in above delay)
                        (if ~'__call-middleware
                          ((sigs-api/get-middleware-fn ~'__call-middleware) ~'signal) ; Can throw
                          (do                                               ~'signal))))))

                (when ~'__run-result
                  (~'__run-result)))))))))

(comment
  (with-signal  (signal! {:level :warn :let [x :x] :msg ["Test" "message" x] :data {:a :A :x x} :run (+ 1 2)}))
  (macroexpand '(signal! {:level :warn :let [x :x] :msg ["Test" "message" x] :data {:a :A :x x} :run (+ 1 2)}))

  ;; Basic flow benchmarks (2020 Apple MBP M1)
  (binding [*sig-handlers* nil]

    [(enc/qb 1e6 ; [9.65 17.35 208.51 229.5]
       (signal! {:level :info, :run nil, :elide? true})
       (signal! {:level :info, :run nil, :allow? false})
       (signal! {:level :info, :run nil, :allow? true })
       (signal! {:level :info, :run nil}))

     (enc/qb 1e6 ; [8.36 15.78 722.11 309.06 739.22]
       (signal! {:level :info, :run "run", :elide? true})
       (signal! {:level :info, :run "run", :allow? false})
       (signal! {:level :info, :run "run", :allow? true })
       (signal! {:level :info, :run "run", :trace? false})
       (signal! {:level :info, :run "run"}))]))
