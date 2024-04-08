<a href="https://www.taoensso.com/clojure" title="More stuff by @ptaoussanis at www.taoensso.com"><img src="https://www.taoensso.com/open-source.png" alt="Taoensso open source" width="340"/></a>  
[**Documentation**](#documentation) | [Latest releases](#latest-releases) | [Get support][GitHub issues]

# Telemere

### Structured telemetry library for Clojure/Script

> This library is still under development, ETA: [April 2024](https://www.taoensso.com/roadmap).

**Telemere** handles **traditional and structured logging**, **tracing**, and **performance measurement** with one clean, simple, unified API.

It's a next-generation **observability toolkit** and modern replacement for [Timbre](https://www.taoensso.com/timbre), representing the refinement and culmination of ideas brewing over Timbre's 12+ years in a wide variety of real-world Clojure/Script environments.

## Latest release/s

- `v1.0.0-alpha3` (dev): [release info](https://clojars.org/com.taoensso/telemere/versions/1.0.0-alpha3) (for early adopters)

[![Main tests][Main tests SVG]][Main tests URL]
[![Graal tests][Graal tests SVG]][Graal tests URL]

See [here][GitHub releases] for earlier releases.

## Why Telemere?

#### Usability

- Simple, lightweight API that's **easy to use**, **easy to configure**, and **deeply flexible**.
- **Sensible defaults** to make getting started **fast and easy**.
- Extensive **beginner-oriented** [documentation][GitHub wiki] and [docstrings][clj-doc docs].

#### Interop

- First-class **out-the-box interop** with [OpenTelemetry](https://opentelemetry.io/), [SLF4J v2](https://www.slf4j.org/), [clojure.tools.logging](https://github.com/clojure/tools.logging), and [Tufte](https://www.taoensso.com/tufte).
- Included shim for easy/gradual [migration from Timbre](/TODO).

#### Performance

- Hyper-optimized and **blazing fast**, see [benchmarks](#performance).
- **Scales comfortably** from the smallest disposable code, to the most massive and complex production environments.

#### Flexibility

- Config via plain **Clojure vals and fns** for easy customization, composition, and REPL debug.
- Top-notch support for **environmental config** (JVM props, ENV vars, edn resources, etc.).
- Expressive **per-call** and **per-handler** filtering at both **runtime** and **compile-time**.
- Easily filter by namespace and id pattern, level, **level by namespace pattern**, etc.
- Support for auto **sampling**, **rate-limiting**, and **back-pressure monitoring**.
- Support for **fully configurable a/sync dispatch** (blocking, dropping, sliding, etc.).

## Video demo

See for intro and usage: (**TODO**: coming later)

<a href="https://www.youtube.com/watch?v=TODO" target="_blank">
 <img src="https://img.youtube.com/vi/TODO/maxresdefault.jpg" alt="Telemere demo video" width="480" border="0" />
</a>

## Quick example

```clojure
;; TODO: coming later
```

## Observability tips

See [here](/TODO) for general advice re: building and maintaining **highly observable** Clojure/Script systems.

## Benchmarks

Telemere is **highly optimized** and offers terrific performance at any scale:

| Compile-time filtering? | Runtime filtering? | Time? | Trace? | nsecs
| :-:       | :-: | :-: | :-: | --:
| ✓ (elide) | -   | -   | -   | 0
| -         | ✓   | -   | -   | 200
| -         | ✓   | ✓   | -   | 280
| -         | ✓   | ✓   | ✓   | 650

Measurements:

- Are **~nanoseconds per signal call** (= milliseconds per 1e6 calls)
- Exclude handler runtime (which depends on handler/s, is usually async)
- Taken on a 2020 Macbook Pro M1, running OpenJDK 21

**Tip**: Telemere offers extensive per-call and per-handler **filtering**, **sampling**, and **rate-limiting**. Use these to ensure that you're not capturing useless/low-value information in production. See [here](/TODO) for more tips!

## Documentation

- [Wiki][GitHub wiki] (getting started, usage, etc.) (**TODO:** coming later)
- API reference: [Codox][Codox docs], [clj-doc][clj-doc docs]

## Funding

You can [help support][sponsor] continued work on this project, thank you!! 🙏

## License

Copyright &copy; 2023-2024 [Peter Taoussanis][].  
Licensed under [EPL 1.0](LICENSE.txt) (same as Clojure).

<!-- Common -->

[GitHub releases]: ../../releases
[GitHub issues]:   ../../issues
[GitHub wiki]:     ../../wiki

[Peter Taoussanis]: https://www.taoensso.com
[sponsor]:          https://www.taoensso.com/sponsor

<!-- Project -->

[Codox docs]:   https://taoensso.github.io/telemere/
[clj-doc docs]: https://cljdoc.org/d/com.taoensso/telemere/

[Clojars SVG]: https://img.shields.io/clojars/v/com.taoensso/telemere.svg
[Clojars URL]: https://clojars.org/com.taoensso/telemere

[Main tests SVG]:  https://github.com/taoensso/telemere/actions/workflows/main-tests.yml/badge.svg
[Main tests URL]:  https://github.com/taoensso/telemere/actions/workflows/main-tests.yml
[Graal tests SVG]: https://github.com/taoensso/telemere/actions/workflows/graal-tests.yml/badge.svg
[Graal tests URL]: https://github.com/taoensso/telemere/actions/workflows/graal-tests.yml
