<a href="https://www.taoensso.com/clojure" title="More stuff by @ptaoussanis at www.taoensso.com"><img src="https://www.taoensso.com/open-source.png" alt="Taoensso open source" width="340"/></a>  
[**API**][cljdoc docs] | [**Wiki**][GitHub wiki] | [Latest releases](#latest-releases) | [Slack channel][]

# <img src="https://raw.githubusercontent.com/taoensso/telemere/master/imgs/telemere-logo.svg" alt="Telemere logo" width="360"/>

### Structured telemetry library for Clojure/Script

**Telemere** is a next-generation replacement for [Timbre](https://www.taoensso.com/timbre) that offers one simple **unified API** for **traditional logging**, **structured logging**, **tracing**, and **basic performance monitoring**.

Friendly enough for complete beginners, but flexible enough for the most complex and performance-sensitive real-world projects.

It helps enable Clojure/Script systems that are easily **observable**, **robust**, and **debuggable** - and it represents the refinement and culmination of ideas brewing over 12+ years in [Timbre](https://www.taoensso.com/timbre), [Tufte](https://www.taoensso.com/tufte), [Truss](https://www.taoensso.com/truss), etc.

Supports Clojure, ClojureScript, [GraalVM](https://en.wikipedia.org/wiki/GraalVM), but not ([yet](../../wiki/6-FAQ#does-telemere-work-with-babashka)) [Babashka](https://github.com/babashka/babashka).

See [here](../../wiki/1-Getting-started) for **full introduction**.

## Latest release/s

- `2024-08-26` `v1.0.0-beta21`: [release info](../../releases/tag/v1.0.0-beta21) (for early adopters/feedback)

[![Main tests][Main tests SVG]][Main tests URL]
[![Graal tests][Graal tests SVG]][Graal tests URL]

See [here][GitHub releases] for earlier releases.

## Quick examples

```clojure
(require '[taoensso.telemere :as t])

;; (Just works / no config necessary for typical use cases)

;; Without structured data
(t/log! :info "Hello world!") ; %> Basic log   signal (has message)
(t/event! ::my-id :debug)     ; %> Basic event signal (just id)

;; With structured data
(t/log! {:level :info, :data {...}} "Hello again!")
(t/event! ::my-id {:level :debug, :data {...}})

;; Trace (auto interops with OpenTelemetry)
;; Tracks form runtime, return value, and (nested) parent tree
(t/trace! {:id ::my-id :data {...}}
  (do-some-work))

;; Check resulting signal content for debug/tests
(t/with-signal (t/event! ::my-id)) ; => {:keys [ns level id data msg_ ...]}

;; Transform signals
(t/set-middleware! (fn [signal] (assoc signal :my-key "my-val")))

;; Filter signals by returning nil
(t/set-middleware! (fn [signal] (when-not (-> signal :data :skip-me?) signal)))

;; Getting fancy (all costs are conditional!)
(t/log!
  {:level       :debug
   :sample-rate (my-dynamic-sample-rate)
   :when        (my-conditional)
   :rate-limit  {"1 per sec" [1  1000]
                 "5 per min" [5 60000]}

   :do (inc-my-metric!)
   :let
   [diagnostics (my-expensive-diagnostics)
    formatted   (my-expensive-format diagnostics)]

   :data
   {:diagnostics diagnostics
    :formatted   formatted
    :local-state *my-dynamic-context*}}

  ;; Message string or vector to join as string
  ["Something interesting happened!" formatted])
```

## Why Telemere?

### Ergonomics

- Elegant, lightweight API that's **easy to use**, **easy to configure**, and **deeply flexible**.
- **Sensible defaults** to make getting started **fast and easy**.
- Extensive **beginner-oriented** [documentation][GitHub wiki], [docstrings](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere), and error messages.

### Interop

- 1st-class **out-the-box interop** with [SLF4J v2](../../wiki/3-Config#java-logging), [tools.logging](../../wiki/3-Config#toolslogging), [OpenTelemetry](../../wiki/3-Config#opentelemetry), and [Tufte](../../wiki/3-Config#tufte).
- Included [shim](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.timbre) for easy/gradual [migration from Timbre](../../wiki/5-Migrating).
- Extensive set of [handlers](../../wiki/4-Handlers#included-handlers) included out-the-box.

### Scaling

- Hyper-optimized and **blazing fast**, see [benchmarks](#benchmarks).
- An API that **scales comfortably** from the smallest disposable code, to the most massive and complex real-world production environments.
- Auto [handler stats](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#get-handlers-stats) for debugging performance and other issues at scale.

### Flexibility

- Config via plain **Clojure vals and fns** for easy customization, composition, and REPL debugging.
- Unmatched [environmental config](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:environmental-config) support: JVM properties, environment variables, or classpath resources. Per platform, or cross-platform.
- Unmatched [filtering](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:filters) support: by namespace, id pattern, level, level by namespace pattern, etc. At runtime and compile-time.
- Fully [configurable](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:handler-dispatch-options) **a/sync dispatch support**: blocking, dropping, sliding, etc.
- Turn-key **sampling**, **rate-limiting**, and **back-pressure monitoring** with sensible defaults.

## Comparisons

- Telemere [compared](../../wiki/5-Migrating#from-timbre) to [Timbre](https://www.taoensso.com/timbre) (Telemere's predecessor)
- Telemere [compared](../../wiki/6-FAQ#how-does-telemere-compare-to-mulog) to [Mulog](https://github.com/BrunoBonacci/mulog) (Structured micro-logging library)

## Next-gen observability

A key hurdle in building **observable systems** is that it's often inconvenient and costly to get out the kind of **detailed info** that we need when debugging.

Telemere's strategy to address this is to:

1. Provide **lean, low-fuss syntax** to let you conveniently convey program state.
2. Use the unique power of **Lisp macros** to let you **dynamically filter costs as you filter signals** (pay only for what you need, when you need it).
3. For those signals that *do* pass filtering: move costs from the callsite to a/sync handlers with explicit [threading and back-pressure semantics](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:handler-dispatch-options) and  [performance monitoring](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#get-handlers-stats).

The effect is more than impressive micro-benchmarks. This approach enables a fundamental (qualitative) change in one's approach to observability.

It enables you to write code that is **information-verbose by default**.

## Video demo

See for intro and basic usage:

<a href="https://www.youtube.com/watch?v=-L9irDG8ysM" target="_blank">
 <img src="https://img.youtube.com/vi/-L9irDG8ysM/maxresdefault.jpg" alt="Telemere demo video" width="480" border="0" />
</a>

## More examples

```clojure
;; Set minimum level
(t/set-min-level!       :warn) ; For all    signals
(t/set-min-level! :log :debug) ; For `log!` signals only

;; Set namespace and id filters
(t/set-ns-filter! {:disallow "taoensso.*" :allow "taoensso.sente.*"})
(t/set-id-filter! {:allow #{::my-particular-id "my-app/*"}})

;; Set minimum level for `event!` signals for particular ns pattern
(t/set-min-level! :event "taoensso.sente.*" :warn)

;; See `t/help:filters` docstring for more

;; Use middleware to:
;;   - Transform signals
;;   - Filter    signals by arb conditions (incl. data/content)

(t/set-middleware!
  (fn [signal]
    (if (-> signal :data :skip-me?)
      nil ; Filter signal (don't handle)
      (assoc signal :passed-through-middleware? true))))

(t/with-signal (t/event! ::my-id {:data {:skip-me? true}}))  ; => nil
(t/with-signal (t/event! ::my-id {:data {:skip-me? false}})) ; => {...}

;; Signal handlers

(t/get-handlers) ; => {<handler-id> {:keys [handler-fn handler-stats_ dispatch-opts]}}

(t/add-handler! :my-console-handler
  (t/handler:console {}) ; Returns handler fn, has many opts
  {:async {:mode :dropping, :buffer-size 1024, :n-threads 1}
   :priority    100
   :sample-rate 0.5
   :min-level   :info
   :ns-filter   {:disallow "taoensso.*"}
   :rate-limit  {"1 per sec" [1 1000]}
   ;; See `t/help:handler-dispatch-options` for more
   })

;; Print human-readable output to console
(t/add-handler! :my-console-handler
  (t/handler:console
    {:output-fn (t/format-signal-fn {...})}))

;; Print edn to console
(t/add-handler! :my-console-handler
  (t/handler:console
    {:output-fn (t/pr-signal-fn {:pr-fn :edn})}))

;; Print JSON to console
;; Ref.  <https://github.com/metosin/jsonista> (or any alt JSON lib)
#?(:clj (require '[jsonista.core :as jsonista]))
(t/add-handler! :my-console-handler
  (t/handler:console
    {:output-fn
     #?(:cljs :json ; Use js/JSON.stringify
        :clj  jsonista/write-value-as-string)}))
```

See [examples.cljc](https://github.com/taoensso/telemere/blob/master/examples.cljc) for REPL-ready snippets!

## API overview

See relevant docstrings (links below) for usage info-

### Creating signals

| Name                                                                                                        | Signal kind | Main arg | Optional arg   | Returns                      |
| :---------------------------------------------------------------------------------------------------------- | :---------- | :------- | :------------- | :--------------------------- |
| [`log!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#log!)                     | `:log`      | `msg`    | `opts`/`level` | Signal allowed?              |
| [`event!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#event!)                 | `:event`    | `id`     | `opts`/`level` | Signal allowed?              |
| [`error!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#error!)                 | `:error`    | `error`  | `opts`/`id`    | Given error                  |
| [`trace!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#trace!)                 | `:trace`    | `form`   | `opts`/`id`    | Form result                  |
| [`spy!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#spy!)                     | `:spy`      | `form`   | `opts`/`level` | Form result                  |
| [`catch->error!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#catch-%3Eerror!) | `:error`    | `form`   | `opts`/`id`    | Form value or given fallback |
| [`signal!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#signal!)               | `<arb>`     | `opts`   | -              | Depends on opts              |

### Internal help

Detailed help is available without leaving your IDE:

| Var                                                                                                                                       | Help with                                                                |
| :---------------------------------------------------------------------------------------------------------------------------------------- | :----------------------------------------------------------------------- |
| [`help:signal-creators`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-creators)                   | Creating signals                                                         |
| [`help:signal-options`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-options)                     | Options when creating signals                                            |
| [`help:signal-content`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content)                     | Signal content (map given to middleware/handlers)                        |
| [`help:filters`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:filters)                                   | Signal filtering and transformation                                      |
| [`help:handlers`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:handlers)                                 | Signal handler management                                                |
| [`help:handler-dispatch-options`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:handler-dispatch-options) | Signal handler dispatch options                                          |
| [`help:environmental-config`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:environmental-config)         | Config via JVM properties, environment variables, or classpath resources |

### Included handlers

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

You can also easily [write your own handlers](../../wiki/4-Handlers#writing-handlers).

## Community

My plan for Telemere is to offer a **stable core of limited scope**, then to focus on making it as easy for the **community** to write additional stuff like handlers, middleware, and utils.

See [here](../../wiki/8-Community) for community resources.

## Documentation

- [Wiki][GitHub wiki] (getting started, usage, etc.)
- API reference via [cljdoc][cljdoc docs] or [Codox][Codox docs]
- Extensive [internal help](#internal-help) (no need to leave your IDE)
- Support via [Slack channel][] or [GitHub issues][]
- [General observability tips](../../wiki/7-Tips) (advice on building and maintaining observable Clojure/Script systems, and getting the most out of Telemere)

## Benchmarks

Telemere is **highly optimized** and offers great performance at any scale:

| Compile-time filtering? | Runtime filtering? | Profile? | Trace? | nsecs |
| :---------------------: | :----------------: | :------: | :----: | ----: |
|        ✓ (elide)        |         -          |    -     |   -    |     0 |
|            -            |         ✓          |    -     |   -    |   350 |
|            -            |         ✓          |    ✓     |   -    |   450 |
|            -            |         ✓          |    ✓     |   ✓    |  1000 |

Measurements:

- Are **~nanoseconds per signal call** (= milliseconds per 1e6 calls)
- Exclude [handler runtime](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#get-handlers-stats) (which depends on handler/s, is usually async)
- Taken on a 2020 Macbook Pro M1, running Clojure v1.12 and OpenJDK v22

### Performance philosophy

Telemere is optimized for *real-world* performance. This means **prioritizing flexibility** and realistic usage over synthetic micro-benchmarks.

Large applications can produce absolute *heaps* of data, not all equally valuable. Quickly processing infinite streams of unmanageable junk is an anti-pattern. As scale and complexity increase, it becomes more important to **strategically plan** what data to collect, when, in what quantities, and how to manage it.

Telemere is designed to help with all that. It offers [rich data](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content) and unmatched [filtering](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:filters) support - including per-signal and per-handler **sampling** and **rate-limiting**.

Use these to ensure that you're not capturing useless/low-value/high-noise information in production! With appropriate planning, Telemere is designed to scale to systems of any size and complexity. 

See [here](../../wiki/7-Tips) for detailed tips on real-world usage.

## Funding

You can [help support][sponsor] continued work on this project, thank you!! 🙏

## License

Copyright &copy; 2023-2024 [Peter Taoussanis][].  
Licensed under [EPL 1.0](LICENSE.txt) (same as Clojure).

<!-- Common -->

[GitHub releases]: ../../releases
[GitHub issues]:   ../../issues
[GitHub wiki]:     ../../wiki
[Slack channel]: https://www.taoensso.com/telemere/slack

[Peter Taoussanis]: https://www.taoensso.com
[sponsor]:          https://www.taoensso.com/sponsor

<!-- Project -->

[Codox docs]:   https://taoensso.github.io/telemere/
[cljdoc docs]: https://cljdoc.org/d/com.taoensso/telemere/

[Clojars SVG]: https://img.shields.io/clojars/v/com.taoensso/telemere.svg
[Clojars URL]: https://clojars.org/com.taoensso/telemere

[Main tests SVG]:  https://github.com/taoensso/telemere/actions/workflows/main-tests.yml/badge.svg
[Main tests URL]:  https://github.com/taoensso/telemere/actions/workflows/main-tests.yml
[Graal tests SVG]: https://github.com/taoensso/telemere/actions/workflows/graal-tests.yml/badge.svg
[Graal tests URL]: https://github.com/taoensso/telemere/actions/workflows/graal-tests.yml
