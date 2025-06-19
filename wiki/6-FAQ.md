# Does Telemere replace Timbre?

> [Timbre](https://www.taoensso.com/timbre) is a pure Clojure/Script logging library, and ancestor of Telemere.

**Yes**, Telemere's functionality is a **superset of Timbre**, and offers *many* improvements over Timbre.

But Timbre will **continue to be maintained and supported**, and will even receive some backwards-compatible improvements back-ported from Telemere.

There is **no pressure to migrate** if you'd prefer not to.

See section [5-Migrating](./5-Migrating#from-timbre) for migration info.

# Why not just update Timbre?

> [Timbre](https://www.taoensso.com/timbre) is a pure Clojure/Script logging library, and ancestor of Telemere.

Why release Telemere as a *new library* instead of just updating Timbre?

Timbre was first released 12+ years ago, and has mostly attempted to keep breaks in that time minimal. Which means that its fundamental design is now 12+ years old.

I've learnt a lot since then, and would write Timbre differently if I were doing it again today. There's many refinements I've wanted to make over the years, but held back both because of the effort involved and because of not wanting to break Timbre users that are happy with it the way it is.

Since receiving [open source funding](https://www.taoensso.com/my-work), undertaking larger projects became feasible - so I decided to experiment with a proof-of-concept rewrite free of all historical constraints.

That eventually grew into Telemere. And I'm happy enough with the result that I feel confident in saying that there's nothing Timbre does better than Telemere, but plenty that Telemere does better than Timbre. Telemere is easier to use, faster, more robust, and significantly more flexible. It offers a better platform for what will be (I hope) the next many years of service.

I will **continue to maintain and support** Timbre for users that are happy with it, though I've also tried to make [migration](./5-Migrating#from-timbre) as easy as possible.

Over time, I also intend to back-port many backwards-compatible improvements from Telemere to Timbre. For one, Telemere's core was actually written as a library that can eventually be used by Telemere, Timbre, and also [Tufte](https://taoensso.com/tufte).

This will eventually ease long-term maintenance, increase reliability, and help provide unified capabilities across all 3.

# Does Telemere replace Tufte?

> [Tufte](https://www.taoensso.com/tufte) is a simple performance monitoring library for Clojure/Script by the author of Telemere.

**No**, Telemere does **not** replace [Tufte](https://www.taoensso.com/tufte). They work great together, and the [upcoming](https://www.taoensso.com/roadmap) Tufte v3 will share the same core as Telemere and offer an **identical API** for managing filters and handlers.

There is **some feature overlap** though since Telemere offers basic performance measurement as part of its tracing features.

For comparison:

- Telemere offers dynamic profiling of a single form to a simple `:runtime-nsecs`.
- Tufte offers dynamic and thread-local profiling of *arbitrary nested forms* to *detailed and mergeable runtime stats*.

Basically, Tufte has much richer performance monitoring capabilities.

They're focused on complementary things. When both are in use:

- Tufte can be used for detailed performance measurement, and
- Telemere can be used for conveying (aggregate) performance information as part of your system's general observability signals.

# Does Telemere work with GraalVM?

> [GraalVM](https://en.wikipedia.org/wiki/GraalVM) is a JDK alternative with ahead-of-time compilation for faster app initialization and improved runtime performance, etc.

**Yes**, this shouldn't be a problem.

# Does Telemere work with Babashka?

> [Babashka](https://github.com/babashka/babashka) is a native Clojure interpreter for scripting with fast startup.

**No**, not currently - though support should be possible with a little work. The current bottleneck is a dependency on [Encore](https://github.com/taoensso/encore), which uses some classes not available in Babashka. With some work it should be possible to remove the dependency, and so also reduce library size.

If there's interest in this, please [upvote](https://github.com/taoensso/roadmap/issues/22) on my open source roadmap.

# Why no format-style messages?

Telemere's message API can do everything that traditional print *or* format style message builders can do but **much more flexibly** - and with pure Clojure/Script (so no arcane pattern syntax).

To coerce/format/prepare args, just use the relevant Clojure/Script utils.

 **Signal messages are always lazy** (as are a signal's `:let` and `:data` [options](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-options)), so you only pay the cost of arg prep and message building *if/when a signal is actually created* (i.e. after filtering, sampling, rate limiting, etc.).

Examples:

```clojure
;; A fixed message (string arg)
(tel/log! "A fixed message") ; %> {:msg "A fixed message"}

;; A joined message (vector arg)
(let [user-arg "Bob"]
  (tel/log! ["User" (str "`" user-arg "`") "just logged in!"]))
;; %> {:msg_ "User `Bob` just logged in!` ...}

;; With arg prep
(let [user-arg "Bob"
      usd-balance-str "22.4821"]

  (tel/log!
    {:let
     [username (clojure.string/upper-case user-arg)
      usd-balance (parse-double usd-balance-str)]

     :data
     {:username    username
      :usd-balance usd-balance}}

    ["User" username "has balance:" (str "$" (Math/round usd-balance))]))

;; %> {:msg "User BOB has balance: $22" ...}

(tel/log! (str "This message " "was built " "by `str`"))
;; %> {:msg "This message was built by `str`"}

(tel/log! (format "This message was built by `%s`" "format"))
;; %> {:msg "This message was built by `format`"}
```

Note that you can even use `format` or any other formatter/s of your choice. Your signal message is the result of executing code, so build it however you want.

See also [`msg-skip`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#msg-skip) and [`msg-splice`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#msg-splice) for some handy utils.

# How to use Telemere from a library?

See section [9-Authors](./9-Authors.md).

# How does Telemere compare to μ/log?

> [μ/log](https://github.com/BrunoBonacci/mulog) is an excellent "micro-logging library" for Clojure that shares many of the same capabilities and objectives as Telemere.

Some **similarities** between Telemere and μ/log:

- Both emphasize **structured data** rather than string messages
- Both offer **tracing** to understand (nested) program flow
- Both offer a (nested) **context** mechanism for arb application state
- Both are **fast** and offer **async handling**
- Both offer a variety of **handlers** and are designed for ease of use

Some particular **strengths of μ/log** that I'm aware of:

- More **established/mature**
- Wider **range of handlers** (incl. Kafka, Kinesis, Prometheus, Zipkin, etc.)
- More **community resources** (videos, guides, users, etc.)
- **Smaller code** base (Telemere currently depends on [Encore](https://github.com/taoensso/encore))
- There may be others!

Some particular **strengths of Telemere**:

- Both **Clj and Cljs support** (μ/log is Clj only)
- Rich **filtering capabilities** (see [`help:filters`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:filters)) incl. compile-time elision
- Rich **dispatch control** (see [`help:handler-dispatch-options`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:handler-dispatch-options))
- Rich **environmental config** (see [`help:environmental-config`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:environmental-config)) for all platforms
- Detailed **handler stats** (see [`get-handlers-stats`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#get-handlers-stats))
- Single **unified API** for all telemetry and traditional logging needs (see [`help:signal-creators`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-creators))
- Lazy `:let`, `:data`, `:msg`, `:do` - evaluated only **after filtering**
- Extensive [in-IDE documentation](./1-Getting-started#internal-help)

**My subjective thoughts**:

μ/log is an awesome, well-designed library with quality documentation and a solid API. It's **absolutely worth checking out** - you may well prefer it to Telemere!

The two libraries have many shared capabilities and objectives.

Ultimately I wrote Telemere because:

1. I have some particular needs, including very complex and large-scale applications that benefit from the kind of flexibility that Telemere offers re: filtering, dispatch, environmental config, lazy (post-filter) evaluation, etc.
2. I have some particular tastes re: my ideal API.
3. I wanted something that integrated particularly well with [Tufte](https://taoensso.com/tufte) and could share an identical API for filtering, handlers, etc.
4. I wanted a modern replacement for [Timbre](https://www.taoensso.com/timbre) users that offered a superset of its functionality and an [easy migration path](./5-Migrating#from-timbre).

# Why the unusual arg order for `event!`?

For their 2 arg arities, every standard signal creator _except_ [event!](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#event!) takes an opts map as its _first_ argument.

Why the apparent inconsistency?

It's an intentional trade-off. `event!` is unique in 3x ways:

1. Its primary argument is typically very short (just an id keyword).
2. Its primary argument never depends on `:let` bindings.
3. Its opts typically include long or even multi-lined `:data`.

If `event!` shared the same arg order as other signal creators, the common case would be something like `(event! {:data <multi-line>} ::dangling-id)` which gets unnecessarily awkward and doesn’t read well IMO. I want to know what event we’re talking about, before you tell me about the associated data.

In contrast, creators like `log!` both tend to have a large/r primary argument (message) - and their primary argument often depends on `:let` bindings - e.g. `(log! {:id ::my-id, :let […]} <message depending on let bindings>)`. In these cases it reads much clearer to go left->right. We start with an id, specify some data, then use that data to construct a message.

So basically the choice in trade-off was:

1. Prefer **consistency**, or
2. Prefer **ergonomics** of the common case usage

I went with option 2 for several reasons:

- There _is_ actually consistency, it’s just not as obvious - the typically-larger argument always goes _last_.
- Most IDEs generally do a good job of reminding about the arg order.
- The same trade-off may come up again in future for other new signal kinds, and I prefer that we adopt the pattern of optimising for common-case ergonomics.
- One can always easily call `signal!` directly - this takes a single map arg, so lets you easily specify all args in preferred order. (I tend to exclusively use `signal!` myself since I prefer this flexibility).

If there’s popular demand, I’d also be happy to add something like `ev!` which could choose the alternative trade-off. Though I’d recommend folks try `event!` as-is first, since I think the initial aversion/surprise might wear off with use.

# Other questions?

Please [open a Github issue](https://github.com/taoensso/telemere/issues) or ping on Telemere's [Slack channel](https://www.taoensso.com/telemere/slack). I'll regularly update the FAQ to add common questions. - [Peter](https://www.taoensso.com)
