This project uses [**Break Versioning**](https://www.taoensso.com/break-versioning).

---

# `v1.0.0-beta20` (2024-08-23)

> **Dep/s**: [Telemere](https://clojars.org/com.taoensso/telemere/versions/1.0.0-beta20) and [Telemere SLF4J provider](https://clojars.org/com.taoensso/slf4j-telemere/versions/1.0.0-beta20) are on Clojars.  
> **Versioning**: Telemere uses [Break Versioning](https://www.taoensso.com/break-versioning).

This is a **pre-release** intended for **early adopters** and those who'd like to give feedback. New betas will be released frequently, while I continue to fix issues and make other improvements/additions.

The included handlers and utils are **still undergoing changes**, though the [signal creator](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-creators) and [signal content](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content) APIs should already be mostly stable.

Please **report any unexpected problems** on [GitHub](https://github.com/taoensso/telemere/issues) or the [Slack channel](https://www.taoensso.com/telemere/slack), thank you! 🙏 

\- [Peter Taoussanis](https://www.taoensso.com)

## Recent changes

Beta 20:

* **\[mod]** Generalize "intake", rename -> "interop" \[ef678bcc]
* **\[mod]** Make `:host` output opt-in for default signal handlers \[88eb5211]

Earlier:

* \[mod] OpenTelemetry handler: rename (generalize) \[064ef323] (beta 19)
* \[mod] OpenTelemetry handler: revert #10 \[599236f4] (beta 18)
* \[mod] Decrease level of :on-init signals \[4d2b5d46] (beta 18)
* \[mod] Removed `*auto-stop-handlers?*` var (beta 15)
* \[mod] Removed `:needs-stopping?` [handler dispatch opt](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:handler-dispatch-options) (beta 15)
* \[mod] Cljs handlers MUST now include stop (0) arity (beta 15)
* \[mod] Users MUST now **manually call** [`stop-handlers!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#stop-handlers!) (beta 15)
* \[mod] SLF4J and `tools.logging` signals now have a custom `:kind` and no `:id` (beta 14)
* \[mod] Renamed `get-min-level` -> [`get-min-levels`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#get-min-levels) (beta 13)
* \[mod] Renamed `shut-down-handlers!` -> [`stop-handlers!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#stop-handlers!) (beta 13)
* \[mod] Changed default **handler back-pressure** mechanism from `:dropping` to `:blocking` (eaiser for most users to understand and detect; override when calling [`add-handler!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#add-handler!)) (beta 11)
* \[mod] [`pr-signal-fn`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils#pr-signal-fn) now takes only a **single opts map** (beta 10)
* \[mod] [User-level kvs](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-options) are **no longer included by default** in handler output. `:incl-kvs?` option has been added to [`format-signal-fn`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils#format-signal-fn) and [`pr-signal-fn`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils#pr-signal-fn) (beta 7)
* \[mod] Middleware must now be a **single fn**, use [`comp-middleware`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#comp-middleware) to create one fn from many (beta 7)
* \[mod] [OpenTelemetry handler](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.open-telemetry#handler:open-telemetry) is **no longer auto added** (beta 1)
* \[mod] Various API improvements to [included handlers](https://github.com/taoensso/telemere/wiki/4-Handlers#included-handlers) and [utils](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils)

## Recent additions

Beta 20:

* **\[new]** OpenTelemetry handler: improve span interop \[84957c6d]

Earlier:

* \[new] OpenTelemetry handler: add experimental trace output \[67cb4941] (beta 18)
* \[new] Improve uid control, switch to nano-style by default \[5ab2736c] (beta 18)
* \[new] Add host info to signal content \[1cef1957] (beta 18)
* \[new] Add extra tracing info to signal content \[d635318f] (beta 18)
* \[new] Ongoing [API](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere) and [wiki](https://github.com/taoensso/telemere/wiki) doc improvements (beta 15)
* \[new] [#5] Added [comparison to Mulog](https://github.com/taoensso/telemere/wiki/6-FAQ#how-does-telemere-compare-to-mulog) (beta 15)
* \[new] SLF4J and `tools.logging` signals now have a namespace (from logger name) (beta 14)
* \[new] Added [`get-handlers-stats`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#get-handlers-stats) (beta 13)
* \[new] [`add-handler!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#add-handler!) can now specify per-handler `:drain-msecs` (beta 13)
* \[new] Added [`*auto-stop-handlers?*`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#*auto-stop-handlers?*) (beta 13)
* \[new] [`remove-handler!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#remove-handler!) now auto stops relevant handlers after removal (beta 13)
* \[new] [`with-handler`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#with-handler) and [`with-handler+`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#with-handler+) now auto stops relevant handlers after use (beta 12)
* \[new] (Advanced) Handler fns can now include `:dispatch-opts` metadata, useful for handler authors that want to set defaults for use by [`add-handler!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#add-handler!) (beta 8)
* \[new] Added [Slack handler](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.slack#handler:slack) (beta 8)
* \[new] Added [TCP](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.sockets#handler:tcp-socket) and [UDP](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.sockets#handler:udp-socket) socket handlers (beta 7)
* \[new] Clj [signal content](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content) now includes `:thread {:keys [group name id]}` key (beta 7)
* \[new] Added [postal (email) handler](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.postal#handler:postal) (beta 5)
* \[new] Handlers now block to try drain their signal queues on shutdown (beta 3)
* \[new] Rate limiter performance improvements (via Encore) (beta 3)

## Recent fixes

Beta 20:

* None

Earlier:

* \[fix] OpenTelemetry handler: use signal callsite Context as root span parent \[a8e92303] (beta 19)
* \[fix] [#16] OpenTelemetry handler: coerce line attrs (@flyingmachine) \[17349a08] (beta 19)
* \[fix] Decrease min Java version (11->8) (@flyingmachine) \[a1c50f10] (beta 19)
* \[fix] Broken handler ns and kind filters \[23194238] (beta 16)
* \[fix] [#10] OpenTelemetry handler: render keywords as plain strings \[6e94215e] (beta 15)
* \[fix] [#11] OpenTelemetry handler: signals without message fail \[863cea15] (beta 15)
* \[fix] [#14] File handler: Don't truncate gzip output \[2d4b0497] (beta 15)
* \[fix] Don't drop signals while draining async buffer during shutdown, add tests (via Encore) (beta 12, beta 13)
* \[fix] [`pr-signal-fn`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils#pr-signal-fn) wasn't realizing delayed messages, add tests \[cf72017a] (beta 11)
* \[fix] [`pr-signal-fn`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils#pr-signal-fn) broken custom pr-fn support, add tests \[e7cce0c1] (beta 10)
* \[fix] [#6] Missing root stack trace, add tests \[213c6470] (beta 9)
* \[fix] Broken AOT support, add tests \[ffea1a30] (beta 1)
* \[fix] SLF4J broken timestamps, add tests \[e222297a] (beta 1)
