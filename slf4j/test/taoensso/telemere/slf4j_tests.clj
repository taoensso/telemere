(ns taoensso.telemere.slf4j-tests
  (:require
   [clojure.test :refer [deftest is]]
   [taoensso.telemere.slf4j :as slf4j]))

(deftest marker-mutations
  (let [marker (#'slf4j/est-marker!                "TestCompound" "TestM1")]
    (is (=     (#'slf4j/get-marker-names marker) #{"TestCompound" "TestM1"}))

    (do    (#'slf4j/est-marker!                "TestCompound" "TestM1" "TestM2"))
    (is (= (#'slf4j/get-marker-names marker) #{"TestCompound" "TestM1" "TestM2"}))))
