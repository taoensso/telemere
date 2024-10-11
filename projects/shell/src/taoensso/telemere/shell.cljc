(ns taoensso.telemere.shell
  "Experimental, subject to change.
  Minimal Telemere shell API for library authors, etc.
  Allows library code to use Telemere if it's present, or fall back to
  something like tools.logging otherwise."
  {:author "Peter Taoussanis (@ptaoussanis)"}
  #?(:clj  (:require [clojure.java.io :as jio])
     :cljs (:require-macros [taoensso.telemere.shell :refer [compile-if]])))

(comment
  (require  '[taoensso.telemere :as t] '[taoensso.encore :as enc])
  (remove-ns 'taoensso.telemere.shell)
  (:api (enc/interns-overview)))

;;;; Private

#?(:clj
   (defmacro ^:private compile-if [test then else]
     (if (try (eval test) (catch Throwable _ false)) then else)))

#?(:clj
   (def ^:private telemere-present?
     "Is Telemere present (not necessarily loaded)?"
     (compile-if (jio/resource "taoensso/telemere.cljc") true false)))

#?(:clj
   (defn- require-telemere! []
     (try
       (require 'taoensso.telemere) ; For macro expansion
       (catch Exception e
         (throw
           (ex-info "Failed to require `taoensso.telemere` - `(require-telemere-if-present)` call missing?"
             {} e))))))

#?(:clj
   (defn- get-source "From Encore" [macro-form macro-env]
     (let [{:keys [line column file]} (meta macro-form)
           file
           (if-not (:ns macro-env)
             *file* ; Compiling Clj
             (or    ; Compiling Cljs
               (when-let [url (and file (try (jio/resource file) (catch Exception _)))]
                 (try (.getPath (jio/file url)) (catch Exception _))
                 (do            (str      url)))
               file))

           file
           (when (string? file)
             (when-not (contains? #{"NO_SOURCE_PATH" "NO_SOURCE_FILE" ""} file)
               file))

           m {:ns (str *ns*)}
           m (if line   (assoc m :line   line)   m)
           m (if column (assoc m :column column) m)
           m (if file   (assoc m :file   file)   m)]
       m)))

#?(:clj
   (defn- signal-opts [macro-form macro-env opts]
     (if (map? opts)
       (conj {:location* (get-source macro-form macro-env)} (dissoc opts :fallback))
       (throw
         (ex-info "Signal opts must be a map"
           {:given {:value opts, :type (type opts)}})))))

;;;; Public

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
     Telemere shell API for library authors, etc.

     Expands to `taoensso.telemere/signal!` call if Telemere is present.
     Otherwise expands to arbitrary `fallback` opt form.

     Allows library code to use Telemere if it's present, or fall back to
     something like tools.logging otherwise.

     MUST be used with `require-telemere-if-present`, example:

      (ns my-lib
        (:require
          [taoensso.telemere.shell :as t] ; `com.taoensso/telemere-shell` dependency
          [clojure.tools.logging :as ctl] ; `org.clojure/tools.logging`   dependency
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
          [fallback, ; Unique to shell
           #_defaults #_elide? #_allow? #_expansion-id, ; Undocumented
           elidable? location #_location* inst uid middleware middleware+,
           sample-rate kind ns id level when rate-limit rate-limit-by,
           ctx ctx+ parent root trace?, do let data msg error run & kvs]}])}

     [opts]
     (if telemere-present?
       (do (require-telemere!) `(taoensso.telemere/signal! ~(signal-opts &form &env opts)))
       (let      [fb-form  (get opts :fallback)]
         (if-let [run-form (get opts :run)]
           `(let [run-result# ~run-form] ~fb-form run-result#)
           (do                            fb-form))))))

(comment
  (macroexpand
    '(signal!
       {:kind :log, :id :my-id, :level :warn,
        :let  [x :x]
        :msg  ["Hello" "world" x]
        :data {:a :A :x x}
        :fallback (println (str "Hello world " x))})))

#?(:clj
   (defmacro signal-allowed?
     "Experimental, subject to change.
     Returns true iff Telemere is present and signal with given opts would meet
     filtering conditions.

     MUST be used with `require-telemere-if-present`, example:

       (ns my-lib (:require [taoensso.telemere.shell :as t]))
       (t/require-telemere-if-present) ; Just below `ns` form!

       (when (t/signal-allowed? {:level :warn, <...>})
         (my-custom-code))"

     {:arglists
      '([{:as opts :keys
          [#_fallback, ; Unique to shell
           #_defaults #_elide? #_allow? #_expansion-id, ; Undocumented
           elidable? location #_location* #_inst #_uid #_middleware #_middleware+,
           sample-rate kind ns id level when rate-limit rate-limit-by,
           #_ctx #_ctx+ #_parent #_root #_trace?, #_do #_let #_data #_msg #_error #_run #_& #_kvs]}])}

     [opts]
     (if telemere-present?
       (do (require-telemere!) `(taoensso.telemere/signal-allowed? ~(signal-opts &form &env opts)))
       (get opts :fallback nil))))

(comment (macroexpand '(signal-allowed? {:level :warn, :sample-rate 0.5})))
