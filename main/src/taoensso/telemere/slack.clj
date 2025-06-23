(ns taoensso.telemere.slack
  "Telemere -> Slack handler using `clj-slack`,
  Ref. <https://github.com/julienXX/clj-slack>"
  (:require
   [taoensso.truss  :as truss]
   [taoensso.encore :as enc]
   [taoensso.telemere.utils :as utils]
   [clj-slack.core  :as slack]
   [clj-slack.chat  :as slack.chat]))

(comment
  (require  '[taoensso.telemere :as tel])
  (remove-ns (symbol (str *ns*)))
  (:api (enc/interns-overview)))

(def default-dispatch-opts
  {:min-level :info
   :limit
   [[5  (enc/msecs :mins  1)]
    [10 (enc/msecs :mins 15)]
    [15 (enc/msecs :hours 1)]
    [30 (enc/msecs :hours 6)]]})

(defn handler:slack
  "Alpha, subject to change.

  Needs `clj-slack`, Ref. <https://github.com/julienXX/clj-slack>.

  Returns a signal handler that:
    - Takes a Telemere signal (map).
    - Writes the signal as a string to specified Slack channel.

  Can output signals as human or machine-readable (edn, JSON) strings.

  Default handler dispatch options (override when calling `add-handler!`):
    `:min-level` - `:info`
    `:limit` -
      [[5  (enc/msecs :mins  1)] ; Max 5  posts in 1  min
       [10 (enc/msecs :mins 15)] ; Max 10 posts in 15 mins
       [15 (enc/msecs :hours 1)] ; Max 15 posts in 1  hour
       [30 (enc/msecs :hours 6)] ; Max 30 posts in 6  hours
       ]

  Options:
     `:output-fn` - (fn [signal]) => string, see `format-signal-fn` or `pr-signal-fn`
     `:conn-opts` - Map of connection opts given to `clj-slack.chat/post-message`
       Examples:
         {:token \"MY-TOKEN\"}
         {:token \"MY-TOKEN\", :api-url \"https://slack.com/api\"}

     `:post-opts` - Map of post opts given to `clj-slack.chat/post-message`
       Examples:
         {:channel-id \"C12345678\", :username \"MY_BOT\"}

  Tips:
    - See `clj-slack` docs for more info on its options."

  ;; ([] (handler:slack nil))
  ([{:keys [conn-opts post-opts output-fn]
     :or
     {conn-opts {:api-url "https://slack.com/api", :token nil}
      post-opts {:channel-id nil, :username nil}
      output-fn (utils/format-signal-fn)}}]

   (let [{:keys [api-url token]
          :or   {api-url "https://slack.com/api"}} conn-opts

         {:keys [channel-id]} post-opts
         post-opts    (dissoc post-opts :channel-id)

         _ (when-not (string? token)      (truss/ex-info! "Expected `:conn-opts/token` string"      (truss/typed-val token)))
         _ (when-not (string? channel-id) (truss/ex-info! "Expected `:post-opts/channel-id` string" (truss/typed-val channel-id)))

         handler-fn
         (fn a-handler:slack
           ([      ]) ; Stop => noop
           ([signal]
            (when-let [output (output-fn signal)]
              (slack.chat/post-message conn-opts channel-id
                output post-opts))))]

     (with-meta handler-fn
       {:dispatch-opts default-dispatch-opts}))))
