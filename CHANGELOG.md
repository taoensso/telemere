This project uses [**Break Versioning**](https://www.taoensso.com/break-versioning).

---

# `v1.0.0-beta12` (2024-05-14)

> **Dep/s**: [Telemere](https://clojars.org/com.taoensso/telemere/versions/1.0.0-beta12) and [Telemere SLF4J provider](https://clojars.org/com.taoensso/slf4j-telemere/versions/1.0.0-beta12) are on Clojars.  
> **Versioning**: Telemere uses [Break Versioning](https://www.taoensso.com/break-versioning).

This is a **pre-release** intended for **early adopters** and those who'd like to give feedback. New betas will be released frequently, while I continue to fix issues and make other improvements/additions.

The included handlers and utils are **still undergoing changes**, though the signal creator API (and signal content) should already be mostly stable.

Please **report any unexpected problems** on [GitHub](https://github.com/taoensso/telemere/issues) or the [Slack channel](https://www.taoensso.com/telemere/slack), thank you! 🙏 

Big thanks to those that have patiently helped report and debug issues, especially DenisMc!

\- Peter Taoussanis

## Recent changes

* \[mod] Changed default **handler back-pressure** mechanism from `:dropping` to `:blocking` (eaiser for most users to understand and detect; override when calling [`add-handler!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#add-handler!)) (beta11)
* \[mod] [`pr-signal-fn`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils#pr-signal-fn) now takes only a **single opts map** (beta10)
* \[mod] [User-level kvs](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-options) are **no longer included by default** in handler output. `:incl-kvs?` option has been added to [`format-signal-fn`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils#format-signal-fn) and [`pr-signal-fn`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils#pr-signal-fn) (beta7)
* \[mod] Middleware must now be a **single fn**, use [`comp-middleware`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#comp-middleware) to create one fn from many (beta7)
* \[mod] [OpenTelemetry handler](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.open-telemetry#handler:open-telemetry-logger) is **no longer auto added** (beta1)
* \[mod] Various API improvements to [included handlers](https://github.com/taoensso/telemere/wiki/4-Handlers#included-handlers) and [utils](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils)

## Recent additions

* \[new] Ongoing [API](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere) and [wiki](https://github.com/taoensso/telemere/wiki) doc improvements
* \[new] [`add-handler!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#add-handler!) can now specify per-handler `:max-shutdown-msecs` (beta12)
* \[new] [`with-handler`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#with-handler) and [`with-handler+`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#with-handler+) now auto shutdown handlers after use (beta12)
* \[new] (Advanced) Handler fns can now include `:dispatch-opts` metadata, useful for handler authors that want to set defaults for use by [`add-handler!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#add-handler!) (beta8)
* \[new] Added [Slack handler](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.slack#handler:slack) (beta8)
* \[new] Added [TCP](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.sockets#handler:tcp-socket) and [UDP](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.sockets#handler:udp-socket) socket handlers (beta7)
* \[new] Clj [signal content](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content) now includes `:thread {:keys [group name id]}` key (beta7)
* \[new] Added [postal (email) handler](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.postal#handler:postal) (beta5)
* \[new] Handlers now block to try drain their signal queues on shutdown (beta3)
* \[new] Rate limiter performance improvements (via Encore) (beta3)

## Recent fixes

* \[fix] Don't drop signals while draining async buffer during shutdown, add tests (via Encore) (beta12)
* \[fix] [`pr-signal-fn`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils#pr-signal-fn) wasn't realizing delayed messages, add tests \[cf72017a] (beta11)
* \[fix] [`pr-signal-fn`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils#pr-signal-fn) broken custom pr-fn support, add tests \[e7cce0c1] (beta10)
* \[fix] [#6] Missing root stack trace, add tests \[213c6470] (beta9)
* \[fix] Broken AOT support, add tests \[ffea1a30] (beta1)
* \[fix] SLF4J broken timestamps, add tests \[e222297a] (beta1)

---

# `v1.0.0-beta1` (2024-04-19)

> **Dep/s**: [Telemere](https://clojars.org/com.taoensso/telemere/versions/1.0.0-beta1) and [Telemere SLF4J provider](https://clojars.org/com.taoensso/slf4j-telemere/versions/1.0.0-beta1) are on Clojars.  
> **Versioning**: Telemere uses [Break Versioning](https://www.taoensso.com/break-versioning).

This is Telemere's first public pre-release and mostly intended for **early testers** and those that'd like to give feedback.

While no significant changes are expected before the [planned v1.0 final release](https://www.taoensso.com/roadmap), you **probably don't want to use this in production** just yet.

**Please report any unexpected problems** and let me know if anything is unclear, inconvenient, etc. Now's the ideal time to get any last-minute changes in. Thank you! 🙏

\- Peter Taoussanis
