(ns taoensso.telemere.sockets
  "Basic TCP/UDP socket handlers."
  (:require
   [taoensso.encore :as enc :refer [have have?]]
   [taoensso.telemere.utils :as utils])

  (:import
   [java.net Socket InetAddress]
   [java.net DatagramSocket DatagramPacket InetSocketAddress]
   [java.io  PrintWriter]))

(comment
  (require  '[taoensso.telemere :as tel])
  (remove-ns 'taoensso.telemere.sockets)
  (:api (enc/interns-overview)))

(defn handler:tcp-socket
  "Experimental, subject to change.

  Returns a signal handler that:
    - Takes a Telemere signal (map).
    - Sends the signal as a string to specified TCP socket.

  Can output signals as human or machine-readable (edn, JSON) strings.

  Options:
    `:output-fn`   - (fn [signal]) => string, see `format-signal-fn` or `pr-signal-fn`
    `:socket-opts` - {:keys [host port ssl? connect-timeout-msecs]}
      `:host`      - Destination TCP socket hostname string
      `:port`      - Destination TCP socket port     int
      `:ssl?`      - Use SSL/TLS (default false)
      `:connect-timeout-msecs` - Connection timeout (default 3000 msecs)

  Limitations:
    - Failed writes will be retried only once.
    - Writes lock on a single underlying socket, so IO won't benefit from adding
      extra handler threads. Let me know if there's demand for socket pooling."

  ;; ([] (handler:tcp-socket nil))
  ([{:keys [socket-opts output-fn]
     :or   {output-fn (utils/format-signal-fn)}}]

   (let [sw (utils/tcp-socket-writer socket-opts)]
     (fn a-handler:tcp-socket
       ([      ] (sw)) ; Stop => close socket
       ([signal]
        (when-let [output (output-fn signal)]
          (sw output)))))))

(defn handler:udp-socket
  "Highly experimental, subject to change.
  Feedback very welcome!

  Returns a signal handler that:
    - Takes a Telemere signal (map).
    - Sends the signal as a string to specified UDP socket.

  Can output signals as human or machine-readable (edn, JSON) strings.

  Options:
    `:output-fn`          - (fn [signal]) => string, see `format-signal-fn` or `pr-signal-fn`
    `:socket-opts`        - {:keys [host port max-packet-bytes]}
      `:host`             - Destination UDP socket hostname string
      `:port`             - Destination UDP socket port     int
      `:max-packet-bytes` - Max packet size (in bytes) before truncating output (default 512)

    `:truncation-warning-fn`
      Optional (fn [{:keys [max actual signal]}]) to call whenever output is truncated.
      Should be appropriately rate-limited!

  Limitations:
    - Due to UDP limitations, truncates output to `max-packet-bytes`!
    - Failed writes will be retried only once.
    - Writes lock on a single underlying socket, so IO won't benefit from adding
      extra handler threads. Let me know if there's demand for socket pooling.
    - No DTLS (Datagram Transport Layer Security) support,
      please let me know if there's demand."

  ;; ([] (handler:udp-socket nil))
  ([{:keys [socket-opts output-fn truncation-warning-fn]
     :or
     {socket-opts {:max-packet-bytes 512}
      output-fn   (utils/format-signal-fn)}}]

   (let [{:keys [host port max-packet-bytes]
          :or   {max-packet-bytes 512}} socket-opts

         max-packet-bytes (int max-packet-bytes)

         socket (DatagramSocket.) ; No need to change socket once created
         lock   (Object.)]

     (when-not (string? host) (throw (ex-info "Expected `:host` string" (enc/typed-val host))))
     (when-not (int?    port) (throw (ex-info "Expected `:port` int"    (enc/typed-val port))))

     (.connect socket (InetSocketAddress. (str host) (int port)))

     (fn a-handler:udp-socket
       ([      ] (locking lock (.close socket))) ; Stop => close socket
       ([signal]
        (when-let [output (output-fn signal)]
          (let [ba     (enc/str->utf8-ba (str output))
                ba-len (alength ba)
                packet (DatagramPacket. ba (min ba-len max-packet-bytes))]

            (when (and truncation-warning-fn (> ba-len max-packet-bytes))
              ;; Fn should be appropriately rate-limited
              (truncation-warning-fn {:max max-packet-bytes, :actual ba-len, :signal signal}))

            (locking lock
              (try
                (.send (DatagramSocket.) packet)
                (catch Exception _ ; Retry once
                  (Thread/sleep 250)
                  (.send (DatagramSocket.) packet)))))))))))
