(ns taoensso.telemere
  "Structured telemetry for Clojure/Script applications.

  See the GitHub page (esp. Wiki) for info on motivation and design:
    <https://github.com/taoensso/telemere>"

  {:author "Peter Taoussanis (@ptaoussanis)"}
  (:require
   [taoensso.encore             :as enc :refer [have have?]]
   [taoensso.encore.signals     :as sigs]
   [taoensso.encore.signals.api :as sigs-api]
   [taoensso.telemere.impl      :as impl]))

(comment
  (remove-ns 'taoensso.telemere)
  (:api (enc/interns-overview)))

(enc/assert-min-encore-version [3 75 0]) ; Wip TODO

;;;; Roadmap
;; - See `ensso/telemere-lib.org`, `ensso/draft-telemere`
;; - [Dec] alpha1: fundamentals
;; - [Jan] alpha2: first logging tools (handlers + utils, `tools.logging`, slf4j, etc.)
;; - [Feb] alpha3: first OpenTelemetry tools

;;;; TODO
;; - Import/kill final old code (in ensso)
;;
;; - Recheck notes, import anything?
;; - Finish book + notes
;; - Reading plan
;;
;; - Sketch first basic handlers (and/or examples?)
;; - Sketch other basic utils to superset Timbre, etc.
;;   - Incl. `tools.logging`, slf4j, etc.
;;
;; - Check TODOs, general polish + documentation
;; - Update Tufte (signal API, config API, signal fields, etc.)

;;;; Shared signal API

(sigs-api/def-api 4 impl/*rt-sig-filter* impl/*sig-handlers* {:purpose "signal"})

(comment
  [level-aliases]
  [handlers-help get-handlers add-handler! remove-handler!]
  [filtering-help
   set-kind-filter! set-ns-filter! set-id-filter! set-min-level!
   with-kind-filter with-ns-filter with-id-filter with-min-level])

;;;; Aliases

(enc/defaliases
  enc/chance
  impl/msg-splice
  impl/msg-skip
  #?(:clj impl/with-signal)
  impl/signal!)

;;;; Context

(enc/defonce ctx-root
  "Advanced feature. Default root value of `*ctx*` var, controlled by
    `(taoensso.encore/get-sys-value {:as :edn} :taoensso.telemere/ctx-root)`.

  Ref. <https://taoensso.github.io/encore/taoensso.encore.html#var-get-sys-value>"
  (enc/get-sys-value {:as :edn} :taoensso.telemere/ctx-root))

(enc/def* ^:dynamic *ctx*
  "Dynamic context: arbitrary app-level state attached as `:ctx` to all signals.
  Value may be any type, but is usually nil or a map.

  Re/bind dynamic     value using `with-ctx`, `with-ctx+`, or `binding`.
  Modify  root (base) value using `set-ctx-root!`.
  Default root (base) value is    `ctx-root`.

  Note that as with all dynamic Clojure vars, \"binding conveyance\" applies
  when using futures, agents, etc.

  Tips:
    - Value may be (or may contain) an atom if you want mutable semantics
    - Value may be of form {<scope-id> <data>} for custom scoping, etc."

  ctx-root)

#?(:clj
   (defmacro set-ctx-root!
     "Set `*ctx*` var's root value. See `*ctx*` for details."
     [root-val] `(enc/update-var-root! *ctx* ~root-val)))

#?(:clj
   (defmacro with-ctx
     "Evaluates given form with given `*ctx*` value.
     See `*ctx*` for details."
     [init-val form]
     `(binding [*ctx* ~init-val]
        ~form)))

(comment (with-ctx "my-ctx" *ctx*))

#?(:clj
   (defmacro with-ctx+
     "Evaluates given form with updated `*ctx*` value.

     `update-map-or-fn` may be:
       - A map to merge with    current `*ctx*` value, or
       - A unary fn to apply to current `*ctx*` value

     See `*ctx*` for details."
     [update-map-or-fn form]
     `(binding [*ctx* (impl/update-ctx *ctx* ~update-map-or-fn)]
        ~form)))

(comment (with-ctx {:a :A1 :b :B1} (with-ctx+ {:a :A2} *ctx*)))

;;;; Middleware

(enc/defonce ^:dynamic *middleware*
  "Optional vector of unary middleware fns to apply (left-to-right/sequentially)
  to each signal before passing it to handlers. If any middleware fn returns nil,
  aborts immediately without calling handlers.

  Useful for transforming each signal before handling."
  nil)

;;;; WIP ; TODO

;; No varargs
;; log!   [msg]     or [opts msg]  ; Sig with msg
;; spy!   [form]    or [opts form] ; Sig with form
;; info!  [msg]     or [opts msg], etc.

;; event! [id]      or [opts id]    ; Sig with id, return truthy if call passed?
;; trace! [id form] or [opts form]? ; Alias for spy?
;; span type?

;; - Util to signal + throw error? (Can use instead of `throw!`)
;;   - e.g. `throw!` [throwable] or [opts throwable]
;;   - note possible conflict with (logging) `error!`

;; - signal-errors
;; - signal-and-rethrow-errors

#_
(defn event
  "Fn version of `event` macro that does not (cannot) capture callsite info.
  Prefer `event` macro when possible."
  ;; TODO Better to not actually offer this?
  ([id opts] (impl/generic-signal :event {:location nil, :opts opts, :id id}))
  ([   opts] (impl/generic-signal :event {:location nil, :opts opts, :id id})))

;; - Possible "kind" values: #{<event> <trace> <profiling> <user>}
