(ns ^:no-doc taoensso.telemere.utils
  "Misc utils useful for Telemere handlers, middleware, etc."
  (:refer-clojure :exclude [newline])
  (:require
   [clojure.string        :as str]
   [taoensso.encore       :as enc :refer [have have?]]
   [taoensso.encore.stats :as stats]))

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

(enc/defaliases enc/pr-edn enc/newline)

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
     NB: assumes that `js/console` exists!"
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

(defn error-in-signal->chain
  "Experimental, subject to change.
  Returns given signal with possible `:error` replaced by
  [{:keys [type msg data]} ...] cause chain.

  Useful when serializing signals to edn/JSON/etc."
  [signal]
  (enc/if-let [error (get signal :error)
               chain (enc/ex-chain :as-map error)]
    (assoc signal :error chain)
    (do    signal)))

(comment (error-in-signal->chain {:level :info :error (ex-info "Ex" {})}))

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
  ([{:as _opts}] (fn format-nsecs [nanosecs] (stats/format-nsecs nanosecs))))

(comment ((format-nsecs-fn) 4747463567))

(defn format-instant-fn
  "Experimental, subject to change.
  Returns a (fn format [instant]) that:
    - Takes a platform instant (`java.time.Instant` or `js/Date`).
    - Returns a formatted human-readable string.

  `:formatter` may be a `java.time.format.DateTimeFormatter` (Clj) or
                        `goog.i18n.DateTimeFormat` (Cljs).

  Defaults to `ISO8601` form (`YYYY-MM-DDTHH:mm:ss.sssZ`),
    e.g.: \"2011-12-03T10:15:130Z\"."

  ([] (format-instant-fn nil))
  #?(:cljs
     ([{:keys [formatter]}]
      ;; (enc/instance! goog.i18n.DateTimeFormat formatter) ; Not required here
      (if formatter  ; `goog.i18n.DateTimeFormat`
        (fn format-instant [instant] (.format formatter instant))
        (fn format-instant [instant] (.toISOString      instant))))

     :clj
     ([{:keys [formatter]
        :or   {formatter java.time.format.DateTimeFormatter/ISO_INSTANT}}]
      (enc/instance! java.time.format.DateTimeFormatter   formatter) ; Thread safe
      (let [^java.time.format.DateTimeFormatter formatter formatter]
        (fn format-instant [^java.time.Instant instant]
          (.format formatter instant))))))

(comment ((format-instant-fn) (enc/now-inst)))

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
  ([{:keys [format-instant-fn]
     :or   {format-instant-fn (format-instant-fn)}}]
   (fn format-signal-prelude [signal]
     (let [{:keys [instant level kind ns id msg_]} signal
           sb (enc/str-builder)
           s+ (enc/sb-appender sb " ")]

       (when instant (when-let [ff format-instant-fn] (s+ (ff instant))))
       (when level   (s+ (format-level level)))

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
      prep-fn (comp error-in-signal->chain minify-signal)}}]

   (fn format-signal->edn [signal]
     (let [signal* (if prep-fn (prep-fn signal) signal)]
       (pr-edn-fn signal*)))))

(def ^:private need-json-fn-error-msg
  (str "`" `format-signal->json-fn "` needs a `:pr-json-fn` value."))

(defn format-signal->json-fn
  "Experimental, subject to change.
  Returns a (fn format->json [signal]) that:
    - Takes a Telemere signal.
    - Returns JSON string of the (minified) signal.

  MUST provide relevant unary `:pr-json-fn`, e.g.:
    `jsonista.core/write-value-as-string` ; Ref. <https://github.com/metosin/jsonista>
    `clojure.data.json/write-str`         ; Ref. <https://github.com/clojure/data.json>
    `cheshire.core/generate-string`       ; Ref. <https://github.com/dakrone/cheshire>"

  ;; ([] (format-signal->json-fn nil)) ; No useful default
  ([{:keys [pr-json-fn prep-fn]
     :or   {prep-fn (comp error-in-signal->chain minify-signal)}}]

   (when (nil? pr-json-fn) (throw (ex-info need-json-fn-error-msg {})))

   (fn format-signal->json [signal]
     (let [signal* (if prep-fn (prep-fn signal) signal)]
       (pr-json-fn signal*)))))

(defn format-signal->str-fn
  "Experimental, subject to change.
  Returns a (fn format->str [signal]) that:
    - Takes a Telemere signal.
    - Returns a formatted string intended for text consoles, etc."
  ([] (format-signal->str-fn nil))
  ([{:keys [#_format-instant-fn format-error-fn
            format-signal-prelude-fn format-nsecs-fn]
     :or
     {;; format-instant-fn     (format-instant-fn)        ; (fn [instant])
      format-error-fn          (format-error-fn)          ; (fn [error])
      format-signal-prelude-fn (format-signal-prelude-fn) ; (fn [signal])
      format-nsecs-fn          (format-nsecs-fn)          ; (fn [nanosecs])
      }}]

   (let [pr-edn    enc/pr-edn*
         upper-qn  upper-qn
         fmt-level format-level
         fmt-id    format-id
         nl        newline]

     (fn format-signal->str [signal]
       (let [sig signal
             sb  (enc/str-builder)
             s+  (partial enc/sb-append sb)
             s++ (partial enc/sb-append sb (str nl " "))
             err-start (str nl "<<< error start <<<" nl)
             err-stop  (str nl ">>> error end >>>")]

         ;; "2024-03-26T11:14:51.806Z INFO EVENT Hostname taoensso.telemere(2,21) ::ev-id - msg", etc.
         (when-let [ff format-signal-prelude-fn] (s+ (ff sig))) ; Prelude

         ;; Content
         (let [{:keys [uid parent data user-kvs ctx sample-rate]} sig]
           (when sample-rate (s++ "sample: " (pr-edn sample-rate)))
           (when uid         (s++ "   uid: " (pr-edn uid)))
           (when parent      (s++ "parent: " (pr-edn parent)))
           (when data        (s++ "  data: " (pr-edn data)))
           (when user-kvs    (s++ "   kvs: " (pr-edn user-kvs)))
           (when ctx         (s++ "   ctx: " (pr-edn ctx))))

         (let [{:keys [run-form error]} sig]
           (when run-form
             (let [{:keys [run-val run-nsecs]} sig
                   run-time (when run-nsecs (when-let [ff format-nsecs-fn] (ff run-nsecs)))]
               (if error
                 (s++ "   run: "
                   (pr-edn
                     {:form  run-form
                      :time  run-time
                      :nsecs run-nsecs}))

                 (s++ "   run: "
                   (pr-edn
                     {:form  run-form
                      :time  run-time
                      :nsecs run-nsecs
                      :val   run-val
                      #?@(:clj [:val-type (enc/class-sym run-val)])})))))

           (when error
             (when-let [ff format-error-fn]
               (s++ #_" error: " err-start (ff error) err-stop))))

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
