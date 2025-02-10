Telemere's signal handlers are just **plain functions** that take a signal (map) to **do something with them** (analyse them, write them to console/file/queue/db/etc.).

Here's a minimal handler: `(fn [signal] (println signal))`.

A second 0-arg arity will be called when stopping the handler. This is handy for stateful handlers or handlers that need to release resources, e.g.:

```
(fn my-handler
  ([signal] (println signal)
  ([] (my-stop-code)))
```

Telemere includes a number of signal handlers out-the-box, and more may be available via the [community](./8-Community#handlers-and-tools).

You can also easily [write your own handlers](#writing-handlers) for any output or integration you need.

# Included handlers

See ✅ links below for **features and usage**,  
See ❤️ links below to **vote on future handlers**:

| Target (↓)                                     |                                                         Clj                                                         |                                               Cljs                                                |
| :--------------------------------------------- | :-----------------------------------------------------------------------------------------------------------------: | :-----------------------------------------------------------------------------------------------: |
| [Apache Kafka](https://kafka.apache.org/)      |                                 [❤️](https://github.com/taoensso/roadmap/issues/12)                                 |                                                 -                                                 |
| [AWS Kinesis](https://aws.amazon.com/kinesis/) |                                 [❤️](https://github.com/taoensso/roadmap/issues/12)                                 |                                                 -                                                 |
| Console                                        |            [✅](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:console)            |   [✅](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:console)   |
| Console (raw)                                  |                                                          -                                                          | [✅](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:console-raw) |
| [Datadog](https://www.datadoghq.com/)          |                                 [❤️](https://github.com/taoensso/roadmap/issues/12)                                 |                        [❤️](https://github.com/taoensso/roadmap/issues/12)                        |
| Email                                          |         [✅](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.postal#handler:postal)         |                                                 -                                                 |
| File/s                                         |             [✅](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:file)              |                                                 -                                                 |
| [Graylog](https://graylog.org/)                |                                 [❤️](https://github.com/taoensso/roadmap/issues/12)                                 |                                                 -                                                 |
| [Jaeger](https://www.jaegertracing.io/)        |                                 [❤️](https://github.com/taoensso/roadmap/issues/12)                                 |                                                 -                                                 |
| [Logstash](https://www.elastic.co/logstash)    |                                 [❤️](https://github.com/taoensso/roadmap/issues/12)                                 |                                                 -                                                 |
| [OpenTelemetry](https://opentelemetry.io/)     | [✅](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.open-telemetry#handler:open-telemetry) |                        [❤️](https://github.com/taoensso/roadmap/issues/12)                        |
| [Redis](https://redis.io/)                     |                                 [❤️](https://github.com/taoensso/roadmap/issues/12)                                 |                                                 -                                                 |
| SQL                                            |                                 [❤️](https://github.com/taoensso/roadmap/issues/12)                                 |                                                 -                                                 |
| [Slack](https://slack.com/)                    |          [✅](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.slack#handler:slack)          |                                                 -                                                 |
| TCP socket                                     |      [✅](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.sockets#handler:tcp-socket)       |                                                 -                                                 |
| UDP socket                                     |      [✅](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.sockets#handler:udp-socket)       |                                                 -                                                 |
| [Zipkin](https://zipkin.io/)                   |                                 [❤️](https://github.com/taoensso/roadmap/issues/12)                                 |                                                 -                                                 |

# Configuring handlers

There's two kinds of config relevant to all signal handlers:

1. **Dispatch** opts (common to all handlers), and
2. **Handler-specific** opts

## Dispatch opts

Handler dispatch opts includes dispatch priority (determines order in which handlers are called), handler filtering, handler middleware, a/sync queue semantics, back-pressure opts, etc.

See [`help:handler-dispatch-options`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:handler-dispatch-options) for full info, and [`default-handler-dispatch-opts`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#default-handler-dispatch-opts) for defaults.

Note that handler middleware in particular is an easily overlooked but powerful feature, allowing you to arbitrarily transform and/or filter every [signal map](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content) before it is given to each handler.

## Handler-specific opts

Handler-specific opts are specified when calling a particular **handler constructor** (like [`handler:console`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:console)) - and documented by the constructor.

Note that it's common for Telemere handlers to be customized by providing *Clojure/Script functions* to the relevant handler constructor call.

See the [utils namespace](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils) for tools useful for customizing and writing signal handlers.

### Console handler

The standard Clj/s console handler ([`handler:console`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:console)) writes signals **as strings** to `*out*`/`*err` or browser console.

By default it writes formatted strings intended for human consumption:

```clojure
;; Create a test signal
(def my-signal
  (t/with-signal
    (t/log! {:id ::my-id, :data {:x1 :x2}} "My message")))

;; Create console handler with default opts (writes formatted string)
(def my-handler (t/handler:console {}))

;; Test handler, remember it's just a (fn [signal])
(my-handler my-signal) ; %>
;; 2024-04-11T10:54:57.202869Z INFO LOG MyHost examples(56,1) ::my-id - My message
;;     data: {:x1 :x2}
```

#### edn output

To instead writes signals as [edn](https://github.com/edn-format/edn):

```clojure
;; Create console handler which writes signals as edn
(def my-handler
  (t/handler:console
    {:output-fn (t/pr-signal-fn {:pr-fn :edn})}))

(my-handler my-signal) ; %>
;; {:inst #inst "2024-04-11T10:54:57.202869Z", :msg_ "My message", :ns "examples", ...}
```

#### JSON output

To instead writes signals as JSON:

```clojure
;; Ref.  <https://github.com/metosin/jsonista> (or any alt JSON lib)
#?(:clj (require '[jsonista.core :as jsonista]))
(def my-handler
  (t/handler:console
    {:output-fn
     (t/pr-signal-fn
       {:pr-fn
        #?(:cljs :json ; Use js/JSON.stringify
           :clj  jsonista/write-value-as-string)})}))

(my-handler my-signal) ; %>
;; {"inst":"2024-04-11T10:54:57.202869Z","msg_":"My message","ns":"examples", ...}
```

Note that when writing JSON with Clojure, you *must* provide an appropriate `pr-fn`. This lets you plug in the JSON serializer of your choice ([jsonista](https://github.com/metosin/jsonista) is my default recommendation).

### Handler-specific per-signal kvs

Telemere includes a handy mechanism for including arbitrary app-level data/opts in individual signals for use by custom middleware and/or handlers.

Any *non-standard* (app-level) keys you include in your signal constructor opts will automatically be included in created signals, e.g.:

```clojure
(t/with-signal
  (t/event! ::my-id
    {:my-middleware-data "foo"
     :my-handler-data    "bar"}))

;; %>
;; {;; App-level kvs included inline (assoc'd to signal root)
;;  :my-middleware-data "foo"
;;  :my-handler-data    "bar"
;;  :kvs ; And also collected together under ":kvs" key
;;  {:my-middleware-data "foo"
;;   :my-handler-data    "bar"}
;;  ... }
```

These app-level data/opts are typically NOT included by default in handler output, making them a great way to convey data/opts to custom middleware/handlers.

# Managing handlers

See [`help:handlers`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-handlers) for info on signal handler management.

## Managing handlers on startup

Want to add or remove a particular handler when your application starts?

Just make an appropriate call to [`add-handler!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#add-handler!) or [`remove-handler!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#remove-handler!).

### Environmental config

If you want to manage handlers **conditionally** based on **environmental config** (JVM properties, environment variables, or classpath resources) - Telemere provides the highly flexible [`get-env`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#get-env) util.

Use this to easily define your own arbitrary cross-platform config, and make whatever conditional handler management decisions you'd like.

## Stopping handlers

Telemere supports complex handlers that may use internal state, buffers, etc.

For this reason, you should **always manually call** [`stop-handlers!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#stop-handlers!) somewhere appropriate to give registered handlers the opportunity to flush buffers, close files, etc.

The best place to do this is usually near the end of your application's `-main` or shutdown procedure, **AFTER** all other code has completed that could create signals.

You can use [`call-on-shutdown!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#call-on-shutdown!) to create a JVM shutdown hook.

Note that `stop-handlers!` will conveniently **block** to finish async handling of any pending signals. The max blocking time can be configured *per-handler* via the `:drain-msecs` [handler dispatch option](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:handler-dispatch-options) and defaults to 6 seconds.

## Handler stats

By default, Telemere handlers maintain **comprehensive internal stats** including handling times and outcome counters.

This can be **really useful** for debugging handlers, and understanding handler performance and back-pressure behaviour in practice.

See [`get-handlers-stats`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#get-handlers-stats) for an output example, etc.

# Writing handlers

Writing your own signal handlers for Telemere is straightforward, and a reasonable choice if you prefer customizing behaviour that way, or want to write signals to a DB/format/service for which a ready-made handler isn't available.

- Signals are just plain Clojure/Script [maps](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content).
- Handlers just plain Clojure/Script fns of 2 arities:

```clojure
(defn my-handler
  ([signal] (println signal)) ; Arity-1 called when handling a signal
  ([] (my-stop-code))         ; Arity-0 called when stopping the handler
  )
```

If you're making a customizable handler for use by others, it's often handy to define a handler **constructor**:

```clojure
(defn handler:my-fancy-handler ; Note constructor naming convention
  "Needs `some-lib`, Ref. <https://github.com/example/some-lib>.

  Returns a signal handler that:
    - Takes a Telemere signal (map).
    - Does something useful with the signal!

  Options:
    `:option1` - Option description
    `:option2` - Option description

  Tips:
    - Tip 1
    - Tip 2"

  ([] (handler:my-fancy-handler nil)) ; Use default opts (iff defaults viable)
  ([{:as constructor-opts}]

   ;; Do option validation and other prep here, i.e. try to keep
   ;; expensive work outside handler function when possible!

   (let [handler-fn ; Fn of exactly 2 arities (1 and 0)
         (fn a-handler:my-fancy-handler ; Note fn naming convention

           ([signal] ; Arity-1 called when handling a signal
            ;; Do something useful with the given signal (write to
            ;; console/file/queue/db, etc.). Return value is ignored.
            )

           ([] ; Arity-0 called when stopping the handler
            ;; Flush buffers, close files, etc. May just noop.
            ;; Return value is ignored.
            ))]

     ;; (Advanced, optional) You can use metadata to provide default
     ;; handler dispatch options (see `help:handler-dispatch-options`)

     (with-meta handler-fn
       {:dispatch-opts
        {:min-level  :info
         :rate-limit
         [[1   1000] ; Max 1  signal  per second
          [10 60000] ; Max 10 signals per minute
          ]}}))))
```

- See [`help:signal-content`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content) for signal map content.
- See [`help:handler-dispatch-options`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:handler-dispatch-options) for dispatch options.
- See the [utils namespace](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils) for tools useful for customizing and writing signal handlers.
- Feel free to [ping me](https://github.com/taoensso/telemere/issues) for assistance, or ask on the [`#telemere` Slack channel](https://www.taoensso.com/telemere/slack).
- [PRs](https://github.com/taoensso/telemere/pulls) are **very welcome** for additions to Telemere's included handlers, or to Telemere's [community resources](./8-Community)!

# Example output

```clojure
(t/log! {:id ::my-id, :data {:x1 :x2}} "My message") =>
```

## Clj console handler

[API](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:console) |  string output:

```
2024-04-11T10:54:57.202869Z INFO LOG MyHost examples(56,1) ::my-id - My message
    data: {:x1 :x2}
```

## Cljs console handler

[API](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:console) |  Chrome console:

<img src="https://raw.githubusercontent.com/taoensso/telemere/master/imgs/handler-output-cljs-console.png" alt="Default ClojureScript console handler output" width="640"/>

## Cljs raw console handler

[API](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:console-raw) | Chrome console, with [cljs-devtools](https://github.com/binaryage/cljs-devtools):

<img src="https://raw.githubusercontent.com/taoensso/telemere/master/imgs/handler-output-cljs-console-raw.png" alt="Raw ClojureScript console handler output" width="640"/>

## Clj file handler

[API](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:file) |  MacOS terminal:

<img src="https://raw.githubusercontent.com/taoensso/telemere/master/imgs/handler-output-clj-file.png" alt="Default Clojure file handler output" width="640"/>
