(ns taoensso.telemere.postal
  "Email handler using `postal`,
    Ref. <https://github.com/drewr/postal>."
  (:require
   [taoensso.encore :as enc :refer [have have?]]
   [taoensso.telemere.utils :as utils]
   [postal.core :as postal]))

(comment
  (require  '[taoensso.telemere :as tel])
  (remove-ns 'taoensso.telemere.postal)
  (:api (enc/interns-overview)))

;;;; Implementation

(defn format-signal->subject-fn
  "Experimental, subject to change.
  Returns a (fn format [signal]) that:
    - Takes a Telemere signal.
    - Returns a formatted email subject like:
      \"INFO EVENT :taoensso.telemere.postal/ev-id1 - msg\""
  ([] (format-signal->subject-fn nil))
  ([{:keys [max-len subject-signal-key]
     :or
     {max-len 128
      subject-signal-key :postal/subject}}]

   (fn format-signal->subject [signal]
     (or
       (get signal subject-signal-key) ; Custom subject

       ;; Simplified `format-signal->prelude-fn`
       (let [{:keys [level kind #_ns id msg_]} signal
             sb    (enc/str-builder)
             s+spc (enc/sb-appender sb " ")]

         (when level (s+spc (utils/format-level level)))
         (when kind  (s+spc (utils/upper-qn     kind)))
         (when id    (s+spc (utils/format-id nil id)))
         (when-let [msg (force msg_)] (s+spc "- " msg))

         (enc/get-substr-by-len (str sb) 0 max-len))))))

(comment
  ((format-signal->subject-fn)
   (tel/with-signal (tel/event! ::ev-id1 #_{:postal/subject "My subject"}))))

;;;; Handler

(defn handler:postal
  "Experimental, subject to change. Feedback welcome!

  Needs `postal`,
    Ref. <https://github.com/drewr/postal>.

  Returns a (fn handler [signal]) that:
    - Takes a Telemere signal.
    - Sends an email with formatted signal content to the configured recipient.

  Useful for emailing important alerts to admins, etc.

  NB can incur financial costs!!
  See tips section re: protecting against unexpected costs.

  Options:

    `:postal/conn-opts` - Map of connection opts provided to `postal`
      Examples:
        {:host \"mail.isp.net\",   :user \"jsmith\",           :pass \"a-secret\"},
        {:host \"smtp.gmail.com\", :user \"jsmith@gmail.com\", :pass \"a-secret\" :port 587 :tls true},
        {:host \"email-smtp.us-east-1.amazonaws.com\", :port 587, :tls true
         :user \"AKIAIDTP........\" :pass \"AikCFhx1P.......\"}

    `:postal/msg-opts` - Map of message options
      Examples:
        {:from \"foo@example.com\",        :to \"bar@example.com\"},
        {:from \"Alice <foo@example.com\", :to \"Bob <bar@example.com>\"},
        {:from \"no-reply@example.com\",   :to [\"first-responders@example.com\",
                                                \"devops@example.com\"],
         :cc \"engineering@example.com\"
         :X-MyHeader \"A custom header\"}

    `:format-signal-fn`          - (fn [signal]) => output, see `help:signal-formatters`
    `:format-signal->subject-fn` - (fn [signal]) => email subject string

  Tips:

    - Sending emails can incur financial costs!
      Use appropriate dispatch filtering options when calling `add-handler!` to prevent
      handler from sending unnecessary emails!

      At least ALWAYS set an appropriate `:rate-limit` option, e.g.:
        (add-handler! :my-postal-handler (handler:postal {<my-handler-opts})
          {:rate-limit {\"Max 1 per min\"     [1 (enc/msecs :mins  1)]
                        \"Max 3 per 15 mins\" [3 (enc/msecs :mins 15)]
                        \"Max 5 per hour\"    [5 (enc/msecs :hours 1)]}, ...}), etc.

    - Sending emails is slow!
      Use appropriate async dispatch options when calling `add-handler!` to prevent
      handler from blocking signal creator calls, e.g.:
        (add-handler! :my-postal-handler (handler:postal {<my-handler-opts>})
          {:async {:mode :dropping, :buffer-size 128, :n-threads 4} ...}), etc.

    - Ref. <https://github.com/drewr/postal> for more info on `postal` options."

  ([] (handler:postal nil))
  ([{:keys
     [postal/conn-opts
      postal/msg-opts
      format-signal-fn
      format-signal->subject-fn]

     :or
     {format-signal-fn          (utils/format-signal->str-fn)
      format-signal->subject-fn (format-signal->subject-fn)}}]

   (when-not conn-opts (throw (ex-info "No `:postal/conn-opts` was provided" {})))
   (when-not msg-opts  (throw (ex-info "No `:postal/msg-opts` was provided"  {})))

   (let []
     (defn a-handler:postal
       ([]) ; Shut down (no-op)
       ([signal]
        (let [msg
              (assoc msg-opts
                :subject (format-signal->subject-fn)
                :body
                [{:type    "text/plain; charset=utf-8"
                  :content (format-signal-fn signal)}])

              [result ex]
              (try
                [(postal/send-message conn-opts msg) nil]
                (catch Exception ex [nil ex]))

              success? (= (get result :code) 0)]

          (when-not success?
            (throw (ex-info "Failed to send email" result ex)))))))))
