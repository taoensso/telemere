# Does Telemere replace Timbre?

> [Timbre](https:/www.taoensso.com/timbre) is a pure Clojure/Script logging library, and ancestor of Telemere.

**Yes**, Telemere's functionality is a **superset of Timbre**, and offers *many* improvements over Timbre.

But Timbre will **continue to be maintained and supported**, and will even receive some backwards-compatible improvements back-ported from Telemere.

There is **no pressure to migrate** if you'd prefer not to.

See section [5-Migrating](./5-Migrating#from-timbre) for migration info.

# Why not just update Timbre?

> [Timbre](https:/www.taoensso.com/timbre) is a pure Clojure/Script logging library, and ancestor of Telemere.

Why release Telemere as a *new library* instead of just updating Timbre?

Timbre was first released 12+ years ago, and has mostly attempted to keep breaks in that time minimal. Which means that its fundamental design is now 12+ years old.

I've learnt a lot since then, and would write Timbre differently if I were doing it again today. There's many improvements I've wanted to make over the years, but held back both because of the effort involved and because of not wanting to break Timbre users that are happy with it the way it is.

Since receiving [open source funding](https://www.taoensso.com/my-work), undertaking larger projects became feasible - so I decided to experiment with a proof-of-concept rewrite free of all historical constraints.

That eventually grew into Telemere.

I will **continue to maintain and support** Timbre for users that are happy with it, though I've also tried to make [migration](./5-Migrating#from-timbre) as easy as possible.

Over time, I also intend to back-port many backwards-compatible improvements from Telemere to Timbre. For one, Telemere's core was actually written as a library that will eventually be used by Telemere, Timbre, and also [Tufte](https://taoensso.com/tufte).

This will eventually ease long-term maintenance, increase reliability, and help provide unified capabilities across all 3.

# Does Telemere replace Tufte?

> [Tufte](https:/www.taoensso.com/tufte) is a simple performance monitoring library for Clojure/Script by the author of Telemere.

**No**, Telemere does **not** replace [Tufte](https:/www.taoensso.com/tufte). They work great together, and the [upcoming](https:/www.taoensso.com/roadmap) Tufte v4 will share the same core as Telemere and offer an **identical API** for managing filters and handlers.

There is **some feature overlap** though since Telemere offers basic performance measurement as part of its tracing features.

For comparison:

- Telemere offers dynamic profiling of a single form to a simple `:runtime-nsecs`.
- Tufte offers dynamic and thread-local profiling of *arbitrary nested forms* to *detailed and mergeable runtime stats*.

Basically, Tufte has much richer performance monitoring capabilities.

They're focused on complementary things. When both are in use:

- Tufte can be used for detailed performance measurement, and
- Telemere can be used for conveying (aggregate) performance information as part of your system's general observability signals.

# Other questions?

Please [open a Github issue](https://github.com/taoensso/telemere/issues). I'll regularly update the FAQ to add common questions.