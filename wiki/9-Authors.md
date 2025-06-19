Are you a library author/maintainer that's considering **using Telemere in your library**?

You have **a few options** below-

# Options

## Modern logging facade

[Trove](https://www.taoensso.com/trove) is a minimal, modern alternative to [tools.logging](https://github.com/clojure/tools.logging) that supports all of Telemere's structured logging and rich filtering features.

Basically:

1. You include the (very small) Trove dependency with your library
2. Your library logs using the [Trove API](https://github.com/taoensso/trove#to-choose-a-backend)
3. Your users then [choose](https://github.com/taoensso/trove#to-choose-a-backend) their preferred backend (Telemere, etc.)

This would be my first recommendation, and is what I'm planning to use for future updates to [Sente](https://www.taoensso.com/sente), [Carmine](https://www.taoensso.com/carmine), etc.

## Traditional logging facade (basic logging only)

Many libraries need only basic logging. In these cases it can be beneficial to do your logging through a common traditional logging facade like [tools.logging](https://github.com/clojure/tools.logging) or [SLF4J](https://www.slf4j.org/).

Though these'll limit you to basic features (e.g. no structured logging or [rich filtering](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#get-filters)).

## Telemere as a transitive dependency

You could just include [Telemere](https://clojars.org/com.taoensso/telemere) in your **library's dependencies**. Your library (and users) will then have access to the full Telemere API.

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