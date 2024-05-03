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
  "Returns the given signal without user-level kvs."
  [signal]
  (if-let [kvs (get signal :kvs)]
    (reduce-kv (fn [m k _v] (dissoc m k)) (dissoc signal :kvs) kvs)
    signal))

(comment (remove-kvs {:a :A, :b :B, :kvs {:a :A}}))

(defn minify-signal
  "Experimental, subject to change.
  Returns minimal signal map, removing:
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
     Returns a new `java.io.FileOutputStream` for given `java.io.File`, etc."
     ^java.io.FileOutputStream [file append?]
     (java.io.FileOutputStream. (as-file file) (boolean append?))))

#?(:clj
   (defn file-writer
     "Experimental, subject to change. Feedback welcome!

     Opens the specified file and returns a stateful fn of 2 arities:
       [content] => Writes given content to file, or no-ops if closed.
       []        => Closes the writer.

     Useful for basic handlers that write to a file, etc.

     Notes:
       - Automatically creates file and parent dirs as necessary.
       - Writer should be manually closed after use (with zero-arity call).
       - Flushes after every write.
       - Thread safe, locks on single file stream."

     [file append?]
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

(comment (def fw1 (file-writer "test.txt" true)) (fw1 "x") (fw1))

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
     "Experimental, subject to change. Feedback welcome!

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

     [host port
      {:keys
       [ssl? connect-timeout-msecs,
        socket-fn ssl-socket-fn] :as opts

       :or
       {connect-timeout-msecs 3000
        socket-fn     default-socket-fn
        ssl-socket-fn default-ssl-socket-fn}}]

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
    - Returns a formatted human-readable string like:
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
           (s+nl class "/" method " at " file ":" line)))
       (str sb))))

(comment (println (format-clj-stacktrace (:trace (enc/ex-map (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"})))))))

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

           (let [s+cause (enc/sb-appender sb (str nls "Caused: "))]
             (s+ "  Root: ")
             (doseq [{:keys [type msg data]} (rseq chain)]
               (s+cause type " - " msg)
               (when data
                 (s+ nl "  data: " (enc/pr-edn* data)))))

           (when trace
             (s+ nl nl "Root stack trace:" nl)
             #?(:cljs (s+                    trace)
                :clj  (format-clj-stacktrace trace)))

           (str sb)))))))

(comment
  (do                         (throw      (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"}))))
  (do                         (enc/ex-map (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"}))))
  (println (str "--\n" ((format-error-fn) (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"}))))))

(defn format-signal->prelude-fn
  "Experimental, subject to change.
  Returns a (fn format [signal]) that:
    - Takes a Telemere signal.
    - Returns a formatted prelude string like:
      \"2024-03-26T11:14:51.806Z INFO EVENT Hostname taoensso.telemere(2,21) ::ev-id - msg\""
  ([] (format-signal->prelude-fn nil))
  ([{:keys [format-inst-fn]
     :or   {format-inst-fn (format-inst-fn)}}]

   (fn format-signal->prelude [signal]
     (let [{:keys [inst level kind ns id msg_]} signal
           sb    (enc/str-builder)
           s+spc (enc/sb-appender sb " ")]

       (when inst  (when-let [ff format-inst-fn] (s+spc (ff inst))))
       (when level (s+spc (format-level level)))

       (if kind (s+spc (upper-qn kind)) (s+spc "DEFAULT"))
       #?(:clj  (s+spc (hostname)))

       ;; "<ns>:(<line>,<column>)"
       (when-let [base (or ns (get signal :file))]
         (let [s+ (partial enc/sb-append sb)] ; Without separator
           (s+ " " base)
           (when-let [l (get signal :line)]
             (s+ "(" l)
             (when-let [c (get signal :column)] (s+ "," c))
             (s+ ")"))))

       (when id (s+spc (format-id ns id)))
       (when-let [msg (force msg_)] (s+spc "- " msg))
       (str sb)))))

(comment ((format-signal->prelude-fn) (tel/with-signal (tel/event! ::ev-id))))

(defn ^:no-doc signal-content-handler
  "Private, don't use.
  Returns a (fn handle [signal handle-fn value-fn]) for internal use.
  Content equivalent to `format-signal->prelude-fn`."
  ([] (signal-content-handler nil))
  ([{:keys
     [format-nsecs-fn
      format-error-fn
      raw-error?
      incl-thread?
      incl-kvs?]

     :or
     {format-nsecs-fn (format-nsecs-fn) ; (fn [nanosecs])
      format-error-fn (format-error-fn) ; (fn [error])
      }}]

   (let [err-start (str newline "<<< error <<<" newline)
         err-stop  (str newline ">>> error >>>")]

     (fn a-signal-content-handler [signal hf vf]
       (let [{:keys [uid parent data kvs ctx #?(:clj thread) sample-rate]} signal]
         (when              sample-rate          (hf "sample: " (vf sample-rate)))
         (when              uid                  (hf "   uid: " (vf uid)))
         (when              parent               (hf "parent: " (vf parent)))
         #?(:clj (when (and thread incl-thread?) (hf "thread: " (vf thread))))
         (when              data                 (hf "  data: " (vf data)))
         (when         (and kvs incl-kvs?)       (hf "   kvs: " (vf kvs)))
         (when              ctx                  (hf "   ctx: " (vf ctx))))

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
  ([{:keys [pr-edn-fn prep-fn end-with-newline?]
     :or
     {pr-edn-fn pr-edn
      prep-fn (comp error-in-signal->maps minify-signal)
      end-with-newline? true}}]

   (let [nl newline]
     (fn format-signal->edn [signal]
       (let [signal* (if prep-fn (prep-fn signal) signal)
             output  (pr-edn-fn signal*)]

         (if end-with-newline?
           (str output nl)
           (do  output)))))))

(comment ((format-signal->edn-fn) {:level :info, :msg "msg"}))

(defn format-signal->json-fn
  "Experimental, subject to change.
  Returns a (fn format->json [signal]) that:
    - Takes a Telemere signal.
    - Returns JSON string of the (minified) signal.

  (Clj only): An appropriate `:pr-json-fn` MUST be provided.
    jsonista is one good option, Ref. <https://github.com/metosin/jsonista>:

    (require '[jsonista.core :as jsonista])
    (format-signal->json-fn {:pr-json-fn jsonista/write-value-as-string ...})"

  ([] (format-signal->json-fn nil))
  ([{:keys [pr-json-fn prep-fn end-with-newline?]
     :or
     {#?@(:cljs [pr-json-fn pr-json])
      prep-fn (comp error-in-signal->maps minify-signal)
      end-with-newline? true}}]

   (when-not pr-json-fn
     (throw
       (ex-info (str "No `" `format-signal->json-fn "` `:pr-json-fn` was provided") {})))

   (let [nl newline]
     (fn format-signal->json [signal]
       (let [signal* (if prep-fn (prep-fn signal) signal)
             output  (pr-json-fn signal*)]

         (if end-with-newline?
           (str output nl)
           (do  output)))))))

(comment ((format-signal->json-fn) {:level :info, :msg "msg"}))

(defn format-signal->str-fn
  "Experimental, subject to change.
  Returns a (fn format->str [signal]) that:
    - Takes a Telemere signal.
    - Returns a formatted string intended for text consoles, etc."
  ([] (format-signal->str-fn nil))
  ([{:keys
     [format-signal->prelude-fn
      format-nsecs-fn
      format-error-fn
      incl-thread?
      incl-kvs?
      end-with-newline?]

     :or
     {format-signal->prelude-fn (format-signal->prelude-fn) ; (fn [signal])
      format-nsecs-fn           (format-nsecs-fn)           ; (fn [nanosecs])
      format-error-fn           (format-error-fn)           ; (fn [error])
      end-with-newline? true}}]

   (let [nl newline
         signal-content-handler ; (fn [signal hf vf]
         (signal-content-handler
           {:format-nsecs-fn format-nsecs-fn
            :format-error-fn format-error-fn
            :incl-thread?    incl-thread?
            :incl-kvs?       incl-kvs?})]

     (fn format-signal->str [signal]
       (let [sb  (enc/str-builder)
             s+  (partial enc/sb-append sb)
             s++ (partial enc/sb-append sb (str newline " "))]

         (when-let [ff format-signal->prelude-fn] (s+ (ff signal))) ; Prelude
         (signal-content-handler signal s++ enc/pr-edn*) ; Content
         (when end-with-newline? (enc/sb-append sb nl))
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
