(defproject com.taoensso/telemere-slf4j "1.2.1"
  :author "Peter Taoussanis <https://www.taoensso.com>"
  :description "Telemere backend/provider for SLF4J API v2"
  :url "https://www.taoensso.com/telemere"

  :license
  {:name "Eclipse Public License - v 1.0"
   :url  "https://www.eclipse.org/legal/epl-v10.html"}

  :scm {:name "git" :url "https://github.com/taoensso/telemere"}

  :java-source-paths ["src/java"]
  :javac-options ["-source" "8" "-target" "8" "-g"] ; Support Java >= v8
  :dependencies      []

  :profiles
  {:provided
   {:dependencies
    [[org.clojure/clojure   "1.12.5"]
     [org.slf4j/slf4j-api   "2.0.18"]
     [com.taoensso/telemere "1.2.1"]]}

   :dev
   {:plugins
    [[lein-pprint  "1.3.2"]
     [lein-ancient "1.0.0"]]}}

  :aliases
  {"deploy-lib" ["do" #_["build-once"] ["deploy" "clojars"] ["install"]]})
