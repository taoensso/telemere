(ns taoensso.telemere-tests
  (:require
   [clojure.test            :as test :refer [deftest testing is]]
   [taoensso.encore         :as enc  :refer [throws? submap?] :rename {submap? sm?}]
   [taoensso.encore.signals :as sigs]
   [taoensso.telemere       :as tel]
   [taoensso.telemere.impl  :as impl
    :refer  [signal!       with-signal           with-signals]
    :rename {signal! sig!, with-signal with-sig, with-signals with-sigs}]

   [taoensso.telemere.utils                  :as utils]
   [taoensso.telemere.timbre                 :as timbre]
   #_[taoensso.telemere.tools-logging        :as tools-logging]
   #_[taoensso.telemere.streams              :as streams]
   #?(:clj [taoensso.telemere.slf4j          :as slf4j])
   #?(:clj [taoensso.telemere.open-telemetry :as otel])
   #?(:clj [clojure.tools.logging            :as ctl])

   #?(:default [taoensso.telemere.handlers.console :as handlers:console])
   #?(:clj     [taoensso.telemere.handlers.file    :as handlers:file])))

(comment
  (remove-ns      'taoensso.telemere-tests)
  (test/run-tests 'taoensso.telemere-tests))

;;;; Utils

(do
  (def ^:dynamic *dynamic-var* nil)

  (def   t0s "2024-06-09T21:15:20.170Z")
  (def   t0  (enc/as-inst t0s))
  (def udt0  (enc/as-udt  t0))

  (def  ex-info-type (#'enc/ex-type (ex-info "" {})))
  (def  ex1 (ex-info "Ex1" {}))
  (def  ex2 (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"})))
  (defn ex1! [] (throw ex1))

  (defn ex1?     [x] (= (enc/ex-root x) ex1))
  (def pex1?     (enc/pred ex1?))
  (def pstr?     (enc/pred string?))
  (def pnat-int? (enc/pred enc/nat-int?)))

(let [rt-sig-filter_ (atom nil)
      sig-handlers_  (atom nil)]

  (test/use-fixtures :once
    (enc/test-fixtures
      {:before
       (fn []
         (reset! rt-sig-filter_ impl/*rt-sig-filter*)
         (reset! sig-handlers_  impl/*sig-handlers*)
         (enc/set-var-root!     impl/*sig-handlers*  nil)
         (enc/set-var-root!     impl/*rt-sig-filter* nil))

       :after
       (fn []
         (enc/set-var-root! impl/*rt-sig-filter* @rt-sig-filter_)
         (enc/set-var-root! impl/*sig-handlers*  @sig-handlers_))})))

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
  [(is (= (with-sigs (sig! {:level :info, :elide? true               })) [[nil nil] nil]) "With compile-time elision")
   (is (= (with-sigs (sig! {:level :info, :elide? true,  :run (+ 1 2)})) [[3   nil] nil]) "With compile-time elision, run-form")
   (is (= (with-sigs (sig! {:level :info, :allow? false              })) [[nil nil] nil]) "With runtime suppression")
   (is (= (with-sigs (sig! {:level :info, :allow? false, :run (+ 1 2)})) [[3   nil] nil]) "With runtime suppression, run-form")

   (is (->> (sig! {:level :info, :elide? true,  :run (ex1!)}) (throws? :ex-info "Ex1")) "With compile-time elision, throwing run-form")
   (is (->> (sig! {:level :info, :allow? false, :run (ex1!)}) (throws? :ex-info "Ex1")) "With runtime suppression,  throwing run-form")

   (let [[[rv1 _] [sv1]] (with-sigs (sig! {:level :info              }))
         [[rv2 -] [sv2]] (with-sigs (sig! {:level :info, :run (+ 1 2)}))]

     [(is (= rv1 true)) (is (sm? sv1 {:ns "taoensso.telemere-tests", :level :info, :run-form nil,      :run-val nil, :run-nsecs nil}))
      (is (= rv2    3)) (is (sm? sv2 {:ns "taoensso.telemere-tests", :level :info, :run-form '(+ 1 2), :run-val 3,   :run-nsecs pnat-int?}))])

   (testing "Nested signals"
     (let [[[outer-rv _] [outer-sv]] (with-sigs (sig! {:level :info, :run (with-sigs (sig! {:level :warn, :run "inner-run"}))}))
           [[inner-rv _] [inner-sv]] outer-rv]

       [(is (= inner-rv "inner-run"))
        (is (sm? inner-sv {:level :warn, :run-val "inner-run"}))
        (is (sm? outer-sv {:level :info  :run-val [[inner-rv nil] [inner-sv]]}))]))

   (testing "Instants"
     (let [sv1 (with-sig (sig! {:level :info                             }))
           sv2 (with-sig (sig! {:level :info, :run (reduce + (range 1e6))}))
           sv3 (with-sig (sig! {:level :info, :run (reduce + (range 1e6))
                                :inst   ; Allow custom instant
                                #?(:clj  java.time.Instant/EPOCH
                                   :cljs (js/Date. 0))}))]

       [(let [{start :inst, end :end-inst} sv1]
          [(is (enc/inst? start))
           (is (nil?      end))])

        (let [{start :inst, end :end-inst} sv2]
          [(is (enc/inst?  start))
           (is (enc/inst?  end))
           (is (> (inst-ms end) (inst-ms start)))])

        (let [{start :inst, end :end-inst} sv3]
          [(is (enc/inst?  start))
           (is (enc/inst?  end))
           (is (= (inst-ms start) 0)               "Respect custom instant")
           (is (> (inst-ms end)   (inst-ms start)) "End instant is start + run-nsecs")
           (is (< (inst-ms end)   1e6)             "End instant is start + run-nsecs")])]))

   (testing "Support arb extra user kvs"
     (let [sv (with-sig (sig! {:level :info, :my-k1 "v1", :my-k2 "v2"}))]
       (is          (sm? sv   {:level :info, :my-k1 "v1", :my-k2 "v2"
                               :kvs         {:my-k1 "v1", :my-k2 "v2"}}))))

   (testing "`:msg` basics"
     (let [c               (enc/counter)
           [[rv1 _] [sv1]] (with-sigs :raw nil (sig! {:level :info, :run (c), :msg             "msg1"}))         ; No     delay
           [[rv2 _] [sv2]] (with-sigs :raw nil (sig! {:level :info, :run (c), :msg        [    "msg2:"  (c)]}))  ; Auto   delay
           [[rv3 _] [sv3]] (with-sigs :raw nil (sig! {:level :info, :run (c), :msg (delay (str "msg3: " (c)))})) ; Manual delay
           [[rv4 _] [sv4]] (with-sigs :raw nil (sig! {:level :info, :run (c), :msg        (str "msg4: " (c))}))  ; No     delay
           [[rv5 _] [sv5]] (with-sigs :raw nil (sig! {:level :info, :run (c), :msg        (str "msg5: " (c)), :allow? false}))]

       [(is (= rv1 0)) (is (=  (:msg_ sv1) "msg1"))
        (is (= rv2 1)) (is (= @(:msg_ sv2) "msg2: 6"))
        (is (= rv3 2)) (is (= @(:msg_ sv3) "msg3: 7"))
        (is (= rv4 3)) (is (=  (:msg_ sv4) "msg4: 4"))
        (is (= rv5 5)) (is (=  (:msg_ sv5) nil))
        (is (= @c  8)  "5x run + 3x message (1x suppressed)")]))

   (testing "`:data` basics"
     (vec
       (for [dk [:data :my-k1]] ; User kvs share same behaviour as data
         (let [c               (enc/counter)
               [[rv1 _] [sv1]] (with-sigs :raw nil (sig! {:level :info, :run (c), dk        {:c1 (c)}}))
               [[rv2 _] [sv2]] (with-sigs :raw nil (sig! {:level :info, :run (c), dk (delay {:c2 (c)})}))
               [[rv3 _] [sv3]] (with-sigs :raw nil (sig! {:level :info, :run (c), dk        {:c3 (c)},  :allow? false}))
               [[rv4 _] [sv4]] (with-sigs :raw nil (sig! {:level :info, :run (c), dk (delay {:c4 (c)}), :allow? false}))
               [[rv5 _] [sv5]] (with-sigs :raw nil (sig! {:level :info, :run (c), dk        [:c5 (c)]}))
               [[rv6 _] [sv6]] (with-sigs :raw nil (sig! {:level :info, :run (c), dk (delay [:c6 (c)])}))]

           [(is (= rv1 0)) (is (=        (get sv1 dk)  {:c1 1}))
            (is (= rv2 2)) (is (= (force (get sv2 dk)) {:c2 8}))
            (is (= rv3 3)) (is (=        (get sv3 dk)  nil))
            (is (= rv4 4)) (is (= (force (get sv4 dk)) nil))
            (is (= rv5 5)) (is (=        (get sv5 dk)  [:c5 6]) "`:data` can be any type")
            (is (= rv6 7)) (is (= (force (get sv6 dk)) [:c6 9]) "`:data` can be any type")
            (is (= @c  10) "6x run + 4x data (2x suppressed)")]))))

   (testing "`:let` basics"
     (let [c               (enc/counter)
           [[rv1 _] [sv1]] (with-sigs :raw nil (sig! {:level :info, :run (c), :let [_ (c)]}))
           [[rv2 _] [sv2]] (with-sigs :raw nil (sig! {:level :info, :run (c), :let [_ (c)], :allow? false}))
           [[rv3 _] [sv3]] (with-sigs :raw nil (sig! {:level :info, :run (c), :let [_ (c)]}))]

       [(is (= rv1 0))
        (is (= rv2 2))
        (is (= rv3 3))
        (is (= @c  5) "3x run + 2x let (1x suppressed)")]))

   (testing "`:let` + `:msg`"
     (let [c               (enc/counter)
           [[rv1 _] [sv1]] (with-sigs :raw nil (sig! {:level :info, :run (c), :let [n (c)], :msg             "msg1"}))               ; No     delay
           [[rv2 _] [sv2]] (with-sigs :raw nil (sig! {:level :info, :run (c), :let [n (c)], :msg        [    "msg2:"  n     (c)]}))  ; Auto   delay
           [[rv3 _] [sv3]] (with-sigs :raw nil (sig! {:level :info, :run (c), :let [n (c)], :msg (delay (str "msg3: " n " " (c)))})) ; Manual delay
           [[rv4 _] [sv4]] (with-sigs :raw nil (sig! {:level :info, :run (c), :let [n (c)], :msg        (str "msg4: " n " " (c))}))  ; No     delay
           [[rv5 _] [sv5]] (with-sigs :raw nil (sig! {:level :info, :run (c), :let [n (c)], :msg        (str "msg5: " n " " (c)), :allow? false}))]

       [(is (= rv1 0)) (is (=  (:msg_ sv1) "msg1"))
        (is (= rv2 2)) (is (= @(:msg_ sv2) "msg2: 3 10"))
        (is (= rv3 4)) (is (= @(:msg_ sv3) "msg3: 5 11"))
        (is (= rv4 6)) (is (=  (:msg_ sv4) "msg4: 7 8"))
        (is (= rv5 9)) (is (=  (:msg_ sv5) nil))
        (is (= @c  12) "5x run + 4x let (1x suppressed) + 3x msg (1x suppressed)")]))

   (testing "`:do` + `:let` + `:data`/`:my-k1`"
     (vec
       (for [dk [:data :my-k1]]
         (let [c               (enc/counter)
               [[rv1 _] [sv1]] (with-sigs :raw nil (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk        {:n n, :c1 (c)}}))
               [[rv2 _] [sv2]] (with-sigs :raw nil (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk (delay {:n n, :c2 (c)})}))
               [[rv3 _] [sv3]] (with-sigs :raw nil (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk        {:n n, :c3 (c)},  :allow? false}))
               [[rv4 _] [sv4]] (with-sigs :raw nil (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk (delay {:n n, :c4 (c)}), :allow? false}))
               [[rv5 _] [sv5]] (with-sigs :raw nil (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk        [:n n, :c5 (c)]}))
               [[rv6 _] [sv6]] (with-sigs :raw nil (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk (delay [:n n, :c6 (c)])}))]

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
         (let [c               (enc/counter)
               [[rv1 _] [sv1]] (with-sigs :raw nil (let [n (c)] (sig! {:level :info, :run (c), dk        {:n n, :c1 (c)}})))
               [[rv2 _] [sv2]] (with-sigs :raw nil (let [n (c)] (sig! {:level :info, :run (c), dk (delay {:n n, :c2 (c)})})))
               [[rv3 _] [sv3]] (with-sigs :raw nil (let [n (c)] (sig! {:level :info, :run (c), dk        {:n n, :c3 (c)},  :allow? false})))
               [[rv4 _] [sv4]] (with-sigs :raw nil (let [n (c)] (sig! {:level :info, :run (c), dk (delay {:n n, :c4 (c)}), :allow? false})))
               [[rv5 _] [sv5]] (with-sigs :raw nil (let [n (c)] (sig! {:level :info, :run (c), dk        [:n n, :c5 (c)]})))
               [[rv6 _] [sv6]] (with-sigs :raw nil (let [n (c)] (sig! {:level :info, :run (c), dk (delay [:n n, :c6 (c)])})))]

           [(is (= rv1 1))  (is (=        (get sv1 dk)  {:n 0, :c1 2}))
            (is (= rv2 4))  (is (= (force (get sv2 dk)) {:n 3, :c2 14}))
            (is (= rv3 6))  (is (=        (get sv3 dk)  nil))
            (is (= rv4 8))  (is (= (force (get sv4 dk)) nil))
            (is (= rv5 10)) (is (=        (get sv5 dk)  [:n 9,  :c5 11]))
            (is (= rv6 13)) (is (= (force (get sv6 dk)) [:n 12, :c6 15]))
            (is (= @c  16)  "6x run + 6x let (0x suppressed) + 4x data (2x suppressed)")]))))

   (testing "Call middleware"
     (let [c               (enc/counter)
           [[rv1 _] [sv1]] (with-sigs :raw nil (sig! {:level :info, :run (c), :middleware [#(assoc % :m1 (c)) #(assoc % :m2 (c))]}))
           [[rv2 _] [sv2]] (with-sigs :raw nil (sig! {:level :info, :run (c), :middleware [#(assoc % :m1 (c)) #(assoc % :m2 (c))], :allow? false}))
           [[rv3 _] [sv3]] (with-sigs :raw nil (sig! {:level :info, :run (c), :middleware [#(assoc % :m1 (c)) #(assoc % :m2 (c))]}))
           [[rv4 _] [sv4]] (with-sigs :raw nil (sig! {:level :info,           :middleware [(fn [_] "signal-value")]}))]

       [(is (= rv1 0))    (is (sm? sv1 {:m1 1 :m2 2}))
        (is (= rv2 3))    (is (nil?    sv2))
        (is (= rv3 4))    (is (sm? sv3 {:m1 5 :m2 6}))
        (is (= rv4 true)) (is (=       sv4 "signal-value"))
        (is (= @c  7)     "3x run + 4x middleware")]))

   #?(:clj
      (testing "Printing"
        (let [sv1 (with-sig (sig! {:level :info, :run (+ 1 2), :my-k1 :my-v1}))
              sv1 ; Ensure instants are printable
              (-> sv1
                (update :inst     enc/inst->udt)
                (update :end-inst enc/inst->udt))]

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

      [(is (->> (sig! {:level :info, :when  (ex1!)}) (throws? :ex-info "Ex1")) "`~filterable-expansion/allow` throws at call")
       (is (->> (sig! {:level :info, :inst  (ex1!)}) (throws? :ex-info "Ex1")) "`~inst-form`                  throws at call")
       (is (->> (sig! {:level :info, :id    (ex1!)}) (throws? :ex-info "Ex1")) "`~id-form`                    throws at call")
       (is (->> (sig! {:level :info, :uid   (ex1!)}) (throws? :ex-info "Ex1")) "`~uid-form`                   throws at call")
       (is (->> (sig! {:level :info, :run   (ex1!)}) (throws? :ex-info "Ex1")) "`~run-form` rethrows at call")
       (is (sm? @sv_  {:level :info, :error pex1?})                            "`~run-form` rethrows at call *after* dispatch")

       (testing "`@signal-value_`: trap with wrapped handler"
         [(testing "Throwing `~let-form`"
            (reset-state!)
            [(is (true? (sig! {:level :info, :let [_ (ex1!)]})))
             (is (= @sv_ :nx))
             (is (sm? @error_ {:handler-id :hid1, :error pex1?}))])

          (testing "Throwing call middleware"
            (reset-state!)
            [(is (true? (sig! {:level :info, :middleware [(fn [_] (ex1!))]})))
             (is (= @sv_ :nx))
             (is (sm? @error_ {:handler-id :hid1, :error pex1?}))])

          (testing "Throwing handler middleware"
            (reset-state!)
            (binding [*throwing-handler-middleware?* true]
              [(is (true? (sig! {:level :info})))
               (is (= @sv_ :nx))
               (is (sm? @error_ {:handler-id :hid1, :error pex1?}))]))

          (testing "Throwing `@data_`"
            (reset-state!)
            [(is (true? (sig! {:level :info, :data (delay (ex1!))})))
             (is (= @sv_ :nx))
             (is (sm? @error_ {:handler-id :hid1, :error pex1?}))])

          (testing "Throwing user kv"
            (reset-state!)
            [(is (true? (sig! {:level :info, :my-k1 (ex1!)})))
             (is (= @sv_ :nx))
             (is (sm? @error_ {:handler-id :hid1, :error pex1?}))])])])))

(deftest _ctx
  (testing "Context (`*ctx*`)"
    [(is (= (binding [tel/*ctx* "my-ctx"] tel/*ctx*) "my-ctx") "Supports manual `binding`")
     (is (= (tel/with-ctx       "my-ctx"  tel/*ctx*) "my-ctx") "Supports any data type")

     (is (= (tel/with-ctx "my-ctx1"       (tel/with-ctx+ nil                        tel/*ctx*)) "my-ctx1")              "nil update => keep old-ctx")
     (is (= (tel/with-ctx "my-ctx1"       (tel/with-ctx+ (fn [old] [old "my-ctx2"]) tel/*ctx*)) ["my-ctx1" "my-ctx2"])  "fn  update => apply")
     (is (= (tel/with-ctx {:a :A1 :b :B1} (tel/with-ctx+ {:a :A2 :c :C2}            tel/*ctx*)) {:a :A2 :b :B1 :c :C2}) "map update => merge")

     (let [sv (with-sig (sig! {:level :info, :ctx "my-ctx"}))] (is (sm? sv {:ctx "my-ctx"}) "Can be set via call opt"))]))

(deftest _tracing
  (testing "Tracing"
    [(let [sv (with-sig (sig! {:level :info                      }))] (is (sm? sv {:parent nil})))
     (let [sv (with-sig (sig! {:level :info, :parent {:id   :id0}}))] (is (sm? sv {:parent {:id :id0       :uid :submap/nx}}) "`:parent/id`  can be set via call opt"))
     (let [sv (with-sig (sig! {:level :info, :parent {:uid :uid0}}))] (is (sm? sv {:parent {:id :submap/nx :uid      :uid0}}) "`:parent/uid` can be set via call opt"))

     (testing "Auto call id, uid"
       (let [sv (with-sig (sig! {:level :info, :parent {:id :id0, :uid :uid0}, :run impl/*trace-parent*, :data impl/*trace-parent*}))]
         [(is (sm? sv {:parent  {:id :id0, :uid :uid0}}))
          (is (sm? sv {:run-val {:id nil,  :uid (get sv :uid ::nx)}}) "`*trace-parent*`     visible to run-form, bound to call's auto {:keys [id uid]}")
          (is (sm? sv {:data    nil})                                 "`*trace-parent*` not visible to data-form ")]))

     (testing "Manual call id, uid"
       (let [sv (with-sig (sig! {:level :info, :parent {:id :id0, :uid :uid0}, :id :id1, :uid :uid1, :run impl/*trace-parent*, :data impl/*trace-parent*}))]
         [(is (sm? sv {:parent  {:id :id0, :uid :uid0}}))
          (is (sm? sv {:run-val {:id :id1, :uid :uid1}}) "`*trace-parent*`     visible to run-form, bound to call's auto {:keys [id uid]}")
          (is (sm? sv {:data    nil})                    "`*trace-parent*` not visible to data-form ")]))

     (testing "Tracing can be disabled via call opt"
       (let [sv (with-sig (sig! {:level :info, :parent {:id :id0, :uid :uid0}, :id :id1, :uid :uid1, :run impl/*trace-parent*, :data impl/*trace-parent*, :trace? false}))]
         [(is (sm? sv {:parent  {:id :id0, :uid :uid0}}))
          (is (sm? sv {:run-val nil}))]))

     (testing "Signal nesting"
       (let [[[outer-rv _] [outer-sv]]
             (with-sigs
               (sig! {                       :level :info, :id :id1, :uid :uid1,
                      :run (with-sigs (sig! {:level :info, :id :id2, :uid :uid2, :run impl/*trace-parent*}))}))

             [[inner-rv _] [inner-sv]] outer-rv]

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
        [(is (= (impl/signal-opts `foo! {:level :info} :id :level :dsc  [::my-id               ]) {:level :info, :id ::my-id}))
         (is (= (impl/signal-opts `foo! {:level :info} :id :level :dsc  [::my-id         :warn ]) {:level :warn, :id ::my-id}))
         (is (= (impl/signal-opts `foo! {:level :info} :id :level :dsc  [::my-id {:level :warn}]) {:level :warn, :id ::my-id}))

         (is (= (impl/signal-opts `foo! {:level :info} :id :level :asc [               ::my-id]) {:level :info, :id ::my-id}))
         (is (= (impl/signal-opts `foo! {:level :info} :id :level :asc [:warn          ::my-id]) {:level :warn, :id ::my-id}))
         (is (= (impl/signal-opts `foo! {:level :info} :id :level :asc [{:level :warn} ::my-id]) {:level :warn, :id ::my-id}))

         (is (= (impl/signal-catch-opts {:id :main-id, :location {:ns "ns"}, :catch->error           true})  [{:id :main-id, :location {:ns "ns"}} {:location {:ns "ns"}, :id :main-id}]))
         (is (= (impl/signal-catch-opts {:id :main-id, :location {:ns "ns"}, :catch->error      :error-id})  [{:id :main-id, :location {:ns "ns"}} {:location {:ns "ns"}, :id :error-id}]))
         (is (= (impl/signal-catch-opts {:id :main-id, :location {:ns "ns"}, :catch->error {:id :error-id}}) [{:id :main-id, :location {:ns "ns"}} {:location {:ns "ns"}, :id :error-id}]))

         (is (throws? :ex-info "Invalid `foo!` args: single map arg is USUALLY a mistake" (impl/signal-opts `foo! {:level :info} :id :level :dsc [{:msg "msg"}])))
         (is (throws? :ex-info "Invalid `foo!` args: given opts should not contain `:id`" (impl/signal-opts `foo! {:level :info} :id :level :dsc [:my-id1 {:id ::my-id2}])))]))

   (testing "event!" ; id + ?level => allowed?
     [(let [[[rv] [sv]] (with-sigs (tel/event! :id1                ))] [(is (= rv true)) (is (sm?  sv {:kind :event, :line :submap/ex, :level :info, :id :id1}))])
      (let [[[rv] [sv]] (with-sigs (tel/event! :id1          :warn ))] [(is (= rv true)) (is (sm?  sv {:kind :event, :line :submap/ex, :level :warn, :id :id1}))])
      (let [[[rv] [sv]] (with-sigs (tel/event! :id1 {:level  :warn}))] [(is (= rv true)) (is (sm?  sv {:kind :event, :line :submap/ex, :level :warn, :id :id1}))])
      (let [[[rv] [sv]] (with-sigs (tel/event! :id1 {:allow? false}))] [(is (= rv nil))  (is (nil? sv))])])

   (testing "error!" ; error + ?id => error
     [(let [[[rv] [sv]] (with-sigs (tel/error!                 ex1))] [(is (ex1? rv)) (is (sm?  sv {:kind :error, :line :submap/ex, :level :error, :error pex1?, :id  nil}))])
      (let [[[rv] [sv]] (with-sigs (tel/error!           :id1  ex1))] [(is (ex1? rv)) (is (sm?  sv {:kind :error, :line :submap/ex, :level :error, :error pex1?, :id :id1}))])
      (let [[[rv] [sv]] (with-sigs (tel/error! {:id      :id1} ex1))] [(is (ex1? rv)) (is (sm?  sv {:kind :error, :line :submap/ex, :level :error, :error pex1?, :id :id1}))])
      (let [[[rv] [sv]] (with-sigs (tel/error! {:allow? false} ex1))] [(is (ex1? rv)) (is (nil? sv))])])

   (testing "log!" ; msg + ?level => allowed?
     [(let [[[rv] [sv]] (with-sigs (tel/log!                 "msg"))] [(is (= rv true)) (is (sm?  sv {:kind :log, :line :submap/ex, :msg_ "msg", :level :info}))])
      (let [[[rv] [sv]] (with-sigs (tel/log!          :warn  "msg"))] [(is (= rv true)) (is (sm?  sv {:kind :log, :line :submap/ex, :msg_ "msg", :level :warn}))])
      (let [[[rv] [sv]] (with-sigs (tel/log! {:level  :warn} "msg"))] [(is (= rv true)) (is (sm?  sv {:kind :log, :line :submap/ex, :msg_ "msg", :level :warn}))])
      (let [[[rv] [sv]] (with-sigs (tel/log! {:allow? false} "msg"))] [(is (= rv nil))  (is (nil? sv))])])

   (testing "catch->error!" ; form + ?id => run value or ?return
     [(let [[[rv re] [sv]] (with-sigs (tel/catch->error!                   (+ 1 2)))] [(is (= rv    3)) (is (nil? sv))])
      (let [[[rv re] [sv]] (with-sigs (tel/catch->error!                    (ex1!)))] [(is (ex1?   re)) (is (sm? sv {:kind :error, :line :submap/ex, :level :error, :error pex1?, :id  nil}))])
      (let [[[rv re] [sv]] (with-sigs (tel/catch->error!             :id1   (ex1!)))] [(is (ex1?   re)) (is (sm? sv {:kind :error, :line :submap/ex, :level :error, :error pex1?, :id :id1}))])
      (let [[[rv re] [sv]] (with-sigs (tel/catch->error! {:id        :id1}  (ex1!)))] [(is (ex1?   re)) (is (sm? sv {:kind :error, :line :submap/ex, :level :error, :error pex1?, :id :id1}))])
      (let [[[rv re] [sv]] (with-sigs (tel/catch->error! {:rethrow?  false} (ex1!)))] [(is (nil?   re)) (is (sm? sv {:kind :error, :line :submap/ex, :level :error, :error pex1?, :id  nil}))])
      (let [[[rv re] [sv]] (with-sigs (tel/catch->error! {:catch-val :foo}  (ex1!)))] [(is (= rv :foo)) (is (sm? sv {:kind :error, :line :submap/ex, :level :error, :error pex1?, :id  nil}))])
      (let [[[rv re] [sv]] (with-sigs (tel/catch->error! {:catch-val :foo} (+ 1 2)))] [(is (= rv    3)) (is (nil? sv))])
      (let [[[rv re] [sv]] (with-sigs (tel/catch->error! {:catch-val :foo ; Overrides `:rethrow?`
                                                          :rethrow?  true} (+ 1 2)))] [(is (= rv 3))    (is (nil? sv))])

      (let [[[rv]    [sv]] (with-sigs (tel/catch->error! {:catch-val     nil
                                                          :catch-sym     my-err
                                                          :data {:my-err my-err}} (ex1!)))]
        [(is (= rv nil)) (is (sm? sv {:kind :error, :data {:my-err pex1?}}))])])

   (testing "trace!" ; run + ?id => run result (value or throw)
     [(let [[[rv]   [sv]] (with-sigs (tel/trace!            (+ 1 2)))] [(is (= rv 3))  (is (sm?  sv {:kind :trace, :line :submap/ex, :level :info, :id  nil, :msg_ "(+ 1 2) => 3"}))])
      (let [[[rv]   [sv]] (with-sigs (tel/trace! {:msg nil} (+ 1 2)))] [(is (= rv 3))  (is (sm?  sv {:kind :trace, :line :submap/ex, :level :info, :id  nil, :msg_ nil}))])
      (let [[[rv]   [sv]] (with-sigs (tel/trace!      :id1  (+ 1 2)))] [(is (= rv 3))  (is (sm?  sv {:kind :trace, :line :submap/ex, :level :info, :id :id1}))])
      (let [[[rv]   [sv]] (with-sigs (tel/trace! {:id :id1} (+ 1 2)))] [(is (= rv 3))  (is (sm?  sv {:kind :trace, :line :submap/ex, :level :info, :id :id1}))])
      (let [[[_ re] [sv]] (with-sigs (tel/trace!      :id1   (ex1!)))] [(is (ex1? re)) (is (sm?  sv {:kind :trace, :line :submap/ex, :level :info, :id :id1, :error pex1?,
                                                                                                     :msg_ #?(:clj  "(ex1!) !> clojure.lang.ExceptionInfo"
                                                                                                              :cljs "(ex1!) !> cljs.core/ExceptionInfo")}))])
      (let [[[rv]   [sv]] (with-sigs (tel/trace! {:allow? false} (+ 1 2)))] [(is (= rv 3)) (is (nil? sv))])
      (let [[_ [sv1 sv2]]
            (with-sigs (tel/trace! {:id :id1, :catch->error :id2} (ex1!)))]
        [(is (sm? sv1 {:kind :trace, :line :submap/ex, :level :info,  :id :id1}))
         (is (sm? sv2 {:kind :error, :line :submap/ex, :level :error, :id :id2}))
         (is (= (:location sv1) (:location sv2)) "Error inherits exact same location")])])

   (testing "spy" ; run + ?level => run result (value or throw)
     [(let [[[rv]   [sv]] (with-sigs (tel/spy!                (+ 1 2)))] [(is (= rv 3))  (is (sm?  sv {:kind :spy, :line :submap/ex, :level :info, :msg_ "(+ 1 2) => 3"}))])
      (let [[[rv]   [sv]] (with-sigs (tel/spy!     {:msg nil} (+ 1 2)))] [(is (= rv 3))  (is (sm?  sv {:kind :spy, :line :submap/ex, :level :info, :msg_ nil}))])
      (let [[[rv]   [sv]] (with-sigs (tel/spy!         :warn  (+ 1 2)))] [(is (= rv 3))  (is (sm?  sv {:kind :spy, :line :submap/ex, :level :warn}))])
      (let [[[rv]   [sv]] (with-sigs (tel/spy! {:level :warn} (+ 1 2)))] [(is (= rv 3))  (is (sm?  sv {:kind :spy, :line :submap/ex, :level :warn}))])
      (let [[[_ re] [sv]] (with-sigs (tel/spy!         :warn   (ex1!)))] [(is (ex1? re)) (is (sm?  sv {:kind :spy, :line :submap/ex, :level :warn, :error pex1?,
                                                                                                       :msg_ #?(:clj  "(ex1!) !> clojure.lang.ExceptionInfo"
                                                                                                                :cljs "(ex1!) !> cljs.core/ExceptionInfo")}))])
      (let [[[rv]   [sv]] (with-sigs (tel/spy! {:allow? false} (+ 1 2)))] [(is (= rv 3)) (is (nil? sv))])
      (let [[_ [sv1 sv2]]
            (with-sigs (tel/spy! {:id :id1, :catch->error :id2} (ex1!)))]
        [(is (sm? sv1 {:kind :spy,   :line :submap/ex, :level :info,  :id :id1}))
         (is (sm? sv2 {:kind :error, :line :submap/ex, :level :error, :id :id2}))
         (is (= (:location sv1) (:location sv2)) "Error inherits exact same location")])])

   #?(:clj
      (testing "uncaught->error!"
        (let [sv_ (atom ::nx)]
          [(do (enc/set-var-root! impl/*sig-handlers* [(sigs/wrap-handler "h1" (fn h1 [x] (reset! sv_ x)) nil {:async nil})]) :set-handler)
           ;;
           (is (nil? (tel/uncaught->error!)))
           (is (do (.join (impl/threaded (ex1!))) (sm? @sv_ {:kind :error, :line :submap/ex, :level :error, :error pex1?, :id  nil})))
           ;;
           (is (nil? (tel/uncaught->error! :id1)))
           (is (do (.join (impl/threaded (ex1!))) (sm? @sv_ {:kind :error, :line :submap/ex, :level :error, :error pex1?, :id :id1})))
           ;;
           (is (nil? (tel/uncaught->error! {:id :id1})))
           (is (do (.join (impl/threaded (ex1!))) (sm? @sv_ {:kind :error, :line :submap/ex, :level :error, :error pex1?, :id :id1})))
           ;;
           (do (enc/set-var-root! impl/*sig-handlers* nil) :unset-handler)])))])

;;;; Intake

(comment (def ^org.slf4j.Logger sl (org.slf4j.LoggerFactory/getLogger "MyTelemereSLF4JLogger")))

#?(:clj
   (deftest _intake
     [(testing "`clojure.tools.logging` -> Telemere"
        [(is (sm? (tel/check-intakes) {:tools-logging {:present? true, :sending->telemere? true, :telemere-receiving? true}}))
         (is (sm? (with-sig (ctl/info "Hello" "x" "y")) {:level :info, :location nil, :ns nil, :kind :log, :id :taoensso.telemere/tools-logging, :msg_ "Hello x y"}))
         (is (sm? (with-sig (ctl/warn "Hello" "x" "y")) {:level :warn, :location nil, :ns nil, :kind :log, :id :taoensso.telemere/tools-logging, :msg_ "Hello x y"}))
         (is (sm? (with-sig (ctl/error ex1 "An error")) {:level :error, :error pex1?}) "Errors")])

      (testing "Standard out/err streams -> Telemere"
        [(is (sm?   (tel/check-intakes) {:system/out {:sending->telemere? false, :telemere-receiving? false},
                                         :system/err {:sending->telemere? false, :telemere-receiving? false}}))

         (is (true? (tel/streams->telemere!)))
         (is (sm?   (tel/check-intakes) {:system/out {:sending->telemere? true,  :telemere-receiving? true},
                                         :system/err {:sending->telemere? true,  :telemere-receiving? true}}))

         (is (true? (tel/streams->reset!)))
         (is (sm?   (tel/check-intakes) {:system/out {:sending->telemere? false, :telemere-receiving? false},
                                         :system/err {:sending->telemere? false, :telemere-receiving? false}}))

         (is (sm? (with-sig (tel/with-out->telemere (println "Hello" "x" "y")))
               {:level :info, :location nil, :ns nil, :kind :system/out, :msg_ "Hello x y"}))])

      (testing "SLF4J -> Telemere"
        [(is (sm? (tel/check-intakes) {:slf4j {:present? true, :sending->telemere? true, :telemere-receiving? true}}))
         (let [^org.slf4j.Logger sl (org.slf4j.LoggerFactory/getLogger "MyTelemereSLF4JLogger")]
           [(testing "Basics"
              [(is (sm? (with-sig (.info sl "Hello"))               {:level :info, :location nil, :ns nil, :kind :log, :id :taoensso.telemere/slf4j, :msg_ "Hello"}) "Legacy API: info basics")
               (is (sm? (with-sig (.warn sl "Hello"))               {:level :warn, :location nil, :ns nil, :kind :log, :id :taoensso.telemere/slf4j, :msg_ "Hello"}) "Legacy API: warn basics")
               (is (sm? (with-sig (-> (.atInfo sl) (.log "Hello"))) {:level :info, :location nil, :ns nil, :kind :log, :id :taoensso.telemere/slf4j, :msg_ "Hello"}) "Fluent API: info basics")
               (is (sm? (with-sig (-> (.atWarn sl) (.log "Hello"))) {:level :warn, :location nil, :ns nil, :kind :log, :id :taoensso.telemere/slf4j, :msg_ "Hello"}) "Fluent API: warn basics")])

            (testing "Message formatting"
              (let [msgp "X is {} and Y is {}", expected {:msg_ "X is x and Y is y", :data {:slf4j/args ["x" "y"]}}]
                [(is (sm? (with-sig (.info sl msgp "x" "y"))                                                           expected) "Legacy API: formatted message, raw args")
                 (is (sm? (with-sig (-> (.atInfo sl) (.setMessage msgp) (.addArgument "x") (.addArgument "y") (.log))) expected) "Fluent API: formatted message, raw args")]))

            (is (sm? (with-sig (-> (.atInfo sl) (.addKeyValue "k1" "v1") (.addKeyValue "k2" "v2") (.log))) {:data {:slf4j/kvs {"k1" "v1", "k2" "v2"}}}) "Fluent API: kvs")

            (testing "Markers"
              (let [m1 (#'slf4j/est-marker! "M1")
                    m2 (#'slf4j/est-marker! "M2")
                    cm (#'slf4j/est-marker! "Compound" "M1" "M2")]

                [(is (sm? (with-sig (.info sl cm "Hello"))                                    {:data #:slf4j{:marker-names #{"Compound" "M1" "M2"}}}) "Legacy API: markers")
                 (is (sm? (with-sig (-> (.atInfo sl) (.addMarker m1) (.addMarker cm) (.log))) {:data #:slf4j{:marker-names #{"Compound" "M1" "M2"}}}) "Fluent API: markers")]))

            (testing "Errors"
              [(is (sm? (with-sig (.warn sl "An error" ^Throwable ex1))     {:level :warn, :error pex1?}) "Legacy API: errors")
               (is (sm? (with-sig (-> (.atWarn sl) (.setCause ex1) (.log))) {:level :warn, :error pex1?}) "Fluent API: errors")])

            (testing "MDC (Mapped Diagnostic Context)"
              (with-open [_   (org.slf4j.MDC/putCloseable "k1" "v1")]
                (with-open [_ (org.slf4j.MDC/putCloseable "k2" "v2")]
                  [(is (sm? (with-sig (->          sl  (.info "Hello"))) {:level :info, :ctx {"k1" "v1", "k2" "v2"}}) "Legacy API: MDC")
                   (is (sm? (with-sig (-> (.atInfo sl) (.log  "Hello"))) {:level :info, :ctx {"k1" "v1", "k2" "v2"}}) "Fluent API: MDC")])))])])]))

;;;; Timbre shim

(deftest _timbre-shim
  [(is (sm? (with-sig (timbre/log :warn              "x1" nil "x2")) {:kind :log, :level :warn,  :id timbre/shim-id, :msg_ "x1 nil x2", :data {:vargs ["x1" nil "x2"]}, :ns pstr?}))
   (is (sm? (with-sig (timbre/info                   "x1" nil "x2")) {:kind :log, :level :info,  :id timbre/shim-id, :msg_ "x1 nil x2", :data {:vargs ["x1" nil "x2"]}, :ns pstr?}))
   (is (sm? (with-sig (timbre/error                  "x1" nil "x2")) {:kind :log, :level :error, :id timbre/shim-id, :msg_ "x1 nil x2", :data {:vargs ["x1" nil "x2"]}, :ns pstr?}))

   (is (sm? (with-sig (timbre/logf :warn  "%s %s %s" "x1" nil "x2")) {:kind :log, :level :warn,  :id timbre/shim-id, :msg_ "x1 nil x2", :data {:vargs ["x1" nil "x2"]}, :ns pstr?}))
   (is (sm? (with-sig (timbre/infof       "%s %s %s" "x1" nil "x2")) {:kind :log, :level :info,  :id timbre/shim-id, :msg_ "x1 nil x2", :data {:vargs ["x1" nil "x2"]}, :ns pstr?}))
   (is (sm? (with-sig (timbre/errorf      "%s %s %s" "x1" nil "x2")) {:kind :log, :level :error, :id timbre/shim-id, :msg_ "x1 nil x2", :data {:vargs ["x1" nil "x2"]}, :ns pstr?}))

   (is (sm? (with-sig (timbre/info ex1 "x1" "x2")) {:kind :log, :level :info, :error pex1?, :msg_ "x1 x2", :data {:vargs ["x1" "x2"]}}) "First-arg error")

   (is (sm? (with-sig (timbre/spy! :info "my-name" (+ 1 2))) {:kind :spy,   :level :info,  :id timbre/shim-id, :msg_ "my-name => 3",    :ns pstr?}))
   (is (sm? (with-sig (timbre/spy!                 (+ 1 2))) {:kind :spy,   :level :debug, :id timbre/shim-id, :msg_ "(+ 1 2) => 3",    :ns pstr?}))
   (is (sm? (with-sig (timbre/spy!                  (ex1!))) {:kind :error, :level :error, :id timbre/shim-id, :msg_ nil, :error pex1?, :ns pstr?}))

   (let [[[rv re] [sv]] (with-sigs (timbre/log-errors             (ex1!)))] [(is (nil? re)) (is (sm? sv {:kind :error, :level :error, :error pex1?, :id timbre/shim-id}))])
   (let [[[rv re] [sv]] (with-sigs (timbre/log-and-rethrow-errors (ex1!)))] [(is (ex1? re)) (is (sm? sv {:kind :error, :level :error, :error pex1?, :id timbre/shim-id}))])])

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

   #?(:clj
      (testing "File writer"
        (let [f  (java.io.File/createTempFile "file-writer-test" ".txt")
              fw (utils/file-writer f false)]

          [(is (true? (fw "1")))
           (is (true? (.delete f)))
           (do (Thread/sleep 500) :sleep) ; Wait for `exists` cache to clear
           (is (true? (fw "2")))
           (is (= (slurp f) "2"))

           (is (true? (.delete        f)))
           (is (true? (.createNewFile f))) ; Can break stream without triggering auto reset

           (is (fw :writer/reset!))
           (is (true? (fw "3")))
           (is (= (slurp f) "3"))
           (is (true? (fw "3")))
           (is (true? (.delete f)))])))

   (testing "Formatters, etc."
     [(is (= (utils/error-in-signal->maps {:level :info, :error ex2})
            {:level :info, :error [{:type ex-info-type, :msg "Ex2", :data {:k2 "v2"}}
                                   {:type ex-info-type, :msg "Ex1", :data {:k1 "v1"}}]}))

      (is (= (utils/minify-signal {:level :info, :location {:ns "ns"}, :file "file"}) {:level :info}))
      (is (= ((utils/format-nsecs-fn) 1.5e9) "1.50s")) ; More tests in Encore
      (is (= ((utils/format-inst-fn)     t0) "2024-06-09T21:15:20.170Z"))

      (is (enc/str-starts-with? ((utils/format-error-fn) ex2)
            #?(:clj  "  Root: clojure.lang.ExceptionInfo - Ex1\n  data: {:k1 \"v1\"}\n\nCaused: clojure.lang.ExceptionInfo - Ex2\n  data: {:k2 \"v2\"}\n\nRoot stack trace:\n"
               :cljs "  Root: cljs.core/ExceptionInfo - Ex1\n  data: {:k1 \"v1\"}\n\nCaused: cljs.core/ExceptionInfo - Ex2\n  data: {:k2 \"v2\"}\n\nRoot stack trace:\n")))

      (let [sig     (with-sig (tel/event! ::ev-id {:inst t0}))
            prelude ((utils/format-signal-prelude-fn) sig)] ; "2024-06-09T21:15:20.170Z INFO EVENT taoensso.telemere-tests(592,35) ::ev-id"
        [(is (enc/str-starts-with? prelude "2024-06-09T21:15:20.170Z INFO EVENT"))
         (is (enc/str-ends-with?   prelude "::ev-id"))
         (is (string? (re-find #"taoensso.telemere-tests\(\d+,\d+\)" prelude)))])

      (testing "format-signal->edn-fn"
        (let [sig  (update (with-sig (tel/event! ::ev-id {:inst t0})) :inst enc/inst->udt)
              sig* (enc/read-edn ((utils/format-signal->edn-fn) sig))]
          (is
            (enc/submap? sig*
              {:schema 1, :kind :event, :id ::ev-id, :level :info,
               :ns      "taoensso.telemere-tests"
               :inst    udt0
               :line    pnat-int?
               :column  pnat-int?}))))

      #?(:cljs
         (testing "format-signal->json-fn"
           (let [sig  (with-sig (tel/event! ::ev-id {:inst t0}))
                 sig* (enc/read-json ((utils/format-signal->json-fn) sig))]
             (is
               (enc/submap? sig*
                 {"schema" 1, "kind" "event", "id" "taoensso.telemere-tests/ev-id",
                  "level" "info", "ns" "taoensso.telemere-tests",
                  "inst"    t0s
                  "line"    pnat-int?
                  "column"  pnat-int?})))))

      (testing "format-signal->str-fn"
        (let [sig (with-sig (tel/event! ::ev-id {:inst t0}))]
          (is (enc/str-starts-with? ((utils/format-signal->str-fn) sig)
                "2024-06-09T21:15:20.170Z INFO EVENT"))))])])

;;;; File handler

#?(:clj (alias 'fh 'taoensso.telemere.handlers.file))

#?(:clj
   (deftest _file-names
     [(is (= (fh/get-file-name "/logs/app.log" nil  nil false) "/logs/app.log"))
      (is (= (fh/get-file-name "/logs/app.log" nil  nil true)  "/logs/app.log"))
      (is (= (fh/get-file-name "/logs/app.log" "ts" nil true)  "/logs/app.log-ts"))
      (is (= (fh/get-file-name "/logs/app.log" "ts" 1   false) "/logs/app.log-ts.1"))
      (is (= (fh/get-file-name "/logs/app.log" "ts" 1   true)  "/logs/app.log-ts.1.gz"))
      (is (= (fh/get-file-name "/logs/app.log" nil  1   false) "/logs/app.log.1"))
      (is (= (fh/get-file-name "/logs/app.log" nil  1   true)  "/logs/app.log.1.gz"))]))

#?(:clj
   (deftest _file-timestamps
     [(is (= (fh/format-file-timestamp :daily   (fh/udt->edy udt0)) "2024-06-09d"))
      (is (= (fh/format-file-timestamp :weekly  (fh/udt->edy udt0)) "2024-06-03w"))
      (is (= (fh/format-file-timestamp :monthly (fh/udt->edy udt0)) "2024-06-01m"))]))

(comment (fh/manage-test-files! :create))

#?(:clj
   (deftest _file-handling
     [(is (boolean (fh/manage-test-files! :create)))

      (testing "`scan-files`"
        ;; Just checking basic counts here, should be sufficient
        [(is (= (count (fh/scan-files "test/logs/app1.log" nil     nil :sort))  1) "1 main, 0 parts")
         (is (= (count (fh/scan-files "test/logs/app1.log" :daily  nil :sort))  0) "0 stamped")
         (is (= (count (fh/scan-files "test/logs/app2.log" nil     nil :sort))  6) "1 main, 5 parts (+gz)")
         (is (= (count (fh/scan-files "test/logs/app3.log" nil     nil :sort))  6) "1 main, 5 parts (-gz")
         (is (= (count (fh/scan-files "test/logs/app4.log" nil     nil :sort)) 11) "1 main, 5 parts (+gz) + 5 parts (-gz)")
         (is (= (count (fh/scan-files "test/logs/app5.log" nil     nil :sort))  1) "1 main, 0 unstamped")
         (is (= (count (fh/scan-files "test/logs/app5.log" :daily  nil :sort))  5) "5 stamped")
         (is (= (count (fh/scan-files "test/logs/app6.log" nil     nil :sort))  1) "1 main, 0 unstamped")
         (is (= (count (fh/scan-files "test/logs/app6.log" :daily  nil :sort)) 25) "5 stamped * 5 parts")
         (is (= (count (fh/scan-files "test/logs/app6.log" :weekly nil :sort))  5) "5 stamped")])

      (testing "`archive-main-file!`"
        [(is (= (let [df (fh/debugger)] (fh/archive-main-file! "test/logs/app1.log" nil nil 2 :gz df) (df))
               [[:rename "test/logs/app1.log" "test/logs/app1.log.1.gz"]]))

         (is (= (let [df (fh/debugger)] (fh/archive-main-file! "test/logs/app2.log" nil nil 2 :gz df) (df))
               [[:delete "test/logs/app2.log.5.gz"]
                [:delete "test/logs/app2.log.4.gz"]
                [:delete "test/logs/app2.log.3.gz"]
                [:delete "test/logs/app2.log.2.gz"]
                [:rename "test/logs/app2.log.1.gz" "test/logs/app2.log.2.gz"]
                [:rename "test/logs/app2.log"      "test/logs/app2.log.1.gz"]]))

         (is (= (let [df (fh/debugger)] (fh/archive-main-file! "test/logs/app3.log" nil nil 2 :gz df) (df))
               [[:delete "test/logs/app3.log.5"]
                [:delete "test/logs/app3.log.4"]
                [:delete "test/logs/app3.log.3"]
                [:delete "test/logs/app3.log.2"]
                [:rename "test/logs/app3.log.1" "test/logs/app3.log.2"]
                [:rename "test/logs/app3.log"   "test/logs/app3.log.1.gz"]]))

         (is (= (let [df (fh/debugger)] (fh/archive-main-file! "test/logs/app6.log" :daily "2021-01-01d" 2 :gz df) (df))
               [[:delete "test/logs/app6.log-2021-01-01d.5.gz"]
                [:delete "test/logs/app6.log-2021-01-01d.4.gz"]
                [:delete "test/logs/app6.log-2021-01-01d.3.gz"]
                [:delete "test/logs/app6.log-2021-01-01d.2.gz"]
                [:rename "test/logs/app6.log-2021-01-01d.1.gz" "test/logs/app6.log-2021-01-01d.2.gz"]
                [:rename "test/logs/app6.log"                  "test/logs/app6.log-2021-01-01d.1.gz"]]))])

      (testing "`prune-archive-files!`"
        [(is (= (let [df (fh/debugger)] (fh/prune-archive-files! "test/logs/app1.log" nil    2 df) (df)) []))
         (is (= (let [df (fh/debugger)] (fh/prune-archive-files! "test/logs/app2.log" nil    2 df) (df)) []))
         (is (= (let [df (fh/debugger)] (fh/prune-archive-files! "test/logs/app5.log" nil    2 df) (df)) []))
         (is (= (let [df (fh/debugger)] (fh/prune-archive-files! "test/logs/app5.log" :daily 2 df) (df))
               [[:delete "test/logs/app5.log-2020-01-01d"]
                [:delete "test/logs/app5.log-2020-01-02d"]
                [:delete "test/logs/app5.log-2020-02-01d"]]))

         (is (= (let [df (fh/debugger)] (fh/prune-archive-files! "test/logs/app6.log" :daily 2 df) (df))
               [[:delete "test/logs/app6.log-2020-01-01d.5.gz"]
                [:delete "test/logs/app6.log-2020-01-01d.4.gz"]
                [:delete "test/logs/app6.log-2020-01-01d.3.gz"]
                [:delete "test/logs/app6.log-2020-01-01d.2.gz"]
                [:delete "test/logs/app6.log-2020-01-01d.1.gz"]

                [:delete "test/logs/app6.log-2020-01-02d.5.gz"]
                [:delete "test/logs/app6.log-2020-01-02d.4.gz"]
                [:delete "test/logs/app6.log-2020-01-02d.3.gz"]
                [:delete "test/logs/app6.log-2020-01-02d.2.gz"]
                [:delete "test/logs/app6.log-2020-01-02d.1.gz"]

                [:delete "test/logs/app6.log-2020-02-01d.5.gz"]
                [:delete "test/logs/app6.log-2020-02-01d.4.gz"]
                [:delete "test/logs/app6.log-2020-02-01d.3.gz"]
                [:delete "test/logs/app6.log-2020-02-01d.2.gz"]
                [:delete "test/logs/app6.log-2020-02-01d.1.gz"]])

           "Prune oldest 3 intervals, with 5 parts each")])

      (is (boolean (fh/manage-test-files! :delete)))]))

;;;; Other handlers

(deftest _handler-constructors
  [#?(:default (is (fn? (handlers:console/handler:console))))
   #?(:cljs    (is (fn? (handlers:console/handler:console-raw))))
   #?(:clj     (is (fn? (handlers:file/handler:file))))
   #?(:clj     (is (fn? (otel/handler:open-telemetry-logger))))])

(comment (def attrs-map otel/signal->attrs-map))

#?(:clj
   (deftest _open-telemetry
     [(testing "attr-name"
        [(is (= (#'otel/attr-name :foo)          "foo"))
         (is (= (#'otel/attr-name :foo-bar-baz)  "foo_bar_baz"))
         (is (= (#'otel/attr-name :foo/bar-baz)  "foo.bar_baz"))
         (is (= (#'otel/attr-name :Foo/Bar-BAZ)  "foo.bar_baz"))
         (is (= (#'otel/attr-name "Foo Bar-Baz") "foo_bar_baz"))
         (is (= (#'otel/attr-name :x1.x2/x3-x4 :foo/bar-baz)
               "x1.x2.x3_x4.foo.bar_baz"))])

      (testing "merge-prefix-map"
        [(is (= (#'otel/merge-prefix-map nil       "pf"     nil) nil))
         (is (= (#'otel/merge-prefix-map nil       "pf"      {}) nil))
         (is (= (#'otel/merge-prefix-map {"a" "A"} "pf" {:a :A}) {"a" "A", "pf.a" :A}))
         (is (= (#'otel/merge-prefix-map {}        "pf"
                  {:a/b1 "v1" :a/b2 "v2" :nil nil, :map {:k1 "v1"}})

               {"pf.a.b1" "v1", "pf.a.b2" "v2", "pf.nil" nil, "pf.map" {:k1 "v1"}}))])

      (testing "as-attrs"
        (is (= (str
                 (#'otel/as-attrs
                   {:string "s", :keyword :foo/bar, :long 5, :double 5.0, :nil nil,
                    :longs   [5   5.0 5.0],
                    :doubles [5.0 5   5],
                    :bools   [true false nil],
                    :mixed   [5 "5" nil],
                    :strings ["a" "b" "c"],
                    :map     {:k1 "v1"}}))

              "{bools=[true, false, false], double=5.0, doubles=[5.0, 5.0, 5.0], keyword=\":foo/bar\", long=5, longs=[5, 5, 5], map=[[:k1 \"v1\"]], mixed=[5, \"5\", nil], nil=\"nil\", string=\"s\", strings=[\"a\", \"b\", \"c\"]}")))

      (testing "signal->attrs-map"
        (let [attrs-map #'otel/signal->attrs-map]
          [(is (= (attrs-map nil    {                }) {"error" false}))
           (is (= (attrs-map :attrs {:attrs {:a1 :A1}}) {"error" false, :a1 :A1}))
           (is
             (sm?
               (attrs-map :attrs
                 {:ns   "ns"
                  :line 100
                  :file "file"

                  :error ex2
                  :kind  :event
                  :level :info
                  :id    ::id1
                  :uid   #uuid "7e9c1df6-78e4-40ac-8c5c-e2353df9ab82"

                  :run-form    '(+ 3 2)
                  :run-val     5
                  :run-nsecs   100
                  :sample-rate 0.5

                  :parent
                  {:id  ::parent-id1
                   :uid #uuid "443154cf-b6cf-47bf-b86a-8b185afee256"}

                  :attrs {:a1 :A1}})

               {"ns"   "ns"
                "line" 100
                "file" "file"

                "error" true
                "exception.type"       'clojure.lang.ExceptionInfo
                "exception.message"    "Ex1"
                "exception.stacktrace" (enc/pred string?)
                "exception.data.k1"    "v1"

                "kind"       :event
                "level"      :info
                "id"         :taoensso.telemere-tests/id1
                "parent.id"  :taoensso.telemere-tests/parent-id1
                "uid"        #uuid "7e9c1df6-78e4-40ac-8c5c-e2353df9ab82"
                "parent.uid" #uuid "443154cf-b6cf-47bf-b86a-8b185afee256"

                "run.form"     '(+ 3 2)
                "run.val"      5
                "run.val_type" 'java.lang.Long
                "run.nsecs"    100
                "sample"       0.5

                :a1 :A1}))]))]))

;;;;

#?(:cljs
   (defmethod test/report [:cljs.test/default :end-run-tests] [m]
     (when-not (test/successful? m)
       ;; Trigger non-zero `lein test-cljs` exit code for CI
       (throw (ex-info "ClojureScript tests failed" {})))))

#?(:cljs (test/run-tests))
