<a href="https://www.taoensso.com/clojure" title="More stuff by @ptaoussanis at www.taoensso.com"><img src="https://www.taoensso.com/open-source.png" alt="Taoensso open source" width="340"/></a>  
[**API**][cljdoc docs] | [**Wiki**][GitHub wiki] | [Latest releases](#latest-releases) | [Slack channel][]

# <img src="https://raw.githubusercontent.com/taoensso/telemere/master/imgs/telemere-logo.svg" alt="Telemere logo" width="360"/>

### Structured telemetry library for Clojure/Script

**Telemere** is a next-generation replacement for [Timbre](https://www.taoensso.com/timbre). It handles **structured and traditional logging**, **tracing**, and **basic performance monitoring** with a simple unified API.

It helps enable Clojure/Script systems that are **observable**, **robust**, and **debuggable** - and it represents the refinement and culmination of ideas brewing over 12+ years in [Timbre](https://www.taoensso.com/timbre), [Tufte](https://www.taoensso.com/tufte), [Truss](https://www.taoensso.com/truss), etc.

> [Terminology] *Telemetry* derives from the Greek *tele* (remote) and *metron* (measure). It refers to the collection of *in situ* (in position) data, for transmission to other systems for monitoring/analysis. *Logs* are the most common form of software telemetry. So think of telemetry as the *superset of logging-like activities* that help monitor and understand (software) systems.

## Latest release/s

- `2024-05-23` `v1.0.0-beta13`: [release info](../../releases/tag/v1.0.0-beta13) (for early adopters/feedback)

[![Main tests][Main tests SVG]][Main tests URL]
[![Graal tests][Graal tests SVG]][Graal tests URL]

See [here][GitHub releases] for earlier releases.

## Why Telemere?

#### Ergonomics

- Elegant, lightweight API that's **easy to use**, **easy to configure**, and **deeply flexible**.
- **Sensible defaults** to make getting started **fast and easy**.
- Extensive **beginner-oriented** [documentation][GitHub wiki], [docstrings](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso), and error messages.

#### Interop

- 1st-class **out-the-box interop** with [SLF4J v2](https://www.slf4j.org/), [tools.logging](https://github.com/clojure/tools.logging), [OpenTelemetry](https://opentelemetry.io/), and [Tufte](https://www.taoensso.com/tufte).
- Included [shim](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.timbre) for easy/gradual [migration from Timbre](../../wiki/5-Migrating).
- Extensive set of [handlers](../../wiki/4-Handlers#included-handlers) included out-the-box.

#### Scaling

- Hyper-optimized and **blazing fast**, see [benchmarks](#benchmarks).
- An API that **scales comfortably** from the smallest disposable code, to the most massive and complex real-world production environments.
- Auto [handler stats](https://cljdoc.org/d/com.taoensso/telemere/1.0.0-beta13/api/taoensso.telemere#get-handlers-stats) for debugging performance and other issues at scale.

#### Flexibility

- Config via plain **Clojure vals and fns** for easy customization, composition, and REPL debugging.
- Unmatched [environmental config](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:environmental-config) support: JVM properties, environment variables, or classpath resources. Per platform, or cross-platform.
- Unmatched [filtering](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:filters) support: by namespace, id pattern, level, level by namespace pattern, etc. At runtime and compile-time.
- Fully [configurable](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:handler-dispatch-options) **a/sync dispatch support**: blocking, dropping, sliding, etc.
- Turn-key **sampling**, **rate-limiting**, and **back-pressure monitoring** with sensible defaults.

## Video demo

See for intro and basic usage:

<a href="https://www.youtube.com/watch?v=-L9irDG8ysM" target="_blank">
 <img src="https://img.youtube.com/vi/-L9irDG8ysM/maxresdefault.jpg" alt="Telemere demo video" width="480" border="0" />
</a>

## Quick examples

```clojure
(require '[taoensso.telemere :as t])

(t/log! {:id ::my-id, :data {:x1 :x2}} "My message") %>

;; 2024-04-11T10:54:57.202869Z INFO LOG Schrebermann.local examples(56,1) ::my-id - My message
;;    data: {:x1 :x2}

(t/log! "This will send a `:log` signal to the Clj/s console")
(t/log! :info "This will do the same, but only when the current level is >= `:info`")

;; Easily construct messages
(let [x "constructed"] (t/log! :info ["Here's a" x "message!"]))

;; Attach an id
(t/log! {:level :info, :id ::my-id} "This signal has an id")

;; Attach arb user data
(t/log! {:level :info, :data {:x :y}} "This signal has structured data")

;; Capture for debug/testing
(t/with-signal (t/log! "This will be captured"))
;; => {:keys [location level id data msg_ ...]}

;; `:let` bindings available to `:data` and message, only paid for
;; when allowed by minimum level and other filtering criteria
(t/log!
  {:level :info
   :let [expensive-metric1 (last (for [x (range 100), y (range 100)] (* x y)))]
   :data {:metric1 expensive-metric1}}
  ["Message with metric value:" expensive-metric1])

;; With sampling 50% and 1/sec rate limiting
(t/log!
  {:sample-rate 0.5
   :rate-limit  {"1 per sec" [1 1000]}}
  "This signal will be sampled and rate limited")

;;; A quick taste of filtering...

(t/set-ns-filter! {:disallow "taoensso.*" :allow "taoensso.sente.*"}) ; Set namespace filter
(t/set-id-filter! {:allow #{::my-particular-id "my-app/*"}})          ; Set id        filter

(t/set-min-level!       :warn) ; Set minimum level
(t/set-min-level! :log :debug) ; Set minimul level for `:log` signals

;; Set minimum level for `:event` signals originating in the "taoensso.sente.*" ns
(t/set-min-level! :event "taoensso.sente.*" :warn)
```

See [examples.cljc](https://github.com/taoensso/telemere/blob/master/examples.cljc) for more REPL-ready snippets.

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

Help is available without leaving your IDE:

| Var                                                                                                                                       | Help with                                                                 |
| :---------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------ |
| [`help:signal-creators`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-creators)                   | Creating signals                                                          |
| [`help:signal-options`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-options)                     | Signal options                                                            |
| [`help:signal-content`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content)                     | Signal content (map given to middleware/handlers)                         |
| [`help:filters`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:filters)                                   | Signal and handler filters                                                |
| [`help:handlers`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:handlers)                                 | Signal handlers                                                           |
| [`help:handler-dispatch-options`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:handler-dispatch-options) | Signal handler dispatch options                                           |
| [`help:environmental-config`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:environmental-config)         | Config via JVM properties, environment variables, or classpath resources. |

### Included handlers

See linked docstrings below for features and usage:

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

See [here](../../wiki/4-Handlers) for more/upcoming handlers, community handlers, info on **writing your own handlers**, etc.

## Documentation

- [Wiki][GitHub wiki] (getting started, usage, etc.)
- API reference: [cljdoc][cljdoc docs] or [Codox][Codox docs]
- Support: [Slack channel][] or [GitHub issues][]
- [General observability tips](../../wiki/7-Tips) (advice on building and maintaining observable Clojure/Script systems, and getting the most out of Telemere)

## Benchmarks

Telemere is **highly optimized** and offers terrific performance at any scale:

| Compile-time filtering? | Runtime filtering? | Time? | Trace? | nsecs |
| :---------------------: | :----------------: | :---: | :----: | ----: |
|        ✓ (elide)        |         -          |   -   |   -    |     0 |
|            -            |         ✓          |   -   |   -    |   200 |
|            -            |         ✓          |   ✓   |   -    |   280 |
|            -            |         ✓          |   ✓   |   ✓    |   650 |

Measurements:

- Are **~nanoseconds per signal call** (= milliseconds per 1e6 calls)
- Exclude [handler runtime](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#get-handlers-stats) (which depends on handler/s, is usually async)
- Taken on a 2020 Macbook Pro M1, running OpenJDK 21

**Tip**: Telemere offers extensive [filtering](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:filters) support - including dead-easy per-signal and per-handler **sampling** and **rate-limiting**. Use these to ensure that you're not capturing useless/low-value information in production. See [here](../../wiki/7-Tips) for more tips!

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
