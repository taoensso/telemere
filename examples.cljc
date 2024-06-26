(ns examples
  "Basic Telemere usage examples that appear in the Wiki or docstrings."
  (:require [taoensso.telemere :as t]))

;;;; README "Quick example"

(require '[taoensso.telemere :as t])

;; Start simple
(t/log! "This will send a `:log` signal to the Clj/s console")
(t/log! :info "This will do the same, but only when the current level is >= `:info`")

;; Easily construct messages
(let [x "constructed"] (t/log! :info ["Here's a" x "message!"]))

;; Attach an id
(t/log! {:level :info, :id ::my-id} "This signal has an id")

;; Attach arb user data
(t/log! {:level :info, :data {:x :y}} "This signal has structured data")

;; Capture for debug/testing
(t/with-signal (t/log! "This will be captured"))
;; => {:keys [location level id data msg_ ...]}

;; `:let` bindings available to `:data` and message, only paid for
;; when allowed by minimum level and other filtering criteria
(t/log!
  {:level :info
   :let [expensive-metric1 (last (for [x (range 100), y (range 100)] (* x y)))]
   :data {:metric1 expensive-metric1}}
  ["Message with metric value:" expensive-metric1])

;; With sampling 50% and 1/sec rate limiting
(t/log!
  {:sample-rate 0.5
   :rate-limit  {"1 per sec" [1 1000]}}
  "This signal will be sampled and rate limited")

;;; A quick taste of filtering...

(t/set-ns-filter! {:disallow "taoensso.*" :allow "taoensso.sente.*"}) ; Set namespace filter
(t/set-id-filter! {:allow #{::my-particular-id "my-app/*"}})          ; Set id        filter

(t/set-min-level!       :warn) ; Set minimum level
(t/set-min-level! :log :debug) ; Set minimul level for `log!` signals

;; Set minimum level for `event!` signals originating in the "taoensso.sente.*" ns
(t/set-min-level! :event "taoensso.sente.*" :warn)

;;; Example handler output

(t/log! {:id ::my-id, :data {:x1 :x2}} "My message") ; =>
;; 2024-04-11T10:54:57.202869Z INFO LOG Schrebermann.local examples(56,1) ::my-id - My message
;;    data: {:x1 :x2}

;;;; Docstring snippets

(t/with-signal (t/event! ::my-id))
(t/with-signal (t/event! ::my-id :warn))
(t/with-signal
  (t/event! ::my-id
    {:let  [x "x"] ; Available to `:data` and `:msg`
     :data {:x x}
     :msg  ["My msg:" x]}))

(t/with-signal (t/log! "My msg"))
(t/with-signal (t/log! :warn "My msg"))
(t/with-signal
  (t/log!
    {:let  [x "x"] ; Available to `:data` and `:msg`
     :data {:x x}}
    ["My msg:" x]))

(t/with-signal (throw (t/error!         (ex-info "MyEx" {}))))
(t/with-signal (throw (t/error! ::my-id (ex-info "MyEx" {}))))
(t/with-signal
  (throw
    (t/error!
      {:let  [x "x"] ; Available to `:data` and `:msg`
       :data {:x x}
       :msg  ["My msg:" x]}
      (ex-info "MyEx" {}))))

(t/with-signal (t/trace! (+ 1 2)))
(t/with-signal (t/trace! ::my-id (+ 1 2)))
(t/with-signal
  (t/trace!
    {:let  [x "x"] ; Available to `:data` and `:msg`
     :data {:x x}}
    (+ 1 2)))

(t/with-signal (t/spy! (+ 1 2)))
(t/with-signal (t/spy! :debug (+ 1 2)))
(t/with-signal
  (t/spy!
    {:let  [x "x"] ; Available to `:data` and `:msg`
     :data {:x x}}
    (+ 1 2)))

(t/with-signal (t/catch->error! (/ 1 0)))
(t/with-signal (t/catch->error! ::my-id (/ 1 0)))
(t/with-signal
  (t/catch->error!
    {:let  [x "x"] ; Available to `:data` and `:msg`
     :data {:x x}
     :msg  ["My msg:" x my-error]
     :catch-val "Return value when form throws"
     :catch-sym my-error}
    (/ 1 0)))

;;;; Wiki examples

;;; Filter signals

(t/set-min-level! :info) ; Set global minimum level
(t/with-signal (t/event! ::my-id1 :info))  ; => {:keys [inst id ...]}
(t/with-signal (t/event! ::my-id1 :debug)) ; => nil (signal not allowed)

(t/with-min-level :trace ; Override global minimum level
  (t/with-signal (t/event! ::my-id1 :debug))) ; => {:keys [inst id ...]}

;; Deny all signals in matching namespaces
(t/set-ns-filter! {:deny "some.nosy.namespace.*"})

;;; Configuring handlers

;; Create a test signal
(def my-signal
  (t/with-signal
    (t/log! {:id ::my-id, :data {:x1 :x2}} "My message")))

;; Create console handler with default opts (writes formatted string)
(def my-handler (t/handler:console))

;; Test handler, remember it's just a (fn [signal])
(my-handler my-signal) ; =>
;; 2024-04-11T10:54:57.202869Z INFO LOG Schrebermann.local examples(56,1) ::my-id - My message
;;     data: {:x1 :x2}

;; Create console which writes signals as edn
(def my-handler
  (t/handler:console
    {:output-fn (t/pr-signal-fn {:pr-fn :edn})}))

(my-handler my-signal) ; =>
;; {:inst #inst "2024-04-11T10:54:57.202869Z", :msg_ "My message", :ns "examples", ...}

;; Create console which writes signals as JSON
#?(:clj (require '[jsonista.core :as jsonista]))
(def my-handler
  (t/handler:console
    {:output-fn
     (t/pr-signal-fn
       {:pr-fn
        #?(:cljs :json
           :clj  jsonista.core/write-value-as-string)})}))

(my-handler my-signal) ; =>
;; {"inst":"2024-04-11T10:54:57.202869Z","msg_":"My message","ns":"examples", ...}

;;; Writing handlers

(defn handler:my-handler ; Note naming convention
  "Needs `some-lib`, Ref. <https://github.com/example/some-lib>.

  Returns a (fn handler [signal] that:
    - Takes a Telemere signal (map).
    - Does something with the signal.

  Options:
    `:option1` - Option description
    `:option2` - Option description

  Tips:
    - Tip 1
    - Tip 2"

  ([] (handler:my-handler nil)) ; Use default opts (when defaults viable)
  ([{:as constructor-opts}]

   ;; Do option validation and other prep here, i.e. try to keep expensive work
   ;; outside handler function when possible.

   (let [handler-fn
         (fn a-handler:my-handler ; Note naming convention

           ;; Main arity, called by Telemere when handler should process given signal
           ([signal]
            ;; Do something with given signal (write to console/file/queue/db, etc.).
            ;; Return value is ignored.
            )

           ;; Optional stop arity for handlers that need to close/release resources or
           ;; otherwise finalize themselves during system shutdown, etc. Called by
           ;; Telemere when appropriate, but ONLY IF the handler's dispatch options
           ;; include a truthy `:needs-stopping?` value (false by default).
           ([]
            ;; Close/release resources, etc.
            ))

         ;; (Advanced) optional default handler dispatch opts,
         ;; see `add-handler!` for full list of possible opts
         default-dispatch-opts
         {:min-level  :info
          :rate-limit [[1 (enc/msecs :min 1)]]}]

     (with-meta handler-fn default-dispatch-opts))))

;;; Message building

;; A fixed message (string arg)
(t/log! "A fixed message") ; %> {:msg "A fixed message"}

;; A joined message (vector arg)
(let [user-arg "Bob"]
  (t/log! ["User" (str "`" user-arg "`") "just logged in!"]))
;; %> {:msg_ "User `Bob` just logged in!` ...}

;; With arg prep
(let [user-arg "Bob"
      usd-balance-str "22.4821"]

  (t/log!
    {:let
     [username (clojure.string/upper-case user-arg)
      usd-balance (parse-double usd-balance-str)]

     :data
     {:username    username
      :usd-balance usd-balance}}

    ["User" username "has balance:" (str "$" (Math/round usd-balance))]))

;; %> {:msg "User BOB has balance: $22" ...}

(t/log! (str "This message " "was built " "by `str`"))
;; %> {:msg "This message was built by `str`"}

(t/log! (format "This message was built by `%s`" "format"))
;; %> {:msg "This message was built by `format`"}

;;; User kvs

(t/with-signal
  (t/event! ::my-id
    {:my-middleware-data "foo"
     :my-handler-data    "bar"}))

;; %>
;; {;; User kvs included inline (assoc'd to signal root)
;;  :my-middleware-data "foo"
;;  :my-handler-data    "bar"
;;  :kvs ; And also collected together under ":kvs" key
;;    {:my-middleware-data "foo"
;;     :my-handler-data    "bar"}
;;  ... }
