(ns ^:no-doc taoensso.telemere.handlers
  "Built-in Telemere handlers."
  (:require
   [clojure.string         :as str]
   [taoensso.encore        :as enc :refer [have have?]]
   [taoensso.telemere.impl :as impl]))

(comment
  (remove-ns 'taoensso.telemere.handlers)
  (:api (enc/interns-overview)))
