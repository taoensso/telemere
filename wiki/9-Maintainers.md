Are you a library maintainer that's considering **using Telemere in your library**?

See below for some considerations and advice-

# Consider a facade

Ask yourself the question: do you specifically *need/want* Telemere?

Many libraries only need very basic logging. In these cases it can be beneficial to do your logging through a facade like [tools.logging](https://github.com/clojure/tools.logging) or [SLF4J](https://www.slf4j.org/).

**Upside**: users can then easily choose and configure their **preferred backend** (including Telemere, which can easily [act as a backend](./3-Config#interop) for both tools.logging and SLF4J).

**Downside**: your logging features will necessarily be limited to the lowest-common-denominator features supported by your chosen facade (tools.logging or SLF4J, etc.). In particular, you'll be giving up support for Telemere's structured logging features and [rich filtering](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#get-filters) (including filtering or setting minimum levels by namespaces).

# Consider API stability

Telemere is still currently in **beta** as of May 2024.

While the [signal creator](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-creators) and [signal content](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content) APIs should already be mostly stable, I would still **recommend against** using Telemere in any public libraries until after Telemere's **stable v1 release** (current ETA >= [August 2024](https://taoensso.com/roadmap)).

# Using Telemere in your library

If you **do** need/want support for Telemere's structured logging features and/or [rich filtering](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#get-filters), then you've got a couple options-

## Telemere as a non-optional dependency

This is straight-forward: you include Telemere in your library's dependencies, and you can make use of Telemere's full API from your own library.

Telemere's [default config](./1-Getting-started#default-config) is sensible (with println-like console output), so many of library users won't need to configure or interact with Telemere at all.

The most common thing library users may want to do is **adjust the minimum level** of signals created by your library. And since your users might not be familiar with Telemere, I'd recommend including something like the following in a convenient place like your library's main API namespace:

```clojure
(defn set-min-log-level!
  "Sets minimum level of Telemere signals (logs) created by <my-library>.
  Possible levels (from most to least verbose):
    #{:trace :debug :info :warn :error :fatal :report}.

  The default level is `:warn`."
  [level]
  (tel/set-min-level! nil "my-library.namespace"   min-level)
  (tel/set-min-level! nil "my-library.namespace.*" min-level)
  nil)

(defonce ^:private __set-default-log-level (set-min-log-level! :warn))
```

This way your users can easily disable, decrease, or increase signal output from your library without even needing to touch Telemere or to be aware of its existence.

## Telemere as an optional dependency

I have a solution planned for this that I'm still testing. Will add more info prior to Telemere's [stable v1 release](https://www.taoensso.com/roadmap).

# Migrating from Timbre

Do you have a library that currently uses [Timbre](https://www.taoensso.com/timbre), and you're considering a change to Telemere?

## With a facade

First, I'd encourage you to [consider a facade](#consider-a-fascade).

Unless you specifically want features *only* available to Telemere/Timbre, using a facade would give your users the option to choose **whichever backend they prefer**.

Since both Telemere and Timbre support [tools.logging](https://github.com/clojure/tools.logging) and [SLF4J](https://www.slf4j.org/), either one of those would be a reasonable choice.

(Though note that Timbre's current SLF4J support is a little fragile. Timbre v7 [will introduce](https://github.com/taoensso/roadmap/issues/11) improved native SLF4J support, so you may want to wait for that if you are considering SLF4J).

Example migration steps:

1. Migrate your library's Timbre logging calls to equivalent [`tools.logging`](https://github.com/clojure/tools.logging) calls.
2. Remove your library's Timbre dependency.
3. Advise your users about the dropped dependency, and tell them that they'll now need to opt-in to [use Telemere](https://github.com/taoensso/telemere/wiki/3-Config#toolslogging), [use Timbre](https://taoensso.github.io/timbre/taoensso.timbre.tools.logging.html#var-use-timbre), or use some other logging backend for tools.logging that they prefer.

## Without a facade

In this case you'll need to decide if you want to use Telemere as an [optional](#telemere-as-an-optional-dependency) or [non-optional](#telemere-as-a-non-optional-dependency) dependency.

Will add more info prior to Telemere's [stable v1 release](https://www.taoensso.com/roadmap).
