(ns examples
  "Basic Telemere usage examples that appear in the Wiki or docstrings."
  (:require [taoensso.telemere :as t]))

(comment

;;;; README "Quick examples"

(require '[taoensso.telemere :as t])

;; (Just works / no config necessary for typical use cases)

;; Without structured data
(t/log! :info "Hello world!") ; %> Basic log   signal (has message)
(t/event! ::my-id :debug)     ; %> Basic event signal (just id)

;; With structured data
(t/log! {:level :info, :data {}} "Hello again!")
(t/event! ::my-id {:level :debug, :data {}})

;; Trace (auto interops with OpenTelemetry)
;; Tracks form runtime, return value, and (nested) parent tree
(t/trace! {:id ::my-id :data {}}
  (do-some-work))

;; Check resulting signal content for debug/tests
(t/with-signal (t/event! ::my-id)) ; => {:keys [ns level id data msg_ ...]}

;; Getting fancy (all costs are conditional!)
(t/log!
  {:level         :debug
   :sample-rate   0.75 ; 75% sampling (noop 25% of the time)
   :when          (my-conditional)
   :rate-limit    {"1 per sec" [1  1000]
                   "5 per min" [5 60000]}
   :rate-limit-by my-user-ip-address ; Optional rate-limit scope

   :do (inc-my-metric!)
   :let
   [diagnostics (my-expensive-diagnostics)
    formatted   (my-expensive-format diagnostics)]

   :data
   {:diagnostics diagnostics
    :formatted   formatted
    :local-state *my-dynamic-context*}}

  ;; Message string or vector to join as string
  ["Something interesting happened!" formatted])

;; Set minimum level
(t/set-min-level!       :warn) ; For all    signals
(t/set-min-level! :log :debug) ; For `log!` signals only

;; Set namespace and id filters
(t/set-ns-filter! {:disallow "taoensso.*" :allow "taoensso.sente.*"})
(t/set-id-filter! {:allow #{::my-particular-id "my-app/*"}})

;; Set minimum level for `event!` signals for particular ns pattern
(t/set-min-level! :event "taoensso.sente.*" :warn)

;; Use middleware to:
;;   - Transform signals
;;   - Filter    signals by arb conditions (incl. data/content)

(t/set-middleware!
  (fn [signal]
    (if (-> signal :data :skip-me?)
      nil ; Filter signal (don't handle)
      (assoc signal :passed-through-middleware? true))))

(t/with-signal (t/event! ::my-id {:data {:skip-me? true}}))  ; => nil
(t/with-signal (t/event! ::my-id {:data {:skip-me? false}})) ; => {...}

;; See `t/help:filters` docstring for more filtering options

;;;; README "More examples"

;; Add your own signal handler
(t/add-handler! :my-handler
  (fn
    ([signal] (println signal))
    ([] (println "Handler has shut down"))))

;; Use `add-handler!` to set handler-level filtering and back-pressure
(t/add-handler! :my-handler
  (fn
    ([signal] (println signal))
    ([] (println "Handler has shut down")))

  {:async {:mode :dropping, :buffer-size 1024, :n-threads 1}
   :priority    100
   :sample-rate 0.5
   :min-level   :info
   :ns-filter   {:disallow "taoensso.*"}
   :rate-limit  {"1 per sec" [1 1000]}
   ;; See `t/help:handler-dispatch-options` for more
   })

;; See current handlers
(t/get-handlers) ; => {<handler-id> {:keys [handler-fn handler-stats_ dispatch-opts]}}

;; Add built-in console handler to print human-readable output
(t/add-handler! :my-handler
  (t/handler:console
    {:output-fn (t/format-signal-fn {})}))

;; Add built-in console handler to print edn output
(t/add-handler! :my-handler
  (t/handler:console
    {:output-fn (t/pr-signal-fn {:pr-fn :edn})}))

;; Add built-in console handler to print JSON output
;; Ref.  <https://github.com/metosin/jsonista> (or any alt JSON lib)
#?(:clj (require '[jsonista.core :as jsonista]))
(t/add-handler! :my-handler
  (t/handler:console
    {:output-fn
     #?(:cljs :json ; Use js/JSON.stringify
        :clj   jsonista/write-value-as-string)}))

;;;; Docstring examples

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
(def my-handler (t/handler:console {}))

;; Test handler, remember it's just a (fn [signal])
(my-handler my-signal) ; %>
;; 2024-04-11T10:54:57.202869Z INFO LOG MyHost examples(56,1) ::my-id - My message
;;     data: {:x1 :x2}

;; Create console handler which writes signals as edn
(def my-handler
  (t/handler:console
    {:output-fn (t/pr-signal-fn {:pr-fn :edn})}))

(my-handler my-signal) ; %>
;; {:inst #inst "2024-04-11T10:54:57.202869Z", :msg_ "My message", :ns "examples", ...}

;; Create console handler which writes signals as JSON
;; Ref.  <https://github.com/metosin/jsonista> (or any alt JSON lib)
#?(:clj (require '[jsonista.core :as jsonista]))
(def my-handler
  (t/handler:console
    {:output-fn
     (t/pr-signal-fn
       {:pr-fn
        #?(:cljs :json ; Use js/JSON.stringify
           :clj  jsonista/write-value-as-string)})}))

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
  ([])                        ; Arity-0 called when stopping the handler
  ([signal] (println signal)) ; Arity-1 called when handling a signal
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

           ([] ; Arity-0 called when stopping the handler
            ;; Flush buffers, close files, etc. May just noop.
            ;; Return value is ignored.
            )

           ([signal] ; Arity-1 called when handling a signal
            ;; Do something useful with the given signal (write to
            ;; console/file/queue/db, etc.). Return value is ignored.
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

;;;; Misc extra examples

(t/log! {:id ::my-id, :data {:x1 :x2}} ["My 2-part" "message"]) ; %>
;; 2024-04-11T10:54:57.202869Z INFO LOG MyHost examples(56,1) ::my-id - My 2-part message
;;    data: {:x1 :x2}

;; `:let` bindings are available to `:data` and message, but only paid
;; for when allowed by minimum level and other filtering criteria
(t/log!
  {:level :info
   :let   [expensive (reduce * (range 1 12))] ; 12 factorial
   :data  {:my-metric expensive}}
  ["Message with metric:" expensive])

;; With sampling 50% and 1/sec rate limiting
(t/log!
  {:sample-rate 0.5
   :rate-limit  {"1 per sec" [1 1000]}}
  "This signal will be sampled and rate limited")

;; Several signal creators are available for convenience.
;; All offer the same options, but each has an API optimized
;; for a particular use case:

(t/log! {:level :info, :id ::my-id} "Hi!")          ; [msg] or [level-or-opts msg]
(t/event! ::my-id {:level :info, :msg "Hi!"})       ; [id]  or [id level-or-opts]
(t/signal! {:level :info, :id ::my-id, :msg "Hi!"}) ; [opts]

)
