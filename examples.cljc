(ns examples
  "Basic Telemere usage examples that appear in the Wiki or docstrings."
  (:require [taoensso.telemere :as t]))

;;;; README "Quick example"

(require '[taoensso.telemere :as t])

(t/log! {:id ::my-id, :data {:x1 :x2}} "My message") ; %>
;; 2024-04-11T10:54:57.202869Z INFO LOG Schrebermann.local examples(56,1) ::my-id - My message
;;    data: {:x1 :x2}

(t/log!       "This will send a `:log` signal to the Clj/s console")
(t/log! :info "This will do the same, but only when the current level is >= `:info`")

;; Easily construct messages from parts
(t/log! :info ["Here's a" "joined" "message!"])

;; Attach an id
(t/log! {:level :info, :id ::my-id} "This signal has an id")

;; Attach arb data
(t/log! {:level :info, :data {:x :y}} "This signal has structured data")

;; Capture for debug/testing
(t/with-signal (t/log! "This will be captured"))
;; => {:keys [location level id data msg_ ...]}

;; `:let` bindings are available to `:data` and message, but only paid
;; for when allowed by minimum level and other filtering criteria
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

;; There are several signal creators available for convenience.
;; All support the same options but each offer's a calling API
;; optimized for a particular use case. Compare:

;; `log!` - [msg] or [level-or-opts msg]
(t/with-signal (t/log! {:level :info, :id ::my-id} "Hi!"))

;; `event!` - [id] or [id level-or-opts]
(t/with-signal (t/event! ::my-id {:level :info, :msg "Hi!"}))

;; `signal!` - [opts]
(t/with-signal (t/signal! {:level :info, :id ::my-id, :msg "Hi!"}))

;; See `t/help:signal-creators` docstring for more

;;; A quick taste of filtering

(t/set-ns-filter! {:disallow "taoensso.*" :allow "taoensso.sente.*"}) ; Set namespace filter
(t/set-id-filter! {:allow #{::my-particular-id "my-app/*"}})          ; Set id        filter

(t/set-min-level!       :warn) ; Set minimum level for all    signals
(t/set-min-level! :log :debug) ; Set minimul level for `log!` signals

;; Set minimum level for `event!` signals originating in
;; the "taoensso.sente.*" ns
(t/set-min-level! :event "taoensso.sente.*" :warn)

;; See `t/help:filters` docstring for more

;;; Use middleware to transform signals and/or filter signals
;;; by signal data/content/etc.

(t/set-middleware!
  (fn [signal]
    (if (get-in signal [:data :hide-me?])
      nil ; Suppress signal (don't handle)
      (assoc signal :passed-through-middleware? true))))

(t/with-signal (t/event! ::my-id {:data {:hide-me? true}}))  ; => nil
(t/with-signal (t/event! ::my-id {:data {:hide-me? false}})) ; => {...}

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

;; Disallow all signals in matching namespaces
(t/set-ns-filter! {:disallow "some.nosy.namespace.*"})

;;; Configuring handlers

;; Create a test signal
(def my-signal
  (t/with-signal
    (t/log! {:id ::my-id, :data {:x1 :x2}} "My message")))

;; Create console handler with default opts (writes formatted string)
(def my-handler (t/handler:console))

;; Test handler, remember it's just a (fn [signal])
(my-handler my-signal) ; %>
;; 2024-04-11T10:54:57.202869Z INFO LOG Schrebermann.local examples(56,1) ::my-id - My message
;;     data: {:x1 :x2}

;; Create console handler which writes signals as edn
(def my-handler
  (t/handler:console
    {:output-fn (t/pr-signal-fn {:pr-fn :edn})}))

(my-handler my-signal) ; %>
;; {:inst #inst "2024-04-11T10:54:57.202869Z", :msg_ "My message", :ns "examples", ...}

;; Create console handler which writes signals as JSON
#?(:clj (require '[jsonista.core :as jsonista]))
(def my-handler
  (t/handler:console
    {:output-fn
     (t/pr-signal-fn
       {:pr-fn
        #?(:cljs :json
           :clj  jsonista.core/write-value-as-string)})}))

(my-handler my-signal) ; %>
;; {"inst":"2024-04-11T10:54:57.202869Z","msg_":"My message","ns":"examples", ...}

;; Deregister the default console handler
(t/remove-handler! :default/console)

;; Register our custom console handler
(t/add-handler! :my-handler my-handler
  ;; Lots of options here for filtering, etc.
  ;; See `help:handler-dispatch-options` docstring!
  {})

;; NB make sure to always stop handlers at the end of your
;; `-main` or shutdown procedure
(t/call-on-shutdown! t/stop-handlers!)

;; See `t/help:handlers` docstring for more

;;; Writing handlers

;; Handlers are just fns of 2 arities

(defn my-basic-handler
  ([signal] (println signal)) ; Arity-1 called when handling a signal
  ([])                        ; Arity-0 called when stopping the handler
  )

;; If you're making a customizable handler for use by others, it's often
;; handy to define a handler constructor

(defn handler:my-fancy-handler ; Note constructor naming convention
  "Needs `some-lib`, Ref. <https://github.com/example/some-lib>.

  Returns a signal handler that:
    - Takes a Telemere signal (map).
    - Does something useful with the signal!

  Options:
    `:option1` - Option description
    `:option2` - Option description

  Tips:
    - Tip 1
    - Tip 2"

  ([] (handler:my-fancy-handler nil)) ; Use default opts (iff defaults viable)
  ([{:as constructor-opts}]

   ;; Do option validation and other prep here, i.e. try to keep
   ;; expensive work outside handler function when possible!

   (let [handler-fn ; Fn of exactly 2 arities
         (fn a-handler:my-fancy-handler ; Note fn naming convention

           ([signal] ; Arity-1 called when handling a signal
            ;; Do something useful with the given signal (write to
            ;; console/file/queue/db, etc.). Return value is ignored.
            )

           ([] ; Arity-0 called when stopping the handler
            ;; Flush buffers, close files, etc. May just noop.
            ;; Return value is ignored.
            ))]

     ;; (Advanced, optional) You can use metadata to provide default
     ;; handler dispatch options (see `help:handler-dispatch-options`)

     (with-meta handler-fn
       {:dispatch-opts
        {:min-level  :info
         :rate-limit
         [[1   1000] ; Max 1  signal  per second
          [10 60000] ; Max 10 signals per minute
          ]}}))))

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

;;; App-level kvs

(t/with-signal
  (t/event! ::my-id
    {:my-middleware-data "foo"
     :my-handler-data    "bar"}))

;; %>
;; {;; App-level kvs included inline (assoc'd to signal root)
;;  :my-middleware-data "foo"
;;  :my-handler-data    "bar"
;;  :kvs ; And also collected together under ":kvs" key
;;  {:my-middleware-data "foo"
;;   :my-handler-data    "bar"}
;;  ... }
