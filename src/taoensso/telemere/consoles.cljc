(ns ^:no-doc taoensso.telemere.consoles
  "Private ns, implementation detail.
  Core console handlers, aliased in main Telemere ns."
  (:require
   [taoensso.encore         :as enc :refer [have have?]]
   [taoensso.telemere.utils :as utils]))

(comment
  (require  '[taoensso.telemere :as tel])
  (remove-ns 'taoensso.telemere.consoles)
  (:api (enc/interns-overview)))

;;;; Handlers

#?(:clj
   (defn ^:public handler:console
     "Experimental, subject to change.

     Returns a (fn handler [signal]) that:
       - Takes a Telemere signal (map).
       - Writes the signal as a string to specified stream.

     A general-purpose `println`-style handler that's well suited for outputting
     signals as human or machine-readable (edn, JSON) strings.

     Options:
       `:output-fn` - (fn [signal]) => string, see `format-signal-fn` or `pr-signal-fn`
       `:stream`    - `java.io.writer`
         Defaults to `*err*` if `utils/error-signal?` is true, and `*out*` otherwise."

     ([] (handler:console nil))
     ([{:keys [output-fn stream]
        :or   {output-fn (utils/format-signal-fn)}}]

      (let [stream (case stream :*out* *out*, :*err* *err* stream)
            error-signal? utils/error-signal?]

        (fn a-handler:console
          ([]) ; Shut down (no-op)
          ([signal]
           (let [^java.io.Writer stream
                 (or stream (if (error-signal? signal) *err* *out*))]
             (when-let [output (output-fn signal)]
               (.write stream (str output))
               (.flush stream))))))))

   :cljs
   (defn ^:public handler:console
     "Experimental, subject to change.

     If `js/console` exists, returns a (fn handler [signal]) that:
       - Takes a Telemere signal (map).
       - Writes the signal as a string to JavaScript console.

     A general-purpose `println`-style handler that's well suited for outputting
     signals as human or machine-readable (edn, JSON) strings.

     Options:
       `:output-fn` - (fn [signal]) => string, see `format-signal-fn` or `pr-signal-fn`"

     ([] (handler:console nil))
     ([{:keys [output-fn]
        :or   {output-fn (utils/format-signal-fn)}}]

      (when (exists? js/console)
        (let [js-console-logger utils/js-console-logger]

          (fn a-handler:console
            ([]) ; Shut down (no-op)
            ([signal]
             (when-let [output (output-fn signal)]
               (let [logger (js-console-logger (get signal :level))]
                 (.call logger logger (str output)))))))))))

#?(:cljs
   (defn- logger-fn [logger]
     ;; (fn [& xs] (.apply logger logger (into-array xs)))
     (fn
       ([x1             ] (.call logger logger x1))
       ([x1 x2          ] (.call logger logger x1 x2))
       ([x1 x2 x3       ] (.call logger logger x1 x2 x3))
       ([x1 x2 x3 & more] (apply        logger x1 x2 x3 more)))))

#?(:cljs
   (defn ^:public handler:console-raw
     "Experimental, subject to change.

     If `js/console` exists, returns a (fn handler [signal]) that:
       - Takes a Telemere signal (map).
       - Writes the raw signal to JavaScript console.

     Intended for use with browser formatting tools like `binaryage/devtools`,
     Ref. <https://github.com/binaryage/cljs-devtools>.

     Options:
       `:preamble-fn`     - (fn [signal])   => string.
       `:format-nsecs-fn` - (fn [nanosecs]) => string."

     ([] (handler:console-raw nil))
     ([{:keys [preamble-fn format-nsecs-fn] :as opts
        :or
        {preamble-fn     (utils/signal-preamble-fn)
         format-nsecs-fn (utils/format-nsecs-fn)}}]

      (when (and (exists? js/console) (exists? js/console.group))
        (let [js-console-logger utils/js-console-logger
              content-fn ; (fn [signal append-fn val-fn])
              (utils/signal-content-fn
                {:format-nsecs-fn format-nsecs-fn
                 :format-error-fn nil
                 :raw-error?      true})]

          (fn a-handler:console-raw
            ([]) ; Shut down (no-op)
            ([signal]
             (let [{:keys [level error]} signal
                   logger (js-console-logger level)]

               ;; Unfortunately groups have no level
               (.group js/console (preamble-fn signal))
               (content-fn signal (logger-fn logger) identity)

               (when-let [stack (and error (.-stack (enc/ex-root error)))]
                 (.call logger logger stack))

               (.groupEnd js/console)))))))))
