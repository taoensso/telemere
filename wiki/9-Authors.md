Are you a library author/maintainer that's considering **using Telemere in your library**?

# Options
## 1. Consider a basic facade

Does your library **really need** Telemere? Many libraries only need very basic logging. In these cases it can be beneficial to do your logging through a common basic facade like [tools.logging](https://github.com/clojure/tools.logging) or [SLF4J](https://www.slf4j.org/).

**Pro**: users can then choose and configure their **preferred backend** - including Telemere, which can easily [act as a backend](./3-Config#interop) for both tools.logging and SLF4J.

**Cons**: you'll be limited by what your facade API offers, and so lose support for Telemere's advanced features like structured logging, [rich filtering](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#get-filters), etc.

## 2. Telemere as a transitive dependency

Include [Telemere](https://clojars.org/com.taoensso/telemere) in your **library's dependencies**. Your library (and users) will then have access to the full Telemere API.

Telemere's [default config](./1-Getting-started#default-config) is sensible (with println-like console output), so many of library users won't need to configure or interact with Telemere at all.

The most common thing library users may want to do is **adjust the minimum level** of signals created by your library. And since your users might not be familiar with Telemere, I'd recommend including something like the following in a convenient place like your library's main API namespace:

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

Include the (super lightweight) [Telemere facade API](https://clojars.org/com.taoensso/telemere-api) in your **library's dependencies**.

Your library will then be able to take advantage of Telemere **when Telemere is present**, or fall back to something like [tools.logging](https://github.com/clojure/tools.logging) otherwise.

The main trade-off is that your signal calls will be more verbose.

See [here](https://cljdoc.org/d/com.taoensso/telemere-api/CURRENT/api/taoensso.telemere.api) for an example and more info.