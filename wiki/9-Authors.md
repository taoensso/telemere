Are you a library author/maintainer that's considering **using Telemere in your library**?

# Options
## 1. Common logging facade (basic logging only)

Many libraries need only basic logging. In these cases it can be beneficial to do your logging through a common logging facade like [tools.logging](https://github.com/clojure/tools.logging) or [SLF4J](https://www.slf4j.org/).

This'll limit you to basic features (e.g. no structured logging or [rich filtering](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#get-filters)) - but your users will have the freedom to choose and configure their **preferred backend** ([incl. Telemere if they like](./3-Config#interop)).

## 2. Telemere as a transitive dependency

Include [Telemere](https://clojars.org/com.taoensso/telemere) in your **library's dependencies**. Your library (and users) will then have access to the full Telemere API.

Telemere's [default config](./1-Getting-started#default-config) is sensible (with println-like console output), so your users are unlikely to need to configure or interact with Telemere much unless they choose to.

The most common thing users may want to do is **adjust the minimum level** of signals created by your library. You can help make this as easy as possible by adding a util to your library:

```clojure
(defn set-min-log-level!
  "Sets Telemere's minimum level for <my-lib> namespaces.
  This will affect all signals (logs) created by <my-lib>.

  Possible minimum levels (from most->least verbose):
    #{:trace :debug :info :warn :error :fatal :report}.

  The default minimum level is `:warn`."
  [min-level]
  (tel/set-min-level! nil "my-lib-ns(.*)" min-level)
  true)

(defonce ^:private __set-default-log-level (set-min-log-level! :warn))
```

This way your users can easily disable, decrease, or increase signal output from your library without even needing to touch Telemere or to be aware of its existence.

## 3. Telemere as an optional dependency

Include the (super lightweight) [Telemere shell API](https://clojars.org/com.taoensso/telemere-shell) in your **library's dependencies**.

Your library will then be able to emit structured logs/telemetry **when Telemere is present**, or fall back to something like [tools.logging](https://github.com/clojure/tools.logging) otherwise.

The main trade-off is that your signal calls will be more verbose:

```clojure
(ns my-lib
  (:require
    [taoensso.telemere.shell :as t] ; `com.taoensso/telemere-shell` dependency
    [clojure.tools.logging :as ctl] ; `org.clojure/tools.logging` dependency
    ))

(t/require-telemere-if-present) ; Just below `ns` form

;; Optional convenience for library users
(defn set-min-level!
  "If it's present, sets Telemere's minimum level for <my-lib> namespaces.
  This will affect all signals (logs) created by <my-lib>.

  Possible minimum levels (from most->least verbose):
    #{:trace :debug :info :warn :error :fatal :report}.

  The default minimum level is `:warn`."
  [min-level]
  (t/if-telemere
    (do (t/set-min-level! nil \"my-lib(.*)\" min-level) true)
    false))

(defonce ^:private __set-default-min-level (set-min-level! :warn))

;; Creates Telemere signal if Telemere is present,
;; otherwise logs with tools.logging
(signal!
  {:kind :log, :id :my-id, :level :warn,
   :let  [x :x]
   :msg  [\"Hello\" \"world\" x]
   :data {:a :A :x x}
   :fallback (ctl/warn (str \"Hello world\" x))})
```

See [here](https://cljdoc.org/d/com.taoensso/telemere-shell/CURRENT/api/taoensso.telemere.shell) for more info.
