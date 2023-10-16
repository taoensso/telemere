# Setup

Add the [relevant dependency](../#latest-releases) to your project:

```clojure
Leiningen: [com.taoensso/telemere               "x-y-z"] ; or
deps.edn:   com.taoensso/telemere {:mvn/version "x-y-z"}
```

And setup your namespace imports:

```clojure
(ns my-app (:require [taoensso.telemere :as tm]))
```
