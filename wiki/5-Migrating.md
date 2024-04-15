# From Timbre

While [Timbre](https://taoensso.com/timbre) will **continue to be maintained and supported** (and will even receive some improvements back-ported from Telemere), most Timbre users will want to at least *consider* updating to Telemere.

Telemere's functionality is a **superset of Timbre**, and offers *many* improvements including:

- Significantly better performance
- A cleaner and more flexible API
- Better support for structured logging
- Much better documentation
- Better built-in handlers
- Easier configuration in many cases
- A more robust architecture, free from all historical constraints

Migrating from Timbre to Telemere should be straightforward **unless you depend on specific/custom appenders** that might not be available for Telemere (yet).

## Checklist

### 1. Appenders

Where Timbre uses the term "appender", Telemere uses the more general "handler". Functionally they're the same thing.

Check which **Timbre appenders** you use, and whether a similar handler is [currently included](./4-Handlers#included-handlers) with Telemere or available via the [community](./8-Community).

If not, you may need to [write something yourself](./4-Handlers#writing-handlers).

This may be easier than it sounds. Remember that signals are just plain Clojure/Script [maps](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-content), and handlers just plain Clojure/Script functions that do something with those maps.

Feel free to [ping me](https://github.com/taoensso/telemere/issues) for assistance, or ask on the [`#telemere` Slack channel](https://clojurians.slack.com/archives/C06ALA6EEUA).

### 2. Imports

Switch your Timbre namespace imports:

```clojure
(ns my-ns
  (:require [taoensso.timbre          :as timbre :refer [...]]) ; Old
  (:require [taoensso.telemere.timbre :as timbre :refer [...]]) ; New
  )
```

The [`taoensso.telemere.timbre`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere.timbre) namespace contains a shim of most of Timbre's API.

Feel free to keep using this shim API **as long as you like**, there's no need to rewrite any of your existing code unless you specifically want to use features that are only possible with Telemere's [signal creators](./1-Getting-started#create-signals), etc.

### 3. Config

You *may* need to update code related to filter config and/or handler management.

This is usually only a few lines of code, and *should* be straightforward.

See section [3-Config](./3-Config) for more info on configuring Telemere.

### 4. Testing

While I believe that the Timbre shim above *should* be robust, it's of course possible that I missed something.

So **please test carefully** before switching to Telemere in production, and **please [report](https://github.com/taoensso/telemere/issues) any issues**! 🙏

In particular - note that Telemere's **handler output** may be **completely different**, so if you have any code/systems (e.g. log aggregators) that depend on the specific output format - **these must also be tested**.

If for any reason your tests are unsuccessful, please don't feel pressured to migrate. Again, I will **continue to maintain and support Timbre**. I have applications running Timbre that I plan to **never migrate** since they're completely stable.

# From tools.logging

This is easy, see [here](./3-Config#clojuretoolslogging).

# From Java logging

This is easy, see [here](./3-Config#java-logging).