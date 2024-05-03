Signal handlers process created signals to *do something with them* (analyse them, write them to console/file/queue/db, etc.).

# Included handlers

A number of signal handlers are included out-the box. Alphabetically:

| Name                                                                                                                                                     | Platform | Output target                                                                                                  | Output format                                                          |
| :------------------------------------------------------------------------------------------------------------------------------------------------------- | :------- | :------------------------------------------------------------------------------------------------------------- | :--------------------------------------------------------------------- |
| [`handler:carmine`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.carmine#handler:carmine) [0]                                | Clj      | [Redis](https://redis.io/) (via [Carmine](https://www.taoensso.com/carmine))                                   | Serialized signals [1]                                                 |
| [`handler:console`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:console)                                            | Clj      | `*out*` or `*err*`                                                                                             | String [2]                                                             |
| [`handler:console`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:console)                                            | Cljs     | Browser console                                                                                                | String [2]                                                             |
| [`handler:console-raw`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:console-raw)                                    | Cljs     | Browser console                                                                                                | Raw signals [3]                                                        |
| [`handler:file`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:file)                                                  | Clj      | File/s on disk                                                                                                 | String [2]                                                             |
| [`handler:logstash`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.logstash#handler:logstash) [0]                             | Clj      | [Logstash](https://www.elastic.co/logstash)                                                                    | TODO                                                                   |
| [`handler:open-telemetry-logger`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.open-telemetry#handler:open-telemetry-logger) | Clj      | [OpenTelemetry](https://opentelemetry.io/) [Java client](https://github.com/open-telemetry/opentelemetry-java) | [LogRecord](https://opentelemetry.io/docs/specs/otel/logs/data-model/) |
| [`handler:postal`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.postal#handler:postal)                                       | Clj      | Email (via [postal](https://github.com/drewr/postal))                                                          | String [2]                                                             |
| [`handler:slack`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.slack#handler:slack)                                          | Clj      | [Slack](https://slack.com/) (via [clj-slack](https://github.com/julienXX/clj-slack))                           | String [2]                                                             |
| [`handler:tcp-socket`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.sockets#handler:tcp-socket)                              | Clj      | TCP socket                                                                                                     | String [2]                                                             |
| [`handler:udp-socket`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.sockets#handler:udp-socket)                              | Clj      | UDP socket                                                                                                     | String [2]                                                             |

- \[0] Coming soon
- \[1] Uses [Nippy](https://taoensso.com/nippy) to support all Clojure's rich data types
- \[2] [Human-readable](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#format-signal-fn) (default), or [machine-readable](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#pr-signal-fn) ([edn](https://github.com/edn-format/edn), [JSON](https://www.json.org/), etc.).
- \[3] For use with browser formatting tools like [cljs-devtools](https://github.com/binaryage/cljs-devtools).
- See relevant docstrings (links above) for features, usage, etc.
- See section [8-Community](8-Community.md) for more (community-supported) handlers.
- If there's other handlers you'd like to see, feel free to [ping me](https://github.com/taoensso/telemere/issues), or ask on the [`#telemere` Slack channel](https://www.taoensso.com/telemere/slack). It helps to know what people most need!

# Configuring handlers

There's two kinds of config relevant to all signal handlers:

1. **Dispatch** opts (common to all handlers), and
2. **Handler-specific** opts

## Dispatch opts

Dispatch opts includes dispatch priority, handler filtering, handler middleware, queue semantics, back-pressure opts, etc.

This is all specified when calling [`add-handler!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#add-handler!) - and documented there.

Note that handler middleware in particular is an often overlooked but powerful feature, allowing you to arbitrarily transform and/or filter every [signal map](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content) before it is given to the handler.

## Handler-specific opts

Handler-specific opts are specified when calling a particular **handler constructor** (like [`handler:console`](https://cljdoc.org/d/com.taoensso/telemere/CONSOLE/api/taoensso.telemere#handler:console)) - and documented by the constructor.

Note that it's common for Telemere handlers to be customized by providing *Clojure/Script functions* to the relevant handler constructor call.

See the [utils namespace](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils) for tools useful for customizing and writing signal handlers.

### Example

The standard Clj/s console handler ([`handler:console`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:console)) writes signals **as strings** to `*out*`/`*err` or browser console.

By default it writes formatted strings intended for human consumption:

```clojure
;; Create a test signal
(def my-signal
  (t/with-signal
    (t/log! {:id ::my-id, :data {:x1 :x2}} "My message")))

;; Create console handler with default opts (writes formatted string)
(def my-handler (t/handler:console))

;; Test handler, remember it's just a (fn [signal])
(my-handler my-signal) ; =>
;; 2024-04-11T10:54:57.202869Z INFO LOG Schrebermann.local examples(56,1) ::my-id - My message
;;     data: {:x1 :x2}
```

To instead writes signals as edn:

```clojure
;; Create console which writes edn
(def my-handler
  (t/handler:console
    {:output-fn (t/pr-signal-fn :edn)}))

(my-handler my-signal) ; =>
;; {:inst #inst "2024-04-11T10:54:57.202869Z", :msg_ "My message", :ns "examples", ...}
```

To instead writes signals as JSON:

```clojure
;; Create console which writes signals as JSON
#?(:clj (require '[jsonista.core :as jsonista]))
(def my-handler
  (t/handler:console
    {:output-fn
     (t/pr-signal-fn
       #?(:cljs :json
          :clj  jsonista.core/write-value-as-string))}))
```

Note that when writing JSON with Clojure, you *must* provide an appropriate `pr-fn`. This lets you plug in the JSON serializer of your choice ([jsonista](https://github.com/metosin/jsonista) is my default recommendation).

### Handler-specific per-signal kvs

Telemere includes a handy mechanism for including arbitrary user-level data/opts in individual signals for use by custom middleware and/or handlers.

Any *non-standard* (user) keys you include in your signal constructor opts will automatically be included in created signals, e.g.:

```clojure
(t/with-signal
  (t/event! ::my-id
    {:my-middleware-data "foo"
     :my-handler-data    "bar"}))

;; %>
;; {;; User kvs included inline (assoc'd to signal root)
;;  :my-middleware-data "foo"
;;  :my-handler-data    "bar"
;;  :kvs ; And also collected together under ":kvs" key
;;    {:my-middleware-data "foo"
;;     :my-handler-data    "bar"}
;;  ... }
```

These user-level data/opts are typically NOT included by default in handler output, making them a great way to convey data/opts to custom middleware/handlers.

# Managing handlers

See [`help:signal-handlers`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-handlers) for info on handler management.

## Managing handlers on startup

Want to add or remove a particular handler when your application starts?

Just make an appropriate call to [`add-handler!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#add-handler!) or [`remove-handler!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#remove-handler!).

## Environmental config

If you want to manage handlers **conditionally** based on **environmental config** (JVM properties, environment variables, or classpath resources) - Telemere provides the highly flexible [`get-env`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#get-env) util.

Use this to easily define your own arbitrary cross-platform config, and make whatever conditional handler management decisions you'd like.

# Writing handlers

Writing your own signal handlers for Telemere is straightforward, and a reasonable choice if you prefer customizing behaviour that way, or want to write signals to a DB/format/service for which a ready-made handler isn't available.

Remember that signals are just plain Clojure/Script [maps](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content), and handlers just plain Clojure/Script functions that do something with those maps.

Here's a simple Telemere handler:

```clojure
(fn my-handler [signal] (println signal))
```

For more complex cases, or for handlers that you want to make available for use by other folks, here's the general template that Telemere uses for all its included handlers:

```clojure
(defn handler:my-handler ; Note naming convention
  "Needs `some-lib`, Ref. <https://github.com/example/some-lib>.

  Returns a (fn handler [signal] that:
    - Takes a Telemere signal (map).
    - Does something with the signal.

  Options:
    `:option1` - Option description
    `:option2` - Option description

  Tips:
    - Tip 1
    - Tip 2"

  ([] (handler:my-handler nil)) ; Use default opts (when defaults viable)
  ([{:as constructor-opts}]

   ;; Do option validation and other prep here, i.e. try to keep expensive work
   ;; outside handler function when possible.

   (let [handler-fn
         (fn a-handler:my-handler ; Note naming convention

           ;; Shutdown arity - called by Telemere exactly once when the handler
           ;; is to be shut down.
           ([]
            ;; Can no-op, or finalize/free resources as necessary.
            )

           ;; Main arity - called by Telemere whenever the handler should handle
           ;; the given signal. Never called after shutdown.
           ([signal]
            ;; Do something with given signal (write to console/file/queue/db, etc.).
            ;; Return value is ignored.
            ))

         ;; (Advanced) optional default handler dispatch opts,
         ;; see `add-handler!` for full list of possible opts
         default-dispatch-opts
         {:min-level  :info
          :rate-limit [[1 (enc/msecs :min 1)]]}]

     (with-meta handler-fn default-dispatch-opts))))
```

- See [`help:signal-content`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content) for signal map content.
- See the [utils namespace](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils) for tools useful for customizing and writing signal handlers.
- See section [8-Community](8-Community.md) for PRs to link to community-authored handlers.

# Example output

```clojure
(t/log! {:id ::my-id, :data {:x1 :x2}} "My message") =>
```

## Clj console handler

String output:

```
2024-04-11T10:54:57.202869Z INFO LOG Schrebermann.local examples(56,1) ::my-id - My message
    data: {:x1 :x2}
```

## Cljs console handler

Chrome console:

<img src="https://raw.githubusercontent.com/taoensso/telemere/master/imgs/handler-output-cljs-console.png" alt="Default ClojureScript console handler output" width="640"/>

## Cljs raw console handler

Chrome console, with [cljs-devtools](https://github.com/binaryage/cljs-devtools):

<img src="https://raw.githubusercontent.com/taoensso/telemere/master/imgs/handler-output-cljs-console-raw.png" alt="Raw ClojureScript console handler output" width="640"/>

## Clj file handler

MacOS terminal:

<img src="https://raw.githubusercontent.com/taoensso/telemere/master/imgs/handler-output-clj-file.png" alt="Default Clojure file handler output" width="640"/>
