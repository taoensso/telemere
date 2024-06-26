Signal handlers process created signals to **do something with them** (analyse them, write them to console/file/queue/db, etc.).

# Included handlers

Telemere includes a number of signal handlers out-the-box.

[Writing your own handlers](#writing-handlers) is also often straight-forward, and [PRs](https://github.com/taoensso/telemere/pulls) are **very welcome** for additions to Telemere's included handlers, or to Telemere's [community resources](./8-Community).

It helps to know what people need! You can [vote on](https://www.taoensso.com/roadmap/vote) additional handlers to add, [ping me](https://github.com/taoensso/telemere/issues), or ask on the [`#telemere` Slack channel](https://www.taoensso.com/telemere/slack). 

Current handlers, alphabetically (see linked docstrings below for features and usage):

| Name                                                                                                                                                     | Platform | Output target                                                                                                  | Output format                                                                                                                                                                                                    |
| :------------------------------------------------------------------------------------------------------------------------------------------------------- | :------- | :------------------------------------------------------------------------------------------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [`handler:console`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:console)                                            | Clj      | `*out*` or `*err*`                                                                                             | [edn/JSON](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#pr-signal-fn) or [human-readable](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#format-signal-fn) |
| [`handler:console`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:console)                                            | Cljs     | Browser console                                                                                                | [edn/JSON](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#pr-signal-fn) or [human-readable](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#format-signal-fn) |
| [`handler:console-raw`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:console-raw)                                    | Cljs     | Browser console                                                                                                | Raw signals for [cljs-devtools](https://github.com/binaryage/cljs-devtools), etc.                                                                                                                                |
| [`handler:file`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:file)                                                  | Clj      | File/s on disk                                                                                                 | [edn/JSON](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#pr-signal-fn) or [human-readable](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#format-signal-fn) |
| [`handler:open-telemetry-logger`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.open-telemetry#handler:open-telemetry-logger) | Clj      | [OpenTelemetry](https://opentelemetry.io/) [Java client](https://github.com/open-telemetry/opentelemetry-java) | [LogRecord](https://opentelemetry.io/docs/specs/otel/logs/data-model/)                                                                                                                                           |
| [`handler:postal`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.postal#handler:postal)                                       | Clj      | Email (via [postal](https://github.com/drewr/postal))                                                          | [edn/JSON](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#pr-signal-fn) or [human-readable](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#format-signal-fn) |
| [`handler:slack`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.slack#handler:slack)                                          | Clj      | [Slack](https://slack.com/) (via [clj-slack](https://github.com/julienXX/clj-slack))                           | [edn/JSON](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#pr-signal-fn) or [human-readable](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#format-signal-fn) |
| [`handler:tcp-socket`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.sockets#handler:tcp-socket)                              | Clj      | TCP socket                                                                                                     | [edn/JSON](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#pr-signal-fn) or [human-readable](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#format-signal-fn) |
| [`handler:udp-socket`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.sockets#handler:udp-socket)                              | Clj      | UDP socket                                                                                                     | [edn/JSON](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#pr-signal-fn) or [human-readable](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#format-signal-fn) |

Planned (upcoming) handlers:

| Name                                                                                                                     | Platform | Output target                                                                | Output format                                    |
| :----------------------------------------------------------------------------------------------------------------------- | :------- | :--------------------------------------------------------------------------- | :----------------------------------------------- |
| [`handler:carmine`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.carmine#handler:carmine)    | Clj      | [Redis](https://redis.io/) (via [Carmine](https://www.taoensso.com/carmine)) | [Serialized](https://taoensso.com/nippy) signals |
| [`handler:logstash`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.logstash#handler:logstash) | Clj      | [Logstash](https://www.elastic.co/logstash)                                  | TODO                                             |

# Configuring handlers

There's two kinds of config relevant to all signal handlers:

1. **Dispatch** opts (common to all handlers), and
2. **Handler-specific** opts

## Dispatch opts

Handler dispatch opts includes dispatch priority (determines order in which handlers are called), handler filtering, handler middleware, a/sync queue semantics, back-pressure opts, etc.

See [`help:handler-dispatch-options`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:handler-dispatch-options) for full info, and [`default-handler-dispatch-opts`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#default-handler-dispatch-opts) for defaults.

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
    {:output-fn (t/pr-signal-fn {:pr-fn :edn})}))

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
       {:pr-fn
        #?(:cljs :json
           :clj  jsonista.core/write-value-as-string)})}))
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

See [`help:handlers`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-handlers) for info on handler management.

## Managing handlers on startup

Want to add or remove a particular handler when your application starts?

Just make an appropriate call to [`add-handler!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#add-handler!) or [`remove-handler!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#remove-handler!).

## Environmental config

If you want to manage handlers **conditionally** based on **environmental config** (JVM properties, environment variables, or classpath resources) - Telemere provides the highly flexible [`get-env`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#get-env) util.

Use this to easily define your own arbitrary cross-platform config, and make whatever conditional handler management decisions you'd like.

## Handler stats

By default, Telemere handlers maintain comprehensive internal info about their handling times and outcomes. See [`get-handlers-stats`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#get-handlers-stats) for more.

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

           ;; Main arity, called by Telemere when handler should process given signal
           ([signal]
            ;; Do something with given signal (write to console/file/queue/db, etc.).
            ;; Return value is ignored.
            )

           ;; Optional stop arity for handlers that need to close/release resources or
           ;; otherwise finalize themselves during system shutdown, etc. Called by
           ;; Telemere when appropriate, but ONLY IF the handler's dispatch options
           ;; include a truthy `:needs-stopping?` value (false by default).
           ([]
            ;; Close/release resources, etc.
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
- [PRs](https://github.com/taoensso/telemere/pulls) are **very welcome** for additions to Telemere's included handlers, or to Telemere's [community resources](./8-Community)

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
