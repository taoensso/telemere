(defproject com.taoensso/slf4j-telemere "1.0.0-beta4"
  :author "Peter Taoussanis <https://www.taoensso.com>"
  :description "Telemere backend/provider for SLF4J API v2"
  :url "https://www.taoensso.com/telemere"

  :license
  {:name "Eclipse Public License - v 1.0"
   :url  "https://www.eclipse.org/legal/epl-v10.html"}

  :java-source-paths ["src/java"]
  :javac-options     ["--release" "11" "-g"] ; Support Java >= v11
  :dependencies      []

  :profiles
  {:provided
   {:dependencies
    [[org.clojure/clojure   "1.11.3"]
     [org.slf4j/slf4j-api   "2.0.13"]
     [com.taoensso/telemere "1.0.0-beta4"]]}

   :dev
   {:plugins
    [[lein-pprint  "1.3.2"]
     [lein-ancient "0.7.0"]]}}

  :aliases
  {"deploy-lib" ["do" #_["build-once"] ["deploy" "clojars"] ["install"]]})
