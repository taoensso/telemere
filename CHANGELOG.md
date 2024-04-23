This project uses [**Break Versioning**](https://www.taoensso.com/break-versioning).

---

# `v1.0.0-beta2` (2024-04-23)

> **Dep/s**: [Telemere](https://clojars.org/com.taoensso/telemere/versions/1.0.0-beta2) and [Telemere SLF4J provider](https://clojars.org/com.taoensso/slf4j-telemere/versions/1.0.0-beta2) are on Clojars.  
> **Versioning**: Telemere uses [Break Versioning](https://www.taoensso.com/break-versioning).

This is a **maintenance pre-release** mainly intended to fix an issue with beta1's AOT support. Telemere also no longer automatically adds an OpenTelemetry handler when OpenTelemetry is present. Instead, see the docs for [manually adding an OpenTelemetry handler](https://github.com/taoensso/telemere/wiki/3-Config#opentelemetry).

Please **report any unexpected problems** on [GitHub](https://github.com/taoensso/telemere/issues) or the [Slack channel](https://www.taoensso.com/telemere/slack), thank you! 🙏

\- Peter

## Changes since `v1.0.0-beta1`

* c47759dd [mod] Don't auto add OpenTelemetry handler
* a9e40296 [mod] Rename `clojure.tools.logging` sys val
* 9ccb815d [mod] Fix AOT support, don't alias conditional vars

## New since `v1.0.0-beta1`

* Misc improvements to docs

---

# `v1.0.0-beta1` (2024-04-19)

> **Dep/s**: [Telemere](https://clojars.org/com.taoensso/telemere/versions/1.0.0-beta1) and [Telemere SLF4J provider](https://clojars.org/com.taoensso/slf4j-telemere/versions/1.0.0-beta1) are on Clojars.  
> **Versioning**: Telemere uses [Break Versioning](https://www.taoensso.com/break-versioning).

This is Telemere's first public pre-release and mostly intended for early testers and those that'd like to give feedback.

While no significant changes are expected before the [planned v1.0 final release](https://www.taoensso.com/roadmap), you **probably don't want to use this in production** just yet.

**Please report any unexpected problems** and let me know if anything is unclear, inconvenient, etc. Now's the ideal time to get any last-minute changes in. Thank you! 🙏

\- Peter Taoussanis