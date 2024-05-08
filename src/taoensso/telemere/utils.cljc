(ns taoensso.telemere.utils
  "Misc utils useful for Telemere handlers, middleware, etc."
  (:refer-clojure :exclude [newline])
  (:require
   [clojure.string          :as str]
   #?(:clj [clojure.java.io :as jio])
   [taoensso.encore         :as enc :refer [have have?]]
   [taoensso.telemere.impl  :as impl]))

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

(comment
  (format-id (str *ns*) ::id1)
  (format-id nil ::id1))

;;;; Public misc

(enc/defaliases enc/newline enc/pr-edn #?(:cljs enc/pr-json) #?(:clj impl/thread-info))

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
  #?(:cljs {:tag 'boolean})
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

(defn remove-kvs
  "Returns given signal without user-level kvs."
  [signal]
  (if-let [kvs (get signal :kvs)]
    (reduce-kv (fn [m k _v] (dissoc m k)) (dissoc signal :kvs) kvs)
    signal))

(comment (remove-kvs {:a :A, :b :B, :kvs {:a :A}}))

(defn minify-signal
  "Experimental, subject to change.
  Returns minimal signal, removing:
    - Keys with nil values, and
    - Keys with redundant values (`:kvs`, `:location`, `:file`).

  Useful when serializing signals to edn/JSON/etc."

  ;; Note that while handlers typically don't include user-level kvs, we
  ;; DO retain these here since signal serialization often implies transit
  ;; to some other system that may still need/want this info before final
  ;; processing/storage/etc.

  [signal]
  (reduce-kv
    (fn [m k v]
      (if (nil? v)
        m
        (case k
          (:kvs :location :file) m
          (assoc m k v))))
    nil signal))

(comment
  (minify-signal (tel/with-signal (tel/event! ::ev-id1)))
  (let [s        (tel/with-signal (tel/event! ::ev-id1))]
    (enc/qb 1e6 ; 683
      (minify-signal s))))

;;;; Files

#?(:clj (defn ^:no-doc as-file ^java.io.File [file] (jio/as-file file)))
#?(:clj
   (defn ^:no-doc writeable-file!
     "Private, don't use.
     Returns writable `java.io.File`, or throws."
     ^java.io.File [file]
     (let [file (as-file  file)]
       (when-not (.exists file)
         (when-let [parent (.getParentFile file)] (.mkdirs parent))
         (.createNewFile file))

       (if (.canWrite file)
         file
         (throw
           (ex-info "Unable to prepare writable `java.io.File`"
             {:path (.getAbsolutePath file)}))))))

#?(:clj
   (defn ^:no-doc file-stream
     "Private, don't use.
     Returns new `java.io.FileOutputStream` for given `java.io.File`."
     ^java.io.FileOutputStream [file append?]
     (java.io.FileOutputStream. (as-file file) (boolean append?))))

#?(:clj
   (defn file-writer
     "Experimental, subject to change.
     Opens the specified file and returns a stateful fn of 2 arities:
       [content] => Writes given content to file, or no-ops if closed.
       []        => Closes the writer.

     Useful for basic handlers that write to a file, etc.

     Notes:
       - Automatically creates file and parent dirs as necessary.
       - Writer should be manually closed after use (with zero-arity call).
       - Flushes after every write.
       - Thread safe, locks on single file stream."

     [{:keys [file append?]
       :or   {append? true}}]

     (when-not file (throw (ex-info "Expected `:file` value" (enc/typed-val file))))

     (let [file    (writeable-file! file)
           stream_ (volatile! (file-stream file append?))
           open?_  (enc/latom true)

           close!
           (fn []
             (when (compare-and-set! open?_ true false)
               (when-let [^java.io.FileOutputStream stream (.deref stream_)]
                 (.close  stream)
                 (vreset! stream_ nil)
                 true)))

           reset!
           (fn []
             (close!)
             (vreset! stream_ (file-stream file append?))
             (reset!  open?_  true)
             true)

           write-ba!
           (fn [^bytes ba-content]
             (when-let [^java.io.FileOutputStream stream (.deref stream_)]
               (.write stream ba-content)
               (.flush stream)
               true))

           file-exists!
           (let [rl (enc/rate-limiter-once-per 250)]
             (fn []
               (or (rl) (.exists file)
                 (throw (java.io.IOException. "File doesn't exist")))))

           lock (Object.)]

       (fn a-file-writer
         ([] (when (open?_) (locking lock (close!))))
         ([content-or-action]
          (case content-or-action ; Undocumented, for dev/testing
            :writer/open?  (open?_)
            :writer/reset! (locking lock (reset!))
            :writer/state  {:file file, :stream (.deref stream_)}
            (when (open?_)
              (let [content content-or-action
                    ba (enc/str->utf8-ba (str content))]
                (locking lock
                  (try
                    (file-exists!)
                    (write-ba! ba)
                    (catch java.io.IOException _ ; Retry once
                      (reset!)
                      (write-ba! ba))))))))))))

(comment (def fw1 (file-writer {:file "test.txt"})) (fw1 "x") (fw1))

;;;; Sockets

#?(:clj
   (defn- default-socket-fn
     "Returns conected `java.net.Socket`, or throws."
     ^java.net.Socket [host port connect-timeout-msecs]
     (let [addr   (java.net.InetSocketAddress. ^String host (int port))
           socket (java.net.Socket.)]

       (if connect-timeout-msecs
         (.connect socket addr (int connect-timeout-msecs))
         (.connect socket addr))

       socket)))

#?(:clj
   (let [factory_ (delay (javax.net.ssl.SSLSocketFactory/getDefault))]
     (defn- default-ssl-socket-fn
       "Returns connected SSL `java.net.Socket`, or throws."
       ^java.net.Socket [^java.net.Socket socket ^String host port]
       (.createSocket ^javax.net.ssl.SSLSocketFactory @factory_
         socket host (int port) true))))

#?(:clj
   (defn tcp-socket-writer
     "Experimental, subject to change.
     Connects to specified TCP socket and returns a stateful fn of 2 arities:
       [content] => Writes given content to socket, or no-ops if closed.
       []        => Closes the writer.

     Useful for basic handlers that write to a TCP socket, etc.

     Options:
       `:ssl?`                  - Use SSL/TLS?
       `:connect-timeout-msecs` - Connection timeout (default 3000 msecs)
       `:socket-fn`             - (fn [host port timeout]) => `java.net.Socket`
       `:ssl-socket-fn`         - (fn [socket host port])  => `java.net.Socket`

     Notes:
       - Writer should be manually closed after use (with zero-arity call).
       - Flushes after every write.
       - Will retry failed writes once, then drop.
       - Thread safe, locks on single socket stream.
       - Advanced users may want a custom implementation using a connection
         pool and/or more sophisticated retry semantics, etc."

     [{:keys
       [host port, ssl? connect-timeout-msecs,
        socket-fn ssl-socket-fn] :as opts

       :or
       {connect-timeout-msecs 3000
        socket-fn     default-socket-fn
        ssl-socket-fn default-ssl-socket-fn}}]

     (when-not (string? host) (throw (ex-info "Expected `:host` string" (enc/typed-val host))))
     (when-not (int?    port) (throw (ex-info "Expected `:port` int"    (enc/typed-val port))))

     (let [new-conn! ; => [<java.net.Socket> <java.io.OutputStream>], or throws
           (fn []
             (try
               (let [^java.net.Socket socket
                     (let [socket (socket-fn host port connect-timeout-msecs)]
                       (if ssl?
                         (ssl-socket-fn socket host port)
                         (do            socket)))]

                 [socket (.getOutputStream socket)])

               (catch Exception ex
                 (throw (ex-info "Failed to create connection" opts ex)))))

           conn_  (volatile! (new-conn!))
           open?_ (enc/latom true)

           close!
           (fn []
             (when (compare-and-set! open?_ true false)
               (when-let [[^java.net.Socket socket] (.deref conn_)]
                 (.close  socket)
                 (vreset! conn_ nil)
                 true)))

           reset!
           (fn []
             (close!)
             (vreset! conn_ (new-conn!))
             (reset!  open?_ true)
             true)

           write-ba!
           (fn [^bytes ba-content]
             (when-let [[_ ^java.io.OutputStream output] (.deref conn_)]
               (.write output ba-content)
               (.flush output)
               true))

           conn-okay!
           (let [rl (enc/rate-limiter-once-per 250)]
             (fn []
               (or
                 (rl)
                 (when-let [[^java.net.Socket socket] (.deref conn_)]
                   (and
                     (not (.isClosed    socket))
                     (do  (.isConnected socket))))
                 (throw (java.io.IOException. "Bad connection")))))

           lock (Object.)]

       (fn a-tcp-socket-writer
         ([] (when (open?_) (locking lock (close!))))
         ([content-or-action]
          (case content-or-action ; Undocumented, for dev/testing
            :writer/open?  (open?_)
            :writer/reset! (locking lock (reset!))
            :writer/state  {:conn (.deref conn_)}
            (when (open?_)
              (let [content content-or-action
                    ba (enc/str->utf8-ba (str content))]
                (locking lock
                  (try
                    (conn-okay!)
                    (write-ba! ba)
                    (catch Exception _ ; Retry once
                      (reset!)
                      (write-ba! ba))))))))))))

;;;; Formatters

(defn format-nsecs-fn
  "Experimental, subject to change.
  Returns a (fn format [nanosecs]) that:
    - Takes a long nanoseconds (e.g. runtime).
    - Returns a human-readable string like:
      \"1.00m\", \"4.20s\", \"340ms\", \"822μs\", etc."
  ([] (format-nsecs-fn nil))
  ([{:as _opts}] (fn format-nsecs [nanosecs] (enc/format-nsecs nanosecs))))

(comment ((format-nsecs-fn) 4747463567))

(enc/defalias enc/format-inst-fn)

(comment ((format-inst-fn) (enc/now-inst)))

#?(:clj
   (defn- format-clj-stacktrace
     [trace]
     (let [sb   (enc/str-builder)
           s+nl (enc/sb-appender sb enc/newline)]
       (doseq [st-el (force trace)]
         (let [{:keys [class method file line]} st-el]
           (s+nl "  " class "/" method " at " file ":" line)))
       (str sb))))

(comment (println (format-clj-stacktrace (:trace (enc/ex-map (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"})))))))

(defn format-error-fn
  "Experimental, subject to change.
  Returns a (fn format [error]) that:
    - Takes a platform error (`Throwable` or `js/Error`).
    - Returns a human-readable error string."
  ([] (format-error-fn nil))
  ([{:as _opts}]
   (let [nl  enc/newline
         nls enc/newlines]

     (fn format-error [error]
       (when-let [em (enc/ex-map error)]
         (let [sb (enc/str-builder)
               s+ (partial enc/sb-append sb)
               {:keys [chain trace]} em]

           (let [s+cause (enc/sb-appender sb (str nls "Caused: "))]
             (s+ "  Root: ")
             (doseq [{:keys [type msg data]} (rseq chain)]
               (s+cause type " - " msg)
               (when data
                 (s+ nl "  data: " (enc/pr-edn* data)))))

           (when trace
             (s+ nl nl "Root stack trace:" nl)
             #?(:cljs (s+                        trace)
                :clj  (s+ (format-clj-stacktrace trace))))

           (str sb)))))))

(comment
  (do                         (throw      (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"}))))
  (do                         (enc/ex-map (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"}))))
  (println (str "--\n" ((format-error-fn) (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"}))))))

;;;;

(defn signal-preamble-fn
  "Experimental, subject to change.
  Returns a (fn preamble [signal]) that:
    - Takes a Telemere signal (map).
    - Returns a signal preamble ?string like:
      \"2024-03-26T11:14:51.806Z INFO EVENT Hostname taoensso.telemere(2,21) ::ev-id - msg\"

  Options:
    `:format-inst-fn` - (fn format [instant]) => string."
  ([] (signal-preamble-fn nil))
  ([{:keys [format-inst-fn]
     :or   {format-inst-fn (format-inst-fn)}}]

   (fn signal-preamble [signal]
     (let [{:keys [inst level kind ns id msg_]} signal
           sb    (enc/str-builder)
           s+spc (enc/sb-appender sb " ")]

       (when inst  (when-let [ff format-inst-fn] (s+spc (ff inst))))
       (when level (s+spc (format-level level)))

       (if kind (s+spc (upper-qn kind)) (s+spc "DEFAULT"))
       #?(:clj  (s+spc (hostname)))

       ;; "<ns>(<line>,<column>)"
       (when-let [base (or ns (get signal :file))]
         (let [s+ (partial enc/sb-append sb)] ; Without separator
           (s+ " " base)
           (when-let [l (get signal :line)]
             (s+ "(" l)
             (when-let [c (get signal :column)] (s+ "," c))
             (s+ ")"))))

       (when id (s+spc (format-id ns id)))
       (when-let [msg (force msg_)] (s+spc "- " msg))

       (when-not (zero? (enc/sb-length sb))
         (str sb))))))

(comment ((signal-preamble-fn) (tel/with-signal (tel/event! ::ev-id))))

(defn signal-content-fn
  "Experimental, subject to change.
  Returns a (fn content [signal]) that:
    - Takes a Telemere signal (map).
    - Returns a signal content ?string (incl. data, ctx, etc.).

  Options:
    `:incl-thread?`    - Include signal `:thread` info? (default false)
    `:incl-kvs?`       - Include signal `:kvs`    info? (default false)
    `:raw-error?`      - Retain unformatted error?      (default false)
    `:format-nsecs-fn` - (fn [nanosecs]) => string.
    `:format-error-fn` - (fn [error])    => string."

  ([] (signal-content-fn nil))
  ([{:keys
     [incl-thread? incl-kvs? raw-error?,
      format-nsecs-fn format-error-fn]

     :or
     {format-nsecs-fn (format-nsecs-fn) ; (fn [nanosecs])
      format-error-fn (format-error-fn) ; (fn [error])
      }}]

   (let [nl        newline
         err-start (str nl "<<< error <<<" nl)
         err-stop  (str nl ">>> error >>>")]

     (fn signal-content
       ([signal]
        (let [sb  (enc/str-builder)
              s++ (enc/sb-appender sb nl)]
          (signal-content signal s++ enc/pr-edn*)
          (when-not (zero? (enc/sb-length sb))
            (str sb))))

       ;; Undocumented, advanced arity
       ([signal append-fn val-fn]
        (let [af append-fn
              vf    val-fn]

          (let [{:keys [uid parent data kvs ctx #?(:clj thread) sample-rate]} signal]
            (when              sample-rate          (af " sample: " (vf sample-rate)))
            (when              uid                  (af "    uid: " (vf uid)))
            (when              parent               (af " parent: " (vf parent)))
            #?(:clj (when (and thread incl-thread?) (af " thread: " (vf thread))))
            (when              data                 (af "   data: " (vf data)))
            (when         (and kvs incl-kvs?)       (af "    kvs: " (vf kvs)))
            (when              ctx                  (af "    ctx: " (vf ctx))))

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
                (af "    run: " (vf run-info))))

            (when error
              (if raw-error?
                (af "  error: " error)
                (when-let [ff format-error-fn]
                  (af err-start (ff error) err-stop)))))))))))

(comment
  ((signal-content-fn) (tel/with-signal (tel/event! ::ev-id)))
  ((signal-content-fn) (tel/with-signal (tel/event! ::ev-id {:data {:k1 "v1"}}))))

(defn pr-signal-fn
  "Experimental, subject to change.
  Returns a (fn pr [signal]) that:
    - Takes a Telemere signal (map).
    - Returns a machine-readable (minified) signal string.

  Options:
    `:pr-fn`         - ∈ #{<unary-fn> :edn (default) :json (Cljs only)}
    `:incl-thread?`  - Include signal `:thread` info?      (default false)
    `:incl-kvs?`     - Include signal `:kvs`    info?      (default false)
    `:incl-newline?` - Include terminating system newline? (default true)

  Examples:
    (pr-signal-fn {:pr-fn :edn  ...}) ; Outputs edn
    (pr-signal-fn {:pr-fn :json ...}) ; Outputs JSON (Cljs only)

    To output JSON for Clj, you must provide an appropriate `:pr-fn`.
    `jsonista` is one good option, Ref. <https://github.com/metosin/jsonista>:

      (require '[jsonista.core :as jsonista])
      (pr-signal-fn {:pr-fn jsonista/write-value-as-string ...})

  See also `format-signal-fn` for human-readable output."
  ([] (pr-signal-fn nil))
  ([{:keys [incl-thread? incl-kvs? incl-newline?, pr-fn prep-fn]
     :or
     {incl-newline? true
      pr-fn         :edn
      prep-fn
      (comp error-in-signal->maps
        minify-signal)}}]

   (let [nl newline
         pr-fn
         (or
           (case  pr-fn
             :edn pr-edn
             :json
             #?(:cljs pr-json
                :clj
                (throw
                  (ex-info "`:json` pr-fn only supported in Cljs. To output JSON in Clj, please provide an appropriate unary fn instead (e.g. jsonista/write-value-as-string)."
                    {})))

             (if (fn? pr-fn)
               (do    pr-fn)
               (enc/unexpected-arg! pr-fn
                 {:context  `pr-signal-fn
                  :param    'pr-fn
                  :expected
                  #?(:clj  '#{:edn       unary-fn}
                     :cljs '#{:edn :json unary-fn})}))))]

     (fn pr-signal [signal]
       (let [not-map? (not (map? signal))
             signal   (if (or incl-kvs?    not-map?) signal (dissoc signal :kvs))
             signal   (if (or incl-thread? not-map?) signal (dissoc signal :thread))
             signal   (if prep-fn (prep-fn signal) signal)
             output   (pr-fn signal)]

         (if incl-newline?
           (str output nl)
           (do  output)))))))

(comment
  ((pr-signal-fn {:pr-fn :edn})           (tel/with-signal (tel/event! ::ev-id {:kvs {:k1 "v1"}})))
  ((pr-signal-fn {:pr-fn (fn [_] "str")}) (tel/with-signal (tel/event! ::ev-id {:kvs {:k1 "v1"}}))))

(defn format-signal-fn
  "Experimental, subject to change.
  Returns a (fn format [signal]) that:
    - Takes a Telemere signal (map).
    - Returns a human-readable signal string.

  Options:
    `:incl-newline?` - Include terminating system newline? (default true)
    `:preamble-fn`   - (fn [signal]) => signal preamble string.
    `:content-fn`    - (fn [signal]) => signal content  string.

  See also `pr-signal-fn` for machine-readable output."
  ([] (format-signal-fn nil))
  ([{:keys [incl-newline? preamble-fn content-fn]
     :or
     {incl-newline? true
      preamble-fn (signal-preamble-fn)
      content-fn  (signal-content-fn)}}]

   (let [nl newline]
     (fn format-signal [signal]
       (let [preamble (when preamble-fn (preamble-fn signal))
             content  (when content-fn  (content-fn  signal))]

         (if (and preamble content)
           (str preamble nl content (when incl-newline? nl))
           (str preamble    content (when incl-newline? nl))))))))

(comment
  (tel/with-ctx {:c :C}
    (println
      ((format-signal-fn)
       (tel/with-signal
         (tel/event! ::ev-id
           {:user-k1 #{:a :b :c}
            :msg   "hi"
            :data  {:a :A}
            ;; :error (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"}))
            :run   (/ 1 0)}))))))
