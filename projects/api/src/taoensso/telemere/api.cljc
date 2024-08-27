(ns taoensso.telemere.api
  "Experimental, subject to change.
  Minimal Telemere facade API for library authors, etc.
  Allows library code to use Telemere if it's present, or fall back to
  something like `tools.logging` otherwise.

  (ns my-lib
    (:require
      [taoensso.telemere.api :as t]   ; `com.taoensso/telemere-api` dependency
      [clojure.tools.logging :as ctl] ; `org.clojure/tools.logging` dependency
      ))

  (t/require-telemere-if-present) ; Just below `ns` form

  ;; Optional convenience for library users
  (defn set-min-level!
    \"If using Telemere, sets Telemere's minimum level for <library> namespaces.
    Possible levels: #{:trace :debug :info :warn :error :fatal :report}.
    Default level: `:warn`.
    [min-level]
    (t/if-telemere
      (do (t/set-min-level! nil \"my-lib(.*)\" min-level) true)
      false))

  (defonce ^:private __set-default-min-level (set-min-level! :warn))

  (signal!
    {:kind :log, :id :my-id, :level :warn,
     :let  [x :x]
     :msg  [\"Hello\" \"world\" x]
     :data {:a :A :x x}
     :fallback (ctl/warn (str \"Hello world\" x))})"

  {:author "Peter Taoussanis (@ptaoussanis)"}
  #?(:clj  (:require [clojure.java.io :as jio])
     :cljs (:require-macros [taoensso.telemere.api :refer [compile-if]])))

(comment
  (require  '[taoensso.telemere :as t] '[taoensso.encore :as enc])
  (remove-ns 'taoensso.telemere.api)
  (:api (enc/interns-overview)))

#?(:clj
   (defmacro ^:private compile-if [test then else]
     (if (try (eval test) (catch Throwable _ false)) then else)))

(def ^:private telemere-present?
  "Is Telemere present (not necessarily loaded)?"
  (compile-if (jio/resource "taoensso/telemere.cljc") true false))

#?(:clj
   (defmacro if-telemere
     "Evaluates to `then` form if Telemere is present when expanding macro.
     Otherwise expands to `else` form."
     ([then     ] (if telemere-present? then nil))
     ([then else] (if telemere-present? then else))))

#?(:clj
   (defmacro require-telemere-if-present
     "Experimental, subject to change.
     Requires Telemere if it's present, otherwise noops.
     Used in cooperation with `signal!`, etc.

     For Cljs:
       - MUST be placed at top of file, just below `ns` form
       - Needs ClojureScript >= v1.9.293 (Oct 2016)"
     [] (if-telemere `(require 'taoensso.telemere))))

(comment (require-telemere-if-present))

#?(:clj
   (defmacro set-min-level!
     "Expands to `taoensso.telemere/set-min-level!` call iff Telemere is present.
     Otherwise expands to `nil` (noops)."
     ([               min-level] (if-telemere `(taoensso.telemere/set-min-level!                  ~min-level)))
     ([kind           min-level] (if-telemere `(taoensso.telemere/set-min-level! ~kind            ~min-level)))
     ([kind ns-filter min-level] (if-telemere `(taoensso.telemere/set-min-level! ~kind ~ns-filter ~min-level)))))

(comment (set-min-level! nil "my-ns(.*)" :warn))

#?(:clj
   (defmacro signal!
     "Experimental, subject to change.
     Telemere facade API for library authors, etc.

     Expands to `taoensso.telemere/signal!` call if Telemere is present.
     Otherwise expands to arbitrary `fallback` opt form.

     Allows library code to use Telemere if it's present, or fall back to
     something like `tools.logging` otherwise.

     MUST be used with `require-telemere-if-present`, example:

      (ns my-lib
        (:require
          [taoensso.telemere.api :as t]   ; `com.taoensso/telemere-api` dependency
          [clojure.tools.logging :as ctl] ; `org.clojure/tools.logging` dependency
          ))

       (t/require-telemere-if-present) ; Just below `ns` form!

       (t/signal!
         {:kind :log, :id :my-id, :level :warn,
          :let  [x :x]
          :msg  [\"Hello\" \"world\" x]
          :data {:a :A :x x}
          :fallback (ctl/warn (str \"Hello world\" x))})

     For more info, see:
       - Telemere `signal!`, Ref. <https://www.taoensso.com/telemere/signal!>
       - Telemere docs,      Ref. <https://www.taoensso.com/telemere>"

     {:arglists #_(taoensso.telemere.impl/signal-arglists :signal!) ; + fallback
      '([{:as opts
          :keys
          [fallback, ; Unique to facade
           #_defaults #_elide? #_allow? #_expansion-id, ; Undocumented
           elidable? location #_location* inst uid middleware,
           sample-rate kind ns id level when rate-limit,
           ctx parent root trace?, do let data msg error run & kvs]}])}

     [opts]
     (if (map? opts)
       (if telemere-present?
         (do
           (try
             (require 'taoensso.telemere) ; For macro expansion
             (catch Exception e
               (throw
                 (ex-info "Failed to require `taoensso.telemere` - `(require-telemere-if-present)` call missing?"
                   {} e))))

           (with-meta ; Keep callsite
             `(taoensso.telemere/signal! ~(dissoc opts :fallback))
             (meta &form)))

         (let      [fb-form  (get opts :fallback)]
           (if-let [run-form (get opts :run)]
             `(let [run-result# ~run-form] ~fb-form run-result#)
             (do                            fb-form))))

       (throw
         (ex-info "`signal!` expects map opts"
           {:given {:value opts, :type (type opts)}})))))

(comment
  (macroexpand
    '(signal!
       {:kind :log, :id :my-id, :level :warn,
        :let  [x :x]
        :msg  ["Hello" "world" x]
        :data {:a :A :x x}
        :fallback (println (str "Hello world " x))})))
