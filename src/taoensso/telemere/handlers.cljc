(ns taoensso.telemere.handlers
  "Built-in Telemere handlers."
  (:require
   [clojure.string          :as str]
   [taoensso.encore         :as enc :refer [have have?]]
   [taoensso.telemere.utils :as utils]))

(comment
  (require  '[taoensso.telemere :as tel])
  (remove-ns 'taoensso.telemere.handlers)
  (:api (enc/interns-overview)))

#?(:clj
   (defn console-handler
     "Experimental, subject to change.

     Returns a (fn handler [signal]) that:
       - Takes a Telemere signal.
       - Writes a formatted signal string to stream.

     Stream (`java.io.Writer`):
       Defaults to `*err*` if `utils/error-signal?` is true, and `*out*` otherwise.

     Common formatting alternatives:
       (utils/format-signal-str->fn) {<opts>}) ; For human-readable string output (default)
       (utils/format-signal->edn-fn) {<opts>}) ; For edn  output
       (utils/format-signal->json-fn {<opts>}) ; For JSON output
       etc.

       See each format builder for options, etc."

     ([] (console-handler nil))
     ([{:keys [format-signal-fn stream]
        :or   {format-signal-fn (utils/format-signal->str-fn)}}]

      (let [stream (case stream :*out* *out*, :*err* *err* stream)
            error-signal? utils/error-signal?
            nl            utils/newline]

        (fn a-console-handler
          ([]) ; Shut down (no-op)
          ([signal]
           (let [^java.io.Writer stream
                 (or stream (if (error-signal? signal) *err* *out*))]
             (when-let [output (format-signal-fn signal)]
               (.write stream (str output nl))
               (.flush stream))))))))

   :cljs
   (defn console-handler
     "Experimental, subject to change.

     If `js/console` exists, returns a (fn handler [signal]) that:
       - Takes a Telemere signal.
       - Writes a formatted signal string to JavaScript console.

     Common formatting alternatives:
       (utils/format-signal-str->fn) {<opts>}) ; For human-readable string output (default)
       (utils/format-signal->edn-fn) {<opts>}) ; For edn  output
       (utils/format-signal->json-fn {<opts>}) ; For JSON output
       etc.

       See each format builder for options, etc."

     ([] (console-handler nil))
     ([{:keys [format-signal-fn]
        :or   {format-signal-fn (utils/format-signal->str-fn)}}]

      (when (exists? js/console)
        (let [js-console-logger utils/js-console-logger
              nl utils/newline]

          (fn a-console-handler
            ([]) ; Shut down (no-op)
            ([signal]
             (when-let [output (format-signal-fn signal)]
               (let [logger (js-console-logger (get signal :level))]
                 (.call logger logger (str output nl)))))))))))

#?(:cljs
   (defn- logger-fn [logger]
     ;; (fn [& xs] (.apply logger logger (into-array xs)))
     (fn
       ([x1             ] (.call logger logger x1))
       ([x1 x2          ] (.call logger logger x1 x2))
       ([x1 x2 x3       ] (.call logger logger x1 x2 x3))
       ([x1 x2 x3 & more] (apply        logger x1 x2 x3 more)))))

#?(:cljs
   (defn raw-console-handler
     "Experimental, subject to change.

     If `js/console` exists, returns a (fn handler [signal]) that:
       - Takes a Telemere signal.
       - Writes raw signal data to JavaScript console.

     Intended for use with browser formatting tools like `binaryage/devtools`,
     Ref. <https://github.com/binaryage/cljs-devtools>."

     ([] (raw-console-handler nil))
     ([{:keys [format-signal-prelude-fn format-nsecs-fn] :as opts
        :or
        {format-signal-prelude-fn (utils/format-signal-prelude-fn) ; (fn [signal])
         format-nsecs-fn          (utils/format-nsecs-fn)          ; (fn [nanosecs])
         }}]

      (when (and (exists? js/console) (exists? js/console.group))
        (let [js-console-logger utils/js-console-logger
              signal-content-handler ; (fn [signal hf vf]
              (utils/signal-content-handler
                {:format-nsecs-fn format-nsecs-fn
                 :format-error-fn nil
                 :raw-error?      true})]

          (fn a-raw-console-handler
            ([]) ; Shut down (no-op)
            ([signal]
             (let [{:keys [level error]} signal
                   logger (js-console-logger level)]

               ;; Unfortunately groups have no level
               (.group js/console (format-signal-prelude-fn signal))
               (signal-content-handler signal (logger-fn logger) identity)

               (when-let [stack (and error (.-stack (enc/ex-root error)))]
                 (.call logger logger stack))

               (.groupEnd js/console)))))))))
