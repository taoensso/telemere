 <img src="https://raw.githubusercontent.com/taoensso/telemere/master/imgs/telemere-logo.svg" alt="Telemere logo" width="360"/>

# Introduction

Telemere is a **structured telemetry** library and next-generation replacement for [Timbre](https://www.taoensso.com/timbre). It helps enable the creation of Clojure/Script systems that are highly **observable**, **robust**, and **debuggable**.

Its key function is to help:

1. **Capture data** in your running Clojure/Script programs, and
2. **Facilitate processing** of that data into **useful information / insight**.

## Signals

The basic unit of data in Telemere is the **signal**.

Signals include **traditional log messages**, **structured log messages**, and **events**. Telemere doesn't make a hard distinction between these - *they're all just signals* with [various attributes](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content).

And they're represented by plain **Clojure/Script maps** with those attributes (keys).

Fundamentally **all signals**:

- Occur or are observed at a particular **location** in your code (file, namespace, line, column).
- Occur or are observed *within* a particular **program state** / context.
- Convey something of value *about* that **program state** / context.

Signals may be *independently* valuable, valuable *in the aggregate* (e.g. statistically), or valuable *in association* with other related signals (e.g. while tracing the flow of some logical activity).

## Functionality

The basic tools of Telemere are:

1. **Signal creators** to conditionally *create* signal maps at points in your code.
2. **Signal handlers** to conditionally *handle* those signal maps (analyse, write to
console/file/queue/db, etc.).

This is just a generalization of **traditional logging** which:

- Conditionally creates **message strings** at points in your code.
- Usually *dumps* those message strings somewhere for future parsing by human eyes or automated tools.

## Data types and structures

The parsing of traditional log messages is often expensive, fragile, and lossy. So a key principle of **structured logging** is to **avoid parsing**, by instead **preserving data types and structures** whenever possible.

Telemere embraces this principle by making such preservation *natural and convenient*.

## Noise reduction

Not all data is equally valuable.

Too much low-value data is often actively *harmful*: expensive to process, to store, and to query. Adding noise just interferes with better data, harming your ability to understand your system.

Telemere embraces this principle by making **effective filtering** likewise *natural and convenient*:

<img src="https://raw.githubusercontent.com/taoensso/telemere/master/imgs/signal-sampling.svg" alt="Telemere sampling" width="640"/>

> Telemere uses the term **filtering** as the superset of both random sampling and other forms of data exclusion/reduction.

## Structured telemetry

To conclude- Telemere handles **structured and traditional logging**, **tracing**, and **basic performance monitoring** with a simple unified API that:

- Preserves data types and structures with **rich signals**, and
- Offers effective noise reduction with **signal filtering**.

Its name is a combination of _telemetry_ and _telomere_:

> *Telemetry* derives from the Greek *tele* (remote) and *metron* (measure). It refers to the collection of *in situ* (in position) data, for transmission to other systems for monitoring/analysis. *Logs* are the most common form of software telemetry. So think of telemetry as the *superset of logging-like activities* that help monitor and understand (software) systems.

> *Telomere* derives from the Greek *télos* (end) and *méros* (part). It refers to a genetic feature commonly found at the end of linear chromosomes that helps to protect chromosome integrity.

# Setup

Add the [relevant dependency](../#latest-releases) to your project:

```clojure
Leiningen: [com.taoensso/telemere               "x-y-z"] ; or
deps.edn:   com.taoensso/telemere {:mvn/version "x-y-z"}
```

And setup your namespace imports:

```clojure
(ns my-app (:require [taoensso.telemere :as t]))
```

# Default config

Telemere is configured sensibly out-the-box.  
See section [3-Config](./3-Config) for customization.

**Default minimum level**: `:info` (signals with lower levels will no-op).

**Default signal handlers**:

> Signal handlers process created signals to *do something with them* (analyse them, write them to console/file/queue/db, etc.)

| Platform | Condition | Handler                                                                                                                                                     |
| -------- | --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Clj      | Always    | [Console handler](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:console) that prints signals to `*out*` or `*err*`.      |
| Cljs     | Always    | [Console handler](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#handler:console) that prints signals to the **browser console**. |

**Default signal intakes**:

> Telemere can create signals from relevant **external API calls**, etc.

| Platform | Condition                                                                                                                                                                                                                                                | Signals from                                                                     |
| -------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| Clj      | [SLF4J API](https://mvnrepository.com/artifact/org.slf4j/slf4j-api) and [Telemere SLF4J backend](https://clojars.org/com.taoensso/slf4j-telemere) present                                                                                                | [SLF4J](https://www.slf4j.org/) logging calls.                                   |
| Clj      | [clojure.tools.logging](https://mvnrepository.com/artifact/org.clojure/tools.logging) present and [`tools-logging->telemere!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.tools-logging#tools-logging-%3Etelemere!) called | [clojure.tools.logging](https://github.com/clojure/tools.logging) logging calls. |
| Clj      | [`streams->telemere!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#streams-%3Etelemere!) called                                                                                                                             | Output to `System/out` and `System/err` streams.                                 |

Run [`check-intakes`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#check-intakes) to help verify/debug:

```clojure
(check-intakes) ; =>
{:tools-logging {:present? false}
 :slf4j         {:sending->telemere? true,  :telemere-receiving? true}
 :system/out    {:sending->telemere? false, :telemere-receiving? false}
 :system/err    {:sending->telemere? false, :telemere-receiving? false}}
```

#  Usage

## Create signals

Use whichever signal creator is most convenient for your needs:

| Name | Signal kind | Main arg | Optional arg | Returns |
|:-- | :-- | :-- | :-- | :-- |
| [`log!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#log!) | `:log` | `msg` | `opts`/`level` | Signal allowed?
| [`event!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#event!) | `:event` | `id` | `opts`/`level` | Signal allowed?
| [`error!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#error!) | `:error` | `error` | `opts`/`id` | Given error
| [`trace!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#trace!) | `:trace` | `form` | `opts`/`id` | Form result
| [`spy!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#spy!) | `:spy` | `form` | `opts`/`level` | Form result
| [`catch->error!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#catch-%3Eerror!) | `:error` | `form` | `opts`/`id` | Form value or given fallback
| [`signal!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#signal!) | `<arb>` | `opts` | - | Depends on opts
 
- See [`help:signal-creators`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-creators) for more info on signal creators.
- See [`help:signal-options`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-options) for signal options (shared by all creators).
- See relevant docstrings (links above) for usage info.
- See [examples.cljc](https://github.com/taoensso/telemere/blob/master/examples.cljc) for REPL-ready examples.

## Check signals

Use the [`with-signal`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#with-signal) or (advanced) [`with-signals`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#with-signals) utils to help test/debug the signals that you're creating:

```clojure
(t/with-signal
  (t/log!
    {:let  [x "x"]
     :data {:x x}}
    ["My msg:" x]))

;; => {:keys [ns inst data msg_ ...]} ; The signal
```

- [`with-signal`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#with-signal) will return the **last** signal created by the given form.
- [`with-signals`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#with-signals) will return **all** signals created by the given form.

Both have several options, see their docstrings (links above) for details.

## Filter signals

A signal will be provided to a handler iff ALL of the following are true:

  1. Signal **creation** is allowed by **compile-time** filter config
  2. Signal **creation** is allowed by **runtime** filter config
  3. Signal **handling** is allowed by **handler** filter config
  4. Signal  **middleware** does not suppress the signal (return nil)
  5. Handler **middleware** does not suppress the signal (return nil)

For 1-3, filtering may depend on (in order):
  Sample rate → namespace → kind → id → level → when form/fn → rate limit

Quick examples of some basic filtering:

```clojure
(t/set-min-level! :info) ; Set global minimum level
(t/with-signal (t/event! ::my-id1 :info))  ; => {:keys [inst id ...]}
(t/with-signal (t/event! ::my-id1 :debug)) ; => nil (signal not allowed)

(t/with-min-level :trace ; Override global minimum level
  (t/with-signal (t/event! ::my-id1 :debug))) ; => {:keys [inst id ...]}

;; Deny all signals in matching namespaces
(t/set-ns-filter! {:deny "some.nosy.namespace.*"})
```

- Filtering is always O(1), except for rate limits which are O(n_windows).
- Signal and handler sampling is *multiplicative*: if a signal is created with *20%* sampling and a handler handles *50%* of received signals, then *10%* of possible signals will be handled. This multiplicative rate is helpfully reflected in each signal's final `:sample-rate` value.
- See [`help:signal-flow`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-flow) for internal docs on signal flow.
- See section [2-Architecture](./2-Architecture) for a flowchart / visual aid.

Runtime signal filters can be set with:

| Global                                                                                                          | Dynamic                                                                                                         | Filters by                                                |
| :-------------------------------------------------------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------- | :-------------------------------------------------------- |
| [`set-kind-filter!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#set-kind-filter!) | [`with-kind-filter`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#with-kind-filter) | Signal kind (`:log`, `:event`, etc.)                      |
| [`set-ns-filter!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#set-ns-filter!)     | [`with-ns-filter`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#with-ns-filter)     | Signal namespace                                          |
| [`set-id-filter!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#set-id-filter!)     | [`with-id-filter`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#with-id-filter)     | Signal id                                                 |
| [`set-min-level`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#set-min-level)       | [`with-min-level`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#with-min-level)     | Signal level (minimum can be specified by kind and/or ns) |

- See relevant docstrings (links above) for usage info.
- Compile-time filters are set with environmental config, see section [3-Config](./3-Config).

# Internal help

Telemere includes extensive internal help docstrings:

| Var                                                                                                                         | Help with                                     |
| :-------------------------------------------------------------------------------------------------------------------------- | :-------------------------------------------- |
| [`help:signal-creators`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-creators)     | List of signal creators                       |
| [`help:signal-options`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-options)       | Options for signal creators                   |
| [`help:signal-content`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content)       | Signal map content                            |
| [`help:signal-flow`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-flow)             | Ordered flow from signal creation to handling |
| [`help:signal-filters`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-filters)       | API for configuring signal filters            |
| [`help:signal-handlers`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-handlers)     | API for configuring signal handlers           |
| [`help:signal-formatters`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-formatters) | Signal formatters for use by handlers         |
