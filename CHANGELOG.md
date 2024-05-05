This project uses [**Break Versioning**](https://www.taoensso.com/break-versioning).

---

# `v1.0.0-beta6` (2024-05-05)

> **Dep/s**: [Telemere](https://clojars.org/com.taoensso/telemere/versions/1.0.0-beta6) and [Telemere SLF4J provider](https://clojars.org/com.taoensso/slf4j-telemere/versions/1.0.0-beta6) are on Clojars.  
> **Versioning**: Telemere uses [Break Versioning](https://www.taoensso.com/break-versioning).

This is a **maintenance pre-release** intended to fix issues that have come up during the beta. See below for details, and please **report any unexpected problems** on [GitHub](https://github.com/taoensso/telemere/issues) or the [Slack channel](https://www.taoensso.com/telemere/slack), thank you! 🙏

\- Peter Taoussanis

## Changes since `v1.0.0-beta1` (2024-04-19)

* d0a15bac [mod] Don't auto add OpenTelemetry handler
* 6d545dfc [mod] Move (simplify) OpenTelemetry ns
* c4d9dd09 [mod] Don't include user-level kvs in default signal content handler
* d3c63e17 [mod] Rename `clojure.tools.logging` sys val
* b271c4f7 [mod] Simplify middleware - don't auto compose

## Fixes since `v1.0.0-beta1` (2024-04-19)

* ffea1a30 [fix] Fix broken AOT support, add AOT tests
* e222297a [fix] SLF4J broken timestamps, add tests

## New since `v1.0.0-beta1` (2024-04-19)

* 2ba23ee7 [new] Add postal (email) handler
* cf22ddf8 [new] Add TCP, UDP socket handlers
* b6e8c5fd [new] Add experimental `:thread` key to Clj signals
* Handlers will now drain their signal queues on shutdown (configurable)
* Rate limiter performance improvements (via Encore)
* Doc improvements based on questions that've come up on Slack, etc.

## Everything since `v1.0.-beta5` (2024-04-29)

* ae823f0d [mod] Rename, refactor signal formatting utils
* b271c4f7 [mod] Simplify middleware - don't auto compose
* cbd786be [fix] Broken postal handler subject
* cf22ddf8 [new] Add TCP, UDP socket handlers
* b6e8c5fd [new] Add experimental `:thread` key to Clj signals
* ea6a0399 [new] Add `:incl-kvs?` opt to edn and JSON formatters
* 3e1f453d [new] Add `:incl-thread?`, `:incl-kvs?` opts to `format-signal->str-fn`
* eed702a4 [new] Add `:end-with-newline` opt to signal formatters

---

# `v1.0.0-beta5` (2024-04-29)

> **Dep/s**: [Telemere](https://clojars.org/com.taoensso/telemere/versions/1.0.0-beta5) and [Telemere SLF4J provider](https://clojars.org/com.taoensso/slf4j-telemere/versions/1.0.0-beta5) are on Clojars.  
> **Versioning**: Telemere uses [Break Versioning](https://www.taoensso.com/break-versioning).

This is a **maintenance pre-release** intended to fix issues that have come up during the beta. See below for details, and please **report any unexpected problems** on [GitHub](https://github.com/taoensso/telemere/issues) or the [Slack channel](https://www.taoensso.com/telemere/slack), thank you! 🙏

\- Peter Taoussanis

## Changes since `v1.0.0-beta1`

* d0a15bac [mod] Don't auto add OpenTelemetry handler
* 6d545dfc [mod] Move (simplify) OpenTelemetry ns
* c4d9dd09 [mod] Don't include user-level kvs in default signal content handler
* d3c63e17 [mod] Rename `clojure.tools.logging` sys val

## Fixes since `v1.0.0-beta1`

* ffea1a30 [fix] Fix broken AOT support, add AOT tests
* e222297a [fix] SLF4J broken timestamps, add tests

## New since `v1.0.0-beta1`

* 2ba23ee7 [new] Add postal (email) handler
* Handlers will now drain their signal queues on shutdown (configurable)
* Rate limiter performance improvements (via Encore)
* Doc improvements based on questions that've come up on Slack, etc.

---

# `v1.0.0-beta3` (2024-04-23)

> **Dep/s**: [Telemere](https://clojars.org/com.taoensso/telemere/versions/1.0.0-beta3) and [Telemere SLF4J provider](https://clojars.org/com.taoensso/slf4j-telemere/versions/1.0.0-beta3) are on Clojars.  
> **Versioning**: Telemere uses [Break Versioning](https://www.taoensso.com/break-versioning).

This is a **maintenance pre-release** intended to fix issues that have come up during the beta. See below for details, and please **report any unexpected problems** on [GitHub](https://github.com/taoensso/telemere/issues) or the [Slack channel](https://www.taoensso.com/telemere/slack), thank you! 🙏

\- Peter Taoussanis

## Changes since `v1.0.0-beta1`

* d0a15bac [mod] Don't auto add OpenTelemetry handler
* 6d545dfc [mod] Move (simplify) OpenTelemetry ns
* d3c63e17 [mod] Rename `clojure.tools.logging` sys val

## Fixes since `v1.0.0-beta1`

* ffea1a30 [fix] Fix broken AOT support, add AOT tests
* e222297a [fix] SLF4J broken timestamps, add tests

## New since `v1.0.0-beta1`

* Handlers will now drain their signal queues on shutdown (configurable)
* Rate limiter performance improvements (via Encore)
* Misc improvements to docs

---

# `v1.0.0-beta1` (2024-04-19)

> **Dep/s**: [Telemere](https://clojars.org/com.taoensso/telemere/versions/1.0.0-beta1) and [Telemere SLF4J provider](https://clojars.org/com.taoensso/slf4j-telemere/versions/1.0.0-beta1) are on Clojars.  
> **Versioning**: Telemere uses [Break Versioning](https://www.taoensso.com/break-versioning).

This is Telemere's first public pre-release and mostly intended for early testers and those that'd like to give feedback.

While no significant changes are expected before the [planned v1.0 final release](https://www.taoensso.com/roadmap), you **probably don't want to use this in production** just yet.

**Please report any unexpected problems** and let me know if anything is unclear, inconvenient, etc. Now's the ideal time to get any last-minute changes in. Thank you! 🙏

\- Peter Taoussanis
