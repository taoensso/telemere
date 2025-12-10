<a href="https://www.taoensso.com/clojure" title="More stuff by @ptaoussanis at www.taoensso.com"><img src="https://www.taoensso.com/open-source.png" alt="Taoensso open source" width="340"/></a>  
[**API**][cljdoc] | [**Wiki**][GitHub wiki] | [Slack][] | Latest release: [v1.2.0](../../releases/tag/v1.2.0) (2025-12-09)

[![Clj tests][Clj tests SVG]][Clj tests URL]
[![Cljs tests][Cljs tests SVG]][Cljs tests URL]
[![Graal tests][Graal tests SVG]][Graal tests URL]

# <img src="https://raw.githubusercontent.com/taoensso/telemere/master/imgs/telemere-logo.svg" alt="Telemere logo" width="360"/>

### Structured logs and telemetry for Clojure/Script

**Telemere** is the next-gen version of [Timbre](https://www.taoensso.com/timbre). It offers **one API** to cover:

- **Traditional logging** (string messages)
- **Structured logging** (rich Clojure data types and structures)
- **Tracing** (nested flow tracking, with optional data)
- Basic **performance monitoring** (nested form runtimes)

It's pure Clj/s, small, **easy to use**, super fast, and **seriously flexible**:

```clojure
(tel/log! {:level :info, :id ::login, :data {:user-id 1234}, :msg "User logged in!"})
```

Works great with:

- [Trove](https://www.taoensso.com/trove) for logging by **library authors**
- [Tufte](https://www.taoensso.com/tufte) for rich **performance monitoring**
- [Truss](https://www.taoensso.com/truss) for **assertions** and error handling

## Why structured logging?

- Traditional logging outputs **strings** (messages).
- Structured logging in contrast outputs **data**. It retains **rich data types and (nested) structures** throughout the logging pipeline from logging callsite → filters → middleware → handlers.

A data-oriented pipeline can make a huge difference - supporting **easier filtering**, **transformation**, and **analysis**. It’s also usually **faster**, since you only pay for serialization if/when you need it. In a lot of cases you can avoid serialization altogether if your final target (DB, etc.) supports the relevant types.

The structured (data-oriented) approach is inherently more flexible, faster, and well suited to the tools and idioms offered by Clojure and ClojureScript.

## Examples

See [examples.cljc](https://github.com/taoensso/telemere/blob/master/examples.cljc) for REPL-ready snippets, or expand below:

<details><summary>Create signals</summary><br/>

```clojure
(require '[taoensso.telemere :as tel])

;; No config needed for typical use cases!!
;; Signals print to console by default for both Clj and Cljs

;; Traditional style logging (data formatted into message string):
(tel/log! {:level :info, :msg (str "User " 1234 " logged in!")})

;; Modern/structured style logging (explicit id and data)
(tel/log! {:level :info, :id :auth/login, :data {:user-id 1234}})

;; Mixed style (explicit id and data, with message string)
(tel/log! {:level :info, :id :auth/login, :data {:user-id 1234}, :msg "User logged in!"})

;; Trace (can interop with OpenTelemetry)
;; Tracks form runtime, return value, and (nested) parent tree
(tel/trace! {:id ::my-id :data {...}}
  (do-some-work))

;; Check resulting signal content for debug/tests
(tel/with-signal (tel/log! {...})) ; => {:keys [ns level id data msg_ ...]}

;; Getting fancy (all costs are conditional!)
(tel/log!
  {:level    :debug
   :sample   0.75 ; 75% sampling (noop 25% of the time)
   :when     (my-conditional)
   :limit    {"1 per sec" [1  1000]
              "5 per min" [5 60000]} ; Rate limit
   :limit-by my-user-ip-address      ; Rate limit scope

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

</details>

<details><summary>Filter signals</summary><br/>

```clojure
;; Set minimum level
(tel/set-min-level!       :warn) ; For all    signals
(tel/set-min-level! :log :debug) ; For `log!` signals specifically

;; Set id and namespace filters
(tel/set-id-filter! {:allow #{::my-particular-id "my-app/*"}})
(tel/set-ns-filter! {:disallow "taoensso.*" :allow "taoensso.sente.*"})

;; SLF4J signals will have their `:ns` key set to the logger's name
;; (typically a source class)
(tel/set-ns-filter! {:disallow "com.noisy.java.package.*"})

;; Set minimum level for `log!` signals for particular ns pattern
(tel/set-min-level! :log "taoensso.sente.*" :warn)

;; Use transforms (xfns) to filter and/or arbitrarily modify signals
;; by signal data/content/etc.

(tel/set-xfn!
  (fn [signal]
    (if (-> signal :data :skip-me?)
      nil ; Filter signal (don't handle)
      (assoc signal :transformed? true))))

(tel/with-signal (tel/log! {... :data {:skip-me? true}}))  ; => nil
(tel/with-signal (tel/log! {... :data {:skip-me? false}})) ; => {...}

;; See `tel/help:filters` docstring for more filtering options
```

</details>

<details><summary>Add handlers</summary><br/>

```clojure
;; Add your own signal handler
(tel/add-handler! :my-handler
  (fn
    ([signal] (println signal))
    ([] (println "Handler has shut down"))))

;; Use `add-handler!` to set handler-level filtering and back-pressure
(tel/add-handler! :my-handler
  (fn
    ([signal] (println signal))
    ([] (println "Handler has shut down")))

  {:async {:mode :dropping, :buffer-size 1024, :n-threads 1}
   :priority  100
   :sample    0.5
   :min-level :info
   :ns-filter {:disallow "taoensso.*"}
   :limit     {"1 per sec" [1 1000]}
   ;; See `tel/help:handler-dispatch-options` for more
   })

;; See current handlers
(tel/get-handlers) ; => {<handler-id> {:keys [handler-fn handler-stats_ dispatch-opts]}}

;; Add console handler to print signals as human-readable text
(tel/add-handler! :my-handler
  (tel/handler:console
    {:output-fn (tel/format-signal-fn {})}))

;; Add console handler to print signals as edn
(tel/add-handler! :my-handler
  (tel/handler:console
    {:output-fn (tel/pr-signal-fn {:pr-fn :edn})}))

;; Add console handler to print signals as JSON
;; Ref.  <https://github.com/metosin/jsonista> (or any alt JSON lib)
#?(:clj (require '[jsonista.core :as jsonista]))
(tel/add-handler! :my-handler
  (tel/handler:console
    {:output-fn
     #?(:cljs :json ; Use js/JSON.stringify
        :clj   jsonista/write-value-as-string)}))
```

</details>

## Why Telemere?

### Ergonomics

- Elegant unified API that's **easy to use** and **deeply flexible**.
- Pure **Clojure vals and fns** for easy config, composition, and REPL debugging.
- **Sensible defaults** to get started fast.
- **Beginner-oriented** [documentation][GitHub wiki], [docstrings](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere), and error messages.

### Interop

- **Interop ready** with [tools.logging](../../wiki/3-Config#toolslogging), [Java logging via SLF4J v2](../../wiki/3-Config#java-logging), [OpenTelemetry](../../wiki/3-Config#opentelemetry), and [Tufte](../../wiki/3-Config#tufte).
- [Timbre shim](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.timbre) for easy/gradual [migration from Timbre](../../wiki/5-Migrating).
- Extensive set of [handlers](../../wiki/4-Handlers#included-handlers) included out-the-box.

### Scaling

- Rich [filtering](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:filters) by namespace, id pattern, level, level by namespace pattern, etc.
- Fully [configurable](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:handler-dispatch-options) **a/sync dispatch support** with per-handler [performance monitoring](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#get-handlers-stats).
- Turn-key **sampling**, **rate limiting**, and **back-pressure monitoring**.
- Highly optimized and [blazing fast](#performance)!

## Comparisons

- Telemere [compared](../../wiki/5-Migrating#from-timbre) to [Timbre](https://www.taoensso.com/timbre) (Telemere's predecessor)
- Telemere [compared](../../wiki/6-FAQ#how-does-telemere-compare-to-%CE%BClog) to [μ/log](https://github.com/BrunoBonacci/mulog) (structured micro-logging library)

## Videos

### Lightning intro (7 mins):

<a href="https://www.youtube.com/watch?v=lOaZ0SgPVu4" target="_blank">
 <img src="https://img.youtube.com/vi/lOaZ0SgPVu4/maxresdefault.jpg" alt="Telemere lightning intro" width="480" border="0" />
</a>

### REPL demo (24 mins):

<a href="https://www.youtube.com/watch?v=-L9irDG8ysM" target="_blank">
 <img src="https://img.youtube.com/vi/-L9irDG8ysM/maxresdefault.jpg" alt="Telemere demo video" width="480" border="0" />
</a>

## API overview

### Creating signals

80% of Telemere's functionality is available through one macro: [`signal!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#signal!) and a rich set of [opts](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-options).

Use that directly, or any of the wrapper macros that you find most convenient. They're **semantically equivalent** but have ergonomics slightly tweaked for different common use cases:

| Name                                                                                                        | Args                       | Returns                      |
| :---------------------------------------------------------------------------------------------------------- | :------------------------- | :--------------------------- |
| [`log!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#log!)                     | `[opts]` or `[?level msg]` | nil                          |
| [`event!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#event!)                 | `[opts]` or `[id ?level]`  | nil                          |
| [`trace!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#trace!)                 | `[opts]` or `[?id run]`    | Form result                  |
| [`spy!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#spy!)                     | `[opts]` or `[?level run]` | Form result                  |
| [`error!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#error!)                 | `[opts]` or `[?id error]`  | Given error                  |
| [`catch->error!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#catch-%3Eerror!) | `[opts]` or `[?id error]`  | Form value or given fallback |
| [`signal!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#signal!)               | `[opts]`                   | Depends on opts              |

### Internal help

Detailed help is available without leaving your IDE:

| Var                                                                                                                                       | Help with                                                                |
| :---------------------------------------------------------------------------------------------------------------------------------------- | :----------------------------------------------------------------------- |
| [`help:signal-creators`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-creators)                   | Creating signals                                                         |
| [`help:signal-options`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-options)                     | Options when creating signals                                            |
| [`help:signal-content`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content)                     | Signal content (map given to transforms/handlers)                        |
| [`help:filters`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:filters)                                   | Signal filtering and transformation                                      |
| [`help:handlers`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:handlers)                                 | Signal handler management                                                |
| [`help:handler-dispatch-options`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:handler-dispatch-options) | Signal handler dispatch options                                          |
| [`help:environmental-config`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:environmental-config)         | Config via JVM properties, environment variables, or classpath resources |

## Performance

Telemere is **highly optimized** and offers great performance at any scale, handling up to **4.2 million filtered signals/sec** on a 2020 Macbook Pro M1.

Signal call benchmarks (per thread):

| Compile-time filtering? | Runtime filtering? | Profile? | Trace? | nsecs / call |
| :---------------------: | :----------------: | :------: | :----: | -----------: |
|        ✓ (elide)        |         -          |    -     |   -    |            0 |
|            -            |         ✓          |    -     |   -    |          350 |
|            -            |         ✓          |    ✓     |   -    |          450 |
|            -            |         ✓          |    ✓     |   ✓    |         1000 |

- Nanoseconds per signal call ~ **milliseconds per 1e6 calls**
- Times exclude [handler runtime](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#get-handlers-stats) (which depends on handler/s, is usually async)
- Benched on a 2020 Macbook Pro M1, running Clojure v1.12 and OpenJDK v22

### Performance philosophy

Telemere is optimized for *real-world* performance. This means **prioritizing flexibility** and realistic usage over synthetic micro-benchmarks.

Large applications can produce absolute *heaps* of data, not all equally valuable. Quickly processing infinite streams of unmanageable junk is an anti-pattern. As scale and complexity increase, it becomes more important to **strategically plan** what data to collect, when, in what quantities, and how to manage it.

Telemere is designed to help with all that. It offers [rich data](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content) and unmatched [filtering](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:filters) support - including per-signal and per-handler **sampling** and **rate limiting**, and zero cost compile-time filtering.

Use these to ensure that you're not capturing useless/low-value/high-noise information in production! With appropriate planning, Telemere is designed to scale to systems of any size and complexity. 

See [here](../../wiki/7-Tips) for detailed tips on real-world usage.

## Included handlers

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

My plan for Telemere is to offer a **stable core of limited scope**, then to focus on making it as easy for the **community** to write additional stuff like handlers, transforms, and utils.

See [here](../../wiki/8-Community) for community resources.

## Documentation

- [Wiki][GitHub wiki] (getting started, usage, etc.)
- API reference via [cljdoc][cljdoc]
- Extensive [internal help](#internal-help) (no need to leave your IDE)
- Support via [Slack][] or [GitHub issues][]
- [General observability tips](../../wiki/7-Tips) (advice on building and maintaining observable Clojure/Script systems, and getting the most out of Telemere)

## Funding

You can [help support][sponsor] continued work on this project and [others][my work], thank you!! 🙏

## License

Copyright &copy; 2023-2025 [Peter Taoussanis][].  
Licensed under [EPL 1.0](LICENSE.txt) (same as Clojure).

<!-- Common -->

[GitHub releases]: ../../releases
[GitHub issues]:   ../../issues
[GitHub wiki]:     ../../wiki
[Slack]:   https://www.taoensso.com/telemere/slack

[Peter Taoussanis]: https://www.taoensso.com
[sponsor]:          https://www.taoensso.com/sponsor
[my work]:          https://www.taoensso.com/clojure-libraries

<!-- Project -->

[cljdoc]: https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere

[Clojars SVG]: https://img.shields.io/clojars/v/com.taoensso/telemere.svg
[Clojars URL]: https://clojars.org/com.taoensso/telemere

[Clj tests SVG]:  https://github.com/taoensso/telemere/actions/workflows/clj-tests.yml/badge.svg
[Clj tests URL]:  https://github.com/taoensso/telemere/actions/workflows/clj-tests.yml
[Cljs tests SVG]:  https://github.com/taoensso/telemere/actions/workflows/cljs-tests.yml/badge.svg
[Cljs tests URL]:  https://github.com/taoensso/telemere/actions/workflows/cljs-tests.yml
[Graal tests SVG]: https://github.com/taoensso/telemere/actions/workflows/graal-tests.yml/badge.svg
[Graal tests URL]: https://github.com/taoensso/telemere/actions/workflows/graal-tests.yml
