Building **observable systems** can be tough, there's no magic solutions. But the benefits can be large, often dramatically **outweighing the costs**.

I'll present some basic/fundamental info here and can expand on this more in future if there's interest.

# General guidance

## Consider what info you (will) need

Try be as **concrete as possible** about what info is (or will be) most valuable about your system. **Get agreement on examples**.

Info may be needed for:

- Debugging
- Business intelligence
- Testing/staging/validation
- Customer support
- Quality management
- Etc.

Try be clear on:

- Who *exactly* will need what information
- In what time frame
- In what form
- And for what purpose (i.e. how will the information **be actionable**)

Always try involve the **final consumer of information** in your design process.

Always try map examples of **expected information** to **expected actionable decisions**, and document these mappings.

The more clear the expected actionable decisions, the more clear the **information's value**.

## Consider data dependencies

Not all data is inherently *useful* (and so valuable).

Be clear on which data is:

- Useful *independently*
- Useful *in the aggregate* (e.g. statistically)
- Useful *in association* with other related data (e.g. while tracing the flow of some logical activity)

Remember to always **question assertions of usefulness**!!

Useful for what **precise purpose** and by **whom**? Can a clear example be provided **mapping information to decisions**?

When aggregates or associations are needed- does a plan exist for producing them from the raw data? Some forethought (e.g. appropriate identifiers and/or indexes) can help avoid big headaches!

## Consider cardinality

Too much low-value data is often actively *harmful*: expensive to process, to store, and to query. Adding noise just interferes with better data, harming your ability to understand your system.

Consider the **quantities** of data that'd best suit your needs *for that data*:

<img src="https://raw.githubusercontent.com/taoensso/telemere/master/imgs/signal-sampling.svg" alt="Telemere sampling" width="640"/>

Telemere offers [extensive filtering](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:filters) capabilities that help you easily express the **conditions** and **quantities** that make sense for your needs. *Use these* both for their effects and as a *form of documentation*.

## Consider evolution

Both data and needs **evolve over time**.

Consider **what is likely subject to change**, and how that might impact your observability needs and therefore design.

Consider the **downstream effects** on data services/storage when something does change.

**Use schemas** when appropriate. Use **version identifiers** when reasonable.

Consider the [differences](https://www.youtube.com/watch?v=oyLBGkS5ICk) between **accretion** and **breakage**.

# Telemere usage tips

- [`log!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#log!) and [`event!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#event!) are both **good general-purpose** signal creators.
  
- **Provide an id** for all signals you create.
  
  Qualified keywords are perfect! Downstream behaviour (e.g. alerts) can then look for these ids rather than messages (which are harder to match and more likely to change).
  
- Keep a documented **index** of all your **signal ids** under version control.
  
  This way you can see all your ids in one place, and precise info on when ids were added/removed/changed.
  
- Use **signal call transforms** to your advantage.
  
  The result of call-side signal transforms is cached and *shared between all handlers* making it an efficient place to modify signals going to >1 handler.
  
- Signal and handler **sampling is multiplicative**.
  
  Both signals and handlers can have independent sample rates, and these MULTIPLY!
  
  If a signal is created with *20%* sampling and a handler handles *50%* of received signals, then *10%* of possible signals will be handled (50% of 20%).
  
  When sampling is active, the final (combined multiplicative) rate is helpfully reflected in each signal's `:sample` rate value ∈ℝ[0,1]. This makes it possible to estimate _unsampled_ cardinalities: for `n` randomly sampled signals matching some criteria, you'd have seen an estimated `Σ(1.0/sample-rate_i)` such signals _without_ sampling, etc.
  
- Transforms can technically return any type, but it's best to return only `nil` or a map. This ensures maximum compatibility with community transforms, handlers, and tools.
  
- Transforms can be used to **filter signals** by returning `nil`.
- Transforms can be used to **split signals**:
  
  Your transforms can *call signal creators* like any other code. Return `nil` after to filter the source signal. Just be aware that new signals will re-enter your handler queue/s as would any other signal - and so may be subject to handling delay and normal handler queue back-pressure.
  
  See also the [`dispatch-signal!`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#dispatch-signal!) util.
  
- Levels can be **arbitrary integers**.
  
  See the value of [`level-aliases`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#level-aliases) to see how the standard keywords (`:info`, `:warn`, etc.) map to integers.
  
- Signals with an `:error` value need not have `:error` level and vice versa.
  
  Telemere doesn't couple the presence of an error value to signal level. This can be handy, but means that you need to be clear on what constitutes an "error signal" for your use case. See also the [`error-signal?`](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#error-signal) util.
  
- Signals may contain arbitrary app-level keys.
  
  Any non-standard [options](https://cljdoc.org/d/com.taoensso/telemere/CURRENT/api/taoensso.telemere#help:signal-options) you give to a signal creator call will be added to the signal it creates:
  
  ```clojure
  (tel/with-signal (tel/log! {:my-key "foo"} "My message")))
  ;; => {:my-key "foo", :kvs {:my-key "foo", ...}, ...}
  ```
  
  Note that all app-level kvs will *also* be available *together* under the signal's `:kvs` key.
  
  App-level kvs are typically *not* included in handler output, so are a great way of providing custom data/opts for use (only) by custom transforms or handlers.
  
- Signal `kind` can be useful in advanced cases.
  
  Every signal has a `kind` key set by default by each signal creator- `log!` calls produce signals with a `:log` kind, etc.
  
  Most folks won't use this, but it can be handy in advanced environments since it allows you to specify a custom taxonomy of signals separate from ids and namespaces.
  
  Signals can be filtered by kind, and minimum levels specified by kind.