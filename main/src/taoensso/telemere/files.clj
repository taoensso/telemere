(ns ^:no-doc taoensso.telemere.files
  "Telemere -> file handler."
  (:require
   [taoensso.truss  :as truss]
   [taoensso.encore :as enc]
   [taoensso.telemere.utils :as utils]))

(comment
  (require  '[taoensso.telemere :as tel])
  (remove-ns (symbol (str *ns*)))
  (:api (enc/interns-overview)))

;;;; Implementation

(defn gzip-file
  "Compresses contents of `file-in` to `file-out` using gzip."
  [file-in file-out]
  (let [file-in  (utils/as-file file-in)
        file-out (utils/as-file file-out)]

    (with-open
      [stream-in  (java.io.FileInputStream.        file-in)
       stream-out (java.io.FileOutputStream.       file-out)
       gz-out     (java.util.zip.GZIPOutputStream. stream-out 2048 false)]

      (let [read-buffer (byte-array 8192)]
        (loop []
          (let [bytes-read (.read stream-in read-buffer)]
            (when-not (== -1 bytes-read)
              (.write gz-out read-buffer 0 bytes-read)
              (recur))))))

    true))

(comment (gzip-file "foo.txt" "foo.txt.gz"))

(defn get-file-name
  "(main-path)(-YYYY-MM-DD(d/w/m))(.part)?(.gz)?"
  ^String [main-path ?timestamp ?part gz?]
  (str main-path
    (when-let [ts ?timestamp] (str "-" ts))
    (when-let [p  ?part]      (str "." p (when gz? ".gz")))))

(comment (get-file-name "test/logs/app.log" nil nil true))

;; Timestamp handling, edy (long epoch day) as base type
(let [utc java.time.ZoneOffset/UTC
      ^java.time.format.DateTimeFormatter dtf
      (.withZone java.time.format.DateTimeFormatter/ISO_LOCAL_DATE
        utc)]

  (let [cf (* 24 60 60 1000)]
    (defn udt->edy ^long [^long udt] (quot udt cf))
    (defn edy->udt ^long [^long edy] (*    edy cf)))

  (let [ta (java.time.temporal.TemporalAdjusters/previousOrSame java.time.DayOfWeek/MONDAY)]
    (defn edy-week ^long [^long edy] (.toEpochDay (.with (java.time.LocalDate/ofEpochDay edy) ta))))

  (let [ta (java.time.temporal.TemporalAdjusters/firstDayOfMonth)]
    (defn edy-month ^long [^long edy] (.toEpochDay (.with (java.time.LocalDate/ofEpochDay edy) ta))))

  (defn file-timestamp->edy ^long [^String timestamp]
    (let [timestamp (subs timestamp 0 (dec (count timestamp)))]
      (.toEpochDay (java.time.LocalDate/parse timestamp dtf))))

  (defn file-last-modified->edy ^long [^java.io.File file]
    (.toEpochDay (.toLocalDate (.atZone (java.time.Instant/ofEpochMilli (.lastModified file)) utc))))

  (defn format-file-timestamp
    ^String [interval ^long edy]
    (case interval
      :daily   (str (.format dtf (java.time.LocalDate/ofEpochDay            edy))  "d")
      :weekly  (str (.format dtf (java.time.LocalDate/ofEpochDay (edy-week  edy))) "w")
      :monthly (str (.format dtf (java.time.LocalDate/ofEpochDay (edy-month edy))) "m")
      (truss/unexpected-arg! interval
        {:param             'interval
         :context  `file-timestamp
         :expected #{:daily :weekly :monthly}}))))

(comment (file-timestamp->edy (format-file-timestamp :weekly (udt->edy (enc/now-udt*)))))

(defn manage-test-files!
  "Describes/creates/deletes files used for tests/debugging, etc."
  [action]
  (truss/have? [:el #{:return :println :create :delete}] action)
  (let [fnames_ (volatile! [])
        action!
        (fn [app timestamp part gz? timestamp main?]
          (let [path  (str "test/logs/app" app ".log")
                fname (get-file-name path (when-not main? timestamp) part gz?)
                file  (utils/as-file fname)]

            (case action
              :return  nil
              :println (println fname)
              :delete  (.delete file)
              :create
              (do
                (utils/writeable-file! file)
                (spit file fname)
                (when timestamp
                  (.setLastModified file
                    (edy->udt (file-timestamp->edy timestamp))))))

            (vswap! fnames_ conj fname)))]

    (doseq [{:keys [app gz? timestamps parts]}
            [{:app 1}
             {:app 2, :gz? true,  :parts [1 2 3 4 5]}
             {:app 3, :gz? false, :parts [1 2 3 4 5]}

             {:app 4, :gz? true,  :parts [1 2 3 4 5]}
             {:app 4, :gz? false, :parts [1 2 3 4 5]}

             {:app 5, :gz? true, :timestamps
              ["2020-01-01d" "2020-01-02d" "2020-02-01d" "2020-02-02d" "2021-01-01d"
               "2020-01-01w"               "2020-02-01m"]}

             {:app 6, :gz? true, :parts [1 2 3 4 5],
              :timestamps
              ["2020-01-01d" "2020-01-02d" "2020-02-01d" "2020-02-02d" "2021-01-01d"
               "2020-01-01w"               "2020-02-01m"]}]]

      (action! app nil nil false (peek timestamps) :main)

      (doseq [timestamp (or timestamps [nil])
              part      (or parts      [nil])]

        (action! app timestamp part gz? timestamp (not :main))))

    @fnames_))

(comment (manage-test-files! :create))

(defn scan-files
  "Returns ?[{:keys [file edy part ...]}] for files in same dir as `main-path` that:
    - Have the same `interval` type ∈ #{:daily :weekly :monthly nil} (=> ?timestamped).
    - Have the given timestamp (e.g. \"2020-01-01d\", or nil for NO timestamp)."
  [main-path interval timestamp sort?]
  (truss/have? [:el #{:daily :weekly :monthly nil}] interval)
  (let [main-file (utils/as-file main-path) ; `logs/app.log`
        main-dir  (.getParentFile (.getAbsoluteFile main-file)) ; `.../logs`

        file-pattern ; Matches ?[_ timestamp part gz]
        (let [main (str "\\Q" (.getName main-file) "\\E")
              end  "(\\.\\d+)?(\\.gz)?"]

          (if interval
            (let [ts-suffix (case interval :daily "d" :weekly "w" :monthly "m")]
              (re-pattern (str main "-(\\d{4}-\\d{2}-\\d{2}" ts-suffix ")" end)))
            (re-pattern   (str main "(__no-timestamp__)?"                  end))))

        ref-timestamp timestamp
        any-timestamp? (and interval (nil? ref-timestamp))]

    (when-let [file-maps
               (not-empty
                 (reduce
                   (fn [acc ^java.io.File file-in]
                     (or
                       (when-let [[_ timestamp part gz] (re-matches file-pattern (.getName file-in))]
                         (when (or any-timestamp? (= timestamp ref-timestamp))
                           (let [edy
                                 (when timestamp
                                   (try
                                     (file-timestamp->edy timestamp)
                                     (catch java.time.format.DateTimeParseException _)))

                                 part      (when part (enc/as-pos-int (subs part 1)))
                                 gz?       (boolean gz)
                                 file-name (get-file-name main-path timestamp part gz?)]

                             (when (or (nil? timestamp) (some? edy))
                               ;; Verify that scanned file name matches our template
                               (let [actual   (.normalize (.toAbsolutePath (.toPath file-in)))
                                     expected (.normalize (.toAbsolutePath (.toPath (utils/as-file file-name))))]
                                 (when-not (= actual expected)
                                   (truss/ex-info! "Unexpected file name"
                                     {:actual actual, :expected expected})))

                               (conj acc
                                 {:file      file-in
                                  :file-name file-name
                                  :timestamp timestamp
                                  :edy       edy
                                  :part      part
                                  :gz?       gz?})))))
                       acc))
                   [] (.listFiles main-dir)))]

      (if sort? ; For unit tests, etc.
        (sort-by (fn [{:keys [edy part]}] [edy part]) file-maps)
        (do                                           file-maps)))))

(comment (group-by :edy (scan-files "logs/app.log" nil nil false)))
(comment
  (mapv #(select-keys % [:full-name :edy :part :gz?])
    (scan-files "test/logs/app6.log" :daily nil :sort)))

;; Debugger used to test/debug file ops
(defn debugger [] (let [log_ (volatile! [])] (fn ([ ] @log_) ([x] (vswap! log_ conj x)))))

(defn- delete-file!           [^java.io.File file] (java.nio.file.Files/delete         (.toPath file)))
(defn- delete-file-if-exists! [^java.io.File file] (java.nio.file.Files/deleteIfExists (.toPath file)))
(defn- move-file!             [^java.io.File from ^java.io.File to]
  (java.nio.file.Files/move
    (.toPath from) (.toPath to)
    (make-array java.nio.file.CopyOption 0)))

(defn- create-file! [^java.io.File file]
  (java.nio.file.Files/createFile (.toPath file)
    (make-array java.nio.file.attribute.FileAttribute 0)))

(defn archive-main-file!
  "Renames main -> <timestamp>.1.gz archive. Makes room by first rotating
  pre-existing parts (n->n+1) and maintaining `max-num-parts` limit.
  Expensive. Must manually reset any main file streams after!"
  [main-path interval timestamp max-num-parts gz? ?debugger]

  ;; Rename n->n+1, deleting when n+1>max
  (when-let [file-maps (scan-files main-path interval timestamp false)] ; [<file-map> ...]
    (let [file-maps-by-edy (group-by :edy file-maps)]            ; {<edy> [<file-map> ...]}
      (enc/run-kv!
        (fn [edy file-maps]
          (doseq [{:keys [^java.io.File file file-name timestamp part gz?]}
                  (sort-by :part enc/rcompare file-maps)]

            (when part
              (let [part  (long part)
                    part+ (inc  part)]

                (if-let [drop? (and max-num-parts (> part+ (long max-num-parts)))]
                  (if-let [df ?debugger]
                    (df [:delete file-name])
                    (delete-file! file))

                  (let [file-name+ (get-file-name main-path timestamp part+ gz?)]
                    (if-let [df ?debugger]
                      (df [:rename file-name file-name+])
                      (move-file! file (utils/as-file file-name+)))))))))
        file-maps-by-edy)))

  ;; Rename main -> <timestamp>.1.gz archive
  (let [arch-file-name-gz (get-file-name main-path timestamp 1 false)
        arch-file-name+gz (get-file-name main-path timestamp 1 gz?)]

    (if-let [df ?debugger]
      (df [:rename main-path arch-file-name+gz])
      (let [main-file    (utils/as-file main-path)         ; `logs/app.log`
            arch-file-gz (utils/as-file arch-file-name-gz) ; `logs/app.log.1`    or `logs/app.log-2020-01-01d.1`
            arch-file+gz (utils/as-file arch-file-name+gz) ; `logs/app.log.1.gz` or `logs/app.log-2020-01-01d.1.gz`
            ]

        (truss/have? false? (.exists arch-file+gz)) ; No pre-existing `.1.gz`
        (move-file!   main-file arch-file-gz)
        (create-file! main-file)

        (when gz?
          (try
            (gzip-file    arch-file-gz arch-file+gz)
            (delete-file! arch-file-gz)
            (catch Throwable t
              (try
                (delete-file-if-exists! arch-file+gz)
                (catch Throwable    cleanup-error
                  (.addSuppressed t cleanup-error)))
              (throw t))))))))

(defn prune-archive-files!
  "Scans files in same dir as `main-path`, and maintains `max-num-intervals` limit
  by deleting ALL parts for oldest intervals. Expensive."
  [main-path interval max-num-intervals ?debugger]
  (when (and interval max-num-intervals)
    (when-let [file-maps (scan-files main-path interval nil false)] ; [<file-map> ...]
      (let [file-maps-by-edy (group-by :edy file-maps)       ; {<edy> [<file-map> ...]}
            n-prune (- (count file-maps-by-edy) (long max-num-intervals))]

        (when (pos? n-prune) ; Prune some (oldest) intervals
          (doseq [old-edy (take n-prune (sort (keys file-maps-by-edy)))]

            ;; Delete every part of this interval
            (doseq [{:keys [^java.io.File file file-name]}
                    (sort-by :part enc/rcompare
                      (get file-maps-by-edy old-edy))]

              (if-let [df ?debugger]
                (df [:delete file-name])
                (delete-file! file)))))))))

;;;; Handler

(def ^:dynamic ^:no-doc *file-maintenance-retry-msecs* 30000)

(defn ^:public handler:file
  "Experimental, subject to change.

  Returns a signal handler that:
    - Takes a Telemere signal (map).
    - Writes (appends) the signal as a string to file specified by `path`.

  Can output signals as human or machine-readable (edn, JSON) strings.

  Depending on options, archive file/s may also be maintained:
    - `logs/app.log.n.gz`             (for     nil `:interval`, non-nil `:max-file-size`)
    - `logs/app.log-YYYY-MM-DDd.n.gz` (for non-nil `:interval`) ; d=daily/w=weekly/m=monthly

  Example files with default options:
    `/logs/telemere.log`                  ; Current file (newest entries)
    `/logs/telemere.log-2020-01-01m.1.gz` ; Archive for Jan 2020, part 1 (newest entries)
    ...
    `/logs/telemere.log-2020-01-01m.8.gz` ; Archive for Jan 2020, part 8 (oldest entries)

  Options:
    `:output-fn`- (fn [signal]) => string, see `format-signal-fn` or `pr-signal-fn`
    `:path` ----- Path string of the target output file (default `logs/telemere.log`)

    `:interval` - ∈ #{nil :daily :weekly :monthly} (default `:monthly`)
      When non-nil, causes interval-based archives to be maintained.

    `:max-file-size` - ∈ #{nil <pos-int>} (default 4MB)
      When `path` file size > ~this many bytes, rotates old content to numbered archives.

    `:max-num-parts` - ∈ #{nil <pos-int>} (default 8)
      Maximum number of numbered archives to retain for any particular interval.

    `:max-num-intervals` - ∈ #{nil <pos-int>} (default 6)
      Maximum number of intervals (days/weeks/months) to retain."

  ([] (handler:file nil))
  ([{:keys
     [output-fn
      path interval
      max-file-size
      max-num-parts
      max-num-intervals
      gzip-archives?]

     :or
     {output-fn (utils/format-signal-fn)
      path "logs/telemere.log" ; Main path, we'll ALWAYS write to this exact file
      interval :monthly
      max-file-size (* 1024 1024 4) ; 4MB
      max-num-parts     8
      max-num-intervals 6
      gzip-archives? true}}]

   (let [main-path path
         main-file (utils/as-file main-path)
         fw (utils/file-writer {:file main-file, :append? true})

         >max-file-size?
         (when   max-file-size
           (let [max-file-size (long max-file-size)
                 rl (enc/rate-limiter-once-per 250)]
             (fn [] (and (not (rl)) (> (.length main-file) max-file-size)))))

         curr-timestamp_ (enc/latom nil) ; Will be bootstrapped based on main file
         prune-pending?_ (enc/latom false)
         maintenance-retry-after_ (enc/latom 0)

         maintenance-ready? (fn [] (>= (System/currentTimeMillis) (long (maintenance-retry-after_))))
         maintenance-failed!
         (fn []
           (reset! maintenance-retry-after_
             (+ (System/currentTimeMillis)
               (long *file-maintenance-retry-msecs*))))

         ;; Called on every write attempt, returns a pending interval transition.
         [next-interval commit-interval!]
         (when interval
           (let [init-edy  (let [n (file-last-modified->edy main-file)] (when (pos? n) n))
                 curr-edy_ (enc/latom init-edy)]

             (when init-edy ; Don't bootstrap "1970-01-01d", etc.
               (reset! curr-timestamp_
                 (format-file-timestamp interval init-edy)))

             [(fn next-interval []
                (let [curr-edy (udt->edy (System/currentTimeMillis))]
                  (when-not (= (curr-edy_) curr-edy) ; Day changed
                    (let [curr-timestamp (format-file-timestamp interval curr-edy)]
                      (if (= (curr-timestamp_) curr-timestamp)
                        ;; Day changed, but week/month did not.
                        (do (reset! curr-edy_ curr-edy) nil)
                        {:curr-edy       curr-edy
                         :curr-timestamp curr-timestamp
                         :prev-timestamp (curr-timestamp_)})))))

              (fn commit-interval! [{:keys [curr-edy curr-timestamp]}]
                (reset! curr-edy_       curr-edy)
                (reset! curr-timestamp_ curr-timestamp))]))

         lock (Object.)]

     (fn a-handler:file
       ([      ] (locking lock (fw))) ; Stop => close writer
       ([signal]
        (when-let [output (output-fn signal)]
          (locking lock
            (let [maintenance-error_ (volatile! nil)]
              (when (and (prune-pending?_) (maintenance-ready?))
                (try
                  (prune-archive-files! main-path interval max-num-intervals nil)
                  (reset! prune-pending?_ false)
                  (catch Exception e
                    (maintenance-failed!)
                    (vreset! maintenance-error_ e))))

              (let [interval-change (when interval      (next-interval))
                    new-interval?   (boolean interval-change)
                    >max-file-size? (when max-file-size (>max-file-size?))
                    reset-stream?   (or   new-interval?  >max-file-size?)]

                (when (and reset-stream?
                        (maintenance-ready?)
                        (nil? @maintenance-error_))
                  (fw) ; Close before moving the main file
                  (try
                    (if new-interval?
                      (do
                        ;; Rename main -> <prev-timestamp>.1.gz, etc.
                        (when-let [prev-timestamp (:prev-timestamp interval-change)]
                          (archive-main-file! main-path interval prev-timestamp
                            max-num-parts gzip-archives? nil))

                        (commit-interval! interval-change)

                        (when max-num-intervals
                          (reset! prune-pending?_ true)
                          (prune-archive-files! main-path interval max-num-intervals nil)
                          (reset! prune-pending?_ false)))

                      ;; Rename main -> <curr-timestamp>.1.gz, etc.
                      (archive-main-file! main-path interval (curr-timestamp_)
                        max-num-parts gzip-archives? nil))

                    (catch Exception e
                      (maintenance-failed!)
                      (vreset! maintenance-error_ e))

                    (finally
                      (fw :writer/reset!))))

                (let [result (fw output)]
                  (if-let [e @maintenance-error_]
                    (throw e)
                    result)))))))))))

(comment
  (manage-test-files! :create)
  (.setLastModified (utils/as-file "test/logs/app6.log")
    (enc/as-udt "1999-01-01T01:00:00.00Z"))

  (let [f (utils/as-file "test/logs/app6.log")] (enc/qb 1e5 (.length f)))
  (let [hfn
        (handler:file
          {:path "test/logs/app6.log"
           :max-num-intervals 2
           :max-num-parts     2})]

    (hfn {:info :level :msg_ "hello"}) (hfn)))
