(defproject com.taoensso/telemere "1.0.0-beta5"
  :author "Peter Taoussanis <https://www.taoensso.com>"
  :description "Structured telemetry library for Clojure/Script"
  :url "https://www.taoensso.com/telemere"

  :license
  {:name "Eclipse Public License - v 1.0"
   :url  "https://www.eclipse.org/legal/epl-v10.html"}

  :dependencies
  [[com.taoensso/encore "3.107.0"]]

  :test-paths ["test" #_"src"]

  :profiles
  {;; :default [:base :system :user :provided :dev]
   :provided {:dependencies [[org.clojure/clojurescript "1.11.132"]
                             [org.clojure/clojure       "1.11.3"]]}
   :c1.12    {:dependencies [[org.clojure/clojure       "1.12.0-alpha11"]]}
   :c1.11    {:dependencies [[org.clojure/clojure       "1.11.3"]]}
   :c1.10    {:dependencies [[org.clojure/clojure       "1.10.1"]]}

   :graal-tests
   {:source-paths ["test"]
    :main taoensso.graal-tests
    :aot [taoensso.graal-tests]
    :uberjar-name "graal-tests.jar"
    :dependencies
    [[org.clojure/clojure                  "1.11.2"]
     [com.github.clj-easy/graal-build-time "1.0.5"]]}

   :test {:aot [] #_[taoensso.telemere-tests]}
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
    [[org.clojure/test.check              "1.1.1"]
     [org.clojure/tools.logging           "1.3.0"]
     [org.slf4j/slf4j-api                "2.0.13"]
     [com.taoensso/slf4j-telemere   "1.0.0-beta5"]
     #_[org.slf4j/slf4j-simple           "2.0.13"]
     #_[org.slf4j/slf4j-nop              "2.0.13"]

     ;;; For optional handlers
     [io.opentelemetry/opentelemetry-api                           "1.37.0"]
     #_[io.opentelemetry/opentelemetry-sdk-extension-autoconfigure "1.37.0"]
     #_[io.opentelemetry/opentelemetry-exporter-otlp               "1.37.0"]
     [metosin/jsonista   "0.3.8"]
     [com.draines/postal "2.0.5"]]

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
   "test-all"  ["do" ["clean"] ["test-clj"] ["test-cljs"]]})
