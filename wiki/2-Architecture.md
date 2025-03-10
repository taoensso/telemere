Telemere's key function is to help:

1. **Capture information** in your running Clojure/Script programs, and
2. **Facilitate processing** of that information to support **insight**.

Its basic tools:

1. **Signal creators** to conditionally *create* signal maps at points in your code.
2. **Signal handlers** to conditionally *handle* those signal maps (analyse, write to
console/file/queue/db, etc.).

So you *call* a *signal creator* to (conditionally) create a *signal* (map) which is then dispatched to registered _signal handlers_ for (conditional) handling.

This flow is visualized below:

<img src="https://raw.githubusercontent.com/taoensso/telemere/master/imgs/signal-flow.svg" alt="Telemere signal flowchart" width="640"/>

- `A/sync queue` semantics are specified via [handler dispatch options](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:handler-dispatch-options).
- The shared **call transform** cache is super useful when doing signal transformations that are expensive and/or involve side effects (like syncing with another service/db to get a unique tx id, etc.).