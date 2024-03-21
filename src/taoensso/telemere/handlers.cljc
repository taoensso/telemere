(ns ^:no-doc taoensso.telemere.handlers
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
   (defn console-handler-fn
     "Experimental, subject to change.

     Returns a (fn handler [signal]) that:
       - Takes a Telemere signal.
       - Writes a formatted signal string to stream.

     Stream (`java.io.Writer`):
       Defaults to `*err*` if `utils/error-signal?` is true, and `*out*` otherwise.

     Common formatting alternatives:
       (utils/format-signal-str->fn)  ; For human-readable string output (default)
       (utils/format-signal->edn-fn)  ; For edn  output
       (utils/format-signal->json-fn) ; For JSON output
       etc.

       See each format builder for options, etc."

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
       - Writes a formatted signal string to JavaScript console.

     Common formatting alternatives:
       (utils/format-signal-str->fn)  ; For human-readable string output (default)
       (utils/format-signal->edn-fn)  ; For edn  output
       (utils/format-signal->json-fn) ; For JSON output
       etc.

       See each format builder for options, etc."

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
   (defn- logger-fn [logger]
     ;; (fn [& xs] (.apply logger logger (into-array xs)))
     (fn
       ([x1             ] (.call logger logger x1))
       ([x1 x2          ] (.call logger logger x1 x2))
       ([x1 x2 x3       ] (.call logger logger x1 x2 x3))
       ([x1 x2 x3 & more] (apply        logger x1 x2 x3 more)))))

#?(:cljs
   (defn raw-console-handler-fn
     "Experimental, subject to change.

     If `js/console` exists, returns a (fn handler [signal]) that:
       - Takes a Telemere signal.
       - Writes raw signal data to JavaScript console.

     Intended for use with browser formatting tools like `binaryage/devtools`,
     Ref. <https://github.com/binaryage/cljs-devtools>."

     ([] (raw-console-handler-fn nil))
     ([{:keys [format-signal-prelude-fn format-nsecs-fn] :as opts
        :or
        {format-signal-prelude-fn (utils/format-signal-prelude-fn) ; (fn [signal])
         format-nsecs-fn          (utils/format-nsecs-fn)          ; (fn [nanosecs])
         }}]

      (when (and (exists? js/console) (exists? js/console.group))
        (let [js-console-logger utils/js-console-logger
              handle-signal-content-fn ; (fn [signal hf vf]
              (utils/handle-signal-content-fn
                {:format-nsecs-fn format-nsecs-fn
                 :format-error-fn nil
                 :raw-error?      true})]

          (fn raw-console-handler [signal]
            (let [{:keys [level error]} signal
                  logger (js-console-logger level)]

              ;; Unfortunately groups have no level
              (.group js/console (format-signal-prelude-fn signal))
              (handle-signal-content-fn signal (logger-fn logger) identity)

              (when-let [stack (and error (.-stack (enc/ex-root error)))]
                (.call logger logger stack))

              (.groupEnd js/console))))))))
