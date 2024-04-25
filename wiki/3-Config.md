See below for config by topic-

# Signal filtering

A signal will be provided to a handler iff ALL of the following are true:

  1. Signal **creation** is allowed by **compile-time** filter config
  2. Signal **creation** is allowed by **runtime** filter config
  3. Signal **handling** is allowed by **handler** filter config
  4. Signal  **middleware** does not suppress the signal (return nil)
  5. Handler **middleware** does not suppress the signal (return nil)

For 1-3, filtering may depend on (in order):
  Sample rate → namespace → kind → id → level → when form/fn → rate limit

- See [`help:signal-filters`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-filters) for info on signal creation filters, **environmental config**, etc.
- See [`add-handler!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#add-handler!) for info on signal handler filters.

# Signal handlers

See section [4-Handlers](./4-Handlers).

# Interop

## clojure.tools.logging

[`clojure.tools.logging`](https://github.com/clojure/tools.logging) can use Telemere as its logging implementation.

To do this:

1. Ensure that you have the `clojure.tools.logging` dependency, and
2. Call [`tools-logging->telemere!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.tools-logging#tools-logging-%3Etelemere!), or set the relevant environmental config as described in its docstring.

Verify successful intake with [`check-intakes`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#check-intakes):

```clojure
(check-intakes) ; =>
{:tools-logging {:sending->telemere? true, :telemere-receiving? true}}
```

## Java logging

[`SLF4J`](https://www.slf4j.org/) can use Telemere as its logging backend.

To do this, ensure that you have the following dependencies:

```clojure
[org.slf4j/slf4j-api          "x.y.z"] ; >= 2.0.0
[com.taoensso/slf4j-telemere  "x.y.z"]
```

When `com.taoensso/slf4j-telemere` is on your classpath AND no other SLF4J backends are, SLF4J will direct all its logging calls to Telemere.

Verify successful intake with [`check-intakes`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#check-intakes):

```clojure
(check-intakes) ; =>
{:slf4j {:sending->telemere? true, :telemere-receiving? true}}
```

For other (non-SLF4J) logging like [Log4j](https://logging.apache.org/log4j/2.x/), [`java.util.logging`](https://docs.oracle.com/javase/8/docs/api/java/util/logging/package-summary.html) (JUL), and [Apache Commons Logging](https://commons.apache.org/proper/commons-logging/) (JCL), use an appropriate [SLF4J bridge](https://www.slf4j.org/legacy.html) and the normal SLF4J config as above.

In this case logging will be forwarded:

1. From Log4j/JUL/JCL/etc. to SLF4J, and
2. From SLF4J to Telemere

## System streams

The JVM's `System/out` and/or `System/err` streams can be set to flush to Telemere signals.

To do this, call [`streams->telemere!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#streams-%3Etelemere!).

Note that Clojure's `*out*`, `*err*` are **not** necessarily automatically affected.

Verify successful intake with [`check-intakes`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#check-intakes):

```clojure
(check-intakes) ; =>
{:system/out {:sending->telemere? true, :telemere-receiving? true}
 :system/err {:sending->telemere? true, :telemere-receiving? true}}
```

## OpenTelemetry

Telemere can send signals as [`LogRecords`](https://opentelemetry.io/docs/specs/otel/logs/data-model/) to [OpenTelemetry](https://opentelemetry.io/).

To do this:

1. Ensure that you have the [OpenTelemetry Java](https://github.com/open-telemetry/opentelemetry-java) dependency.
2. Use [`handler:open-telemetry-logger`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.open-telemetry#handler:open-telemetry-logger) to create an appropriately configured handler, and register it with [`add-handler!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#add-handler!).

## Tufte

> [Tufte](https:/www.taoensso.com/tufte) is a simple performance monitoring library for Clojure/Script by the author of Telemere.

Telemere can easily incorporate Tufte performance data in its signals, just like any other data:

```clojure
(let [[_ perf-data] (tufte/profiled <opts> <form>)]
  (t/log! "Performance data" {:perf-data perf-data}))
```

Telemere and Tufte work great together:

- Their functionality is complementary.
- The [upcoming](https:/www.taoensso.com/roadmap) Tufte v4 will share the same core as Telemere and offer an **identical API** for managing filters and handlers.

## Truss

> [Truss](https://www.taoensso.com/truss) is an assertions micro-library for Clojure/Script by the author of Telemere.

Telemere can easily incorporate Truss assertion failure information in its signals, just like any other (error) data.

The [`catch->error!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#catch-%3Eerror!) signal creator can be particularly convenient for this:

```clojure
(t/catch->error! <form-with-truss-assertion/s>)
```