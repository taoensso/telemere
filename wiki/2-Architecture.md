Telemere's key function is to help:

1. **Capture information** in your running Clojure/Script programs, and
2. **Facilitate processing** of that information to support **insight**.

Its basic tools:

1. **Signal creators** to conditionally *create* signal maps at points in your code.
2. **Signal handlers** to conditionally *handle* those signal maps (analyse, write to
console/file/queue/db, etc.).

So you *call* a *signal creator* to (conditionally) create a *signal* (map) which is then dispatched to registered _signal handlers_ for (conditional) handling.

This flow is described by [`help:signal-flow`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-flow), and is visualized below:

<img src="https://raw.githubusercontent.com/taoensso/telemere/master/imgs/signal-flow.svg" alt="Telemere signal flowchart" width="640"/>

- `A/sync queue` semantics are configured per handler when calling [`add-handler!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#add-handler!). Default semantics are: async with a dropping buffer, and 1 handler thread.
- The shared **signal middleware cache** is super useful when doing signal transformations that are expensive and/or involve side effects (like syncing with another service/db to get a unique tx id, etc.).

For more info see:

| Var                                                                                                                         | Help with                                     |
| :-------------------------------------------------------------------------------------------------------------------------- | :-------------------------------------------- |
| [`help:signal-creators`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-creators)     | List of signal creators                       |
| [`help:signal-options`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-options)       | Options for signal creators                   |
| [`help:signal-content`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content)       | Signal map content                            |
| [`help:signal-flow`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-flow)             | Ordered flow from signal creation to handling |
| [`help:signal-filters`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-filters)       | API for configuring signal filters            |
| [`help:signal-handlers`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-handlers)     | API for configuring signal handlers           |
