(defproject com.taoensso/slf4j-telemere "1.0.0-SNAPSHOT"
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
    [[org.clojure/clojure   "1.11.1"]
     [org.slf4j/slf4j-api   "2.0.12"]
     [com.taoensso/telemere "1.0.0-SNAPSHOT"]]}}

  :aliases
  {"deploy-lib" ["do" #_["build-once"] ["deploy" "clojars"] ["install"]]})
