See below for config by topic-

# Filtering

A signal will be provided to a handler iff ALL of the following are true:

- 1. Signal **creation** is allowed by **signal filters**:
	- a. Compile time: sample rate, kind, ns, id, level, when form, rate limit
	- b. Runtime: sample rate, kind, ns, id, level, when form, rate limit
	  
- 2. Signal **handling** is allowed by **handler filters**:
	- a. Compile time: not applicable
	- b. Runtime: sample rate, kind, ns, id, level, when fn, rate limit
	  
- 3. **Signal middleware** `(fn [signal]) => ?modified-signal` does not return nil
- 4. **Handler middleware** `(fn [signal]) => ?modified-signal` does not return nil

See [`help:filters`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:filters) for more about filtering.

# Signal handlers

See section [4-Handlers](./4-Handlers).

# Interop

## tools.logging

[tools.logging](https://github.com/clojure/tools.logging) can use Telemere as its logging implementation (backend). This'll let tools.logging calls create Telemere signals.

To do this:

1. Ensure that you have the tools.logging [dependency](https://mvnrepository.com/artifact/org.clojure/tools.logging), and
2. Call [`tools-logging->telemere!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.tools-logging#tools-logging-%3Etelemere!), or set the relevant environmental config as described in its docstring.

Verify successful interop with [`check-interop`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#check-interop):

```clojure
(check-interop) ; =>
{:tools-logging {:sending->telemere? true, :telemere-receiving? true}}
```

## Java logging

[SLF4Jv2](https://www.slf4j.org/) can use Telemere as its logging backend. This'll let SLF4J logging calls create Telemere signals.

To do this:

1. Ensure that you have the SLF4J [dependency](https://mvnrepository.com/artifact/org.slf4j/slf4j-api) ( v2+ **only**), and
2. Ensure that you have the Telemere SLF4J backend [dependency](https://clojars.org/com.taoensso/telemere-slf4j)

When `com.taoensso/telemere-slf4j` (2) is on your classpath AND no other SLF4J backends are, SLF4J will automatically direct all its logging calls to Telemere.

Verify successful interop with [`check-interop`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#check-interop):

```clojure
(check-interop) ; =>
{:slf4j {:sending->telemere? true, :telemere-receiving? true}}
```

> Telemere needs SLF4J API **version 2 or newer**. If you're seeing `Failed to load class "org.slf4j.impl.StaticLoggerBinder"` it could be that your project is importing the older v1 API, check with `lein deps :tree` or equivalent.

For other (non-SLF4J) logging like [Log4j](https://logging.apache.org/log4j/2.x/), [java.util.logging](https://docs.oracle.com/javase/8/docs/api/java/util/logging/package-summary.html) (JUL), and [Apache Commons Logging](https://commons.apache.org/proper/commons-logging/) (JCL), use an appropriate [SLF4J bridge](https://www.slf4j.org/legacy.html) and the normal SLF4J config as above.

In this case logging will be forwarded:

1. From Log4j/JUL/JCL/etc. to SLF4J, and
2. From SLF4J to Telemere

## System streams

The JVM's `System/out` and/or `System/err` streams can be set so that they'll create Telemere signals when flushed.

To do this, call [`streams->telemere!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#streams-%3Etelemere!).

Note that Clojure's `*out*`, `*err*` are **not** necessarily automatically affected.

Verify successful interop with [`check-interop`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#check-interop):

```clojure
(check-interop) ; =>
{:system/out {:sending->telemere? true, :telemere-receiving? true}
 :system/err {:sending->telemere? true, :telemere-receiving? true}}
```

## OpenTelemetry

> [OpenTelemetry](https://opentelemetry.io/) is a popular open-source observability framework that provides tools for collecting, processing, and exporting telemetry data like traces, metrics, and logs from software systems.
> 
> Telemere's OpenTelemetry interop is **experimental** - I'm looking for [feedback](https://www.taoensso.com/telemere/slack) on this feature please! 🙏

Telemere can send signals as [`LogRecords`](https://opentelemetry.io/docs/specs/otel/logs/data-model/) with correlated tracing data to configured [OpenTelemetry Java](https://github.com/open-telemetry/opentelemetry-java) [exporters](https://opentelemetry.io/docs/languages/java/exporters/).

This allows output to go (via configured exporters) to a wide variety of targets like [Jaeger](https://www.jaegertracing.io/), [Zipkin](https://zipkin.io/), [AWS X-Ray](https://aws.amazon.com/xray/), [AWS CloudWatch](https://aws.amazon.com/cloudwatch/), etc.

To do this:

1. Ensure that you have the necessary [OpenTelemetry Java](https://github.com/open-telemetry/opentelemetry-java) [dependency](https://mvnrepository.com/artifact/io.opentelemetry/opentelemetry-api).
2. Ensure that the relevant exporters are [appropriately configured](https://opentelemetry.io/docs/languages/java/configuration/) (this is the trickiest part, but not at all specific to Telemere).
3. Create a Telemere signal handler using [`handler:open-telemetry`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.open-telemetry#handler:open-telemetry), and register it using [`add-handler!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#add-handler!).
4. Ensure that [`otel-tracing?`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#otel-tracing?) is enabled if you want tracing interop.

Aside from configuring the exporters (2), Telemere's OpenTelemetry interop **does not require** any use of or familiarity with the OpenTelemetry Java API or concepts. Just use Telemere as you normally would, and the handler (3) will automatically emit detailed log and trace data to your configured exporters (2).

## Tufte

> [Tufte](https://www.taoensso.com/tufte) is a simple performance monitoring library for Clojure/Script by the author of Telemere.

Telemere can easily incorporate Tufte performance data in its signals, just like any other data:

```clojure
(let [[_ perf-data] (tufte/profiled <opts> <form>)]
  (t/log! "Performance data" {:perf-data perf-data}))
```

Telemere and Tufte work great together:

- Their functionality is complementary.
- The [upcoming](https://www.taoensso.com/roadmap) Tufte v3 will share the same core as Telemere and offer an **identical API** for managing filters and handlers.

## Truss

> [Truss](https://www.taoensso.com/truss) is an assertions micro-library for Clojure/Script by the author of Telemere.

Telemere can easily incorporate Truss assertion failure information in its signals, just like any other (error) data.

The [`catch->error!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#catch-%3Eerror!) signal creator can be particularly convenient for this:

```clojure
(t/catch->error! <form-with-truss-assertion/s>)
```
