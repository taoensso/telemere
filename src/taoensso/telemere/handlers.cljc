(ns ^:no-doc taoensso.telemere.handlers
  "Built-in Telemere handlers."
  (:require
   [clojure.string          :as str]
   [taoensso.encore         :as enc :refer [have have?]]
   [taoensso.encore.stats   :as stats]
   [taoensso.telemere.utils :as utils]))

(comment
  (require  '[taoensso.telemere :as tel])
  (remove-ns 'taoensso.telemere.handlers)
  (:api (enc/interns-overview)))

#?(:clj
   (defn console-handler-fn
     "Experimental, subject to change.
     Returns a (fn handler [signal]) that:
       - Takes a Telemere signal.
       - TODO"
     ([] (console-handler-fn nil))
     ([{:keys [format-signal-fn stream]
        :or   {format-signal-fn (utils/format-signal->str-fn)}}]

      (let [stream (case stream :*out* *out*, :*err* *err* stream)
            error-signal? utils/error-signal?
            nl            utils/newline]

        (fn console-handler [signal]
          (let [^java.io.Writer stream
                (or stream (if (error-signal? signal) *err* *out*))]
            (when-let [output (format-signal-fn signal)]
              (.write stream (str output nl))
              (.flush stream)))))))

   :cljs
   (defn console-handler-fn
     "Experimental, subject to change.
     If `js/console` exists, returns a (fn handler [signal]) that:
       - Takes a Telemere signal.
       - TODO"

     ;; TODO Mention `format-signal-fn` options, incl.:
     ;; `utils/format-signal->str-fn`
     ;; `utils/format-signal->edn-fn`
     ;; `utils/format-signal->json-fn`
     ;; `nil` (raw, for use with `binaryage/devtools`, etc.)?

     ([] (console-handler-fn nil))
     ([{:keys [format-signal-fn]
        :or   {format-signal-fn (utils/format-signal->str-fn)}}]

      (when (exists? js/console)
        (let [js-console-logger utils/js-console-logger
              nl utils/newline]

          (fn console-handler [signal]
            (when-let [output (format-signal-fn signal)]
              (let [logger (js-console-logger (get signal :level))]
                (.call logger logger (str output nl))))))))))

#?(:cljs
   (defn raw-console-handler-fn
     "TODO"
     ([] (raw-console-handler-fn nil))
     ([{:keys [format-signal-prelude-fn]
        :or   {format-signal-prelude-fn (utils/format-signal-prelude-fn)}}]

      (when (and (exists? js/console) (exists? js/console.group))

        (let [js-console-logger utils/js-console-logger
              nl utils/newline]

          (fn raw-console-handler [signal]
            (let [logger (js-console-logger (get signal :level))]
              ;; Unfortunately groups have no level
              (.group js/console (format-signal-prelude-fn signal))
              ;; TODO Content
              (.call logger logger (ex-info "Ex" {:k1 :v1 :k2 #{"v1" "v2"}}))
              (.groupEnd js/console))))))))
