(ns ^:no-doc taoensso.telemere.files
  "Private ns, implementation detail.
  Core file handler, aliased in main Telemere ns."
  (:require
   [taoensso.encore :as enc :refer [have have?]]
   [taoensso.telemere.utils :as utils]))

(comment
  (require  '[taoensso.telemere :as tel])
  (remove-ns 'taoensso.telemere.files)
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

      (let [read-buffer (byte-array 4096)]
        (loop []
          (let [bytes-read (.read stream-in read-buffer)]
            (when-not (== -1 bytes-read)
              (.write gz-out read-buffer 0 bytes-read))))))

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
      (enc/unexpected-arg! interval
        {:context  `file-timestamp
         :param    'interval
         :expected #{:daily :weekly :monthly}}))))

(comment (file-timestamp->edy (format-file-timestamp :weekly (udt->edy (enc/now-udt*)))))

(defn manage-test-files!
  "Describes/creates/deletes files used for tests/debugging, etc."
  [action]
  (have? [:el #{:return :println :create :delete}] action)
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
  (have? [:el #{:daily :weekly :monthly nil}] interval)
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
                           (let [edy       (when timestamp (file-timestamp->edy timestamp))
                                 part      (when part (enc/as-pos-int (subs part 1)))
                                 gz?       (boolean gz)
                                 file-name (get-file-name main-path timestamp part gz?)]

                             ;; Verify that scanned file name matches our template
                             (let [actual (.getAbsolutePath file-in)
                                   expected file-name]
                               (when-not (.endsWith actual expected)
                                 (throw
                                   (ex-info "Unexpected file name"
                                     {:actual actual, :expected expected}))))

                             (conj acc
                               {:file      file-in
                                :file-name file-name
                                :timestamp timestamp
                                :edy       edy
                                :part      part
                                :gz?       gz?}))))
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
                    (.delete file))

                  (let [file-name+ (get-file-name main-path timestamp part+ gz?)]
                    (if-let [df ?debugger]
                      (df [:rename file-name file-name+])
                      (.renameTo file (utils/as-file file-name+)))))))))
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

        (have? false? (.exists    arch-file+gz)) ; No pre-existing `.1.gz`
        (.renameTo      main-file arch-file-gz)
        (.createNewFile main-file)

        (when gz?
          (gzip-file arch-file-gz arch-file+gz)
          (.delete   arch-file-gz))))))

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
                (.delete file)))))))))

;;;; Handler

(defn ^:public handler:file
  "Experimental, subject to change. Feedback welcome!

     Returns a (fn handler [signal]) that:
       - Takes a Telemere signal.
       - Writes formatted signal string to file.

  Signals will be appended to file specified by `path`.
  Depending on options, archives may be maintained:
    - `logs/app.log.n.gz`             (for     nil `:interval`, non-nil `:max-file-size`)
    - `logs/app.log-YYYY-MM-DDd.n.gz` (for non-nil `:interval`) ; d=daily/w=weekly/m=monthly

  Example files with default options:
    `/logs/telemere.log`                  ; Current file
    `/logs/telemere.log-2020-01-01m.1.gz` ; Archive for Jan 2020, part 1 (newest entries)
    ...
    `/logs/telemere.log-2020-01-01m.8.gz` ; Archive for Jan 2020, part 8 (oldest entries)

  Options:
    `:format-signal-fn`- (fn [signal])  => output, see `help:signal-formatters`.
    `:path` - Path string of the target output file (default `logs/telemere.log`).
    `:interval` - ∈ #{nil :daily :weekly :monthly} (default `:monthly`).
      When non-nil, causes interval-based archives to be maintained.

    `:max-file-size` ∈ #{nil <pos-int>} (default 4MB)
      When `path` file size > ~this many bytes, rotates old content to numbered archives.

    `:max-num-parts` ∈ #{nil <pos-int>} (default 8)
      Maximum number of numbered archives to retain for any particular interval.

    `:max-num-intervals` ∈ #{nil <pos-int>} (default 6)
      Maximum number of intervals (days/weeks/months) to retain."

  ([] (handler:file nil))
  ([{:keys
     [format-signal-fn
      path interval
      max-file-size
      max-num-parts
      max-num-intervals
      gzip-archives?]

     :or
     {format-signal-fn (utils/format-signal->str-fn)
      path "logs/telemere.log" ; Main path, we'll ALWAYS write to this exact file
      interval :monthly
      max-file-size (* 1024 1024 4) ; 4MB
      max-num-parts     8
      max-num-intervals 6
      gzip-archives? true}}]

   (let [main-path path
         main-file (utils/as-file main-path)
         fw (utils/file-writer main-file true)

         >max-file-size?
         (when   max-file-size
           (let [max-file-size (long max-file-size)
                 rl (enc/rate-limiter-once-per 2500)]
             (fn [] (and (not (rl)) (> (.length main-file) max-file-size)))))

         prev-timestamp_ (enc/latom nil) ; Initially nil
         curr-timestamp_ (enc/latom nil) ; Will be bootstrapped based on main file

         ;; Called on every write attempt,
         ;; maintains `timestamp_`s and returns true iff timestamp changed.
         new-interval!?
         (when interval
           (let [init-edy  (let [n (file-last-modified->edy main-file)] (when (pos? n) n))
                 curr-edy_ (enc/latom init-edy)
                 updated!? ; Returns ?[old new] on change
                 (fn [latom_ new]
                   (let [old (latom_)]
                     (when
                         (and
                           (not=                    old new)
                           (compare-and-set! latom_ old new))
                       [old new])))]

             (when init-edy ; Don't bootstrap "1970-01-01d", etc.
               (reset! curr-timestamp_
                 (format-file-timestamp interval init-edy)))

             (fn new-interval!? []
               (let [curr-edy (udt->edy (System/currentTimeMillis))]
                 (when (updated!? curr-edy_ curr-edy) ; Day changed
                   (let [curr-timestamp (format-file-timestamp interval curr-edy)]
                     (when-let [[prev-timestamp _] (updated!? curr-timestamp_ curr-timestamp)]
                       ;; Timestamp changed (recall: interval may not be daily)
                       (reset! prev-timestamp_ prev-timestamp)
                       true)))))))

         lock (Object.)]

     (fn a-handler:file
       ([] (locking lock (fw))) ; Close writer
       ([signal]
        (when-let [output (format-signal-fn signal)]
          (let [output-str      (str output utils/newline)
                new-interval?   (when interval      (new-interval!?))
                >max-file-size? (when max-file-size (>max-file-size?))
                reset-stream?   (or   new-interval?  >max-file-size?)]

            (locking lock

              (if new-interval?
                (do
                  ;; Rename main -> <prev-timestamp>.1.gz, etc.
                  (when-let [prev-timestamp (prev-timestamp_)]
                    (archive-main-file! main-path interval prev-timestamp
                      max-num-parts gzip-archives? nil))

                  (when max-num-intervals
                    (prune-archive-files! main-path interval
                      max-num-intervals nil)))

                (when >max-file-size?
                  ;; Rename main -> <curr-timestamp>.1.gz, etc.
                  (archive-main-file! main-path interval (curr-timestamp_)
                    max-num-parts gzip-archives? nil)))

              (when reset-stream? (fw :writer/reset!))
              (do                 (fw output-str))))))))))

(comment
  (manage-test-files! :create)
  (.setLastModified (utils/as-file "test/logs/app6.log")
    (enc/as-udt "1999-01-01T01:00:00.00Z"))

  (let [hfn
        (handler:file
          {:path "test/logs/app6.log"
           :max-num-intervals 2
           :max-num-parts     2})]

    (hfn {:info :level :msg_ "hello"}) (hfn)))
