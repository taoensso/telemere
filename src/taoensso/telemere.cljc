(ns taoensso.telemere
  "Structured telemetry for Clojure/Script applications.

  See the GitHub page (esp. Wiki) for info on motivation and design:
    <https://github.com/taoensso/telemere>

  TODO"
  {:author "Peter Taoussanis (@ptaoussanis)"}
  (:require
   [taoensso.encore             :as enc :refer [have have?]]
   [taoensso.encore.signals     :as sigs]
   [taoensso.encore.signals.api :as sigs-api]
   [taoensso.telemere.impl      :as impl]))

(comment
  (remove-ns 'taoensso.telemere)
  (:api (enc/interns-overview)))

(enc/assert-min-encore-version [3 75 0])

;;;; Roadmap
;; - See also `ensso/telemere-lib.org`, `ensso/draft-telemere` ns
;; - [Dec] alpha1: fundamentals
;; - [Jan] alpha2: first logging tools (handlers + utils, `tools.logging`, slf4j, etc.)
;; - [Feb] alpha3: first OpenTelemetry tools

;;;; TODO
;; - Double check reasonable trace interop with runtimes, etc.
;; - Bring *middleware* into main ns, document

;; - Import/kill final old code?
;; - Organize new code, polish, document

;; - Recheck notes
;; - Finish book + notes
;; - Reading plan

;; - Sketch first basic handlers (or examples?)
;; - Sketch other basic utils to superset Timbre, etc.
;;   - Incl. `tools.logging`, slf4j, etc.

;; x Confirm that no min-level => no filtering
;; - Ensure that it's clearly documented that *both* callsite and
;;   handler filters need to pass for a signal to be handled
;; - Check TODOs

;;;; Low-level primitives

(sigs-api/def-api 4 impl/*rt-sig-filter* impl/*sig-handlers* {:purpose "signal"})

#_[level-aliases]
#_[handlers-help get-handlers add-handler! remove-handler!]
#_[filtering-help
   set-kind-filter! set-ns-filter! set-id-filter! set-min-level!
   with-kind-filter with-ns-filter with-id-filter with-min-level]

#_(:doc (meta #'filtering-help))
#_(:doc (meta #'handlers-help))

;; handlers-help
;; handlers-get
;; handler-add
;; handler-remove

;; filtering-help
;; filter-set-kind!
;; filter-set-ns!
;; filter-set-id!
;; filter-set-min-level!

;; with-filter-kind
;; with-filter-ns
;; with-filter-id
;; with-filter-min-level

;;;; Aliases

(enc/defaliases
  impl/msg-splice
  impl/msg-skip
  #?(:clj impl/with-signal)
  impl/signal!
  enc/chance)

;;;; Config

;; - SignalFilter (ns-spec id-spec level-spec)
;; - Init ctx
;; - Handler registration (but only via calls)
;; - xform

#_(sigs/valid-level level-form)
#_(sigs/filterable-expansion {})
#_(sigs/call-handlers! impl/*sig-handlers* signal)

;;;; API

;; No varargs!

;; log!   [msg]     or [opts msg]  ; Sig with msg
;; spy!   [form]    or [opts form] ; Sig with form
;; info!  [msg]     or [opts msg], etc.

;; event! [id]      or [opts id]    ; Sig with id, return truthy if callsite passed?
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

;;;;

(enc/defonce ^:dynamic *middleware* nil) ; TODO Yes/no? In config map? Main ns?
