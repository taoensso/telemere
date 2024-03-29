(ns taoensso.telemere-tests
  (:require
   [clojure.test            :as test :refer [deftest testing is]]
   [taoensso.encore         :as enc  :refer [throws? submap?]]
   [taoensso.encore.signals :as sigs]
   [taoensso.telemere       :as tel]
   [taoensso.telemere.impl  :as impl]
   [taoensso.telemere.utils :as utils]
   #?(:clj [taoensso.telemere.slf4j :as slf4j])
   #?(:clj [clojure.tools.logging   :as ctl])
   #?(:clj [jsonista.core           :as jsonista]))

  #?(:cljs
     (:require-macros
      [taoensso.telemere-tests :refer [sig! ws wsf wst ws1]])))

(comment
  (remove-ns      'taoensso.telemere-tests)
  (test/run-tests 'taoensso.telemere-tests))

;;;; Utils

(enc/defaliases
  #?(:clj     {:alias sig! :src impl/signal!})
  #?(:default {:alias sm?  :src enc/submap?}))

#?(:clj
   (do
     (defmacro ws  [form]                  `(impl/-with-signals (fn [] ~form) {}))
     (defmacro wsf [form]                  `(impl/-with-signals (fn [] ~form) {:force-msg?   true}))
     (defmacro wst [form]                  `(impl/-with-signals (fn [] ~form) {:trap-errors? true}))
     (defmacro ws1 [form] `(let [[_# [s1#]] (impl/-with-signals (fn [] ~form) {:force-msg?   true})] s1#))))

(def ^:dynamic *dynamic-var* nil)

(def  ex1 (ex-info "Ex1" {}))
(def  ex2 (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"})))

(def  ex1-pred (enc/pred #(= % ex1)))
(def  ex2-type (#'enc/ex-type ex2))
(defn ex1! [] (throw ex1))

(def   t0s "2024-06-09T21:15:20.17Z")
(def   t0  (enc/as-inst t0s))
(def udt0  (enc/as-udt t0))

;; (tel/remove-handler! :default-console-handler)
(let [sig-handlers_ (atom nil)]
  (test/use-fixtures :once
    (enc/test-fixtures
      {:after (fn [] (enc/set-var-root! impl/*sig-handlers* @sig-handlers_))
       :before
       (fn []
         (reset! sig-handlers_ impl/*sig-handlers*)
         (enc/set-var-root!    impl/*sig-handlers* nil))})))

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
             (impl/msg-splice ["s1" nil "s2" "s3" impl/msg-skip "s4"])
             (impl/msg-splice nil)
             impl/msg-skip :kw])

         "x y nil [\"z1\" nil \"z2\" \"z3\"] s1 nil s2 s3 s4 :kw"))])

(deftest _signal-macro
  [(is (=   (ws (sig! {:level :info, :elide? true               })) [nil nil]) "With compile-time elision")
   (is (=   (ws (sig! {:level :info, :elide? true,  :run (+ 1 2)})) [3   nil]) "With compile-time elision, run-form")
   (is (=   (ws (sig! {:level :info, :allow? false              })) [nil nil]) "With runtime suppression")
   (is (=   (ws (sig! {:level :info, :allow? false, :run (+ 1 2)})) [3   nil]) "With runtime suppression, run-form")

   (is (->>     (sig! {:level :info, :elide? true,  :run (ex1!)}) (throws? :ex-info "Ex1")) "With compile-time elision, throwing run-form")
   (is (->>     (sig! {:level :info, :allow? false, :run (ex1!)}) (throws? :ex-info "Ex1")) "With runtime suppression,  throwing run-form")

   (let [[rv1 [sv1]] (ws (sig! {:level :info              }))
         [rv2 [sv2]] (ws (sig! {:level :info, :run (+ 1 2)}))]

     [(is (= rv1 true)) (is (sm? sv1 {:ns "taoensso.telemere-tests", :level :info, :run-form nil,      :run-val nil, :run-nsecs nil}))
      (is (= rv2    3)) (is (sm? sv2 {:ns "taoensso.telemere-tests", :level :info, :run-form '(+ 1 2), :run-val 3,   :run-nsecs (enc/pred nat-int?)}))])

   (testing "Nested signals"
     (let [[[inner-rv [inner-sv]] [outer-sv]] (ws (sig! {:level :info, :run (ws (sig! {:level :warn, :run "inner-run"}))}))]
       [(is (= inner-rv "inner-run"))
        (is (sm? inner-sv {:level :warn, :run-val "inner-run"}))
        (is (sm? outer-sv {:level :info  :run-val [inner-rv [inner-sv]]}))]))

   (testing "Instants"
     (let [[_ [sv1]] (ws (sig! {:level :info                             }))
           [_ [sv2]] (ws (sig! {:level :info, :run (reduce + (range 1e6))}))
           [_ [sv3]] (ws (sig! {:level :info, :run (reduce + (range 1e6))
                                :instant ; Allow custom instant
                                #?(:clj  java.time.Instant/EPOCH
                                   :cljs (js/Date. 0))}))]

       [(let [{start :instant, end :end-instant} sv1]
          [(is (enc/inst? start))
           (is (nil?      end))])

        (let [{start :instant, end :end-instant} sv2]
          [(is (enc/inst?  start))
           (is (enc/inst?  end))
           (is (> (inst-ms end) (inst-ms start)))])

        (let [{start :instant, end :end-instant} sv3]
          [(is (enc/inst?  start))
           (is (enc/inst?  end))
           (is (= (inst-ms start) 0)               "Respect custom instant")
           (is (> (inst-ms end)   (inst-ms start)) "End instant is start + run-nsecs")
           (is (< (inst-ms end)   1e6)             "End instant is start + run-nsecs")])]))

   (testing "Support arb extra user kvs"
     (let [[rv [sv]] (ws (sig! {:level :info, :my-k1 "v1", :my-k2 "v2"}))]
       (is           (sm? sv   {:level :info, :my-k1 "v1", :my-k2 "v2"
                                :extra-kvs   {:my-k1 "v1", :my-k2 "v2"}}))))

   (testing "`:msg` basics"
     (let [c           (enc/counter)
           [rv1 [sv1]] (ws (sig! {:level :info, :run (c), :msg             "msg1"}))         ; No     delay
           [rv2 [sv2]] (ws (sig! {:level :info, :run (c), :msg        [    "msg2:"  (c)]}))  ; Auto   delay
           [rv3 [sv3]] (ws (sig! {:level :info, :run (c), :msg (delay (str "msg3: " (c)))})) ; Manual delay
           [rv4 [sv4]] (ws (sig! {:level :info, :run (c), :msg        (str "msg4: " (c))}))  ; No     delay
           [rv5 [sv5]] (ws (sig! {:level :info, :run (c), :msg        (str "msg5: " (c)), :allow? false}))]

       [(is (= rv1 0)) (is (=  (:msg_ sv1) "msg1"))
        (is (= rv2 1)) (is (= @(:msg_ sv2) "msg2: 6"))
        (is (= rv3 2)) (is (= @(:msg_ sv3) "msg3: 7"))
        (is (= rv4 3)) (is (=  (:msg_ sv4) "msg4: 4"))
        (is (= rv5 5)) (is (=  (:msg_ sv5) nil))
        (is (= @c  8)  "5x run + 3x message (1x suppressed)")]))

   (testing "`:data` basics"
     (vec
       (for [dk [:data :my-k1]] ; User kvs share same behaviour as data
         (let [c           (enc/counter)
               [rv1 [sv1]] (ws (sig! {:level :info, :run (c), dk        {:c1 (c)}}))
               [rv2 [sv2]] (ws (sig! {:level :info, :run (c), dk (delay {:c2 (c)})}))
               [rv3 [sv3]] (ws (sig! {:level :info, :run (c), dk        {:c3 (c)},  :allow? false}))
               [rv4 [sv4]] (ws (sig! {:level :info, :run (c), dk (delay {:c4 (c)}), :allow? false}))
               [rv5 [sv5]] (ws (sig! {:level :info, :run (c), dk        [:c5 (c)]}))
               [rv6 [sv6]] (ws (sig! {:level :info, :run (c), dk (delay [:c6 (c)])}))]

           [(is (= rv1 0)) (is (=        (get sv1 dk)  {:c1 1}))
            (is (= rv2 2)) (is (= (force (get sv2 dk)) {:c2 8}))
            (is (= rv3 3)) (is (=        (get sv3 dk)  nil))
            (is (= rv4 4)) (is (= (force (get sv4 dk)) nil))
            (is (= rv5 5)) (is (=        (get sv5 dk)  [:c5 6]) "`:data` can be any type")
            (is (= rv6 7)) (is (= (force (get sv6 dk)) [:c6 9]) "`:data` can be any type")
            (is (= @c  10) "6x run + 4x data (2x suppressed)")]))))

   (testing "`:let` basics"
     (let [c           (enc/counter)
           [rv1 [sv1]] (ws (sig! {:level :info, :run (c), :let [_ (c)]}))
           [rv2 [sv2]] (ws (sig! {:level :info, :run (c), :let [_ (c)], :allow? false}))
           [rv3 [sv3]] (ws (sig! {:level :info, :run (c), :let [_ (c)]}))]

       [(is (= rv1 0))
        (is (= rv2 2))
        (is (= rv3 3))
        (is (= @c  5) "3x run + 2x let (1x suppressed)")]))

   (testing "`:let` + `:msg`"
     (let [c           (enc/counter)
           [rv1 [sv1]] (ws (sig! {:level :info, :run (c), :let [n (c)], :msg             "msg1"}))               ; No     delay
           [rv2 [sv2]] (ws (sig! {:level :info, :run (c), :let [n (c)], :msg        [    "msg2:"  n     (c)]}))  ; Auto   delay
           [rv3 [sv3]] (ws (sig! {:level :info, :run (c), :let [n (c)], :msg (delay (str "msg3: " n " " (c)))})) ; Manual delay
           [rv4 [sv4]] (ws (sig! {:level :info, :run (c), :let [n (c)], :msg        (str "msg4: " n " " (c))}))  ; No     delay
           [rv5 [sv5]] (ws (sig! {:level :info, :run (c), :let [n (c)], :msg        (str "msg5: " n " " (c)), :allow? false}))]

       [(is (= rv1 0)) (is (=  (:msg_ sv1) "msg1"))
        (is (= rv2 2)) (is (= @(:msg_ sv2) "msg2: 3 10"))
        (is (= rv3 4)) (is (= @(:msg_ sv3) "msg3: 5 11"))
        (is (= rv4 6)) (is (=  (:msg_ sv4) "msg4: 7 8"))
        (is (= rv5 9)) (is (=  (:msg_ sv5) nil))
        (is (= @c  12) "5x run + 4x let (1x suppressed) + 3x msg (1x suppressed)")]))

   (testing "`:do` + `:let` + `:data`/`:my-k1`"
     (vec
       (for [dk [:data :my-k1]]
         (let [c           (enc/counter)
               [rv1 [sv1]] (ws (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk        {:n n, :c1 (c)}}))
               [rv2 [sv2]] (ws (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk (delay {:n n, :c2 (c)})}))
               [rv3 [sv3]] (ws (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk        {:n n, :c3 (c)},  :allow? false}))
               [rv4 [sv4]] (ws (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk (delay {:n n, :c4 (c)}), :allow? false}))
               [rv5 [sv5]] (ws (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk        [:n n, :c5 (c)]}))
               [rv6 [sv6]] (ws (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk (delay [:n n, :c6 (c)])}))]

           [(is (= rv1 0))  (is (=        (get sv1 dk)  {:n 2, :c1 3}))
            (is (= rv2 4))  (is (= (force (get sv2 dk)) {:n 6, :c2 16}))
            (is (= rv3 7))  (is (=        (get sv3 dk)  nil))
            (is (= rv4 8))  (is (= (force (get sv4 dk)) nil))
            (is (= rv5 9))  (is (=        (get sv5 dk)  [:n 11, :c5 12]))
            (is (= rv6 13)) (is (= (force (get sv6 dk)) [:n 15, :c6 17]))
            (is (= @c  18)  "6x run + 4x do (2x suppressed) + 4x let (2x suppressed) + 4x data (2x suppressed)")]))))

   (testing "Manual `let` (unconditional) + `:data`/`:my-k1`"
     (vec
       (for [dk [:data :my-k1]]
         (let [c           (enc/counter)
               [rv1 [sv1]] (ws (let [n (c)] (sig! {:level :info, :run (c), dk        {:n n, :c1 (c)}})))
               [rv2 [sv2]] (ws (let [n (c)] (sig! {:level :info, :run (c), dk (delay {:n n, :c2 (c)})})))
               [rv3 [sv3]] (ws (let [n (c)] (sig! {:level :info, :run (c), dk        {:n n, :c3 (c)},  :allow? false})))
               [rv4 [sv4]] (ws (let [n (c)] (sig! {:level :info, :run (c), dk (delay {:n n, :c4 (c)}), :allow? false})))
               [rv5 [sv5]] (ws (let [n (c)] (sig! {:level :info, :run (c), dk        [:n n, :c5 (c)]})))
               [rv6 [sv6]] (ws (let [n (c)] (sig! {:level :info, :run (c), dk (delay [:n n, :c6 (c)])})))]

           [(is (= rv1 1))  (is (=        (get sv1 dk)  {:n 0, :c1 2}))
            (is (= rv2 4))  (is (= (force (get sv2 dk)) {:n 3, :c2 14}))
            (is (= rv3 6))  (is (=        (get sv3 dk)  nil))
            (is (= rv4 8))  (is (= (force (get sv4 dk)) nil))
            (is (= rv5 10)) (is (=        (get sv5 dk)  [:n 9,  :c5 11]))
            (is (= rv6 13)) (is (= (force (get sv6 dk)) [:n 12, :c6 15]))
            (is (= @c  16)  "6x run + 6x let (0x suppressed) + 4x data (2x suppressed)")]))))

   (testing "Call middleware"
     (let [c           (enc/counter)
           [rv1 [sv1]] (ws (sig! {:level :info, :run (c), :middleware [#(assoc % :m1 (c)) #(assoc % :m2 (c))]}))
           [rv2 [sv2]] (ws (sig! {:level :info, :run (c), :middleware [#(assoc % :m1 (c)) #(assoc % :m2 (c))], :allow? false}))
           [rv3 [sv3]] (ws (sig! {:level :info, :run (c), :middleware [#(assoc % :m1 (c)) #(assoc % :m2 (c))]}))
           [rv4 [sv4]] (ws (sig! {:level :info,           :middleware [(fn [_] "signal-value")]}))]

       [(is (= rv1 0))    (is (sm? sv1 {:m1 1 :m2 2}))
        (is (= rv2 3))    (is (nil?    sv2))
        (is (= rv3 4))    (is (sm? sv3 {:m1 5 :m2 6}))
        (is (= rv4 true)) (is (=       sv4 "signal-value"))
        (is (= @c  7)     "3x run + 4x middleware")]))

   #?(:clj
      (testing "Printing"
        (let [sv1 (tel/with-signal (tel/signal! {:level :info, :run (+ 1 2), :my-k1 :my-v1}))
              sv1 ; Ensure instants are printable
              (-> sv1
                (update :instant     enc/inst->udt)
                (update :end-instant enc/inst->udt))]

       [(is (= sv1 (read-string (pr-str sv1))))])))])

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
       :middleware [(fn [sv] (if *throwing-handler-middleware?* (ex1!) sv))]}

      [(is (->> (sig! {:level :info, :when      (ex1!)}) (throws? :ex-info "Ex1")) "`~filterable-expansion/allow` throws at call")
       (is (->> (sig! {:level :info, :instant   (ex1!)}) (throws? :ex-info "Ex1")) "`~instant-form`               throws at call")
       (is (->> (sig! {:level :info, :id        (ex1!)}) (throws? :ex-info "Ex1")) "`~id-form`                    throws at call")
       (is (->> (sig! {:level :info, :uid       (ex1!)}) (throws? :ex-info "Ex1")) "`~uid-form`                   throws at call")
       (is (->> (sig! {:level :info, :run       (ex1!)}) (throws? :ex-info "Ex1")) "`~run-form` rethrows at call")
       (is (sm? @sv_  {:level :info, :error ex1-pred})                             "`~run-form` rethrows at call *after* dispatch")

       (testing "`@signal-value_`: trap with wrapped handler"
         [(testing "Throwing `~let-form`"
            (reset-state!)
            [(is (true? (sig! {:level :info, :let [_ (ex1!)]})))
             (is (= @sv_ :nx))
             (is (sm? @error_ {:handler-id :hid1, :error ex1-pred}))])

          (testing "Throwing call middleware"
            (reset-state!)
            [(is (true? (sig! {:level :info, :middleware [(fn [_] (ex1!))]})))
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
            [(is (true? (sig! {:level :info, :data (delay (ex1!))})))
             (is (= @sv_ :nx))
             (is (sm? @error_ {:handler-id :hid1, :error ex1-pred}))])

          (testing "Throwing user kv"
            (reset-state!)
            [(is (true? (sig! {:level :info, :my-k1 (ex1!)})))
             (is (= @sv_ :nx))
             (is (sm? @error_ {:handler-id :hid1, :error ex1-pred}))])])])))

(deftest _ctx
  (testing "Context (`*ctx*`)"
    [(is (= (binding [tel/*ctx* "my-ctx"] tel/*ctx*) "my-ctx") "Supports manual `binding`")
     (is (= (tel/with-ctx       "my-ctx"  tel/*ctx*) "my-ctx") "Supports any data type")

     (is (= (tel/with-ctx "my-ctx1"       (tel/with-ctx+ nil                        tel/*ctx*)) "my-ctx1")              "nil update => keep old-ctx")
     (is (= (tel/with-ctx "my-ctx1"       (tel/with-ctx+ (fn [old] [old "my-ctx2"]) tel/*ctx*)) ["my-ctx1" "my-ctx2"])  "fn  update => apply")
     (is (= (tel/with-ctx {:a :A1 :b :B1} (tel/with-ctx+ {:a :A2 :c :C2}            tel/*ctx*)) {:a :A2 :b :B1 :c :C2}) "map update => merge")

     (let [[_ [sv]] (ws (sig! {:level :info, :ctx "my-ctx"}))] (is (sm? sv {:ctx "my-ctx"}) "Can be set via call opt"))]))

(deftest _tracing
  (testing "Tracing"
    [(let [[_ [sv]] (ws (sig! {:level :info                      }))] (is (sm? sv {:parent nil})))
     (let [[_ [sv]] (ws (sig! {:level :info, :parent {:id   :id0}}))] (is (sm? sv {:parent {:id :id0       :uid :submap/nx}}) "`:parent/id`  can be set via call opt"))
     (let [[_ [sv]] (ws (sig! {:level :info, :parent {:uid :uid0}}))] (is (sm? sv {:parent {:id :submap/nx :uid      :uid0}}) "`:parent/uid` can be set via call opt"))

     (testing "Auto call id, uid"
       (let [[_ [sv]] (ws (sig! {:level :info, :parent {:id :id0, :uid :uid0}, :run impl/*trace-parent*, :data impl/*trace-parent*}))]
         [(is (sm? sv {:parent  {:id :id0, :uid :uid0}}))
          (is (sm? sv {:run-val {:id nil,  :uid (get sv :uid ::nx)}}) "`*trace-parent*`     visible to run-form, bound to call's auto {:keys [id uid]}")
          (is (sm? sv {:data    nil})                                 "`*trace-parent*` not visible to data-form ")]))

     (testing "Manual call id, uid"
       (let [[_ [sv]] (ws (sig! {:level :info, :parent {:id :id0, :uid :uid0}, :id :id1, :uid :uid1, :run impl/*trace-parent*, :data impl/*trace-parent*}))]
         [(is (sm? sv {:parent  {:id :id0, :uid :uid0}}))
          (is (sm? sv {:run-val {:id :id1, :uid :uid1}}) "`*trace-parent*`     visible to run-form, bound to call's auto {:keys [id uid]}")
          (is (sm? sv {:data    nil})                    "`*trace-parent*` not visible to data-form ")]))

     (testing "Tracing can be disabled via call opt"
       (let [[_ [sv]] (ws (sig! {:level :info, :parent {:id :id0, :uid :uid0}, :id :id1, :uid :uid1, :run impl/*trace-parent*, :data impl/*trace-parent*, :trace? false}))]
         [(is (sm? sv {:parent  {:id :id0, :uid :uid0}}))
          (is (sm? sv {:run-val nil}))]))

     (testing "Signal nesting"
       (let [[[inner-rv [inner-sv]] [outer-sv]]
             (ws (sig! {                :level :info, :id :id1, :uid :uid1,
                        :run (ws (sig! {:level :info, :id :id2, :uid :uid2, :run impl/*trace-parent*}))}))]

         [(is (sm? outer-sv           {:id :id1, :uid :uid1, :parent nil}))
          (is (sm? inner-rv           {:id :id2, :uid :uid2}))
          (is (sm? inner-sv {:parent  {:id :id1, :uid :uid1}}))
          (is (sm? inner-sv {:run-val {:id :id2, :uid :uid2}}))]))]))

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
        [(is (= (impl/signal-opts `signal! {:level :info} :id :level :dsc  [::my-id               ]) {:defaults {:level :info}, :id ::my-id}))
         (is (= (impl/signal-opts `signal! {:level :info} :id :level :dsc  [::my-id         :warn ]) {:defaults {:level :info}, :id ::my-id, :level :warn}))
         (is (= (impl/signal-opts `signal! {:level :info} :id :level :dsc  [::my-id {:level :warn}]) {:defaults {:level :info}, :id ::my-id, :level :warn}))

         (is (= (impl/signal-opts `signal! {:level :info} :id :level :asc [               ::my-id]) {:defaults {:level :info}, :id ::my-id}))
         (is (= (impl/signal-opts `signal! {:level :info} :id :level :asc [:warn          ::my-id]) {:defaults {:level :info}, :id ::my-id, :level :warn}))
         (is (= (impl/signal-opts `signal! {:level :info} :id :level :asc [{:level :warn} ::my-id]) {:defaults {:level :info}, :id ::my-id, :level :warn}))]))

   (testing "event!" ; id + ?level => allowed?
     [(let [[rv [sv]] (ws (tel/event! :id1                ))] [(is (= rv true)) (is (sm?  sv {:kind :event, :line :submap/ex, :level :info, :id :id1}))])
      (let [[rv [sv]] (ws (tel/event! :id1          :warn ))] [(is (= rv true)) (is (sm?  sv {:kind :event, :line :submap/ex, :level :warn, :id :id1}))])
      (let [[rv [sv]] (ws (tel/event! :id1 {:level  :warn}))] [(is (= rv true)) (is (sm?  sv {:kind :event, :line :submap/ex, :level :warn, :id :id1}))])
      (let [[rv [sv]] (ws (tel/event! :id1 {:allow? false}))] [(is (= rv nil))  (is (nil? sv))])])

   (testing "error!" ; error + ?id => error
     [(let [[rv [sv]] (ws (tel/error!                 ex1))] [(is (= rv ex1)) (is (sm?  sv {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id  nil}))])
      (let [[rv [sv]] (ws (tel/error!           :id1  ex1))] [(is (= rv ex1)) (is (sm?  sv {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id :id1}))])
      (let [[rv [sv]] (ws (tel/error! {:id      :id1} ex1))] [(is (= rv ex1)) (is (sm?  sv {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id :id1}))])
      (let [[rv [sv]] (ws (tel/error! {:allow? false} ex1))] [(is (= rv ex1)) (is (nil? sv))])])

   (testing "log!" ; msg + ?level => allowed?
     [(let [[rv [sv]] (ws (tel/log!                 "msg"))] [(is (= rv true)) (is (sm?  sv {:kind :log, :line :submap/ex, :msg_ "msg", :level :info}))])
      (let [[rv [sv]] (ws (tel/log!          :warn  "msg"))] [(is (= rv true)) (is (sm?  sv {:kind :log, :line :submap/ex, :msg_ "msg", :level :warn}))])
      (let [[rv [sv]] (ws (tel/log! {:level  :warn} "msg"))] [(is (= rv true)) (is (sm?  sv {:kind :log, :line :submap/ex, :msg_ "msg", :level :warn}))])
      (let [[rv [sv]] (ws (tel/log! {:allow? false} "msg"))] [(is (= rv nil))  (is (nil? sv))])])

   (testing "trace!" ; run + ?id => run result (value or throw)
     [(let [[rv     [sv]] (wsf (tel/trace!                 (+ 1 2)))] [(is (= rv 3))   (is (sm?  sv {:kind :trace, :line :submap/ex, :level :info, :id  nil, :msg_ "(+ 1 2) => 3"}))])
      (let [[rv     [sv]] (ws  (tel/trace!      {:msg nil} (+ 1 2)))] [(is (= rv 3))   (is (sm?  sv {:kind :trace, :line :submap/ex, :level :info, :id  nil, :msg_ nil}))])
      (let [[rv     [sv]] (ws  (tel/trace!           :id1  (+ 1 2)))] [(is (= rv 3))   (is (sm?  sv {:kind :trace, :line :submap/ex, :level :info, :id :id1}))])
      (let [[rv     [sv]] (ws  (tel/trace!      {:id :id1} (+ 1 2)))] [(is (= rv 3))   (is (sm?  sv {:kind :trace, :line :submap/ex, :level :info, :id :id1}))])
      (let [[[_ re] [sv]] (wst (tel/trace!           :id1   (ex1!)))] [(is (= re ex1)) (is (sm?  sv {:kind :trace, :line :submap/ex, :level :info, :id :id1, :error ex1-pred}))])
      (let [[rv     [sv]] (ws  (tel/trace! {:allow? false} (+ 1 2)))] [(is (= rv 3))   (is (nil? sv))])])

   (testing "spy" ; run + ?level => run result (value or throw)
     [(let [[rv     [sv]] (wsf (tel/spy!                 (+ 1 2)))] [(is (= rv 3))   (is (sm?  sv {:kind :spy, :line :submap/ex, :level :info, :msg_ "(+ 1 2) => 3"}))])
      (let [[rv     [sv]] (wsf (tel/spy!      {:msg nil} (+ 1 2)))] [(is (= rv 3))   (is (sm?  sv {:kind :spy, :line :submap/ex, :level :info, :msg_ nil}))])
      (let [[rv     [sv]] (ws  (tel/spy!          :warn  (+ 1 2)))] [(is (= rv 3))   (is (sm?  sv {:kind :spy, :line :submap/ex, :level :warn}))])
      (let [[rv     [sv]] (ws  (tel/spy!  {:level :warn} (+ 1 2)))] [(is (= rv 3))   (is (sm?  sv {:kind :spy, :line :submap/ex, :level :warn}))])
      (let [[[_ re] [sv]] (wst (tel/spy!          :warn   (ex1!)))] [(is (= re ex1)) (is (sm?  sv {:kind :spy, :line :submap/ex, :level :warn, :error ex1-pred}))])
      (let [[rv     [sv]] (ws  (tel/spy! {:allow? false} (+ 1 2)))] [(is (= rv 3))   (is (nil? sv))])])

   (testing "catch->error!" ; form + ?id => run value or ?return
     [(let [[rv     [sv]] (ws  (tel/catch->error!                   (+ 1 2)))] [(is (= rv 3))    (is (nil? sv))])
      (let [[rv     [sv]] (ws  (tel/catch->error!                    (ex1!)))] [(is (= rv nil))  (is (sm? sv {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id  nil}))])
      (let [[rv     [sv]] (ws  (tel/catch->error!             :id1   (ex1!)))] [(is (= rv nil))  (is (sm? sv {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id :id1}))])
      (let [[rv     [sv]] (ws  (tel/catch->error! {:id        :id1}  (ex1!)))] [(is (= rv nil))  (is (sm? sv {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id :id1}))])
      (let [[[_ re] [sv]] (wst (tel/catch->error! {:rethrow?  true}  (ex1!)))] [(is (= re ex1))  (is (sm? sv {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id  nil}))])
      (let [[rv     [sv]] (ws  (tel/catch->error! {:catch-val :foo}  (ex1!)))] [(is (= rv :foo)) (is (sm? sv {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id  nil}))])
      (let [[rv     [sv]] (ws  (tel/catch->error! {:catch-val :foo} (+ 1 2)))] [(is (= rv 3))    (is (nil? sv))])
      (let [[rv     [sv]] (ws  (tel/catch->error! {:catch-val :foo ; Overrides `:rethrow?`
                                                   :rethrow?  true} (+ 1 2)))] [(is (= rv 3))    (is (nil? sv))])

      (let [[rv     [sv]] (ws  (tel/catch->error!  {:catch-val     nil
                                                    :catch-sym     my-err
                                                    :data {:my-err my-err}} (ex1!)))]
        [(is (= rv nil)) (is (sm? sv {:kind :error, :data {:my-err ex1-pred}}))])])

   #?(:clj
      (testing "uncaught->error!"
        (let [sv_ (atom ::nx)]
          [(do (enc/set-var-root! impl/*sig-handlers* [(sigs/wrap-handler "h1" (fn h1 [x] (reset! sv_ x)) nil {:async nil})]) :set-handler)
           ;;
           (is (nil? (tel/uncaught->error!)))
           (is (do (.join (impl/threaded (ex1!))) (sm? @sv_ {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id  nil})))
           ;;
           (is (nil? (tel/uncaught->error! :id1)))
           (is (do (.join (impl/threaded (ex1!))) (sm? @sv_ {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id :id1})))
           ;;
           (is (nil? (tel/uncaught->error! {:id :id1})))
           (is (do (.join (impl/threaded (ex1!))) (sm? @sv_ {:kind :error, :line :submap/ex, :level :error, :error ex1-pred, :id :id1})))
           ;;
           (do (enc/set-var-root! impl/*sig-handlers* nil) :unset-handler)])))])

;;;; Interop

(comment (def ^org.slf4j.Logger sl (org.slf4j.LoggerFactory/getLogger "MyTelemereSLF4JLogger")))

#?(:clj
   (deftest _interop
     [(testing "`clojure.tools.logging` -> Telemere"
        [(is (sm? (tel/check-interop) {:tools-logging {:present? true, :sending->telemere? true, :telemere-receiving? true}}))
         (is (sm? (ws1 (ctl/info "Hello" "x" "y")) {:level :info, :location nil, :ns nil, :kind :log, :id :taoensso.telemere/tools-logging, :msg_ "Hello x y"}))
         (is (sm? (ws1 (ctl/warn "Hello" "x" "y")) {:level :warn, :location nil, :ns nil, :kind :log, :id :taoensso.telemere/tools-logging, :msg_ "Hello x y"}))
         (is (sm? (ws1 (ctl/error ex1 "An error")) {:level :error, :error ex1}) "Errors")])

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
           (sm? (ws1 (tel/with-out->telemere (println "Hello" "x" "y")))
             {:level :info, :location nil, :ns nil, :kind :system/out, :msg_ "Hello x y"}))])

      (testing "SLF4J -> Telemere"
        [(is (sm? (tel/check-interop) {:slf4j {:present? true, :sending->telemere? true, :telemere-receiving? true}}))
         (let [^org.slf4j.Logger sl (org.slf4j.LoggerFactory/getLogger "MyTelemereSLF4JLogger")]
           [(testing "Basics"
              [(is (sm? (ws1 (.info sl "Hello"))               {:level :info, :location nil, :ns nil, :kind :log, :id :taoensso.telemere/slf4j, :msg_ "Hello"}) "Legacy API: info basics")
               (is (sm? (ws1 (.warn sl "Hello"))               {:level :warn, :location nil, :ns nil, :kind :log, :id :taoensso.telemere/slf4j, :msg_ "Hello"}) "Legacy API: warn basics")
               (is (sm? (ws1 (-> (.atInfo sl) (.log "Hello"))) {:level :info, :location nil, :ns nil, :kind :log, :id :taoensso.telemere/slf4j, :msg_ "Hello"}) "Fluent API: info basics")
               (is (sm? (ws1 (-> (.atWarn sl) (.log "Hello"))) {:level :warn, :location nil, :ns nil, :kind :log, :id :taoensso.telemere/slf4j, :msg_ "Hello"}) "Fluent API: warn basics")])

            (testing "Message formatting"
              (let [msgp "X is {} and Y is {}", expected {:msg_ "X is x and Y is y", :data {:slf4j/args ["x" "y"]}}]
                [(is (sm? (ws1 (.info sl msgp "x" "y"))                                                           expected) "Legacy API: formatted message, raw args")
                 (is (sm? (ws1 (-> (.atInfo sl) (.setMessage msgp) (.addArgument "x") (.addArgument "y") (.log))) expected) "Fluent API: formatted message, raw args")]))

            (is (sm? (ws1 (-> (.atInfo sl) (.addKeyValue "k1" "v1") (.addKeyValue "k2" "v2") (.log))) {:data {:slf4j/kvs {"k1" "v1", "k2" "v2"}}}) "Fluent API: kvs")

            (testing "Markers"
              (let [m1 (slf4j/est-marker! "M1")
                    m2 (slf4j/est-marker! "M2")
                    cm (slf4j/est-marker! "Compound" "M1" "M2")]

                [(is (sm? (ws1 (.info sl cm "Hello"))                                    {:data #:slf4j{:marker-names #{"Compound" "M1" "M2"}}}) "Legacy API: markers")
                 (is (sm? (ws1 (-> (.atInfo sl) (.addMarker m1) (.addMarker cm) (.log))) {:data #:slf4j{:marker-names #{"Compound" "M1" "M2"}}}) "Fluent API: markers")]))

            (testing "Errors"
              [(is (sm? (ws1 (.warn sl "An error" ^Throwable ex1))     {:level :warn, :error ex1}) "Legacy API: errors")
               (is (sm? (ws1 (-> (.atWarn sl) (.setCause ex1) (.log))) {:level :warn, :error ex1}) "Fluent API: errors")])

            (testing "MDC (Mapped Diagnostic Context)"
              (with-open [_   (org.slf4j.MDC/putCloseable "k1" "v1")]
                (with-open [_ (org.slf4j.MDC/putCloseable "k2" "v2")]
                  [(is (sm? (ws1 (->          sl  (.info "Hello"))) {:level :info, :ctx {"k1" "v1", "k2" "v2"}}) "Legacy API: MDC")
                   (is (sm? (ws1 (-> (.atInfo sl) (.log  "Hello"))) {:level :info, :ctx {"k1" "v1", "k2" "v2"}}) "Fluent API: MDC")])))])])]))

;;;; Utils

(deftest _utils
  [(testing "Basic utils"
     [(is (= (utils/upper-qn :foo/bar) "FOO/BAR"))

      (is (= (utils/format-level :info) "INFO"))
      (is (= (utils/format-level     8) "LEVEL:8"))

      (is (= (utils/format-id "foo.bar" :foo.bar/qux) "::qux"))
      (is (= (utils/format-id "foo.baz" :foo.bar/qux) ":foo.bar/qux"))])

   (testing "error-signal?"
     [(is (= (utils/error-signal? {:error    nil}) false))
      (is (= (utils/error-signal? {:error    ex1}) true))
      (is (= (utils/error-signal? {:kind  :error}) true))
      (is (= (utils/error-signal? {:level :error}) true))
      (is (= (utils/error-signal? {:level :fatal}) true))
      (is (= (utils/error-signal? {:error?  true}) true))])

   (testing "Formatters, etc."
     [(is (= (utils/error-in-signal->chain {:level :info, :error ex2})
            {:level :info, :error [{:type ex2-type, :msg "Ex2", :data {:k2 "v2"}}
                                   {:type ex2-type, :msg "Ex1", :data {:k1 "v1"}}]}))

      (is (= (utils/minify-signal {:level :info, :location {:ns "ns"}, :file "file"}) {:level :info}))
      (is (= ((utils/format-nsecs-fn) 1.5e9) "1.50s")) ; More tests in Encore
      (is (= ((utils/format-instant-fn)  t0) "2024-06-09T21:15:20.170Z"))

      (is (enc/str-starts-with? ((utils/format-error-fn) ex2)
            #?(:clj  "  Root: clojure.lang.ExceptionInfo - Ex1\n  data: {:k1 \"v1\"}\n\nCaused: clojure.lang.ExceptionInfo - Ex2\n  data: {:k2 \"v2\"}\n\nRoot stack trace:\n"
               :cljs "  Root: cljs.core/ExceptionInfo - Ex1\n  data: {:k1 \"v1\"}\n\nCaused: cljs.core/ExceptionInfo - Ex2\n  data: {:k2 \"v2\"}\n\nRoot stack trace:\n")))

      (let [sig     (tel/with-signal (tel/event! ::ev-id {:instant t0}))
            prelude ((utils/format-signal-prelude-fn) sig)] ; "2024-06-09T21:15:20.170Z INFO EVENT taoensso.telemere-tests(592,35) ::ev-id"
        [(is (enc/str-starts-with? prelude "2024-06-09T21:15:20.170Z INFO EVENT"))
         (is (enc/str-ends-with?   prelude "::ev-id"))
         (is (string? (re-find #"taoensso.telemere-tests\(\d+,\d+\)" prelude)))])

      (testing "format-signal->edn-fn"
        (let [sig  (update (tel/with-signal (tel/event! ::ev-id {:instant t0})) :instant enc/inst->udt)
              sig* (enc/read-edn ((utils/format-signal->edn-fn) sig))]
          (is
            (enc/submap? sig*
              {:schema 1, :kind :event, :id ::ev-id, :level :info,
               :ns      "taoensso.telemere-tests"
               :instant udt0
               :line    (enc/pred enc/int?)
               :column  (enc/pred enc/int?)}))))

      (testing "format-signal->json-fn"
        (let [sig (update (tel/with-signal (tel/event! ::ev-id {:instant t0})) :instant enc/inst->udt)
              pr-json-fn
              #?(:clj  jsonista.core/write-value-as-string
                 :cljs (fn [x] (.stringify js/JSON (clj->js x))))

              read-json-fn
              #?(:clj  jsonista.core/read-value
                 :cljs (fn [x] (js->clj (js/JSON.parse x))))

              sig*
              (read-json-fn
                ((utils/format-signal->json-fn
                   {:pr-json-fn pr-json-fn}) sig))]

          ;; TODO why is Cljs id coming back as "ev-id"?
          (is
            (enc/submap? sig*
              {"schema" 1, "kind" "event", "id" "taoensso.telemere-tests/ev-id",
               "level" "info", "ns" "taoensso.telemere-tests",
               "instant" udt0
               "line"    (enc/pred enc/int?)
               "column"  (enc/pred enc/int?)}))))

      (testing "format-signal->str-fn"
        (let [sig (tel/with-signal (tel/event! ::ev-id {:instant t0}))]
          (is (enc/str-starts-with? ((utils/format-signal->str-fn) sig)
                "2024-06-09T21:15:20.170Z INFO EVENT"))))])])

;;;; Handlers

;; TODO

;;;;

#?(:cljs
   (defmethod test/report [:cljs.test/default :end-run-tests] [m]
     (when-not (test/successful? m)
       ;; Trigger non-zero `lein test-cljs` exit code for CI
       (throw (ex-info "ClojureScript tests failed" {})))))

#?(:cljs (test/run-tests))
