(ns examples
  "Basic Telemere usage examples that appear in the Wiki or docstrings."
  (:require [taoensso.telemere :as tel]))

(comment

;;;; README "Quick examples"

(require '[taoensso.telemere :as tel])

;; No config needed for typical use cases!!
;; Signals print to console by default for both Clj and Cljs

;; Traditional style logging (data formatted into message string):
(tel/log! {:level :info, :msg (str "User " 1234 " logged in!")})

;; Modern/structured style logging (explicit id and data)
(tel/log! {:level :info, :id :auth/login, :data {:user-id 1234}})

;; Mixed style (explicit id and data, with message string)
(tel/log! {:level :info, :id :auth/login, :data {:user-id 1234}, :msg "User logged in!"})

;; Trace (can interop with OpenTelemetry)
;; Tracks form runtime, return value, and (nested) parent tree
(tel/trace! {:id ::my-id :data {...}}
  (do-some-work))

;; Check resulting signal content for debug/tests
(tel/with-signal (tel/log! {...})) ; => {:keys [ns level id data msg_ ...]}

;; Getting fancy (all costs are conditional!)
(tel/log!
  {:level    :debug
   :sample   0.75 ; 75% sampling (noop 25% of the time)
   :when     (my-conditional)
   :limit    {"1 per sec" [1  1000]
              "5 per min" [5 60000]} ; Rate limit
   :limit-by my-user-ip-address      ; Rate limit scope

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
)

;; Set minimum level
(tel/set-min-level!       :warn) ; For all    signals
(tel/set-min-level! :log :debug) ; For `log!` signals specifically

;; Set id and namespace filters
(tel/set-id-filter! {:allow #{::my-particular-id "my-app/*"}})
(tel/set-ns-filter! {:disallow "taoensso.*" :allow "taoensso.sente.*"})

;; SLF4J signals will have their `:ns` key set to the logger's name
;; (typically a source class)
(tel/set-ns-filter! {:disallow "com.noisy.java.package.*"})

;; Set minimum level for `log!` signals for particular ns pattern
(tel/set-min-level! :log "taoensso.sente.*" :warn)

;; Use transforms (xfns) to filter and/or arbitrarily modify signals
;; by signal data/content/etc.

(tel/set-xfn!
  (fn [signal]
    (if (-> signal :data :skip-me?)
      nil ; Filter signal (don't handle)
      (assoc signal :transformed? true))))

(tel/with-signal (tel/log! {... :data {:skip-me? true}}))  ; => nil
(tel/with-signal (tel/log! {... :data {:skip-me? false}})) ; => {...}

;; See `tel/help:filters` docstring for more filtering options

;; Add your own signal handler
(tel/add-handler! :my-handler
  (fn
    ([signal] (println signal))
    ([] (println "Handler has shut down"))))

;; Use `add-handler!` to set handler-level filtering and back-pressure
(tel/add-handler! :my-handler
  (fn
    ([signal] (println signal))
    ([] (println "Handler has shut down")))

  {:async {:mode :dropping, :buffer-size 1024, :n-threads 1}
   :priority  100
   :sample    0.5
   :min-level :info
   :ns-filter {:disallow "taoensso.*"}
   :limit     {"1 per sec" [1 1000]}
   ;; See `tel/help:handler-dispatch-options` for more
   })

;; See current handlers
(tel/get-handlers) ; => {<handler-id> {:keys [handler-fn handler-stats_ dispatch-opts]}}

;; Add console handler to print signals as human-readable text
(tel/add-handler! :my-handler
  (tel/handler:console
    {:output-fn (tel/format-signal-fn {})}))

;; Add console handler to print signals as edn
(tel/add-handler! :my-handler
  (tel/handler:console
    {:output-fn (tel/pr-signal-fn {:pr-fn :edn})}))

;; Add console handler to print signals as JSON
;; Ref.  <https://github.com/metosin/jsonista> (or any alt JSON lib)
#?(:clj (require '[jsonista.core :as jsonista]))
(tel/add-handler! :my-handler
  (tel/handler:console
    {:output-fn
     #?(:cljs :json ; Use js/JSON.stringify
        :clj   jsonista/write-value-as-string)}))

;;;; Docstring examples

(tel/with-signal (tel/event! ::my-id))
(tel/with-signal (tel/event! ::my-id :warn))
(tel/with-signal
  (tel/event! ::my-id
    {:let  [x "x"] ; Available to `:data` and `:msg`
     :data {:x x}
     :msg  ["My msg:" x]}))

(tel/with-signal (tel/log! "My msg"))
(tel/with-signal (tel/log! :warn "My msg"))
(tel/with-signal
  (tel/log!
    {:let  [x "x"] ; Available to `:data` and `:msg`
     :data {:x x}}
    ["My msg:" x]))

(tel/with-signal (throw (tel/error!         (ex-info "MyEx" {}))))
(tel/with-signal (throw (tel/error! ::my-id (ex-info "MyEx" {}))))
(tel/with-signal
  (throw
    (tel/error!
      {:let  [x "x"] ; Available to `:data` and `:msg`
       :data {:x x}
       :msg  ["My msg:" x]}
      (ex-info "MyEx" {}))))

(tel/with-signal (tel/trace! (+ 1 2)))
(tel/with-signal (tel/trace! ::my-id (+ 1 2)))
(tel/with-signal
  (tel/trace!
    {:let  [x "x"] ; Available to `:data` and `:msg`
     :data {:x x}}
    (+ 1 2)))

(tel/with-signal (tel/spy! (+ 1 2)))
(tel/with-signal (tel/spy! :debug (+ 1 2)))
(tel/with-signal
  (tel/spy!
    {:let  [x "x"] ; Available to `:data` and `:msg`
     :data {:x x}}
    (+ 1 2)))

(tel/with-signal (tel/catch->error! (/ 1 0)))
(tel/with-signal (tel/catch->error! ::my-id (/ 1 0)))
(tel/with-signal
  (tel/catch->error!
    {:let  [x "x"] ; Available to `:data` and `:msg`
     :data {:x x}
     :msg  ["My msg:" x]
     :catch-val "Return value when form throws"}
    (/ 1 0)))

;;;; Wiki examples

;;; Filter signals

(tel/set-min-level! :info) ; Set global minimum level
(tel/with-signal (tel/event! ::my-id1 :info))  ; => {:keys [inst id ...]}
(tel/with-signal (tel/event! ::my-id1 :debug)) ; => nil (signal not allowed)

(tel/with-min-level :trace ; Override global minimum level
  (tel/with-signal (tel/event! ::my-id1 :debug))) ; => {:keys [inst id ...]}

;; Disallow all signals in matching namespaces
(tel/set-ns-filter! {:disallow "some.nosy.namespace.*"})

;;; Configuring handlers

;; Create a test signal
(def my-signal
  (tel/with-signal
    (tel/log! {:id ::my-id, :data {:x1 :x2}} "My message")))

;; Create console handler with default opts (writes formatted string)
(def my-handler (tel/handler:console {}))

;; Test handler, remember it's just a (fn [signal])
(my-handler my-signal) ; %>
;; 2024-04-11T10:54:57.202869Z INFO LOG MyHost examples(56,1) ::my-id - My message
;;     data: {:x1 :x2}

;; Create console handler which writes signals as edn
(def my-handler
  (tel/handler:console
    {:output-fn (tel/pr-signal-fn {:pr-fn :edn})}))

(my-handler my-signal) ; %>
;; {:inst #inst "2024-04-11T10:54:57.202869Z", :msg_ "My message", :ns "examples", ...}

;; Create console handler which writes signals as JSON
;; Ref.  <https://github.com/metosin/jsonista> (or any alt JSON lib)
#?(:clj (require '[jsonista.core :as jsonista]))
(def my-handler
  (tel/handler:console
    {:output-fn
     (tel/pr-signal-fn
       {:pr-fn
        #?(:cljs :json ; Use js/JSON.stringify
           :clj  jsonista/write-value-as-string)})}))

(my-handler my-signal) ; %>
;; {"inst":"2024-04-11T10:54:57.202869Z","msg_":"My message","ns":"examples", ...}

;; Deregister the default console handler
(tel/remove-handler! :defaultel/console)

;; Register our custom console handler
(tel/add-handler! :my-handler my-handler
  ;; Lots of options here for filtering, etc.
  ;; See `help:handler-dispatch-options` docstring!
  {})

;; NB make sure to always stop handlers at the end of your
;; `-main` or shutdown procedure
(tel/call-on-shutdown!
  (fn [] (tel/stop-handlers!)))

;; See `tel/help:handlers` docstring for more

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

   (let [handler-fn ; Fn of exactly 2 arities (1 and 0)
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
         :limit
         [[1   1000] ; Max 1  signal  per second
          [10 60000] ; Max 10 signals per minute
          ]}}))))

;;; Message building

;; A fixed message (string arg)
(tel/log! "A fixed message") ; %> {:msg "A fixed message"}

;; A joined message (vector arg)
(let [user-arg "Bob"]
  (tel/log! ["User" (str "`" user-arg "`") "just logged in!"]))
;; %> {:msg_ "User `Bob` just logged in!` ...}

;; With arg prep
(let [user-arg "Bob"
      usd-balance-str "22.4821"]

  (tel/log!
    {:let
     [username (clojure.string/upper-case user-arg)
      usd-balance (parse-double usd-balance-str)]

     :data
     {:username    username
      :usd-balance usd-balance}}

    ["User" username "has balance:" (str "$" (Math/round usd-balance))]))

;; %> {:msg "User BOB has balance: $22" ...}

(tel/log! (str "This message " "was built " "by `str`"))
;; %> {:msg "This message was built by `str`"}

(tel/log! (enc/format "This message was built by `%s`" "format"))
;; %> {:msg "This message was built by `format`"}

;;; App-level kvs

(tel/with-signal
  (tel/event! ::my-id
    {:my-data-for-xfn     "foo"
     :my-data-for-handler "bar"}))

;; %>
;; {;; App-level kvs included inline (assoc'd to signal root)
;;  :my-data-for-xfn     "foo"
;;  :my-data-for-handler "bar"
;;  :kvs ; And also collected together under ":kvs" key
;;  {:my-data-for-xfn     "foo"
;;   :my-data-for-handler "bar"}
;;  ... }

;;;; Misc extra examples

(tel/log! {:id ::my-id, :data {:x1 :x2}} ["My 2-part" "message"]) ; %>
;; 2024-04-11T10:54:57.202869Z INFO LOG MyHost examples(56,1) ::my-id - My 2-part message
;;    data: {:x1 :x2}

;; `:let` bindings are available to `:data` and message, but only paid
;; for when allowed by minimum level and other filtering criteria
(tel/log!
  {:level :info
   :let   [expensive (reduce * (range 1 12))] ; 12 factorial
   :data  {:my-metric expensive}}
  ["Message with metric:" expensive])

;; With sampling 50% and 1/sec rate limiting
(tel/log!
  {:sample 0.5
   :limit  {"1 per sec" [1 1000]}}
  "This signal will be sampled and rate limited")

;; Several signal creators are available for convenience.
;; All offer the same options, but each has an API optimized
;; for a particular use case:

(tel/log! {:level :info, :id ::my-id} "Hi!")          ; [msg] or [level-or-opts msg]
(tel/event! ::my-id {:level :info, :msg "Hi!"})       ; [id]  or [id level-or-opts]
(tel/signal! {:level :info, :id ::my-id, :msg "Hi!"}) ; [opts]

)
