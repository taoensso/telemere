(ns taoensso.telemere-tests
  (:require
   [clojure.test      :as test :refer [deftest testing is]]
   [taoensso.encore   :as enc]
   [taoensso.telemere :as telemere]))

(comment
  (remove-ns      'taoensso.telemere-tests)
  (test/run-tests 'taoensso.telemere-tests))

;;;;

(deftest _test (is (= 1 1)))

;;;;

#?(:cljs (test/run-tests))
