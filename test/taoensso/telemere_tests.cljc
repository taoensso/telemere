(ns taoensso.telemere-tests
  (:require
   [clojure.test            :as test :refer [deftest testing is]]
   [taoensso.encore         :as enc  :refer [throws? submap?]]
   [taoensso.encore.signals :as sigs]
   [taoensso.telemere       :as tel]
   [taoensso.telemere.impl  :as impl]
   #?(:clj [taoensso.telemere.slf4j :as slf4j])
   #?(:clj [clojure.tools.logging   :as ctl]))

  #?(:cljs
     (:require-macros
      [taoensso.telemere-tests :refer [sig! ws ws*]])))

(comment
  (remove-ns      'taoensso.telemere-tests)
  (test/run-tests 'taoensso.telemere-tests))

;;;; Utils

(enc/defaliases
  #?(:clj     {:alias sig! :src impl/signal!})
  #?(:default {:alias sm?  :src enc/submap?}))

#?(:clj (defmacro ws  [form] `(impl/-with-signal (fn [] ~form) {})))
#?(:clj (defmacro ws* [form] `(impl/-with-signal (fn [] ~form) {:trap-errors? true})))
#?(:clj (defmacro wsv [form] `(impl/-with-signal (fn [] ~form) {:force-msg? true, :return :signal})))

(do
  (def ex1      (ex-info "TestEx" {}))
  (def ex1?     #(=         %  ex1))
  (def ex1-rv?  #(= (:error %) ex1))
  (def ex1-pred (enc/pred ex1?)))

(def ^:dynamic *dynamic-var* nil)

;;;;

#?(:clj
   (deftest _parse-msg-form
     (let [pmf @#'impl/parse-msg-form]
       [(is (= (pmf '["foo"]))      '"foo")
        (is (= (pmf '["foo" "bar"]) '(clojure.core/delay (taoensso.telemere.impl/signal-msg ["foo" "bar"]))))
        (is (= (pmf 'my-symbol)     'my-symbol))])))

(deftest _impl-misc
  ;; Note lots of low-level signal/filtering tests in `taoensso.encore`
  [(is (= (impl/signal-msg
            ["x" "y" nil ["z1" nil "z2" "z3"]
             (impl/msg-splice ["s1" nil "s2" "s3" (impl/msg-skip) "s4"])
             (impl/msg-splice nil)
             (impl/msg-skip) :kw])

         "xynil[\"z1\" nil \"z2\" \"z3\"]s1nils2s3s4:kw"))])

(deftest _signal-macro
  [(is (=   (ws (sig! {:level :info, :elide? true               })) [nil nil]) "With compile-time elision")
   (is (=   (ws (sig! {:level :info, :elide? true,  :run (+ 1 2)})) [3   nil]) "With compile-time elision, run-form")
   (is (=   (ws (sig! {:level :info, :allow? false              })) [nil nil]) "With runtime suppression")
   (is (=   (ws (sig! {:level :info, :allow? false, :run (+ 1 2)})) [3   nil]) "With runtime suppression, run-form")

   (is (->>     (sig! {:level :info, :elide? true,  :run (throw ex1)}) (throws? :ex-info "TestEx")) "With compile-time elision, throwing run-form")
   (is (->>     (sig! {:level :info, :allow? false, :run (throw ex1)}) (throws? :ex-info "TestEx")) "With runtime suppression,  throwing run-form")

   (let [[rv1 sv1] (ws (sig! {:level :info              }))
         [rv2 sv2] (ws (sig! {:level :info, :run (+ 1 2)}))]

     [(is (= rv1 true)) (is (sm? sv1 {:ns "taoensso.telemere-tests", :level :info, :run-form nil,      :run-value nil, :runtime-nsecs nil}))
      (is (= rv2    3)) (is (sm? sv2 {:ns "taoensso.telemere-tests", :level :info, :run-form '(+ 1 2), :run-value 3,   :runtime-nsecs (enc/pred nat-int?)}))])

   (testing "Nested signals"
     (let [[[inner-rv inner-sv] outer-sv] (ws (sig! {:level :info, :run (ws (sig! {:level :warn, :run "inner-run"}))}))]
       [(is (= inner-rv "inner-run"))
        (is (sm? inner-sv {:level :warn, :run-value "inner-run"}))
        (is (sm? outer-sv {:level :info  :run-value [inner-rv inner-sv]}))]))

   (testing "Instants"
     (let [[_ sv1] (ws (sig! {:level :info                             }))
           [_ sv2] (ws (sig! {:level :info, :run (reduce + (range 1e6))}))
           [_ sv3] (ws (sig! {:level :info, :run (reduce + (range 1e6))
                              :instant ; Allow custom instant
                              #?(:clj  java.time.Instant/EPOCH
                                 :cljs (js/Date. 0))}))]

       [(let [{start :instant, end :end-instant} sv1]
          [(is (enc/inst? start))
           (is (enc/inst? end))
           (is   (= start end))])

        (let [{start :instant, end :end-instant} sv2]
          [(is (enc/inst?  start))
           (is (enc/inst?  end))
           (is (> (inst-ms end) (inst-ms start)))])

        (let [{start :instant, end :end-instant} sv3]
          [(is (enc/inst?  start))
           (is (enc/inst?  end))
           (is (= (inst-ms start) 0)               "Respect custom instant")
           (is (> (inst-ms end)   (inst-ms start)) "End instant is start + runtime-nsecs")
           (is (< (inst-ms end)   1e6)             "End instant is start + runtime-nsecs")])]))

   (testing "User opts assoced directly to signal"
     (let [[rv sv] (ws (sig! {:level :info, :my-opt1 "v1", :my-opt2 "v2"}))]
       (is         (sm? sv   {:level :info, :my-opt1 "v1", :my-opt2 "v2"}))))

   (testing "`:msg` basics"
     (let [c         (enc/counter)
           [rv1 sv1] (ws (sig! {:level :info, :run (c), :msg             "msg1"}))        ; No     delay
           [rv2 sv2] (ws (sig! {:level :info, :run (c), :msg        [    "msg2:" (c)]}))  ; Auto   delay
           [rv3 sv3] (ws (sig! {:level :info, :run (c), :msg (delay (str "msg3:" (c)))})) ; Manual delay
           [rv4 sv4] (ws (sig! {:level :info, :run (c), :msg        (str "msg4:" (c))}))  ; No     delay
           [rv5 sv5] (ws (sig! {:level :info, :run (c), :msg        (str "msg5:" (c)), :allow? false}))]

       [(is (= rv1 0)) (is (=  (:msg_ sv1) "msg1"))
        (is (= rv2 1)) (is (= @(:msg_ sv2) "msg2:6"))
        (is (= rv3 2)) (is (= @(:msg_ sv3) "msg3:7"))
        (is (= rv4 3)) (is (=  (:msg_ sv4) "msg4:4"))
        (is (= rv5 5)) (is (=  (:msg_ sv5) nil))
        (is (= @c  8)  "5x run + 3x message (1x suppressed)")]))

   (testing "`:data` basics"
     (vec
       (for [dk [:data :my-opt]] ; User opts share same behaviour as data
         (let [c          (enc/counter)
               [rv1 sv1] (ws (sig! {:level :info, :run (c), dk        {:c1 (c)}}))
               [rv2 sv2] (ws (sig! {:level :info, :run (c), dk (delay {:c2 (c)})}))
               [rv3 sv3] (ws (sig! {:level :info, :run (c), dk        {:c3 (c)},  :allow? false}))
               [rv4 sv4] (ws (sig! {:level :info, :run (c), dk (delay {:c4 (c)}), :allow? false}))
               [rv5 sv5] (ws (sig! {:level :info, :run (c), dk        [:c5 (c)]}))
               [rv6 sv6] (ws (sig! {:level :info, :run (c), dk (delay [:c6 (c)])}))]

           [(is (= rv1 0)) (is (=        (get sv1 dk)  {:c1 1}))
            (is (= rv2 2)) (is (= (force (get sv2 dk)) {:c2 8}))
            (is (= rv3 3)) (is (=        (get sv3 dk)  nil))
            (is (= rv4 4)) (is (= (force (get sv4 dk)) nil))
            (is (= rv5 5)) (is (=        (get sv5 dk)  [:c5 6]) "`:data` can be any type")
            (is (= rv6 7)) (is (= (force (get sv6 dk)) [:c6 9]) "`:data` can be any type")
            (is (= @c  10) "6x run + 4x data (2x suppressed)")]))))

   (testing "`:let` basics"
     (let [c         (enc/counter)
           [rv1 sv1] (ws (sig! {:level :info, :run (c), :let [_ (c)]}))
           [rv2 sv2] (ws (sig! {:level :info, :run (c), :let [_ (c)], :allow? false}))
           [rv3 sv3] (ws (sig! {:level :info, :run (c), :let [_ (c)]}))]

       [(is (= rv1 0))
        (is (= rv2 2))
        (is (= rv3 3))
        (is (= @c  5) "3x run + 2x let (1x suppressed)")]))

   (testing "`:let` + `:msg`"
     (let [c         (enc/counter)
           [rv1 sv1] (ws (sig! {:level :info, :run (c), :let [n (c)], :msg             "msg1"}))              ; No     delay
           [rv2 sv2] (ws (sig! {:level :info, :run (c), :let [n (c)], :msg        [    "msg2:" n ":" (c)]}))  ; Auto   delay
           [rv3 sv3] (ws (sig! {:level :info, :run (c), :let [n (c)], :msg (delay (str "msg3:" n ":" (c)))})) ; Manual delay
           [rv4 sv4] (ws (sig! {:level :info, :run (c), :let [n (c)], :msg        (str "msg4:" n ":" (c))}))  ; No     delay
           [rv5 sv5] (ws (sig! {:level :info, :run (c), :let [n (c)], :msg        (str "msg5:" n ":" (c)), :allow? false}))]

       [(is (= rv1 0)) (is (=  (:msg_ sv1) "msg1"))
        (is (= rv2 2)) (is (= @(:msg_ sv2) "msg2:3:10"))
        (is (= rv3 4)) (is (= @(:msg_ sv3) "msg3:5:11"))
        (is (= rv4 6)) (is (=  (:msg_ sv4) "msg4:7:8"))
        (is (= rv5 9)) (is (=  (:msg_ sv5) nil))
        (is (= @c  12) "5x run + 4x let (1x suppressed) + 3x msg (1x suppressed)")]))

   (testing "`:do` + `:let` + `:data`/`:my-opt`"
     (vec
       (for [dk [:data :my-opt]]
         (let [c         (enc/counter)
               [rv1 sv1] (ws (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk        {:n n, :c1 (c)}}))
               [rv2 sv2] (ws (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk (delay {:n n, :c2 (c)})}))
               [rv3 sv3] (ws (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk        {:n n, :c3 (c)},  :allow? false}))
               [rv4 sv4] (ws (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk (delay {:n n, :c4 (c)}), :allow? false}))
               [rv5 sv5] (ws (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk        [:n n, :c5 (c)]}))
               [rv6 sv6] (ws (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk (delay [:n n, :c6 (c)])}))]

           [(is (= rv1 0))  (is (=        (get sv1 dk)  {:n 2, :c1 3}))
            (is (= rv2 4))  (is (= (force (get sv2 dk)) {:n 6, :c2 16}))
            (is (= rv3 7))  (is (=        (get sv3 dk)  nil))
            (is (= rv4 8))  (is (= (force (get sv4 dk)) nil))
            (is (= rv5 9))  (is (=        (get sv5 dk)  [:n 11, :c5 12]))
            (is (= rv6 13)) (is (= (force (get sv6 dk)) [:n 15, :c6 17]))
            (is (= @c  18)  "6x run + 4x do (2x suppressed) + 4x let (2x suppressed) + 4x data (2x suppressed)")]))))

   (testing "Manual `let` (unconditional) + `:data`/`:my-opt`"
     (vec
       (for [dk [:data :my-opt]]
         (let [c         (enc/counter)
               [rv1 sv1] (ws (let [n (c)] (sig! {:level :info, :run (c), dk        {:n n, :c1 (c)}})))
               [rv2 sv2] (ws (let [n (c)] (sig! {:level :info, :run (c), dk (delay {:n n, :c2 (c)})})))
               [rv3 sv3] (ws (let [n (c)] (sig! {:level :info, :run (c), dk        {:n n, :c3 (c)},  :allow? false})))
               [rv4 sv4] (ws (let [n (c)] (sig! {:level :info, :run (c), dk (delay {:n n, :c4 (c)}), :allow? false})))
               [rv5 sv5] (ws (let [n (c)] (sig! {:level :info, :run (c), dk        [:n n, :c5 (c)]})))
               [rv6 sv6] (ws (let [n (c)] (sig! {:level :info, :run (c), dk (delay [:n n, :c6 (c)])})))]

           [(is (= rv1 1))  (is (=        (get sv1 dk)  {:n 0, :c1 2}))
            (is (= rv2 4))  (is (= (force (get sv2 dk)) {:n 3, :c2 14}))
            (is (= rv3 6))  (is (=        (get sv3 dk)  nil))
            (is (= rv4 8))  (is (= (force (get sv4 dk)) nil))
            (is (= rv5 10)) (is (=        (get sv5 dk)  [:n 9,  :c5 11]))
            (is (= rv6 13)) (is (= (force (get sv6 dk)) [:n 12, :c6 15]))
            (is (= @c  16)  "6x run + 6x let (0x suppressed) + 4x data (2x suppressed)")]))))

   (testing "Call middleware"
     (let [c         (enc/counter)
           [rv1 sv1] (ws (sig! {:level :info, :run (c), :middleware [#(assoc % :m1 (c)) #(assoc % :m2 (c))]}))
           [rv2 sv2] (ws (sig! {:level :info, :run (c), :middleware [#(assoc % :m1 (c)) #(assoc % :m2 (c))], :allow? false}))
           [rv3 sv3] (ws (sig! {:level :info, :run (c), :middleware [#(assoc % :m1 (c)) #(assoc % :m2 (c))]}))
           [rv4 sv4] (ws (sig! {:level :info,           :middleware [(fn [_] "signal-value")]}))]

       [(is (= rv1 0))    (is (sm? sv1 {:m1 1 :m2 2}))
        (is (= rv2 3))    (is (nil?    sv2))
        (is (= rv3 4))    (is (sm? sv3 {:m1 5 :m2 6}))
        (is (= rv4 true)) (is (=       sv4 "signal-value"))
        (is (= @c  7)     "3x run + 4x middleware")]))])

(deftest _handlers
  ;; Basic handler tests are in Encore
  [(testing "Handler middleware"
     (let [c      (enc/counter)
           sv-h1_ (atom nil)
           sv-h2_ (atom nil)
           wh1    (sigs/wrap-handler :hid1 (fn [sv] (reset! sv-h1_ sv)) nil {:async nil, :middleware [#(assoc % :hm1 (c)) #(assoc % :hm2 (c))]})
           wh2    (sigs/wrap-handler :hid2 (fn [sv] (reset! sv-h2_ sv)) nil {:async nil, :middleware [#(assoc % :hm1 (c)) #(assoc % :hm2 (c))]})]

       ;; Note that call middleware output is cached and shared across all handlers
       (binding [impl/*sig-handlers* [wh1 wh2]]
         (let [;; 1x run + 4x handler middleware + 2x call middleware = 7x
               rv1    (sig! {:level :info, :run (c), :middleware [#(assoc % :m1 (c)) #(assoc % :m2 (c))]})
               sv1-h1 @sv-h1_
               sv1-h2 @sv-h2_
               c1     @c

               ;; 1x run
               rv2    (sig! {:level :info, :run (c), :middleware [#(assoc % :m1 (c)) #(assoc % :m2 (c))], :allow? false})
               sv2-h1 @sv-h1_
               sv2-h2 @sv-h2_
               c2     @c ; 8

               ;; 1x run + 4x handler middleware + 2x call middleware = 7x
               rv3    (sig! {:level :info, :run (c), :middleware [#(assoc % :m1 (c)) #(assoc % :m2 (c))]})
               sv3-h1 @sv-h1_
               sv3-h2 @sv-h2_
               c3     @c ; 15

               ;; 4x handler middleware
               rv4    (sig! {:level :info,           :middleware [(fn [_] {:my-sig-val? true})]})
               sv4-h1 @sv-h1_
               sv4-h2 @sv-h2_
               c4     @c]

           [(is (= rv1 0))    (is (sm? sv1-h1 {:m1 1, :m2 2,      :hm1 3,  :hm2 4}))  (is (sm? sv1-h2 {:m1 1, :m2 2,      :hm1 5,  :hm2 6}))
            (is (= rv2 7))    (is (sm? sv2-h1 {:m1 1, :m2 2,      :hm1 3,  :hm2 4}))  (is (sm? sv2-h2 {:m1 1, :m2 2,      :hm1 5,  :hm2 6}))
            (is (= rv3 8))    (is (sm? sv3-h1 {:m1 9, :m2 10,     :hm1 11, :hm2 12})) (is (sm? sv3-h2 {:m1 9, :m2 10,     :hm1 13, :hm2 14}))
            (is (= rv4 true)) (is (sm? sv4-h1 {:my-sig-val? true, :hm1 15, :hm2 16})) (is (sm? sv4-h2 {:my-sig-val? true, :hm1 17, :hm2 18}))
            (is (= c1  7)     "1x run +  4x handler middleware + 2x call middleware")
            (is (= c2  8)     "2x run +  4x handler middleware + 2x call middleware")
            (is (= c3  15)    "3x run +  8x handler middleware + 4x call middleware")
            (is (= c4  19)    "3x run + 12x handler middleware + 4x call middleware")]))))

   (testing "Handler binding conveyance"
     (let [a (atom nil)
           wh1
           (sigs/wrap-handler :hid1 (fn [x] (reset! a *dynamic-var*))
             nil #?(:clj {:async {:mode :dropping}} :cljs nil))]

       (binding [*dynamic-var* "bound", impl/*sig-handlers* [wh1]] (sig! {:level :info}))
       (is (= (do #?(:clj (Thread/sleep 500)) @a) "bound"))))])

(def ^:dynamic *throwing-handler-middleware?* false)

(deftest _throwing
  (let [sv_    (atom :nx)
        error_ (atom :nx)
        reset-state!
        (fn []
          (reset! sv_    :nx)
          (reset! error_ :nx)
          true)]

    (tel/with-handler :hid1
      (fn [sv] (force (:data sv)) (reset! sv_ sv))
      {:async nil, :error-fn (fn [x] (reset! error_ x)), :rl-error nil,
       :middleware [(fn [sv] (if *throwing-handler-middleware?* (throw ex1) sv))]}

      [(is (->> (sig! {:level :info, :when      (throw ex1)}) (throws? :ex-info "TestEx")) "`~filterable-expansion/allow` throws at call")
       (is (->> (sig! {:level :info, :instant   (throw ex1)}) (throws? :ex-info "TestEx")) "`~instant-form`               throws at call")
       (is (->> (sig! {:level :info, :id        (throw ex1)}) (throws? :ex-info "TestEx")) "`~id-form`                    throws at call")
       (is (->> (sig! {:level :info, :uid       (throw ex1)}) (throws? :ex-info "TestEx")) "`~uid-form`                   throws at call")
       (is (->> (sig! {:level :info, :run       (throw ex1)}) (throws? :ex-info "TestEx")) "`~run-form` rethrows at call")
       (is (sm? @sv_  {:level :info, :error ex1-pred})                                     "`~run-form` rethrows at call *after* dispatch")

       (testing "`@signal-value_`: trap with wrapped handler"
         [(testing "Throwing `~let-form`"
            (reset-state!)
            [(is (true? (sig! {:level :info, :let [_ (throw ex1)]})))
             (is (= @sv_ :nx))
             (is (sm? @error_ {:handler-id :hid1, :error ex1-pred}))])

          (testing "Throwing call middleware"
            (reset-state!)
            [(is (true? (sig! {:level :info, :middleware [(fn [_] (throw ex1))]})))
             (is (= @sv_ :nx))
             (is (sm? @error_ {:handler-id :hid1, :error ex1-pred}))])

          (testing "Throwing handler middleware"
            (reset-state!)
            (binding [*throwing-handler-middleware?* true]
              [(is (true? (sig! {:level :info})))
               (is (= @sv_ :nx))
               (is (sm? @error_ {:handler-id :hid1, :error ex1-pred}))]))

          (testing "Throwing `@data_`"
            (reset-state!)
            [(is (true? (sig! {:level :info, :data (delay (throw ex1))})))
             (is (= @sv_ :nx))
             (is (sm? @error_ {:handler-id :hid1, :error ex1-pred}))])

          (testing "Throwing user opt"
            (reset-state!)
            [(is (true? (sig! {:level :info, :my-opt (throw ex1)})))
             (is (= @sv_ :nx))
             (is (sm? @error_ {:handler-id :hid1, :error ex1-pred}))])])])))

(deftest _ctx
  (testing "Context (`*ctx*`)"
    [(is (= (binding [tel/*ctx* "my-ctx"] tel/*ctx*) "my-ctx") "Supports manual `binding`")
     (is (= (tel/with-ctx       "my-ctx"  tel/*ctx*) "my-ctx") "Supports any data type")

     (is (= (tel/with-ctx "my-ctx1"       (tel/with-ctx+ nil                        tel/*ctx*)) "my-ctx1")              "nil update => keep old-ctx")
     (is (= (tel/with-ctx "my-ctx1"       (tel/with-ctx+ (fn [old] [old "my-ctx2"]) tel/*ctx*)) ["my-ctx1" "my-ctx2"])  "fn  update => apply")
     (is (= (tel/with-ctx {:a :A1 :b :B1} (tel/with-ctx+ {:a :A2 :c :C2}            tel/*ctx*)) {:a :A2 :b :B1 :c :C2}) "map update => merge")

     (let [[_ sig] (ws (sig! {:level :info, :ctx "my-ctx"}))] (is (sm? sig {:ctx "my-ctx"}) "Can be set via call opt"))]))

(deftest _tracing
  (testing "Tracing"
    [(let [[_ sv] (ws (sig! {:level :info                      }))] (is (sm? sv {:parent nil})))
     (let [[_ sv] (ws (sig! {:level :info, :parent {:id   :id0}}))] (is (sm? sv {:parent {:id :id0       :uid :submap/nx}}) "`:parent/id`  can be set via call opt"))
     (let [[_ sv] (ws (sig! {:level :info, :parent {:uid :uid0}}))] (is (sm? sv {:parent {:id :submap/nx :uid      :uid0}}) "`:parent/uid` can be set via call opt"))

     (testing "Auto call id, uid"
       (let [[_ sv] (ws (sig! {:level :info, :parent {:id :id0, :uid :uid0}, :run impl/*trace-parent*, :data impl/*trace-parent*}))]
         [(is (sm? sv {:parent    {:id :id0, :uid :uid0}}))
          (is (sm? sv {:run-value {:id nil,  :uid (get sv :uid ::nx)}}) "`*trace-parent*`     visible to run-form, bound to call's auto {:keys [id uid]}")
          (is (sm? sv {:data      nil})                                 "`*trace-parent*` not visible to data-form ")]))

     (testing "Manual call id, uid"
       (let [[_ sv] (ws (sig! {:level :info, :parent {:id :id0, :uid :uid0}, :id :id1, :uid :uid1, :run impl/*trace-parent*, :data impl/*trace-parent*}))]
         [(is (sm? sv {:parent    {:id :id0, :uid :uid0}}))
          (is (sm? sv {:run-value {:id :id1, :uid :uid1}}) "`*trace-parent*`     visible to run-form, bound to call's auto {:keys [id uid]}")
          (is (sm? sv {:data      nil})                    "`*trace-parent*` not visible to data-form ")]))

     (testing "Tracing can be disabled via call opt"
       (let [[_ sv] (ws (sig! {:level :info, :parent {:id :id0, :uid :uid0}, :id :id1, :uid :uid1, :run impl/*trace-parent*, :data impl/*trace-parent*, :trace? false}))]
         [(is (sm? sv {:parent    {:id :id0, :uid :uid0}}))
          (is (sm? sv {:run-value nil}))]))

     (testing "Signal nesting"
       (let [[[inner-rv inner-sv] outer-sv]
             (ws (sig! {                :level :info, :id :id1, :uid :uid1,
                        :run (ws (sig! {:level :info, :id :id2, :uid :uid2, :run impl/*trace-parent*}))}))]

         [(is (sm? outer-sv             {:id :id1, :uid :uid1, :parent nil}))
          (is (sm? inner-rv             {:id :id2, :uid :uid2}))
          (is (sm? inner-sv {:parent    {:id :id1, :uid :uid1}}))
          (is (sm? inner-sv {:run-value {:id :id2, :uid :uid2}}))]))]))

(deftest _sampling
  ;; Capture combined (call * handler) sample rate in Signal when possible
  (let [test1
        (fn [call-sample-rate handler-sample-rate]
          (let [c   (enc/counter)
                sr_ (atom nil)]
            (tel/with-handler "h1"
              (fn h1 [x] (c) (compare-and-set! sr_ nil (:sample-rate x)))
              {:async nil, :sample-rate handler-sample-rate}
              (do
                ;; Repeat to ensure >=1 gets through sampling
                (dotimes [_ 1000] (sig! {:level :info, :sample-rate call-sample-rate}))
                [@sr_ @c]))))]

    [(is (= (test1 nil        nil)  [nil 1000]) "[none   none] = none")
     (is (= (test1 nil (fn [] nil)) [nil 1000]) "[none =>none] = none")
     (is (= (test1 1.0        nil)  [1.0 1000]) "[100%   none] = 100%")
     (is (= (test1 1.0 (fn [] nil)) [1.0 1000]) "[100%   none] = 100%")
     (is (= (test1 nil        1.0)  [1.0 1000]) "[none   100%] = 100%")
     (is (= (test1 nil (fn [] 1.0)) [1.0 1000]) "[none =>100%] = 100%")

     (is (= (test1 0.0        nil)  [nil    0]) "[0%     none] = 0%")
     (is (= (test1 0.0 (fn [] nil)) [nil    0]) "[0%   =>none] = 0%")
     (is (= (test1 nil        0.0)  [nil    0]) "[none     0%] = 0%")
     (is (= (test1 nil (fn [] 0.0)) [nil    0]) "[none   =>0%] = 0%")

     (let [[sr n] (test1 0.5        0.5) ] (is (and (= sr 0.25) (<= 150 n 350)) "[50%   50%] = 25%"))
     (let [[sr n] (test1 0.5 (fn [] 0.5))] (is (and (= sr 0.25) (<= 150 n 350)) "[50% =>50%] = 25%"))]))

;;;;

(deftest _common-signals
  [#?(:clj
      (testing "signal-opts"
        [(is (= (impl/signal-opts :msg, :level, {:level :info}                "msg") {:defaults {:level :info}, :msg "msg"}))
         (is (= (impl/signal-opts :msg, :level, {:level :info} {:level :warn} "msg") {:defaults {:level :info}, :msg "msg", :level :warn}))
         (is (= (impl/signal-opts :msg, :level, {:level :info}         :warn  "msg") {:defaults {:level :info}, :msg "msg", :level :warn}))]))

   (testing "log!" ; msg + ?level => allowed?
     [(let [[rv sv] (ws (tel/log!                 "msg"))] [(is (= rv true)) (is (sm?  sv {:kind :log, :line :submap/ex, :msg_ "msg", :level :info}))])
      (let [[rv sv] (ws (tel/log!          :warn  "msg"))] [(is (= rv true)) (is (sm?  sv {:kind :log, :line :submap/ex, :msg_ "msg", :level :warn}))])
      (let [[rv sv] (ws (tel/log! {:level  :warn} "msg"))] [(is (= rv true)) (is (sm?  sv {:kind :log, :line :submap/ex, :msg_ "msg", :level :warn}))])
      (let [[rv sv] (ws (tel/log! {:allow? false} "msg"))] [(is (= rv nil))  (is (nil? sv))])])

   (testing "event!" ; id + ?level => allowed?
     [(let [[rv sv] (ws (tel/event!                 :id1))] [(is (= rv true)) (is (sm?  sv {:kind :event, :line :submap/ex, :level :info, :id :id1}))])
      (let [[rv sv] (ws (tel/event!          :warn  :id1))] [(is (= rv true)) (is (sm?  sv {:kind :event, :line :submap/ex, :level :warn, :id :id1}))])
      (let [[rv sv] (ws (tel/event! {:level  :warn} :id1))] [(is (= rv true)) (is (sm?  sv {:kind :event, :line :submap/ex, :level :warn, :id :id1}))])
      (let [[rv sv] (ws (tel/event! {:allow? false} :id1))] [(is (= rv nil))  (is (nil? sv))])])

   (testing "error!" ; error + ?id => error
     [(let [[rv sv] (ws (tel/error!                 ex1))] [(is (= rv ex1)) (is (sm?  sv {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id  nil}))])
      (let [[rv sv] (ws (tel/error!           :id1  ex1))] [(is (= rv ex1)) (is (sm?  sv {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id :id1}))])
      (let [[rv sv] (ws (tel/error! {:id      :id1} ex1))] [(is (= rv ex1)) (is (sm?  sv {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id :id1}))])
      (let [[rv sv] (ws (tel/error! {:allow? false} ex1))] [(is (= rv ex1)) (is (nil? sv))])])

   (testing "trace!" ; run + ?id => run result (value or throw)
     [(let [[rv sv] (ws  (tel/trace!                    (+ 1 2)))] [(is (= rv 3))     (is (sm?  sv {:kind :trace, :line :submap/ex, :level :info, :id  nil}))])
      (let [[rv sv] (ws  (tel/trace!           :id1     (+ 1 2)))] [(is (= rv 3))     (is (sm?  sv {:kind :trace, :line :submap/ex, :level :info, :id :id1}))])
      (let [[rv sv] (ws  (tel/trace! {:id      :id1}    (+ 1 2)))] [(is (= rv 3))     (is (sm?  sv {:kind :trace, :line :submap/ex, :level :info, :id :id1}))])
      (let [[rv sv] (ws* (tel/trace!           :id1 (throw ex1)))] [(is (ex1-rv? rv)) (is (sm?  sv {:kind :trace, :line :submap/ex, :level :info, :id :id1, :error ex1-pred}))])
      (let [[rv sv] (ws  (tel/trace! {:allow? false}    (+ 1 2)))] [(is (= rv 3))     (is (nil? sv))])])

   (testing "spy" ; run + ?level => run result (value or throw)
     [(let [[rv sv] (ws  (tel/spy!                    (+ 1 2)))] [(is (= rv 3))     (is (sm?  sv {:kind :spy, :line :submap/ex, :level :info}))])
      (let [[rv sv] (ws  (tel/spy!          :warn     (+ 1 2)))] [(is (= rv 3))     (is (sm?  sv {:kind :spy, :line :submap/ex, :level :warn}))])
      (let [[rv sv] (ws  (tel/spy! {:level  :warn}    (+ 1 2)))] [(is (= rv 3))     (is (sm?  sv {:kind :spy, :line :submap/ex, :level :warn}))])
      (let [[rv sv] (ws* (tel/spy!          :warn (throw ex1)))] [(is (ex1-rv? rv)) (is (sm?  sv {:kind :spy, :line :submap/ex, :level :warn, :error ex1-pred}))])
      (let [[rv sv] (ws  (tel/spy! {:allow? false}    (+ 1 2)))] [(is (= rv 3))     (is (nil? sv))])])

   (testing "catch->error!" ; form + ?id => run value or ?return
     [(let [[rv sv] (ws  (tel/catch->error!                       (+ 1 2)))] [(is (= rv 3))     (is (nil? sv))])
      (let [[rv sv] (ws  (tel/catch->error!                   (throw ex1)))] [(is (= rv nil))   (is (sm? sv {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id  nil}))])
      (let [[rv sv] (ws  (tel/catch->error!             :id1  (throw ex1)))] [(is (= rv nil))   (is (sm? sv {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id :id1}))])
      (let [[rv sv] (ws  (tel/catch->error! {:id        :id1} (throw ex1)))] [(is (= rv nil))   (is (sm? sv {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id :id1}))])
      (let [[rv sv] (ws* (tel/catch->error! {:rethrow?  true} (throw ex1)))] [(is (ex1-rv? rv)) (is (sm? sv {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id  nil}))])
      (let [[rv sv] (ws  (tel/catch->error! {:catch-val :foo} (throw ex1)))] [(is (= rv :foo))  (is (sm? sv {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id  nil}))])
      (let [[rv sv] (ws  (tel/catch->error! {:catch-val :foo}     (+ 1 2)))] [(is (= rv 3))     (is (nil? sv))])
      (let [[rv sv] (ws  (tel/catch->error! {:catch-val :foo ; Overrides `:rethrow?`
                                             :rethrow?  true}     (+ 1 2)))] [(is (= rv 3))     (is (nil? sv))])])

   #?(:clj
      (testing "uncaught->error!"
        (let [sv_ (atom ::nx)]
          [(do (enc/set-var-root! impl/*sig-handlers* [(sigs/wrap-handler "h1" (fn h1 [x] (reset! sv_ x)) nil {:async nil})]) :set-handler)
           ;;
           (is (nil? (tel/uncaught->error!)))
           (is (do (.join (impl/threaded (throw ex1))) (sm? @sv_ {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id  nil})))
           ;;
           (is (nil? (tel/uncaught->error! :id1)))
           (is (do (.join (impl/threaded (throw ex1))) (sm? @sv_ {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id :id1})))
           ;;
           (is (nil? (tel/uncaught->error! {:id :id1})))
           (is (do (.join (impl/threaded (throw ex1))) (sm? @sv_ {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id :id1})))
           ;;
           (do (enc/set-var-root! impl/*sig-handlers* nil) :unset-handler)])))])

;;;; Interop

(comment (def ^org.slf4j.Logger sl (org.slf4j.LoggerFactory/getLogger "MyTelemereSLF4JLogger")))

#?(:clj
   (deftest _interop
     [(testing "`clojure.tools.logging` -> Telemere"
        [(is (sm? (tel/check-interop) {:tools-logging {:present? true, :sending->telemere? true, :telemere-receiving? true}}))
         (is (sm? (wsv (ctl/info "Hello" "x" "y")) {:level :info, :location nil, :ns nil, :kind :log, :id :taoensso.telemere/tools-logging, :msg_ "Hello x y"}))
         (is (sm? (wsv (ctl/warn "Hello" "x" "y")) {:level :warn, :location nil, :ns nil, :kind :log, :id :taoensso.telemere/tools-logging, :msg_ "Hello x y"}))
         (is (sm? (wsv (ctl/error ex1 "An error")) {:level :error, :error ex1}) "Errors")])

      (testing "Standard out/err streams -> Telemere"
        [(is (sm?   (tel/check-interop) {:system/out {:sending->telemere? false, :telemere-receiving? false},
                                         :system/err {:sending->telemere? false, :telemere-receiving? false}}))

         (is (true? (tel/streams->telemere!)))
         (is (sm?   (tel/check-interop) {:system/out {:sending->telemere? true,  :telemere-receiving? true},
                                         :system/err {:sending->telemere? true,  :telemere-receiving? true}}))

         (is (true? (tel/streams->reset!)))
         (is (sm?   (tel/check-interop) {:system/out {:sending->telemere? false, :telemere-receiving? false},
                                         :system/err {:sending->telemere? false, :telemere-receiving? false}}))

         (is
           (sm? (wsv (tel/with-out->telemere (println "Hello" "x" "y")))
             {:level :info, :location nil, :ns nil, :kind :system/out, :msg_ "Hello x y"}))])

      (testing "SLF4J -> Telemere"
        [(is (sm? (tel/check-interop) {:slf4j {:present? true, :sending->telemere? true, :telemere-receiving? true}}))
         (let [^org.slf4j.Logger sl (org.slf4j.LoggerFactory/getLogger "MyTelemereSLF4JLogger")]
           [(testing "Basics"
              [(is (sm? (wsv (.info sl "Hello"))               {:level :info, :location nil, :ns nil, :kind :log, :id :taoensso.telemere/slf4j, :msg_ "Hello"}) "Legacy API: info basics")
               (is (sm? (wsv (.warn sl "Hello"))               {:level :warn, :location nil, :ns nil, :kind :log, :id :taoensso.telemere/slf4j, :msg_ "Hello"}) "Legacy API: warn basics")
               (is (sm? (wsv (-> (.atInfo sl) (.log "Hello"))) {:level :info, :location nil, :ns nil, :kind :log, :id :taoensso.telemere/slf4j, :msg_ "Hello"}) "Fluent API: info basics")
               (is (sm? (wsv (-> (.atWarn sl) (.log "Hello"))) {:level :warn, :location nil, :ns nil, :kind :log, :id :taoensso.telemere/slf4j, :msg_ "Hello"}) "Fluent API: warn basics")])

            (testing "Message formatting"
              (let [msgp "X is {} and Y is {}", expected {:msg_ "X is x and Y is y", :data {:slf4j/args ["x" "y"]}}]
                [(is (sm? (wsv (.info sl msgp "x" "y"))                                                           expected) "Legacy API: formatted message, raw args")
                 (is (sm? (wsv (-> (.atInfo sl) (.setMessage msgp) (.addArgument "x") (.addArgument "y") (.log))) expected) "Fluent API: formatted message, raw args")]))

            (is (sm? (wsv (-> (.atInfo sl) (.addKeyValue "k1" "v1") (.addKeyValue "k2" "v2") (.log))) {:data {:slf4j/kvs {"k1" "v1", "k2" "v2"}}}) "Fluent API: kvs")

            (testing "Markers"
              (let [m1 (slf4j/est-marker! "M1")
                    m2 (slf4j/est-marker! "M2")
                    cm (slf4j/est-marker! "Compound" "M1" "M2")]

                [(is (sm? (wsv (.info sl cm "Hello"))                                    {:data #:slf4j{:marker-names #{"Compound" "M1" "M2"}}}) "Legacy API: markers")
                 (is (sm? (wsv (-> (.atInfo sl) (.addMarker m1) (.addMarker cm) (.log))) {:data #:slf4j{:marker-names #{"Compound" "M1" "M2"}}}) "Fluent API: markers")]))

            (testing "Errors"
              [(is (sm? (wsv (.warn sl "An error" ^Throwable ex1))     {:level :warn, :error ex1}) "Legacy API: errors")
               (is (sm? (wsv (-> (.atWarn sl) (.setCause ex1) (.log))) {:level :warn, :error ex1}) "Fluent API: errors")])

            (testing "MDC (Mapped Diagnostic Context)"
              (with-open [_   (org.slf4j.MDC/putCloseable "k1" "v1")]
                (with-open [_ (org.slf4j.MDC/putCloseable "k2" "v2")]
                  [(is (sm? (wsv (->          sl  (.info "Hello"))) {:level :info, :ctx {"k1" "v1", "k2" "v2"}}) "Legacy API: MDC")
                   (is (sm? (wsv (-> (.atInfo sl) (.log  "Hello"))) {:level :info, :ctx {"k1" "v1", "k2" "v2"}}) "Fluent API: MDC")])))])])]))

;;;;

#?(:cljs (test/run-tests))
