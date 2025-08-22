This project uses [**Break Versioning**](https://www.taoensso.com/break-versioning).

---
# `v1.1.0` (2025-08-22)

## 📦 Dependencies

Available on Clojars:

1. [Telemere](https://clojars.org/com.taoensso/telemere/versions/1.1.0) - main dep
2. [SLF4J provider](https://clojars.org/com.taoensso/telemere-slf4j/versions/1.0.1) - extra dep to [send Java logging](https://github.com/taoensso/telemere/wiki/3-Config#java-logging) to Telemere

This project uses [Break Versioning](https://www.taoensso.com/break-versioning).

This is a **maintenance release** that fixes a few minor issues, improves docs, and adds some extra API flexibility. It should be a safe upgrade for all users of v1.x.

Please **report any unexpected problems** on [GitHub](https://github.com/taoensso/telemere/issues) or the [Slack channel](https://www.taoensso.com/telemere/slack) 🙏 - [Peter Taoussanis](https://www.taoensso.com)

## Since v1.0.01 (2025-05-27)

- \[fix] `:trace` level JS console logging \[b2a8b66]
- \[fix] Clj-kondo warnings for `with-signal/s` \[269c58d]
- \[new] `with-ctx/+` now takes `& body` instead of a single form (via Encore update)

---

# `v1.0.1` (2025-05-27)

## 📦 Dependencies

Available on Clojars:

1. [Telemere](https://clojars.org/com.taoensso/telemere/versions/1.0.1) - main dependency.
2. [SLF4J provider](https://clojars.org/com.taoensso/telemere-slf4j/versions/1.0.1) - additional dependency for users that want their Java logging [to go to](https://github.com/taoensso/telemere/wiki/3-Config#java-logging) Telemere.

This project uses [Break Versioning](https://www.taoensso.com/break-versioning).

## Release notes

This is a **hotfix release** that fixes a few issues, and improves some documentation. It should be a safe upgrade for all users of v1.0.0.

##  Since `v1` (2025-04-30)

* \[fix] [#65] Fix broken callsite `:limit` option \[f08b60b]
* \[fix] Fix bad `signal-content-fn` parent formatting \[3746de8]
* \[doc] Add extra docs re: debugging filtering \[1bdb667]
* \[doc] [#64] Hide some unimportant vars from API docs (@marksto) \[2e0a293]
* \[doc] [#63] Add link to community Axiom handler (@marksto) \[9d040d7]

---

# `v1.0.0` (2025-04-30)

## 📦 Dependencies

Available on Clojars:

1. [Telemere](https://clojars.org/com.taoensso/telemere/versions/1.0.0) - main dependency.
2. [SLF4J provider](https://clojars.org/com.taoensso/telemere-slf4j/versions/1.0.0) - additional dependency for users that want their Java logging [to go to](https://github.com/taoensso/telemere/wiki/3-Config#java-logging) Telemere.

This project uses [Break Versioning](https://www.taoensso.com/break-versioning).

## Release notes

This is the first stable release of Telemere v1! 🍾🥳🎉

Sincere thanks to everyone that's been helping test and give feedback. As always, please **report any unexpected problems** on [GitHub](https://github.com/taoensso/telemere/issues) or the [Slack channel](https://www.taoensso.com/telemere/slack) 🙏

Telemere is part of a suite of practical and complementary **observability tools** for modern Clojure and ClojureScript applications:

- [Telemere](https://www.taoensso.com/telemere) for logging, tracing, and general telemetry
- [Tufte](https://www.taoensso.com/tufte) for performance monitoring ([v3 RC1 just released](https://github.com/taoensso/tufte/releases/tag/v3.0.0-RC1))
- [Truss](https://www.taoensso.com/truss) for assertions and error handling ([v2 recently released](https://github.com/taoensso/truss/releases/tag/v2.1.0))

New to Telemere? [Start here](https://github.com/taoensso/telemere/wiki/1-Getting-started)!  
Upgrading from an earlier version? See the list of changes below👇

Cheers! :-)

\- [Peter Taoussanis](https://www.taoensso.com)

##  Changes since `v1 RC1`

> See linked commits for more info:

In **v1 stable** (2025-04-30):
- \[fix] [#61] OpenTelemetry handler not cancelling timer on shutdown \[51e8a10]
- \[fix] [#32] Fix clj-kondo declaration typo (@icp1994) \[254cd64]
- \[new] Support `:host`, `:thread` override \[31a4fc2]
- \[new] Add callsite info to compile-time errors \[345b125]
- \[doc] Use consistent style for docstring opts \[94fec57]

In **v1 RC5** (2025-03-10):
* \[mod] Rename `:rate-limit` -> `:limit` \[f37f54e] (RC5)
* \[mod] Rename `:sample-rate` -> `:sample` \[1f4b49a] (RC5)
* \[mod] Rename `:middleware` -> `:xfn` \[7cccf67] (RC5)
* \[mod] [#56] `utils/clean-signal-fn` exclude `:schema` by default \[c78eb07] (RC5)
* \[fix] [#57] File handling: use nio API to create missing parent dirs \[af45ffc] (RC5)
* \[fix] [#55] SLF4J signals should include `*ctx*` \[79173a6] (RC5)
* \[fix] [#32] Fix clj-kondo warnings \[c60f33e] (RC5)
* \[new] [#57] File handling: make file stream more robust \[82f4c31] (RC5)

In **v1 RC4** (2025-03-03):
* \[mod] `log!`, `event!` now always return nil \[ac5feb4] (RC4)
* \[mod] [#51] Make default console handler sync by default \[78ed4d7] (RC4)
* \[mod] [#52] `signal-preamble-fn` now ignores nil `:kind` (@marksto) \[634cc53] (RC4)
* \[fix] [#52] `signal-preamble-fn` should use host info in signal (@marksto) \[410ed89] (RC4)
* \[new]Add `log!?`, `event!?` \[ac5feb4] (RC4)
* \[new] Alias `keep-callsite`, mention in `signal!` docs \[bfea515] (RC4)
* \[doc][#50] Expand docs for `set-min-level!` (via Encore update) (RC4)
* \[doc] Mention `:inst` monotonicity \[6b0e0b9] (RC4)

In **v1 RC3** (2025-02-27):
* \[mod] Signal content: drop `:location`, add `:coords` \[fda22ce] (RC3)
* \[mod] Signal options: drop `:location`, add `:coords` \[1f99f71] (RC3)
* \[mod] OpenTelemetry: use standard attr names when possible \[bb715fb] (RC3)
* \[fix] Timbre shim: rename `spy!` -> `spy` (@lvh) \[3a9ffc6] (RC3)
* \[fix] Timbre shim: don't attach empty `:vargs` data \[0e642ba] (RC3)
* \[fix] Fix environment val docs \[db26a5d] (RC3)
* \[fix] `spy!` docstring typo (@rafd) \[35606d9] (RC3)
* \[new] Use [Truss](https://www.taoensso.com/truss) v2 and [contextual exceptions](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#ex-info) when relevant (RC3)
* \[new] [#44] Open Telemetry handler: add span kind option (@farcaller) \[413cce8] (RC3)
* \[new] Reduced Cljs build sizes in some cases (RC3)
* \[doc]Timbre shim: document different `spy` error handling \[1517f30] (RC3)
* \[doc] [#43] ns filters work for SLF4J logger names (@lvh) \[db0498b] (RC3)

In **v1 RC2** (2024-12-24):
* \[mod] [#39] Discontinued separate "shell" library \[096c432] (RC2)
* \[mod] Change return value of experimental [`with-signals`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#with-signals) \[cb6a5d9] (RC2)
* \[mod] Remove rarely-used advanced options from [`catch->error!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#catch-%3Eerror!) \[0de5c09] (RC2)
* \[mod] Remove "- " msg separator from default [preamble](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils#signal-preamble-fn) output \[d61f6c2] (RC2)
* \[mod] [Postal handler](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.postal#handler:postal) now uses default preamble fn for email subject \[706a8b6] (RC2)
* \[mod] Default [`signal-content-fn`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils#signal-content-fn): omit redundant parent/root id namespaces \[55323f1] (RC2)
* \[mod] Default [`signal-content-fn`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils#signal-content-fn): swap `ctx`, `kvs` position \[b208532] (RC2)
* \[mod] Default [`signal-content-fn`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils#signal-content-fn): omit `:root` if it's same as parent \[0464285] (RC2)
* \[mod] Omit empty `:data`, `:ctx` from [signal content](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils#signal-content-fn) output \[d78663a] (RC2)
* \[fix] Broken signal string representation \[8c701d4] (RC2)
* \[fix] Trace formatting: always include root info \[f522307] (RC2)
* \[fix] Trace formatting: properly format nil ids \[68a894e] (RC2)
* \[fix] [#36] Fix missing cljdoc docstrings \[b58ec73] (RC2)
* \[new] Add [`timbre->telemere`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.timbre#timbre->telemere-appender) appender and update docs \[ace6e2d] (RC2)
* \[new] All signal creators can now take single opts map \[d2386d6] (RC2)
* \[new] Add `& opts` support to [`signal!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#signal!), [`signal-allowed?`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#signal-allowed?) \[a04f255] (RC2)
* \[new] Give [`signal!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#signal!) a default kind and level \[7532c2e] (RC2)
* \[new] Better error message when [`signal!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#signal!) given non-map arg \[d563ac1] (RC2)
* \[new] Improve error info on worst-case handler errors \[484b3df] (RC2)
* \[new] Allow manual `:run-val` override \[9dc883d] (RC2)
* \[new] [#34] Add new [`signal-preamble-fn`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.utils#signal-preamble-fn) opts (@Knotschi) \[0822217] (RC2)
* \[new] Alias low-level formatters in utils ns \[9dc9a46] (RC2)
* \[doc] [#33] Add community examples link to [Bling Gist](https://gist.github.com/ptaoussanis/f8a80f85d3e0f89b307a470ce6e044b5) \[8cd4ca9] (RC2)
* \[doc] Better document pattern of using [`trace!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#trace!)/[`spy!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#spy!) with [`catch->error!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#catch-%3Eerror!) \[5c977a3] (RC2)
* \[doc] [#35] Emphasize that [signal opts](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-options) must be a compile-time map \[55720ac] (RC2)
* \[doc] Add [FAQ item](https://github.com/taoensso/telemere/wiki/6-FAQ#why-the-unusual-arg-order-for-event) re: [`event!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#event!) arg order \[822032d] (RC2)
* \[doc] Document that `:msg` may be a delay \[13d9dbf] (RC2)

---

# `v1.0.0-RC1` (2024-10-29)

## 📦 Dependencies

Available on Clojars:

1. [Telemere](https://clojars.org/com.taoensso/telemere/versions/1.0.0-RC1) - main dep for most users
2. [Shell API](https://clojars.org/com.taoensso/telemere-shell/versions/1.0.0-RC1) - alternative minimal dep [for library authors](https://github.com/taoensso/telemere/wiki/9-Authors#3-telemere-as-an-optional-dependency), etc.
3. [SLF4J provider](https://clojars.org/com.taoensso/telemere-slf4j/versions/1.0.0-RC1) - additional dep for users that want to [interop with Java logging](https://github.com/taoensso/telemere/wiki/3-Config#java-logging)

This project uses [Break Versioning](https://www.taoensso.com/break-versioning).

## Release notes

This is the first official **v1 release candidate**. If no unexpected issues come up, this will become **v1 stable**.

As always, please **report any unexpected problems** on [GitHub](https://github.com/taoensso/telemere/issues) or the [Slack channel](https://www.taoensso.com/telemere/slack), thank you! 🙏

\- [Peter Taoussanis](https://www.taoensso.com)

## Recent changes

### Since `v1.0.0-beta25` (2024-09-25)

* No API changes

### Earlier

* \[mod] Update `pr-signal-fn` to use `clean-signal-fn` \[f70363091] (beta 23)
* \[mod] Rename `taoensso.telemere.api` -> `taoensso.telemere.shell` \[a9005e7f1] (beta 23)
* \[mod] Move dep: `com.taoensso/slf4j-telemere` -> [com.taoensso/telemere-slf4j](https://clojars.org/com.taoensso/telemere-slf4j) \[77ed27cfd] (beta 22)
* \[mod] Generalize "intake", rename -> "interop" \[ef678bcc] (beta 20)
* \[mod] Make `:host` output opt-in for default signal handlers \[88eb5211] (beta 20)
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

### Since `v1.0.0-beta25` (2024-09-25)

* **\[new]** Add `:ctx+`, `:middleware+` signal options \[5a8c407] (RC1)
* **\[new]** OpenTelemetry handler: try print map vals as EDN \[c1e1c1e] (RC1)
* **\[new]** [#28] OpenTelemetry handler: support custom signal attrs \[5ef4f12] (RC1)
* **\[new]** Simplify default OpenTelemetry providers code, expose SDK \[19548d3] (RC1)
* **\[new]** Add `dispatch-signal!` util \[5ac8725] (RC1)
* **\[new]** `writeable-file!`: resolve sym links, etc. \[9965450] (RC1)
* **\[new]** Extend IIFE-wrap to Clj \[d0ad99d] (RC1)
* **\[new]** Numerous improvements to docs and examples (RC1)

### Earlier

* \[new] Add `:rate-limit-by` option to all signal creators \[d9c358363] (beta 23)
* \[new] Add `clean-signal-fn` util \[be55f44a8] (beta 23)
* \[new] Add `signal-allowed?` util \[d12b0b145] (beta 23)
* \[new] Allow compile-time config of uid kind \[965c2277f] (beta 23)
* \[new] Avoid duplicated trace bodies \[c9e84e8b3] (beta 23)
* \[new] Cap length of displayed run-form when tracing \[85772f733] (beta 23)
* \[new] Added experimental [shell API](https://cljdoc.org/d/com.taoensso/telemere-shell/CURRENT/api/taoensso.telemere.api) for library authors \[ece51b2ef] (beta 22)
* \[new] Auto stop existing handler when replacing it (beta 22)
* \[new] Added `"(.*)"` wildcard syntax to kind/ns/id filters (beta 22)
* \[new] Internal and doc improvements: \[8066776a8], \[b4b06f324], \[3068ccf8d] (beta 21)
* \[new] OpenTelemetry handler: improve span interop \[84957c6d] (beta 20)
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

### Since `v1.0.0-beta25` (2024-09-25)

* **\[fix]** `signal-opts`: allow map forms as intended \[f7a5663] (RC1)
* **\[fix]** `uncaught->error!` wasn't working (@benalbrecht) \[7f52cb1] (RC1)

### Earlier

* \[fix] Regression affecting deprecated `rate-limiter*` (beta 25)
* \[fix] Don't try count non-list tracing bodies \[88f7a3c7d] (beta 24)
* \[fix] [#21] Work around issue with use in Cljs `core.async/go` bodies \[cbab57be6] (beta 23)
* \[fix] [#20] Wrong :arglists meta on `spy!` \[568906c96] (beta 23)
* \[fix] [#18] Support `{:uid :auto}` for non-tracing signal creators \[f52a04b4d] (beta 23)
* \[fix] Runtime Clj env config now works correctly in uberjars (beta 23)
* \[fix] Signal `:line` info missing for some wrapped-macro cases \[0f09b797e] (beta 22)
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

---

# `v1.0.0-beta25` (2024-09-25)

## 📦 Dependencies

Available on Clojars:

1. [Telemere](https://clojars.org/com.taoensso/telemere/versions/1.0.0-beta25) - main dep for most users
2. [Shell API](https://clojars.org/com.taoensso/telemere-shell/versions/1.0.0-beta25) - alternative minimal dep [for library authors](https://github.com/taoensso/telemere/wiki/9-Authors#3-telemere-as-an-optional-dependency), etc.
3. [SLF4J provider](https://clojars.org/com.taoensso/telemere-slf4j/versions/1.0.0-beta25) - additional dep for users that want to [interop with Java logging](https://github.com/taoensso/telemere/wiki/3-Config#java-logging)

This project uses [Break Versioning](https://www.taoensso.com/break-versioning).

## Release notes

- This is a **pre-release** intended for **early adopters** and those who'd like to give feedback.
- This is expected to be the **last beta** before RC1.
- Please **report any unexpected problems** on [GitHub](https://github.com/taoensso/telemere/issues) or the [Slack channel](https://www.taoensso.com/telemere/slack), thank you! 🙏

\- [Peter Taoussanis](https://www.taoensso.com)

## Recent changes

### Beta 25, 24, 23

* **\[mod]** Update `pr-signal-fn` to use `clean-signal-fn` \[f70363091] (beta 23)
* **\[mod]** Rename `taoensso.telemere.api` -> `taoensso.telemere.shell` \[a9005e7f1] (beta 23)

### Earlier

* \[mod] Move dep: `com.taoensso/slf4j-telemere` -> [com.taoensso/telemere-slf4j](https://clojars.org/com.taoensso/telemere-slf4j) \[77ed27cfd] (beta 22)
* \[mod] Generalize "intake", rename -> "interop" \[ef678bcc] (beta 20)
* \[mod] Make `:host` output opt-in for default signal handlers \[88eb5211] (beta 20)
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

### Beta 25, 24, 23

* **\[new]** Add `:rate-limit-by` option to all signal creators \[d9c358363] (beta 23)
* **\[new]** Add `clean-signal-fn` util \[be55f44a8] (beta 23)
* **\[new]** Add `signal-allowed?` util \[d12b0b145] (beta 23)
* **\[new]** Allow compile-time config of uid kind \[965c2277f] (beta 23)
* **\[new]** Avoid duplicated trace bodies \[c9e84e8b3] (beta 23)
* **\[new]** Cap length of displayed run-form when tracing \[85772f733] (beta 23)
* Updated [Encore](https://www.taoensso.com/encore) to v3.120.0 (2024-09-22) (beta 23)

### Earlier

* \[new] Added experimental [shell API](https://cljdoc.org/d/com.taoensso/telemere-shell/CURRENT/api/taoensso.telemere.api) for library authors \[ece51b2ef] (beta 22)
* \[new] Auto stop existing handler when replacing it (beta 22)
* \[new] Added `"(.*)"` wildcard syntax to kind/ns/id filters (beta 22)
* \[new] Internal and doc improvements: \[8066776a8], \[b4b06f324], \[3068ccf8d] (beta 21)
* \[new] OpenTelemetry handler: improve span interop \[84957c6d] (beta 20)
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

### Beta 25, 24, 23

* **\[fix]** Regression affecting deprecated `rate-limiter*` (beta 25)
* **\[fix]** Don't try count non-list tracing bodies \[88f7a3c7d] (beta 24)
* **\[fix]** [#21] Work around issue with use in Cljs `core.async/go` bodies \[cbab57be6] (beta 23)
* **\[fix]** [#20] Wrong :arglists meta on `spy!` \[568906c96] (beta 23)
* **\[fix]** [#18] Support `{:uid :auto}` for non-tracing signal creators \[f52a04b4d] (beta 23)
* **\[fix]** Runtime Clj env config now works correctly in uberjars (beta 23)

### Earlier

* \[fix] Signal `:line` info missing for some wrapped-macro cases \[0f09b797e] (beta 22)
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
