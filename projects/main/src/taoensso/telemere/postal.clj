(ns taoensso.telemere.postal
  "Email handler using `postal`,
    Ref. <https://github.com/drewr/postal>."
  (:require
   [taoensso.encore         :as enc]
   [taoensso.encore.signals :as sigs]
   [taoensso.telemere.utils :as utils]
   [postal.core :as postal]))

(comment
  (require  '[taoensso.telemere :as tel])
  (remove-ns (symbol (str *ns*)))
  (:api (enc/interns-overview)))

(def default-dispatch-opts
  {:min-level :info
   :rate-limit
   [[5  (enc/msecs :mins  1)]
    [10 (enc/msecs :mins 15)]
    [15 (enc/msecs :hours 1)]
    [30 (enc/msecs :hours 6)]
    ]})

(defn handler:postal
  "Experimental, subject to change.

  Needs `postal`, Ref. <https://github.com/drewr/postal>.

  Returns a signal handler that:
    - Takes a Telemere signal (map).
    - Sends the signal as an email to specified recipient.

  Useful for emailing important alerts to admins, etc.

  Default handler dispatch options (override when calling `add-handler!`):
    `:min-level`  - `:info`
    `:rate-limit` -
      [[5  (enc/msecs :mins  1)] ; Max 5  emails in 1  min
       [10 (enc/msecs :mins 15)] ; Max 10 emails in 15 mins
       [15 (enc/msecs :hours 1)] ; Max 15 emails in 1  hour
       [30 (enc/msecs :hours 6)] ; Max 30 emails in 6  hours
       ]

  Options:
    `:conn-opts` - Map of connection opts given to `postal/send-message`
      Examples:
        {:host \"mail.isp.net\",   :user \"jsmith\",           :pass \"a-secret\"},
        {:host \"smtp.gmail.com\", :user \"jsmith@gmail.com\", :pass \"a-secret\" :port 587 :tls true},
        {:host \"email-smtp.us-east-1.amazonaws.com\", :port 587, :tls true,
         :user \"AKIAIDTP........\", :pass \"AikCFhx1P.......\"}

    `:msg-opts` - Map of message opts given to `postal/send-message`
      Examples:
        {:from \"foo@example.com\",        :to \"bar@example.com\"},
        {:from \"Alice <foo@example.com\", :to \"Bob <bar@example.com>\"},
        {:from \"no-reply@example.com\",   :to [\"first-responders@example.com\",
                                                \"devops@example.com\"],
         :cc \"engineering@example.com\"
         :X-MyHeader \"A custom header\"}

    `:subject-fn`      - (fn [signal]) => email subject string
    `:subject-max-len` - Truncate subjects beyond this length (default 90)

    `:body-fn` - (fn [signal]) => email body content string,
                   see `format-signal-fn` or `pr-signal-fn`

  Tips:
    - Ref. <https://github.com/drewr/postal> for more info on `postal` options.
    - Sending emails can be slow, and can incur financial costs!
      Use appropriate handler dispatch options for async handling and rate limiting, etc."

  ;; ([] (handler:postal nil))
  ([{:keys [conn-opts msg-opts, subject-fn subject-max-len body-fn]
     :or
     {body-fn    (utils/format-signal-fn)
      subject-fn (utils/signal-preamble-fn {:format-inst-fn nil})
      subject-max-len 128}}]

   (when-not (map? conn-opts) (throw (ex-info "Expected `:conn-opts` map" (enc/typed-val conn-opts))))
   (when-not (map? msg-opts)  (throw (ex-info "Expected `:msg-opts` map"  (enc/typed-val msg-opts))))

   (let [subject-fn
         (if-let [n subject-max-len]
           (comp
             (fn [s] (when s (enc/substr (str s) 0 n)))
             subject-fn))

         handler-fn
         (fn a-handler:postal
           ([      ]) ; Stop => noop
           ([signal]
            (enc/when-let [subject (subject-fn signal)
                           body    (body-fn    signal)]
              (let [msg
                    (assoc msg-opts
                      :subject (str subject)
                      :body
                      (if (string? body)
                        [{:type    "text/plain; charset=utf-8"
                          :content (str body)}]
                        body))

                    [result ex]
                    (try
                      [(postal/send-message conn-opts msg) nil]
                      (catch Exception ex [nil ex]))

                    success? (= (get result :code) 0)]

                (when-not success?
                  (throw (ex-info "Failed to send email" result ex)))))))]

     (with-meta handler-fn
       {:dispatch-opts default-dispatch-opts}))))
