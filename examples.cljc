(ns examples
  "Some basic Telemere usage examples."
  (:require
   [taoensso.telemere :as t]))

;; TODO Still be to completed

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
