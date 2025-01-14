(defproject com.taoensso/telemere "1.0.0-SNAPSHOT"
  :author "Peter Taoussanis <https://www.taoensso.com>"
  :description "Structured telemetry library for Clojure/Script"
  :url "https://www.taoensso.com/telemere"

  :license
  {:name "Eclipse Public License - v 1.0"
   :url  "https://www.eclipse.org/legal/epl-v10.html"}

  :scm {:name "git" :url "https://github.com/taoensso/telemere"}

  :dependencies
  [[com.taoensso/encore "3.132.0"]]

  :test-paths ["test" #_"src"]

  :profiles
  {;; :default [:base :system :user :provided :dev]
   :provided {:dependencies [[org.clojure/clojurescript "1.11.132"]
                             [org.clojure/clojure       "1.11.4"]]}
   :c1.12    {:dependencies [[org.clojure/clojure       "1.12.0"]]}
   :c1.11    {:dependencies [[org.clojure/clojure       "1.11.4"]]}
   :c1.10    {:dependencies [[org.clojure/clojure       "1.10.3"]]}

   :graal-tests
   {:source-paths ["test"]
    :main taoensso.graal-tests
    :aot [taoensso.graal-tests]
    :uberjar-name "graal-tests.jar"
    :dependencies
    [[org.clojure/clojure                  "1.11.4"]
     [com.github.clj-easy/graal-build-time "1.0.5"]]}

   :test    {:aot [] #_[taoensso.telemere-tests]}
   :ott-on  {:jvm-opts ["-Dtaoensso.telemere.otel-tracing=true"]}
   :ott-off {:jvm-opts ["-Dtaoensso.telemere.otel-tracing=false"]}
   :dev
   {:jvm-opts
    ["-server"
     "-Dtaoensso.elide-deprecated=true"
     "-Dclojure.tools.logging.to-telemere=true"]

    :global-vars
    {*warn-on-reflection* true
     *assert*             true
     *unchecked-math*     false #_:warn-on-boxed}

    :dependencies
    [[org.clojure/core.async        "1.7.701"]
     [org.clojure/test.check        "1.1.1"]
     [org.clojure/tools.logging     "1.3.0"]
     [org.slf4j/slf4j-api           "2.0.16"]
     [com.taoensso/telemere-slf4j   "1.0.0-SNAPSHOT"]
     #_[org.slf4j/slf4j-simple      "2.0.16"]
     #_[org.slf4j/slf4j-nop         "2.0.16"]
     #_[io.github.paintparty/bling  "0.4.2"]

     ;;; For optional handlers
     [io.opentelemetry/opentelemetry-api                         "1.45.0"]
     [io.opentelemetry/opentelemetry-sdk-extension-autoconfigure "1.45.0"]
     [io.opentelemetry/opentelemetry-exporter-otlp               "1.45.0"]
     #_[io.opentelemetry/opentelemetry-exporters-jaeger           "0.9.1"]
     [metosin/jsonista       "0.3.13"]
     [com.draines/postal     "2.0.5"]
     [org.julienxx/clj-slack "0.8.3"]]

    :plugins
    [[lein-pprint                     "1.3.2"]
     [lein-ancient                    "0.7.0"]
     [lein-cljsbuild                  "1.1.8"]
     [com.taoensso.forks/lein-codox "0.10.11"]]

    :codox
    {:language #{:clojure :clojurescript}
     :base-language :clojure}}}

  :cljsbuild
  {:test-commands {"node" ["node" "target/test.js"]}
   :builds
   [{:id :main
     :source-paths ["src"]
     :compiler
     {:output-to "target/main.js"
      :optimizations :advanced}}

    {:id :test
     :source-paths ["src" "test"]
     :compiler
     {:output-to "target/test.js"
      :target :nodejs
      :optimizations :simple}}]}

  :aliases
  {"start-dev"  ["with-profile" "+dev" "repl" ":headless"]
   "build-once" ["do" ["clean"] ["cljsbuild" "once"]]
   "deploy-lib" ["do" ["build-once"] ["deploy" "clojars"] ["install"]]

   "test-clj"  ["with-profile" "+c1.12:+c1.11:+c1.10" "test"]
   "test-cljs" ["with-profile" "+c1.12" "cljsbuild"   "test"]

   "test-clj-ott-off" ["with-profile" "+ott-off" "test-clj"]
   "test-all"  ["do" ["clean"] ["test-clj"] ["test-clj-ott-off"] ["test-cljs"]]})
