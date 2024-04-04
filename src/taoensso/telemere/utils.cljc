(ns taoensso.telemere.utils
  "Misc utils useful for Telemere handlers, middleware, etc."
  (:refer-clojure :exclude [newline])
  (:require
   [clojure.string  :as str]
   [taoensso.encore :as enc :refer [have have?]]))

(comment
  (require  '[taoensso.telemere :as tel])
  (remove-ns 'taoensso.telemere.utils)
  (:api (enc/interns-overview)))

;;;; Private

(enc/def* ^:no-doc upper-qn
  "Private, don't use.
  `:foo/bar` -> \"FOO/BAR\", etc."
  {:tag #?(:clj 'String :cljs 'string)}
  (enc/fmemoize (fn [x] (str/upper-case (enc/as-qname x)))))

(comment (upper-qn :foo/bar))

(enc/def* ^:no-doc format-level
  "Private, don't use.
  `:info` -> \"INFO\",
      `5` -> \"LEVEL:5\", etc."
  {:tag #?(:clj 'String :cljs 'string)}
  (enc/fmemoize
    (fn [x]
      (if (keyword?   x)
        (upper-qn     x)
        (str "LEVEL:" x)))))

(comment (format-level :info))

(enc/def* ^:no-doc format-id
  "Private, don't use.
  `:foo.bar/baz` -> \"::baz\", etc."
  {:tag #?(:clj 'String :cljs 'string)}
  (enc/fmemoize
    (fn [ns x]
      (if (keyword? x)
        (if (= (namespace x) ns)
          (str "::" (name x))
          (str            x))
        (str x)))))

(comment (format-id (str *ns*) ::id1))

;;;; Public misc

(enc/defaliases enc/newline enc/pr-edn enc/pr-json)

#?(:clj (defn thread-name "Returns string name of current thread." ^String [] (.getName (Thread/currentThread))))
#?(:clj (defn thread-id   "Returns long id of current thread."       ^long [] (.getId   (Thread/currentThread))))

(comment [(thread-name) (thread-id)])

#?(:clj
   (defn host-ip
     "Returns cached local host IP address string, or `timeout-val` (default \"UnknownHost\")."
     (        [timeout-msecs timeout-val] (enc/get-host-ip (enc/msecs :mins 1) timeout-msecs timeout-val))
     (^String [                         ] (enc/get-host-ip (enc/msecs :mins 1) 5000 "UnknownHost"))))

#?(:clj
   (defn hostname
     "Returns cached local hostname string, or `timeout-val` (default \"UnknownHost\")."
     (        [timeout-msecs timeout-val] (enc/get-hostname (enc/msecs :mins 1) timeout-msecs timeout-val))
     (^String [                         ] (enc/get-hostname (enc/msecs :mins 1) 3500 (delay (host-ip 1500 "UnknownHost"))))))

(comment (enc/qb 1e6 (hostname))) ; 56.88

#?(:cljs
   (defn js-console-logger
     "Returns JavaScript console logger to match given signal level:
       `:trace` -> `js/console.trace`,
       `:error` -> `js/console.error`, etc.

     Defaults to `js.console.log` for unmatched signal levels.
     NB: assumes that `js/console` exists, handler constructors should check first!"
     [level]
     (case level
       :trace  js/console.trace
       :debug  js/console.debug
       :info   js/console.info
       :warn   js/console.warn
       :error  js/console.error
       :fatal  js/console.error
       :report js/console.info
       (do     js/console.log))))

(comment (js-console-logger))

(defn error-signal?
  "Experimental, subject to change.
  Returns true iff given signal has an `:error` value, or a `:kind` or `:level`
  that indicates that it's an error."
  #?(:cljs {:tag 'string})
  [signal]
  (and signal
    (boolean
      (or
        (get signal :error)
        (enc/identical-kw? (get signal :kind) :error)
        (case (get signal :level) (:error :fatal) true false)
        (get signal :error?) ; User kv
        ))))

(comment (error-signal? {:level :fatal}))

(defn error-in-signal->maps
  "Experimental, subject to change.
  Returns given signal with possible `:error` replaced by
  [{:keys [type msg data]} ...] cause chain.

  Useful when serializing signals to edn/JSON/etc."
  [signal]
  (enc/if-let [error (get signal :error)
               chain (enc/ex-chain :as-map error)]
    (assoc signal :error chain)
    (do    signal)))

(comment (error-in-signal->maps {:level :info :error (ex-info "Ex" {})}))

(defn minify-signal
  "Experimental, subject to change.
  Returns minimal signal map, removing:
    - Keys with nil values, and
    - Keys with redundant values (`:extra-kvs`, `:location`, `:file`).

  Useful when serializing signals to edn/JSON/etc."
  [signal]
  (reduce-kv
    (fn [m k v]
      (if (nil? v)
        m
        (case k
          (:extra-kvs :location :file) m
          (assoc m k v))))
    nil signal))

(comment
  (minify-signal (tel/with-signal (tel/event! ::ev-id1)))
  (let [s        (tel/with-signal (tel/event! ::ev-id1))]
    (enc/qb 1e6 ; 683
      (minify-signal s))))

;;;; Formatters

(defn format-nsecs-fn
  "Experimental, subject to change.
  Returns a (fn format [nanosecs]) that:
    - Takes a long nanoseconds (e.g. runtime).
    - Returns a formatted human-readable string like:
      \"1.00m\", \"4.20s\", \"340ms\", \"822μs\", etc."
  ([] (format-nsecs-fn nil))
  ([{:as _opts}] (fn format-nsecs [nanosecs] (enc/format-nsecs nanosecs))))

(comment ((format-nsecs-fn) 4747463567))

(enc/defalias enc/format-inst-fn)

(comment ((format-inst-fn) (enc/now-inst)))

(defn format-error-fn
  "Experimental, subject to change.
  Returns a (fn format [error]) that:
    - Takes a platform error (`Throwable` or `js/Error`).
    - Returns a formatted human-readable string"
  ([] (format-error-fn nil))
  ([{:as _opts}]
   (let [nl  enc/newline
         nls enc/newlines]

     (fn format-error [error]
       (when-let [em (enc/ex-map error)]
         (let [sb (enc/str-builder)
               s+ (partial enc/sb-append sb)
               {:keys [chain trace]} em]

           (let [s++ (enc/sb-appender sb (str nls "Caused: "))]
             (s+ "  Root: ")
             (doseq [{:keys [type msg data]} (rseq chain)]
               (s++ type " - " msg)
               (when data
                 (s+ nl "  data: " (enc/pr-edn* data)))))

           (when trace
             (s+ nl nl "Root stack trace:")
             #?(:cljs (s+ nl trace)
                :clj
                (doseq [st-el (force trace)]
                  (let [{:keys [class method file line]} st-el]
                    (s+ nl "" class "/" method " at " file ":" line)))))

           (str sb)))))))

(comment
  (do                         (throw      (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"}))))
  (do                         (enc/ex-map (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"}))))
  (println (str "--\n" ((format-error-fn) (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"}))))))

(defn format-signal-prelude-fn
  "Experimental, subject to change.
  Returns a (fn format [signal]) that:
    - Takes a Telemere signal.
    - Returns a formatted prelude string like:
      \"2024-03-26T11:14:51.806Z INFO EVENT Hostname taoensso.telemere(2,21) ::ev-id - msg\""
  ([] (format-signal-prelude-fn nil))
  ([{:keys [format-inst-fn]
     :or   {format-inst-fn (format-inst-fn)}}]

   (fn format-signal-prelude [signal]
     (let [{:keys [inst level kind ns id msg_]} signal
           sb (enc/str-builder)
           s+ (enc/sb-appender sb " ")]

       (when inst  (when-let [ff format-inst-fn] (s+ (ff inst))))
       (when level (s+ (format-level level)))

       (if kind (s+ (upper-qn kind)) (s+ "DEFAULT"))
       #?(:clj  (s+ (hostname)))

       ;; "<ns>:(<line>,<column>)"
       (when-let [base (or ns (get signal :file))]
         (let [s+ (partial enc/sb-append sb)] ; Without separator
           (s+ " " base)
           (when-let [l (get signal :line)]
             (s+ "(" l)
             (when-let [c (get signal :column)] (s+ "," c))
             (s+ ")"))))

       (when id (s+ (format-id ns id)))
       (when-let [msg (force msg_)] (s+ "- " msg))
       (str sb)))))

(comment ((format-signal-prelude-fn) (tel/with-signal (tel/event! ::ev-id))))

(defn ^:no-doc signal-content-handler
  "Private, don't use.
  Returns a (fn handle [signal handle-fn value-fn]) for internal use.
  Content equivalent to `format-signal-prelude-fn`."
  ([] (signal-content-handler nil))
  ([{:keys [format-nsecs-fn format-error-fn raw-error?]
     :or
     {format-nsecs-fn (format-nsecs-fn) ; (fn [nanosecs])
      format-error-fn (format-error-fn) ; (fn [error])
      }}]

   (let [err-start (str newline "<<< error <<<" newline)
         err-stop  (str newline ">>> error >>>")]

     (fn a-signal-content-handler [signal hf vf]
       (let [{:keys [uid parent data extra-kvs ctx sample-rate]} signal]
         (when sample-rate (hf "sample: " (vf sample-rate)))
         (when uid         (hf "   uid: " (vf uid)))
         (when parent      (hf "parent: " (vf parent)))
         (when data        (hf "  data: " (vf data)))
         (when extra-kvs   (hf "   kvs: " (vf extra-kvs)))
         (when ctx         (hf "   ctx: " (vf ctx))))

       (let [{:keys [run-form error]} signal]
         (when run-form
           (let [{:keys [run-val run-nsecs]} signal
                 run-time (when run-nsecs (when-let [ff format-nsecs-fn] (ff run-nsecs)))
                 run-info
                 (if error
                   {:form  run-form
                    :time  run-time
                    :nsecs run-nsecs}

                   {:form  run-form
                    :time  run-time
                    :nsecs run-nsecs
                    :val   run-val
                    #?@(:clj [:val-type (enc/class-sym run-val)])})]

             (hf "   run: " (vf run-info))))

         (when error
           (if raw-error?
             (hf " error: " error)
             (when-let [ff format-error-fn]
               (hf err-start (ff error) err-stop)))))))))

;;;; Signal formatters

(defn format-signal->edn-fn
  "Experimental, subject to change.
  Returns a (fn format->edn [signal]) that:
    - Takes a Telemere signal.
    - Returns edn string of the (minified) signal."
  ([] (format-signal->edn-fn nil))
  ([{:keys [pr-edn-fn prep-fn]
     :or
     {pr-edn-fn pr-edn
      prep-fn (comp error-in-signal->maps minify-signal)}}]

   (fn format-signal->edn [signal]
     (let [signal* (if prep-fn (prep-fn signal) signal)]
       (pr-edn-fn signal*)))))

(comment ((format-signal->edn-fn) {:level :info, :msg "msg"}))

(defn format-signal->json-fn
  "Experimental, subject to change.
  Returns a (fn format->json [signal]) that:
    - Takes a Telemere signal.
    - Returns JSON string of the (minified) signal."
  ([] (format-signal->json-fn nil))
  ([{:keys [pr-json-fn prep-fn]
     :or
     {pr-json-fn pr-json
      prep-fn (comp error-in-signal->maps minify-signal)}}]

   (enc/try*
     (pr-json-fn "telemere/auto-test")
     (catch :all t
       (throw (ex-info (str "`" `format-signal->json-fn "` `:pr-json` test failed") {} t))))

   (fn format-signal->json [signal]
     (let [signal* (if prep-fn (prep-fn signal) signal)]
       (pr-json-fn signal*)))))

(comment ((format-signal->json-fn) {:level :info, :msg "msg"}))

(defn format-signal->str-fn
  "Experimental, subject to change.
  Returns a (fn format->str [signal]) that:
    - Takes a Telemere signal.
    - Returns a formatted string intended for text consoles, etc."
  ([] (format-signal->str-fn nil))
  ([{:keys [format-signal-prelude-fn
            format-nsecs-fn format-error-fn]
     :or
     {format-signal-prelude-fn (format-signal-prelude-fn) ; (fn [signal])
      format-nsecs-fn          (format-nsecs-fn)          ; (fn [nanosecs])
      format-error-fn          (format-error-fn)          ; (fn [error])
      }}]

   (let [signal-content-handler ; (fn [signal hf vf]
         (signal-content-handler
           {:format-nsecs-fn format-nsecs-fn
            :format-error-fn format-error-fn})]

     (fn format-signal->str [signal]
       (let [sb  (enc/str-builder)
             s+  (partial enc/sb-append sb)
             s++ (partial enc/sb-append sb (str newline " "))]

         (when-let [ff format-signal-prelude-fn] (s+ (ff signal))) ; Prelude
         (signal-content-handler signal s++ enc/pr-edn) ; Content
         (str sb))))))

(comment
  (tel/with-ctx {:c :C}
    (println
      ((format-signal->str-fn)
       (tel/with-signal
         (tel/event! ::ev-id
           {:user-k1 #{:a :b :c}
            :msg   "hi"
            :data  {:a :A}
            ;; :error (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"}))
            :run   (/ 1 0)}))))))
