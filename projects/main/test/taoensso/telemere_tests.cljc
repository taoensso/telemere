(ns taoensso.telemere-tests
  (:require
   [clojure.test            :as test :refer [deftest testing is]]
   [clojure.core.async      :as async]
   [taoensso.encore         :as enc  :refer [throws? submap?] :rename {submap? sm?}]
   [taoensso.encore.signals :as sigs]
   [taoensso.telemere       :as tel]
   [taoensso.telemere.impl  :as impl
    :refer  [signal!       with-signal           with-signals]
    :rename {signal! sig!, with-signal with-sig, with-signals with-sigs}]

   [taoensso.telemere.utils           :as utils]
   [taoensso.telemere.timbre          :as timbre]
   #_[taoensso.telemere.tools-logging :as tools-logging]
   #_[taoensso.telemere.streams       :as streams]
   #?@(:clj
       [[taoensso.telemere.slf4j          :as slf4j]
        [taoensso.telemere.open-telemetry :as otel]
        [taoensso.telemere.files          :as files]
        [clojure.tools.logging            :as ctl]])))

(comment
  (remove-ns      'taoensso.telemere-tests)
  (test/run-tests 'taoensso.telemere-tests))

;;;; Utils

(do
  (def ^:dynamic *dynamic-var* nil)

  (do (def t1s "2024-01-01T01:01:01.110Z") (def t1 (enc/as-inst t1s)) (def udt1 (enc/as-udt t1)))
  (do (def t2s "2024-02-02T02:02:02.120Z") (def t2 (enc/as-inst t2s)) (def udt2 (enc/as-udt t2)))
  (do (def t3s "2024-03-03T03:03:03.130Z") (def t3 (enc/as-inst t3s)) (def udt3 (enc/as-udt t3)))

  (def  ex-info-type (#'enc/ex-type (ex-info "" {})))
  (def  ex1 (ex-info "Ex1" {}))
  (def  ex2 (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"})))
  (def  ex2-chain (enc/ex-chain :as-map ex2))
  (defn ex1! [] (throw ex1))

  (defn ex1?     [x] (= (enc/ex-root x) ex1))
  (def pstr?     (enc/pred string?))
  (def pnat-int? (enc/pred enc/nat-int?))
  (def pinst?    (enc/pred enc/inst?)))

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
  [(is (= (with-sigs (sig! {:level :info, :elide? true               })) {:value nil}) "With compile-time elision")
   (is (= (with-sigs (sig! {:level :info, :elide? true,  :run (+ 1 2)})) {:value   3}) "With compile-time elision, run-form")
   (is (= (with-sigs (sig! {:level :info, :allow? false              })) {:value nil}) "With runtime suppression")
   (is (= (with-sigs (sig! {:level :info, :allow? false, :run (+ 1 2)})) {:value   3}) "With runtime suppression, run-form")

   (is (->> (sig! {:level :info, :elide? true,  :run (ex1!)}) (throws? :ex-info "Ex1")) "With compile-time elision, throwing run-form")
   (is (->> (sig! {:level :info, :allow? false, :run (ex1!)}) (throws? :ex-info "Ex1")) "With runtime suppression,  throwing run-form")

   (let [{rv1 :value, [sv1] :signals} (with-sigs (sig! {:level :info              }))
         {rv2 :value, [sv2] :signals} (with-sigs (sig! {:level :info, :run (+ 1 2)}))]

     [(is (= rv1 true)) (is (sm? sv1 {:ns "taoensso.telemere-tests", :level :info, :run-form nil,      :run-val nil, :run-nsecs nil}))
      (is (= rv2    3)) (is (sm? sv2 {:ns "taoensso.telemere-tests", :level :info, :run-form '(+ 1 2), :run-val 3,   :run-nsecs pnat-int?}))])

   (testing "Nested signals"
     (let [{outer-rv :value, [outer-sv] :signals} (with-sigs (sig! {:level :info, :run (with-sigs (sig! {:level :warn, :run "inner-run"}))}))
           {inner-rv :value, [inner-sv] :signals} outer-rv]

       [(is (= inner-rv "inner-run"))
        (is (sm? inner-sv {:level :warn, :run-val         "inner-run"}))
        (is (sm? outer-sv {:level :info  :run-val {:value "inner-run", :signals [inner-sv]}}))]))

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
     (let [c (enc/counter)
           {rv1 :value, [sv1] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :msg             "msg1"}))         ; No     delay
           {rv2 :value, [sv2] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :msg        [    "msg2:"  (c)]}))  ; Auto   delay
           {rv3 :value, [sv3] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :msg (delay (str "msg3: " (c)))})) ; Manual delay
           {rv4 :value, [sv4] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :msg        (str "msg4: " (c))}))  ; No     delay
           {rv5 :value, [sv5] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :msg        (str "msg5: " (c)), :allow? false}))]

       [(is (= rv1 0)) (is (=  (:msg_ sv1) "msg1"))
        (is (= rv2 1)) (is (= @(:msg_ sv2) "msg2: 6"))
        (is (= rv3 2)) (is (= @(:msg_ sv3) "msg3: 7"))
        (is (= rv4 3)) (is (=  (:msg_ sv4) "msg4: 4"))
        (is (= rv5 5)) (is (=  (:msg_ sv5) nil))
        (is (= @c  8)  "5x run + 3x message (1x suppressed)")]))

   (testing "`:data` basics"
     (vec
       (for [dk [:data :my-k1]] ; User kvs share same behaviour as data
         (let [c (enc/counter)
               {rv1 :value, [sv1] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), dk        {:c1 (c)}}))
               {rv2 :value, [sv2] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), dk (delay {:c2 (c)})}))
               {rv3 :value, [sv3] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), dk        {:c3 (c)},  :allow? false}))
               {rv4 :value, [sv4] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), dk (delay {:c4 (c)}), :allow? false}))
               {rv5 :value, [sv5] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), dk        [:c5 (c)]}))
               {rv6 :value, [sv6] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), dk (delay [:c6 (c)])}))]

           [(is (= rv1 0)) (is (=        (get sv1 dk)  {:c1 1}))
            (is (= rv2 2)) (is (= (force (get sv2 dk)) {:c2 8}))
            (is (= rv3 3)) (is (=        (get sv3 dk)  nil))
            (is (= rv4 4)) (is (= (force (get sv4 dk)) nil))
            (is (= rv5 5)) (is (=        (get sv5 dk)  [:c5 6]) "`:data` can be any type")
            (is (= rv6 7)) (is (= (force (get sv6 dk)) [:c6 9]) "`:data` can be any type")
            (is (= @c  10) "6x run + 4x data (2x suppressed)")]))))

   (testing "`:let` basics"
     (let [c (enc/counter)
           {rv1 :value, [sv1] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :let [_ (c)]}))
           {rv2 :value, [sv2] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :let [_ (c)], :allow? false}))
           {rv3 :value, [sv3] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :let [_ (c)]}))]

       [(is (= rv1 0))
        (is (= rv2 2))
        (is (= rv3 3))
        (is (= @c  5) "3x run + 2x let (1x suppressed)")]))

   (testing "`:let` + `:msg`"
     (let [c (enc/counter)
           {rv1 :value, [sv1] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :let [n (c)], :msg             "msg1"}))               ; No     delay
           {rv2 :value, [sv2] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :let [n (c)], :msg        [    "msg2:"  n     (c)]}))  ; Auto   delay
           {rv3 :value, [sv3] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :let [n (c)], :msg (delay (str "msg3: " n " " (c)))})) ; Manual delay
           {rv4 :value, [sv4] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :let [n (c)], :msg        (str "msg4: " n " " (c))}))  ; No     delay
           {rv5 :value, [sv5] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :let [n (c)], :msg        (str "msg5: " n " " (c)), :allow? false}))]

       [(is (= rv1 0)) (is (=  (:msg_ sv1) "msg1"))
        (is (= rv2 2)) (is (= @(:msg_ sv2) "msg2: 3 10"))
        (is (= rv3 4)) (is (= @(:msg_ sv3) "msg3: 5 11"))
        (is (= rv4 6)) (is (=  (:msg_ sv4) "msg4: 7 8"))
        (is (= rv5 9)) (is (=  (:msg_ sv5) nil))
        (is (= @c  12) "5x run + 4x let (1x suppressed) + 3x msg (1x suppressed)")]))

   (testing "`:do` + `:let` + `:data`/`:my-k1`"
     (vec
       (for [dk [:data :my-k1]]
         (let [c (enc/counter)
               {rv1 :value, [sv1] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk        {:n n, :c1 (c)}}))
               {rv2 :value, [sv2] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk (delay {:n n, :c2 (c)})}))
               {rv3 :value, [sv3] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk        {:n n, :c3 (c)},  :allow? false}))
               {rv4 :value, [sv4] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk (delay {:n n, :c4 (c)}), :allow? false}))
               {rv5 :value, [sv5] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk        [:n n, :c5 (c)]}))
               {rv6 :value, [sv6] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :do (c), :let [n (c)], dk (delay [:n n, :c6 (c)])}))]

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
         (let [c (enc/counter)
               {rv1 :value, [sv1] :signals} (with-sigs :raw nil (let [n (c)] (sig! {:level :info, :run (c), dk        {:n n, :c1 (c)}})))
               {rv2 :value, [sv2] :signals} (with-sigs :raw nil (let [n (c)] (sig! {:level :info, :run (c), dk (delay {:n n, :c2 (c)})})))
               {rv3 :value, [sv3] :signals} (with-sigs :raw nil (let [n (c)] (sig! {:level :info, :run (c), dk        {:n n, :c3 (c)},  :allow? false})))
               {rv4 :value, [sv4] :signals} (with-sigs :raw nil (let [n (c)] (sig! {:level :info, :run (c), dk (delay {:n n, :c4 (c)}), :allow? false})))
               {rv5 :value, [sv5] :signals} (with-sigs :raw nil (let [n (c)] (sig! {:level :info, :run (c), dk        [:n n, :c5 (c)]})))
               {rv6 :value, [sv6] :signals} (with-sigs :raw nil (let [n (c)] (sig! {:level :info, :run (c), dk (delay [:n n, :c6 (c)])})))]

           [(is (= rv1 1))  (is (=        (get sv1 dk)  {:n 0, :c1 2}))
            (is (= rv2 4))  (is (= (force (get sv2 dk)) {:n 3, :c2 14}))
            (is (= rv3 6))  (is (=        (get sv3 dk)  nil))
            (is (= rv4 8))  (is (= (force (get sv4 dk)) nil))
            (is (= rv5 10)) (is (=        (get sv5 dk)  [:n 9,  :c5 11]))
            (is (= rv6 13)) (is (= (force (get sv6 dk)) [:n 12, :c6 15]))
            (is (= @c  16)  "6x run + 6x let (0x suppressed) + 4x data (2x suppressed)")]))))

   (testing "Dynamic bindings, etc."
     [(let [sv
            (binding[#?@(:clj [impl/*sig-spy-off-thread?* true])
                     *dynamic-var* "dynamic-val"
                     tel/*ctx*     "ctx-val"]
              (with-sig (sig! {:level :info, :data {:*dynamic-var* *dynamic-var*, :*ctx* tel/*ctx*}})))]
        (is (sm? sv                         {:data {:*dynamic-var* "dynamic-val", :*ctx* "ctx-val"}})
          "Retain dynamic bindings in place at time of signal call"))

      (let [sv (with-sig (sig! {:level :info, :ctx "my-ctx"}))]
        (is (sm? sv {:ctx "my-ctx"}) "`*ctx*` can be overridden via call opt"))

      (let [sv (binding [tel/*ctx* {:foo :bar}]
                 (with-sig (sig! {:level :info, :ctx+ {:baz :qux}})))]
        (is (sm? sv {:ctx {:foo :bar, :baz :qux}}) "`*ctx*` can be updated via call opt"))])

   (testing "Middleware"
     [(testing "Dynamic middleware (`*middleware*`)"
        [(is (sm? (tel/with-middleware nil               (with-sig (sig! {:level :info                 })))               {:level :info                 }) "nil middleware ~ identity")
         (is (sm? (tel/with-middleware identity          (with-sig (sig! {:level :info                 })))               {:level :info                 }) "nil middleware ~ identity")
         (is (sm? (tel/with-middleware #(assoc % :foo 1) (with-sig (sig! {:level :info                 })))               {:level :info, :foo 1         }))
         (is (sm? (tel/with-middleware #(assoc % :foo 1) (with-sig (sig! {:level :info, :middleware #(assoc % :foo 2)}))) {:level :info, :foo 2         }) "call > dynamic")
         (is (sm? (tel/with-middleware #(assoc % :foo 1) (with-sig (sig! {:level :info, :middleware nil})))               {:level :info, :foo :submap/nx}) "call > dynamic")
         (is (=   (tel/with-middleware #(do nil)         (with-sig (sig! {:level :info                 })))               nil)                             "return nil => suppress")
         (is (sm? (tel/with-middleware #(do nil)         (with-sig (sig! {:level :info, :middleware nil})))               {:level :info})                  "call > dynamic")])

      (testing "Call middleware"
        (let [c (enc/counter)
              {rv1 :value, [sv1] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :middleware (tel/comp-middleware #(assoc % :m1 (c)) #(assoc % :m2 (c)))}))
              {rv2 :value, [sv2] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :middleware (tel/comp-middleware #(assoc % :m1 (c)) #(assoc % :m2 (c))), :allow? false}))
              {rv3 :value, [sv3] :signals} (with-sigs :raw nil (sig! {:level :info, :run (c), :middleware (tel/comp-middleware #(assoc % :m1 (c)) #(assoc % :m2 (c)))}))
              {rv4 :value, [sv4] :signals} (with-sigs :raw nil (sig! {:level :info,           :middleware (fn [_] "signal-value")}))
              {rv5 :value, [sv5] :signals} (with-sigs :raw nil (sig! {:level :info,           :middleware (fn [_] nil)}))]

          [(is (= rv1 0))    (is (sm? sv1 {:m1 1 :m2 2}))
           (is (= rv2 3))    (is (nil?    sv2))
           (is (= rv3 4))    (is (sm? sv3 {:m1 5 :m2 6}))
           (is (= rv4 true)) (is (=       sv4 "signal-value"))
           (is (= rv5 true)) (is (nil?    sv5))
           (is (= @c  7)     "3x run + 4x middleware")]))

      (testing "Mixed middleware"
        [(let [sv
               (binding [tel/*middleware* #(assoc % :foo true)]
                 (with-sig (sig! {:level :info, :middleware+ #(assoc % :bar true)})))]
           (is (sm? sv {:foo true, :bar true})))])])

   #?(:clj
      (testing "Printing"
        (let [sv1 (dissoc (with-sig (sig! {:level :info, :run (+ 1 2), :my-k1 :my-v1})) :_otel-context)
              sv1 ; Ensure instants are printable
              (-> sv1
                (update-in [:inst]     enc/inst->udt)
                (update-in [:end-inst] enc/inst->udt))]

          [(is (= sv1 (read-string (pr-str sv1))))])))])

(deftest _handlers
  ;; Basic handler tests are in Encore
  [(testing "Handler middleware"
     (let [c      (enc/counter)
           sv-h1_ (atom nil)
           sv-h2_ (atom nil)
           wh1    (sigs/wrap-handler :hid1 (fn [sv] (reset! sv-h1_ sv)) nil {:async nil, :middleware (tel/comp-middleware #(assoc % :hm1 (c)) #(assoc % :hm2 (c)))})
           wh2    (sigs/wrap-handler :hid2 (fn [sv] (reset! sv-h2_ sv)) nil {:async nil, :middleware (tel/comp-middleware #(assoc % :hm1 (c)) #(assoc % :hm2 (c)))})]

       ;; Note that call middleware output is cached and shared across all handlers
       (binding [impl/*sig-handlers* [wh1 wh2]]
         (let [;; 1x run + 4x handler middleware + 2x call middleware = 7x
               rv1    (sig! {:level :info, :run (c), :middleware (tel/comp-middleware #(assoc % :m1 (c)) #(assoc % :m2 (c)))})
               sv1-h1 @sv-h1_
               sv1-h2 @sv-h2_
               c1     @c

               ;; 1x run
               rv2    (sig! {:level :info, :run (c), :middleware (tel/comp-middleware #(assoc % :m1 (c)) #(assoc % :m2 (c))), :allow? false})
               sv2-h1 @sv-h1_
               sv2-h2 @sv-h2_
               c2     @c ; 8

               ;; 1x run + 4x handler middleware + 2x call middleware = 7x
               rv3    (sig! {:level :info, :run (c), :middleware (tel/comp-middleware #(assoc % :m1 (c)) #(assoc % :m2 (c)))})
               sv3-h1 @sv-h1_
               sv3-h2 @sv-h2_
               c3     @c ; 15

               ;; 4x handler middleware
               rv4    (sig! {:level :info, :middleware (fn [_] {:my-sig-val? true})})
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
       (is (= (do #?(:clj (Thread/sleep 500)) @a) "bound"))))

   #?(:clj
      (testing "High-volume, cross-thread handler calls"
        (every? true?
          (flatten
            (for [_ (range 16)]
              (let [n 1e4
                    test1
                    (fn [min-num-handled-sigs dispatch-opts]
                      (let [fp (enc/future-pool [:ratio 1.0])
                            c1 (enc/counter)
                            c2 (enc/counter)
                            c3 (enc/counter)
                            c4 (enc/counter)
                            c5 (enc/counter)
                            c6 (enc/counter)]

                        (tel/with-handler :hid1
                          (fn ([_] (c5)) ([] (c6)))
                          (assoc dispatch-opts :needs-stopping? true)
                          (do
                            (dotimes [_ n] (fp (fn [] (c1) (tel/event! ::ev-id1 {:run (c2), :do (c3)}) (c4))))
                            (fp)))

                        [(is (== @c1 n) "fp start    count should always == n")
                         (is (== @c4 n) "fp end      count should always == n")
                         (is (== @c2 n) "Signal :run count should always == n")
                         (is (== @c6 1) "Shutdown    count should always == 1")
                         (is (>= @c3 min-num-handled-sigs) "Depends on buffer semantics, >n possible with :sync backp-fn calls")
                         (is (>= @c5 min-num-handled-sigs) "Depends on buffer semantics, >n possible with :sync backp-fn calls")]))]

                [(test1 n  {:async {:mode :sync}})
                 (test1 n  {:async {:mode :blocking, :buffer-size 64}})
                 (test1 64 {:async {:mode :dropping, :buffer-size 64}})
                 (test1 64 {:async {:mode :sliding,  :buffer-size 64}})]))))))])

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
       :middleware (fn [sv] (if *throwing-handler-middleware?* (ex1!) sv))}

      [(is (->> (sig! {:level :info, :when  (ex1!)}) (throws? :ex-info "Ex1")) "`~filterable-expansion/allow` throws at call")
       (is (->> (sig! {:level :info, :inst  (ex1!)}) (throws? :ex-info "Ex1")) "`~inst-form`                  throws at call")
       (is (->> (sig! {:level :info, :id    (ex1!)}) (throws? :ex-info "Ex1")) "`~id-form`                    throws at call")
       (is (->> (sig! {:level :info, :uid   (ex1!)}) (throws? :ex-info "Ex1")) "`~uid-form`                   throws at call")
       (is (->> (sig! {:level :info, :run   (ex1!)}) (throws? :ex-info "Ex1")) "`~run-form` rethrows at call")
       (is (sm? @sv_  {:level :info, :error  ex1})                             "`~run-form` rethrows at call *after* dispatch")

       (testing "`@signal-value_`: trap with wrapped handler"
         [(testing "Throwing `~let-form`"
            (reset-state!)
            [(is (true? (sig! {:level :info, :let [_ (ex1!)]})))
             (is (= @sv_ :nx))
             (is (sm? @error_ {:handler-id :hid1, :error ex1}))])

          (testing "Throwing call middleware"
            (reset-state!)
            [(is (true? (sig! {:level :info, :middleware (fn [_] (ex1!))})))
             (is (= @sv_ :nx))
             (is (sm? @error_ {:handler-id :hid1, :error ex1}))])

          (testing "Throwing handler middleware"
            (reset-state!)
            (binding [*throwing-handler-middleware?* true]
              [(is (true? (sig! {:level :info})))
               (is (= @sv_ :nx))
               (is (sm? @error_ {:handler-id :hid1, :error ex1}))]))

          (testing "Throwing `@data_`"
            (reset-state!)
            [(is (true? (sig! {:level :info, :data (delay (ex1!))})))
             (is (= @sv_ :nx))
             (is (sm? @error_ {:handler-id :hid1, :error ex1}))])

          (testing "Throwing user kv"
            (reset-state!)
            [(is (true? (sig! {:level :info, :my-k1 (ex1!)})))
             (is (= @sv_ :nx))
             (is (sm? @error_ {:handler-id :hid1, :error ex1}))])])])))

(deftest _tracing
  (testing "Tracing"
    [(testing "Opts overrides"
       [(let [sv (with-sig (sig! {:level :info                                 }))] (is (sm? sv {:parent nil})))
        (let [sv (with-sig (sig! {:level :info, :parent {:id   :id1, :foo :bar}}))] (is (sm? sv {:parent {:id :id1       :uid :submap/nx, :foo :bar}}) "Manual `:parent/id`"))
        (let [sv (with-sig (sig! {:level :info, :parent {:uid :uid1, :foo :bar}}))] (is (sm? sv {:parent {:id :submap/nx :uid      :uid1, :foo :bar}}) "Manual `:parent/uid`"))
        (let [sv (with-sig (sig! {:level :info, :root   {:id   :id1, :foo :bar}}))] (is (sm? sv {:root   {:id :id1       :uid :submap/nx, :foo :bar}}) "Manual `:root/id`"))
        (let [sv (with-sig (sig! {:level :info, :root   {:uid :uid1, :foo :bar}}))] (is (sm? sv {:root   {:id :submap/nx :uid      :uid1, :foo :bar}}) "Manual `:root/uid`"))
        (let [sv (with-sig (sig! {:level :info}))                                 ] (is (sm? sv {:uid nil})))
        (let [sv (with-sig (sig! {:level :info, :uid :auto}))                     ] (is (sm? sv {:uid (enc/pred string?)})))
        (let [sv (binding [tel/*uid-fn* (fn [_] "my-uid")]
                   (with-sig (sig! {:level :info, :uid :auto})))                  ] (is (sm? sv {:uid "my-uid"})))])

     (testing "Auto parent/root"
       [(testing "Tracing disabled"
          (let [sv (with-sig (sig! {:level :info, :id :id1, :uid :uid1, :trace? false
                                    :run  {:parent impl/*trace-parent*, :root impl/*trace-root*}
                                    :data {:parent impl/*trace-parent*, :root impl/*trace-root*}}))]

            [(is (sm? sv           {:parent nil, :root nil}))
             (is (sm? sv {:run-val {:parent nil, :root nil}}) "`*trace-x*`     visible to run-form")
             (is (sm? sv {:data    {:parent nil, :root nil}}) "`*trace-x*` NOT visible to data-form")]))

        (when #?(:cljs true :clj (not impl/enabled:otel-tracing?))
          (testing "Tracing enabled"
            (let [sv (with-sig (sig! {:level :info, :id :id1, :uid :uid1, :trace? true
                                      :run  {:parent impl/*trace-parent*, :root impl/*trace-root*}
                                      :data {:parent impl/*trace-parent*, :root impl/*trace-root*}}))]

              [(is (sm? sv {:parent nil, :root {:id :id1, :uid :uid1}}))
               (is (sm? sv {:run-val  {:parent {:id :id1, :uid :uid1}, :root {:id :id1, :uid :uid1}}}) "`*trace-x*`     visible to run-form")
               (is (sm? sv {:data     {:parent nil,                    :root nil}})                    "`*trace-x*` NOT visible to data-form")])))])

     (testing "Manual parent/root"
       [(testing "Tracing disabled"
          (let [sv (with-sig (sig! {:level :info, :id :id1, :uid :uid1, :trace? false,
                                    :parent {:id :id2, :uid :uid2},
                                    :root   {:id :id3, :uid :uid3}
                                    :run    {:parent impl/*trace-parent*, :root impl/*trace-root*}
                                    :data   {:parent impl/*trace-parent*, :root impl/*trace-root*}}))]

            [(is (sm? sv {:parent {:id :id2, :uid :uid2}, :root {:id :id3, :uid :uid3}}))
             (is (sm? sv {:run-val {:parent nil, :root nil}}) "`*trace-x*`     visible to run-form")
             (is (sm? sv {:data    {:parent nil, :root nil}}) "`*trace-x*` NOT visible to data-form")]))

        (when #?(:cljs true :clj (not impl/enabled:otel-tracing?))
          (testing "Tracing enabled"
            (let [sv (with-sig (sig! {:level :info, :id :id1, :uid :uid1, :trace? true,
                                      :parent {:id :id2, :uid :uid2},
                                      :root   {:id :id3, :uid :uid3}
                                      :run    {:parent impl/*trace-parent*, :root impl/*trace-root*}
                                      :data   {:parent impl/*trace-parent*, :root impl/*trace-root*}}))]

              [(is (sm? sv {:parent {:id :id2, :uid :uid2}, :root {:id :id3, :uid :uid3}}))
               (is (sm? sv {:run-val {:parent {:id :id1, :uid :uid1}, :root {:id :id3, :uid :uid3}}}) "`*trace-x*`     visible to run-form")
               (is (sm? sv {:data    {:parent nil,                    :root nil}})                    "`*trace-x*` NOT visible to data-form")
               ])))])

     (testing "Signal nesting"
       (when #?(:cljs true :clj (not impl/enabled:otel-tracing?))
         (let [{s1-rv :value, [s1-sv] :signals}
               (with-sigs
                 (sig! {                       :level :info, :id :id1, :uid :uid1
                        :run (with-sigs (sig! {:level :info, :id :id2, :uid :uid2
                                               :run
                                               {:parent impl/*trace-parent*
                                                :root   impl/*trace-root*}}))}))

               {s2-rv :value, [s2-sv] :signals} s1-rv]

           [(is (sm? s1-sv           {:id :id1, :uid :uid1, :parent nil}))
            (is (sm? s2-sv {:parent  {:id :id1, :uid :uid1}}))

            (is (sm? s2-rv           {:parent {:id :id2, :uid :uid2}, :root {:id :id1, :uid :uid1}}))
            (is (sm? s2-sv {:run-val {:parent {:id :id2, :uid :uid2}, :root {:id :id1, :uid :uid1}}}))]))

       #?(:clj
          (enc/compile-when impl/enabled:otel-tracing?
            (let [{s1-rv :value, [s1-sv] :signals}
                  (with-sigs
                    (sig! {                       :level :info, :id :id1
                           :run (with-sigs (sig! {:level :info, :id :id2
                                                  :run
                                                  {:parent impl/*trace-parent*
                                                   :root   impl/*trace-root*}}))}))

                  {s2-rv :value, [s2-sv] :signals} s1-rv

                  c1 (:_otel-context s1-sv)
                  c2 (:_otel-context s2-sv)

                  s1-trace-id (impl/otel-trace-id c1)
                  s2-trace-id (impl/otel-trace-id c2)

                  s1-span-id  (impl/otel-span-id  c1)
                  s2-span-id  (impl/otel-span-id  c2)]

              [(is (impl/viable-tracer (force tel/*otel-tracer*))                   "Viable tracer")
               (is (every? string? [s1-trace-id s2-trace-id s1-span-id s2-span-id]) "Viable tracer produces spans with ids")
               (is (=    s1-trace-id s2-trace-id))
               (is (not= s1-span-id  s2-span-id))

               (is (sm? s1-sv          {:id :id1, :uid s1-span-id, :parent nil, :root {:id :id1, :uid s1-trace-id}}))
               (is (sm? s2-sv {:parent {:id :id1, :uid s1-span-id}}))

               (is (sm? s2-rv           {:parent {:id :id2, :uid s2-span-id}, :root {:id :id1, :uid s1-trace-id}}))
               (is (sm? s2-sv {:run-val {:parent {:id :id2, :uid s2-span-id}, :root {:id :id1, :uid s1-trace-id}}}))]))))]))

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
  [(testing "event!" ; id + ?level => allowed?
     [(let [{rv :value, [sv] :signals} (with-sigs (tel/event! :id1                    ))] [(is (= rv true)) (is (sm?  sv {:kind :event, :line :submap/some, :level :info, :id :id1}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/event! :id1              :warn)) ] [(is (= rv true)) (is (sm?  sv {:kind :event, :line :submap/some, :level :warn, :id :id1}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/event! :id1      {:level :warn}))] [(is (= rv true)) (is (sm?  sv {:kind :event, :line :submap/some, :level :warn, :id :id1}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/event! {:id :id1, :level :warn}))] [(is (= rv true)) (is (sm?  sv {:kind :event, :line :submap/some, :level :warn, :id :id1}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/event! :id1 {:allow? false}))    ] [(is (= rv nil))  (is (nil? sv))])])

   (testing "log!" ; ?level + msg => allowed?
     [(let [{rv :value, [sv] :signals} (with-sigs (tel/log!                      "msg")) ] [(is (= rv true)) (is (sm?  sv {:kind :log, :line :submap/some, :msg_ "msg", :level :info}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/log!          :warn       "msg")) ] [(is (= rv true)) (is (sm?  sv {:kind :log, :line :submap/some, :msg_ "msg", :level :warn}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/log! {:level  :warn}      "msg")) ] [(is (= rv true)) (is (sm?  sv {:kind :log, :line :submap/some, :msg_ "msg", :level :warn}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/log! {:level  :warn, :msg "msg"}))] [(is (= rv true)) (is (sm?  sv {:kind :log, :line :submap/some, :msg_ "msg", :level :warn}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/log! {:allow? false}      "msg")) ] [(is (= rv nil))  (is (nil? sv))])])

   (testing "trace!" ; ?id + run => unconditional run result (value or throw)
     [(let [{rv :value, [sv] :signals} (with-sigs (tel/trace!                 (+ 1 2))) ] [(is (= rv 3))  (is (sm?  sv {:kind :trace, :line :submap/some, :level :info, :id  nil, :msg_ "(+ 1 2) => 3"}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/trace! {:msg nil}      (+ 1 2))) ] [(is (= rv 3))  (is (sm?  sv {:kind :trace, :line :submap/some, :level :info, :id  nil, :msg_ nil}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/trace!      :id1       (+ 1 2))) ] [(is (= rv 3))  (is (sm?  sv {:kind :trace, :line :submap/some, :level :info, :id :id1}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/trace! {:id :id1}      (+ 1 2))) ] [(is (= rv 3))  (is (sm?  sv {:kind :trace, :line :submap/some, :level :info, :id :id1}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/trace! {:id :id1, :run (+ 1 2)}))] [(is (= rv 3))  (is (sm?  sv {:kind :trace, :line :submap/some, :level :info, :id :id1}))])
      (let [{re :error, [sv] :signals} (with-sigs (tel/trace!      :id1        (ex1!))) ] [(is (ex1? re)) (is (sm?  sv {:kind :trace, :line :submap/some, :level :info, :id :id1, :error ex1,
                                                                                                           :msg_ #?(:clj  "(ex1!) !> clojure.lang.ExceptionInfo"
                                                                                                                    :cljs "(ex1!) !> cljs.core/ExceptionInfo")}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/trace! {:allow? false} (+ 1 2)))] [(is (= rv 3)) (is (nil? sv))])

      (testing "Use with `catch->error!`"
        (let [{re :error, [sv1 sv2] :signals} (with-sigs (tel/trace! ::id1 (tel/catch->error! ::id2 (ex1!))))]
          [(is (ex1? re))
           (is (sm? sv1 {:kind :error, :line :submap/some, :level :error, :id ::id2, :error ex1, :parent {:id ::id1}}))
           (is (sm? sv2 {:kind :trace, :line :submap/some, :level :info,  :id ::id1, :error ex1, :root   {:id ::id1}}))]))

      (testing  ":run-form" ; Undocumented, experimental
        [(is (sm? (with-sig (tel/trace! :non-list))    {:run-form :non-list}))
         (is (sm? (with-sig (tel/trace! (+ 1 2 3 4)))  {:run-form '(+ 1 2 3 4)}))
         (is (sm? (with-sig (tel/trace! (+ 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16))) {:run-form '(+ ...)}))
         (is (sm? (with-sig (tel/trace! {:run-form my-run-form} (+ 1 2 3 4)))        {:run-form 'my-run-form :kvs nil}))])

      (testing ":run-val" ; Undocumented, experimental
        [(is (sm? (with-sig (tel/trace!                     (+ 2 2))) {:run-val 4,        :msg_ "(+ 2 2) => 4"}))
         (is (sm? (with-sig (tel/trace! {:run-val "custom"} (+ 2 2))) {:run-val "custom", :msg_ "(+ 2 2) => custom", :kvs nil}))])])

   (testing "spy" ; ?level + run => unconditional run result (value or throw)
     [(let [{rv :value, [sv] :signals} (with-sigs (tel/spy!                     (+ 1 2))) ] [(is (= rv 3))  (is (sm?  sv {:kind :spy, :line :submap/some, :level :info, :msg_ "(+ 1 2) => 3"}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/spy! {:msg nil}          (+ 1 2))) ] [(is (= rv 3))  (is (sm?  sv {:kind :spy, :line :submap/some, :level :info, :msg_ nil}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/spy!         :warn       (+ 1 2))) ] [(is (= rv 3))  (is (sm?  sv {:kind :spy, :line :submap/some, :level :warn}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/spy! {:level :warn}      (+ 1 2))) ] [(is (= rv 3))  (is (sm?  sv {:kind :spy, :line :submap/some, :level :warn}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/spy! {:level :warn, :run (+ 1 2)}))] [(is (= rv 3))  (is (sm?  sv {:kind :spy, :line :submap/some, :level :warn}))])
      (let [{re :error, [sv] :signals} (with-sigs (tel/spy!         :warn         (ex1!)))] [(is (ex1? re)) (is (sm?  sv {:kind :spy, :line :submap/some, :level :warn, :error ex1,
                                                                                                             :msg_ #?(:clj  "(ex1!) !> clojure.lang.ExceptionInfo"
                                                                                                                      :cljs "(ex1!) !> cljs.core/ExceptionInfo")}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/spy! {:allow? false} (+ 1 2))) ] [(is (= rv 3)) (is (nil? sv))])

      (testing "Use with `catch->error!`"
        (let [{re :error, [sv1 sv2] :signals} (with-sigs (tel/spy! :warn (tel/catch->error! :error (ex1!))))]
          [(is (ex1? re))
           (is (sm? sv1 {:kind :error, :line :submap/some, :level :error, :error ex1, :parent {}}))
           (is (sm? sv2 {:kind :spy,   :line :submap/some, :level :warn,  :error ex1, :root   {}}))]))])

   (testing "error!" ; ?id + error => unconditional given error
     [(let [{rv :value, [sv] :signals} (with-sigs (tel/error!                        ex1)) ] [(is (ex1? rv)) (is (sm?  sv {:kind :error, :line :submap/some, :level :error, :error ex1, :id  nil}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/error!           :id1         ex1)) ] [(is (ex1? rv)) (is (sm?  sv {:kind :error, :line :submap/some, :level :error, :error ex1, :id :id1}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/error! {:id      :id1}        ex1)) ] [(is (ex1? rv)) (is (sm?  sv {:kind :error, :line :submap/some, :level :error, :error ex1, :id :id1}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/error! {:id      :id1, :error ex1}))] [(is (ex1? rv)) (is (sm?  sv {:kind :error, :line :submap/some, :level :error, :error ex1, :id :id1}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/error! {:allow? false}        ex1)) ] [(is (ex1? rv)) (is (nil? sv))])
      (let [c (enc/counter)]                      (tel/error!                (do (c) ex1))    (is (= @c 1) "Error form evaluated exactly once"))])

   (testing "catch->error!" ; ?id + run => unconditional run value or ?return
     [(let [{rv :value, [sv] :signals} (with-sigs (tel/catch->error!                   (+ 1 2)))] [(is (= rv    3)) (is (nil? sv))])
      (let [{re :error, [sv] :signals} (with-sigs (tel/catch->error!                    (ex1!)))] [(is (ex1?   re)) (is (sm? sv {:kind :error, :line :submap/some, :level :error, :error ex1, :id  nil}))])
      (let [{re :error, [sv] :signals} (with-sigs (tel/catch->error!             :id1   (ex1!)))] [(is (ex1?   re)) (is (sm? sv {:kind :error, :line :submap/some, :level :error, :error ex1, :id :id1}))])
      (let [{re :error, [sv] :signals} (with-sigs (tel/catch->error! {:id        :id1}  (ex1!)))] [(is (ex1?   re)) (is (sm? sv {:kind :error, :line :submap/some, :level :error, :error ex1, :id :id1}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/catch->error! {:catch-val :foo}  (ex1!)))] [(is (= rv :foo)) (is (sm? sv {:kind :error, :line :submap/some, :level :error, :error ex1, :id  nil}))])
      (let [{rv :value, [sv] :signals} (with-sigs (tel/catch->error! {:catch-val :foo} (+ 1 2)))] [(is (= rv    3)) (is (nil? sv))])])

   #?(:clj
      (testing "uncaught->error!"
        (let [sv_ (atom ::nx)]
          [(do (enc/set-var-root! impl/*sig-handlers* [(sigs/wrap-handler "h1" (fn h1 [x] (reset! sv_ x)) nil {:async nil})]) :set-handler)
           ;;
           (is (nil? (tel/uncaught->error!)))
           (is (do (.join (enc/threaded :user (ex1!))) (sm? @sv_ {:kind :error, :line :submap/some, :level :error, :error ex1, :id  nil})))
           ;;
           (is (nil? (tel/uncaught->error! :id1)))
           (is (do (.join (enc/threaded :user (ex1!))) (sm? @sv_ {:kind :error, :line :submap/some, :level :error, :error ex1, :id :id1})))
           ;;
           (is (nil? (tel/uncaught->error! {:id :id1})))
           (is (do (.join (enc/threaded :user (ex1!))) (sm? @sv_ {:kind :error, :line :submap/some, :level :error, :error ex1, :id :id1})))
           ;;
           (do (enc/set-var-root! impl/*sig-handlers* nil) :unset-handler)])))])

;;;;

(deftest _core-async
  (testing "Signals in go macros"
    [(async/go (tel/log! "hello"))]))

(deftest _dispatch-signal!
  [(sm? (tel/with-signal (tel/dispatch-signal! (assoc (tel/with-signal :trap (tel/log! "hello")) :level :warn)))
     {:kind :log, :level :warn, :line :submap/some})])

#?(:clj
   (deftest _uncaught->handler!
     (let [p (promise)]
       [(do (tel/add-handler! ::p (fn ([]) ([sig] (p sig))) {}) :add-handler)
        (is (nil? (tel/uncaught->error! ::uncaught)))
        (do (enc/threaded :user (throw ex1)) :run-thread)
        (is (sm? (deref p 2000 nil) {:kind :error, :level :error, :id ::uncaught, :error ex1}))
        (is (nil? (tel/uncaught->error! nil)))
        (do (tel/remove-handler! ::p) :remove-handler)])))

;;;; Interop

(comment (def ^org.slf4j.Logger sl (org.slf4j.LoggerFactory/getLogger "my.class")))

#?(:clj
   (deftest _interop
     [(testing "tools.logging -> Telemere"
        [(is (sm? (tel/check-interop) {:tools-logging {:present? true, :sending->telemere? true, :telemere-receiving? true}}))
         (is (sm? (with-sig (ctl/info "Hello" "x" "y")) {:level :info, :ns "taoensso.telemere-tests", :kind :tools-logging, :msg_ "Hello x y", :inst pinst?}))
         (is (sm? (with-sig (ctl/warn "Hello" "x" "y")) {:level :warn, :ns "taoensso.telemere-tests", :kind :tools-logging, :msg_ "Hello x y", :inst pinst?}))
         (is (sm? (with-sig (ctl/error ex1 "An error")) {:level :error, :error ex1, :inst pinst?}) "Errors")])

      (testing "Standard out/err streams -> Telemere"
        [(is (sm?   (tel/check-interop) {:system/out {:sending->telemere? false, :telemere-receiving? false},
                                         :system/err {:sending->telemere? false, :telemere-receiving? false}}))

         (is (true? (tel/streams->telemere!)))
         (is (sm?   (tel/check-interop) {:system/out {:sending->telemere? true,  :telemere-receiving? true},
                                         :system/err {:sending->telemere? true,  :telemere-receiving? true}}))

         (is (true? (tel/streams->reset!)))
         (is (sm?   (tel/check-interop) {:system/out {:sending->telemere? false, :telemere-receiving? false},
                                         :system/err {:sending->telemere? false, :telemere-receiving? false}}))

         (is (sm? (with-sig (tel/with-out->telemere (println "Hello" "x" "y")))
               {:level :info, :location nil, :ns nil, :kind :system/out, :msg_ "Hello x y"}))])

      (testing "SLF4J -> Telemere"
        [(is (sm? (tel/check-interop) {:slf4j {:present? true, :sending->telemere? true, :telemere-receiving? true}}))
         (let [^org.slf4j.Logger sl (org.slf4j.LoggerFactory/getLogger "my.class")]
           [(testing "Basics"
              [(is (sm? (with-sig (.info sl "Hello"))               {:level :info, :ns "my.class", :kind :slf4j, :msg_ "Hello", :inst pinst?}) "Legacy API: info basics")
               (is (sm? (with-sig (.warn sl "Hello"))               {:level :warn, :ns "my.class", :kind :slf4j, :msg_ "Hello", :inst pinst?}) "Legacy API: warn basics")
               (is (sm? (with-sig (-> (.atInfo sl) (.log "Hello"))) {:level :info, :ns "my.class", :kind :slf4j, :msg_ "Hello", :inst pinst?}) "Fluent API: info basics")
               (is (sm? (with-sig (-> (.atWarn sl) (.log "Hello"))) {:level :warn, :ns "my.class", :kind :slf4j, :msg_ "Hello", :inst pinst?}) "Fluent API: warn basics")])

            (testing "Message formatting"
             (let [msgp "x={},y={}", expected {:msg_ "x=1,y=2", :data {:slf4j/args ["1" "2"]}}]
               [(is (sm? (with-sig (.info sl msgp "1" "2"))                                                           expected) "Legacy API: formatted message, raw args")
                (is (sm? (with-sig (-> (.atInfo sl) (.setMessage msgp) (.addArgument "1") (.addArgument "2") (.log))) expected) "Fluent API: formatted message, raw args")]))

            (is (sm? (with-sig (-> (.atInfo sl) (.addKeyValue "k1" "v1") (.addKeyValue "k2" "v2") (.log))) {:data {:slf4j/kvs {"k1" "v1", "k2" "v2"}}}) "Fluent API: kvs")

            (testing "Markers"
              (let [m1 (#'slf4j/est-marker! "M1")
                    m2 (#'slf4j/est-marker! "M2")
                    cm (#'slf4j/est-marker! "Compound" "M1" "M2")]

                [(is (sm? (with-sig (.info sl cm "Hello"))                                    {:data {:slf4j/marker-names #{"Compound" "M1" "M2"}}}) "Legacy API: markers")
                 (is (sm? (with-sig (-> (.atInfo sl) (.addMarker m1) (.addMarker cm) (.log))) {:data {:slf4j/marker-names #{"Compound" "M1" "M2"}}}) "Fluent API: markers")]))

            (testing "Errors"
              [(is (sm? (with-sig (.warn sl "An error" ^Throwable ex1))     {:level :warn, :error ex1}) "Legacy API: errors")
               (is (sm? (with-sig (-> (.atWarn sl) (.setCause ex1) (.log))) {:level :warn, :error ex1}) "Fluent API: errors")])

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

   (is (sm? (with-sig (timbre/info ex1 "x1" "x2")) {:kind :log, :level :info, :error ex1, :msg_ "x1 x2", :data {:vargs ["x1" "x2"]}}) "First-arg error")

   (is (sm?                            (with-sig (timbre/spy! :info "my-name" (+ 1 2))) {:kind :spy, :level :info,  :id timbre/shim-id, :msg_ "my-name => 3", :ns pstr?}))
   (is (sm? (tel/with-min-level :debug (with-sig (timbre/spy! (+ 1 2))))                {:kind :spy, :level :debug, :id timbre/shim-id, :msg_ "(+ 1 2) => 3", :ns pstr?}))

   (let [{[sv1 sv2] :signals} (tel/with-min-level :debug (with-sigs (timbre/spy! (ex1!))))]
     [(is (sm? sv1 {:kind :error, :level :error, :id timbre/shim-id, :msg_ nil,  :error ex1, :ns pstr?}))
      (is (sm? sv2 {:kind :spy,   :level :debug, :id timbre/shim-id, :msg_ pstr? :error ex1, :ns pstr?}))])

   (let [{re :error, [sv] :signals} (with-sigs (timbre/log-errors             (ex1!)))] [(is (nil? re)) (is (sm? sv {:kind :error, :level :error, :error ex1, :id timbre/shim-id}))])
   (let [{re :error, [sv] :signals} (with-sigs (timbre/log-and-rethrow-errors (ex1!)))] [(is (ex1? re)) (is (sm? sv {:kind :error, :level :error, :error ex1, :id timbre/shim-id}))])])

;;;; Utils

(deftest _utils
  [(testing "error-signal?"
     [(is (= (utils/error-signal? {:error    nil}) false))
      (is (= (utils/error-signal? {:error    ex1}) true))
      (is (= (utils/error-signal? {:kind  :error}) true))
      (is (= (utils/error-signal? {:level :error}) true))
      (is (= (utils/error-signal? {:level :fatal}) true))
      (is (= (utils/error-signal? {:error?  true}) true))])

   (testing "clean-signal-fn"
     (let [sig
           {:level    :info
            :id       nil
            :msg_     (delay "msg")
            :error    ex2
            :location "loc"
            :kvs      "kvs"
            :file     "file"
            :thread   "thread"
            :a        "a"
            :b        "b"}]

       [(is (= ((utils/clean-signal-fn)                      sig) {:level :info, :msg_ "msg", :error ex2-chain}))
        (is (= ((utils/clean-signal-fn {:incl-kvs?  true})   sig) {:level :info, :msg_ "msg", :error ex2-chain, :a "a", :b "b"}))
        (is (= ((utils/clean-signal-fn {:incl-nils? true})   sig) {:level :info, :msg_ "msg", :error ex2-chain, :id nil}))
        (is (= ((utils/clean-signal-fn {:incl-keys #{:kvs}}) sig) {:level :info, :msg_ "msg", :error ex2-chain, :kvs "kvs"}))
        (is (= ((utils/clean-signal-fn {:incl-keys #{:a}})   sig) {:level :info, :msg_ "msg", :error ex2-chain, :a "a"}))
        (is (= ((utils/clean-signal-fn {:incl-keys
                                        #{:location :kvs :file :thread}}) sig) {:level :info, :msg_ "msg", :error ex2-chain,
                                                                                :location "loc", :kvs "kvs", :file "file", :thread "thread"}))]))

   (testing "Misc utils"
     [(is (= (utils/remove-signal-kvs   {:a :A, :b :B, :kvs {:b :B}}) {:a :A}))
      (is (= (utils/remove-signal-nils  {:a :A, :b nil}) {:a :A}))
      (is (= (utils/force-signal-msg    {:a :A, :msg_ (delay "msg")}) {:a :A, :msg_ "msg"}))
      (is (= (utils/expand-signal-error {:level :info, :error ex2}) {:level :info, :error ex2-chain}))])

   #?(:clj
      (testing "File writer"
        (let [f  (java.io.File/createTempFile "file-writer-test" ".txt")
              fw (utils/file-writer {:file f, :append? false})]

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
     [(is (= ((utils/format-nsecs-fn) 1.5e9) "1.50s")) ; More tests in Encore
      (is (= ((utils/format-inst-fn)     t1) "2024-01-01T01:01:01.110Z"))

      (testing "format-error-fn"
        (let [ex2-str ((utils/format-error-fn) ex2)]
          [(is (enc/str-starts-with? ex2-str
                 #?(:clj  "  Root: clojure.lang.ExceptionInfo - Ex1\n  data: {:k1 \"v1\"}\n\nCaused: clojure.lang.ExceptionInfo - Ex2\n  data: {:k2 \"v2\"}\n\nRoot stack trace:\n"
                    :cljs "  Root: cljs.core/ExceptionInfo - Ex1\n  data: {:k1 \"v1\"}\n\nCaused: cljs.core/ExceptionInfo - Ex2\n  data: {:k2 \"v2\"}\n\nRoot stack trace:\n")))

           (is (enc/str-contains? ex2-str           "Root stack trace:"))
           (is (enc/str-contains? ex2-str "invoke") "Root stack trace includes content")]))

      (testing "signal-preamble-fn"
        (let [sig      (with-sig :raw :trap (tel/event! ::ev-id {:inst t1, :msg ["a" "b"]}))
              preamble ((utils/signal-preamble-fn) sig)] ; "2024-06-09T21:15:20.170Z INFO EVENT taoensso.telemere-tests(592,35) ::ev-id"
          [(is (enc/str-starts-with? preamble "2024-01-01T01:01:01.110Z INFO EVENT"))
           (is (enc/str-ends-with?   preamble "::ev-id a b"))
           (is (string? (re-find #"taoensso.telemere-tests\(\d+,\d+\)" preamble)))]))

      (testing "pr-signal-fn"
        (let [sig (with-sig :raw :trap (tel/event! ::ev-id {:inst t1, :msg ["a" "b"]}))]

          [(testing ":edn pr-fn"
             (let [sig   (update sig :inst enc/inst->udt)
                   sig*1 (enc/read-edn ((tel/pr-signal-fn {:pr-fn :edn}) sig))
                   sig*2 (enc/read-edn ((tel/pr-signal-fn)               sig))]

               [(is (= sig*1 sig*2) "Default :pr-fn is :edn")
                (is
                  (enc/submap? sig*1
                    {:schema 1, :kind :event, :id ::ev-id, :level :info,
                     :ns      "taoensso.telemere-tests"
                     :msg_    "a b"
                     :inst    udt1
                     :line    pnat-int?
                     :column  pnat-int?}))]))

           #?(:cljs
              (testing ":json pr-fn"
                (let [sig* (enc/read-json ((tel/pr-signal-fn {:pr-fn :json}) sig))]
                  (is
                    (enc/submap? sig*
                      {"schema" 1, "kind" "event", "id" "taoensso.telemere-tests/ev-id",
                       "level" "info",             "ns" "taoensso.telemere-tests"
                       "msg_"    "a b"
                       "inst"    t1s
                       "line"    pnat-int?
                       "column"  pnat-int?})))))

           (testing "Custom pr-fn"
             (is (= ((tel/pr-signal-fn {:pr-fn (fn [_] "str")}) sig) (str "str" utils/newline))))]))

      (testing "format-signal-fn"
        (let [sig (with-sig :raw :trap (tel/event! ::ev-id {:inst t1, :msg ["a" "b"]}))]
          [(is (enc/str-starts-with? ((tel/format-signal-fn)         sig       ) "2024-01-01T01:01:01.110Z INFO EVENT"))
           (is (enc/str-ends-with?   ((tel/format-signal-fn) (dissoc sig :host)) "::ev-id a b\n"))]))])])

;;;; File handler

#?(:clj
   (deftest _file-names
     [(is (= (files/get-file-name "/logs/app.log" nil  nil false) "/logs/app.log"))
      (is (= (files/get-file-name "/logs/app.log" nil  nil true)  "/logs/app.log"))
      (is (= (files/get-file-name "/logs/app.log" "ts" nil true)  "/logs/app.log-ts"))
      (is (= (files/get-file-name "/logs/app.log" "ts" 1   false) "/logs/app.log-ts.1"))
      (is (= (files/get-file-name "/logs/app.log" "ts" 1   true)  "/logs/app.log-ts.1.gz"))
      (is (= (files/get-file-name "/logs/app.log" nil  1   false) "/logs/app.log.1"))
      (is (= (files/get-file-name "/logs/app.log" nil  1   true)  "/logs/app.log.1.gz"))]))

#?(:clj
   (deftest _file-timestamps
     [(is (= (files/format-file-timestamp :daily   (files/udt->edy udt1)) "2024-01-01d"))
      (is (= (files/format-file-timestamp :weekly  (files/udt->edy udt1)) "2024-01-01w"))
      (is (= (files/format-file-timestamp :monthly (files/udt->edy udt1)) "2024-01-01m"))]))

(comment (files/manage-test-files! :create))

#?(:clj
   (deftest _file-handling
     [(is (boolean (files/manage-test-files! :create)))

      (testing "`scan-files`"
        ;; Just checking basic counts here, should be sufficient
        [(is (= (count (files/scan-files "test/logs/app1.log" nil     nil :sort))  1) "1 main, 0 parts")
         (is (= (count (files/scan-files "test/logs/app1.log" :daily  nil :sort))  0) "0 stamped")
         (is (= (count (files/scan-files "test/logs/app2.log" nil     nil :sort))  6) "1 main, 5 parts (+gz)")
         (is (= (count (files/scan-files "test/logs/app3.log" nil     nil :sort))  6) "1 main, 5 parts (-gz")
         (is (= (count (files/scan-files "test/logs/app4.log" nil     nil :sort)) 11) "1 main, 5 parts (+gz) + 5 parts (-gz)")
         (is (= (count (files/scan-files "test/logs/app5.log" nil     nil :sort))  1) "1 main, 0 unstamped")
         (is (= (count (files/scan-files "test/logs/app5.log" :daily  nil :sort))  5) "5 stamped")
         (is (= (count (files/scan-files "test/logs/app6.log" nil     nil :sort))  1) "1 main, 0 unstamped")
         (is (= (count (files/scan-files "test/logs/app6.log" :daily  nil :sort)) 25) "5 stamped * 5 parts")
         (is (= (count (files/scan-files "test/logs/app6.log" :weekly nil :sort))  5) "5 stamped")])

      (testing "`archive-main-file!`"
        [(is (= (let [df (files/debugger)] (files/archive-main-file! "test/logs/app1.log" nil nil 2 :gz df) (df))
               [[:rename "test/logs/app1.log" "test/logs/app1.log.1.gz"]]))

         (is (= (let [df (files/debugger)] (files/archive-main-file! "test/logs/app2.log" nil nil 2 :gz df) (df))
               [[:delete "test/logs/app2.log.5.gz"]
                [:delete "test/logs/app2.log.4.gz"]
                [:delete "test/logs/app2.log.3.gz"]
                [:delete "test/logs/app2.log.2.gz"]
                [:rename "test/logs/app2.log.1.gz" "test/logs/app2.log.2.gz"]
                [:rename "test/logs/app2.log"      "test/logs/app2.log.1.gz"]]))

         (is (= (let [df (files/debugger)] (files/archive-main-file! "test/logs/app3.log" nil nil 2 :gz df) (df))
               [[:delete "test/logs/app3.log.5"]
                [:delete "test/logs/app3.log.4"]
                [:delete "test/logs/app3.log.3"]
                [:delete "test/logs/app3.log.2"]
                [:rename "test/logs/app3.log.1" "test/logs/app3.log.2"]
                [:rename "test/logs/app3.log"   "test/logs/app3.log.1.gz"]]))

         (is (= (let [df (files/debugger)] (files/archive-main-file! "test/logs/app6.log" :daily "2021-01-01d" 2 :gz df) (df))
               [[:delete "test/logs/app6.log-2021-01-01d.5.gz"]
                [:delete "test/logs/app6.log-2021-01-01d.4.gz"]
                [:delete "test/logs/app6.log-2021-01-01d.3.gz"]
                [:delete "test/logs/app6.log-2021-01-01d.2.gz"]
                [:rename "test/logs/app6.log-2021-01-01d.1.gz" "test/logs/app6.log-2021-01-01d.2.gz"]
                [:rename "test/logs/app6.log"                  "test/logs/app6.log-2021-01-01d.1.gz"]]))])

      (testing "`prune-archive-files!`"
        [(is (= (let [df (files/debugger)] (files/prune-archive-files! "test/logs/app1.log" nil    2 df) (df)) []))
         (is (= (let [df (files/debugger)] (files/prune-archive-files! "test/logs/app2.log" nil    2 df) (df)) []))
         (is (= (let [df (files/debugger)] (files/prune-archive-files! "test/logs/app5.log" nil    2 df) (df)) []))
         (is (= (let [df (files/debugger)] (files/prune-archive-files! "test/logs/app5.log" :daily 2 df) (df))
               [[:delete "test/logs/app5.log-2020-01-01d"]
                [:delete "test/logs/app5.log-2020-01-02d"]
                [:delete "test/logs/app5.log-2020-02-01d"]]))

         (is (= (let [df (files/debugger)] (files/prune-archive-files! "test/logs/app6.log" :daily 2 df) (df))
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

      (is (boolean (files/manage-test-files! :delete)))]))

;;;; Other handlers

(deftest _handler-constructors
  [#?(:default (is (fn? (tel/handler:console))))
   #?(:cljs    (is (fn? (tel/handler:console-raw))))
   #?(:clj     (is (fn? (tel/handler:file))))
   #?(:clj     (is (fn? (otel/handler:open-telemetry))))])

(comment (def attrs-map otel/signal->attrs-map))

#?(:clj
   (defn signal->attrs* [signal]
     (enc/map-keys str
       (into {}
         (.asMap ^io.opentelemetry.api.common.Attributes
           (#'otel/signal->attrs signal))))))

(comment (signal->attrs* {:level :info}))

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

      (testing "signal->attrs"
        [(is (= (signal->attrs* {:level :info})          {"error" false, "level" "INFO"}))
         (is (= (signal->attrs* {:level :info, :k1 :v1}) {"error" false, "level" "INFO"}) "app-level kvs excluded")
         (is (sm?
               (signal->attrs*
                 {:kind  :event
                  :level :info

                  :ns   "ns"
                  :file "file"
                  :line 100

                  :id     ::id1
                  :uid                           #uuid "7e9c1df6-78e4-40ac-8c5c-e2353df9ab82"
                  :parent {:id ::parent-id1 :uid #uuid "443154cf-b6cf-47bf-b86a-8b185afee256"}
                  :root   {:id ::root-id1   :uid #uuid "82a53b6a-b28a-4102-8025-9e735dee103c"}

                  :run-form    '(+ 3 2)
                  :run-val     5
                  :run-nsecs   100
                  :sample-rate 0.5

                  :data
                  {:key-kw :val-kw
                   :num-set #{1 2 3 4 5}
                   :mix-set #{1 2 3 4 5 "foo"}}

                  :error ex2
                  :otel/attrs {:a1 :A1}})

               {"kind" ":event"
                "level" "INFO"

                "ns"   "ns"
                "file" "file"
                "line" 100

                "id"         ":taoensso.telemere-tests/id1",
                "uid"        "7e9c1df6-78e4-40ac-8c5c-e2353df9ab82",
                "parent.id"  ":taoensso.telemere-tests/parent-id1",
                "parent.uid" "443154cf-b6cf-47bf-b86a-8b185afee256",
                "root.id"    ":taoensso.telemere-tests/root-id1",
                "root.uid"   "82a53b6a-b28a-4102-8025-9e735dee103c",

                "run.form"     "(+ 3 2)"
                "run.val"      5
                "run.val_type" "java.lang.Long"
                "run.nsecs"    100
                "sample"       0.5

                "data.key_kw"  ":val-kw",
                "data.num_set" [1 4 3 2 5],
                "data.mix_set" "#{\"foo\" 1 4 3 2 5}",

                "error"                true
                "exception.type"       "clojure.lang.ExceptionInfo"
                "exception.message"    "Ex1"
                "exception.data.k1"    "v1"
                "exception.stacktrace" (enc/pred string?)

                "a1" ":A1"}))])]))

;;;;

#?(:cljs
   (defmethod test/report [:cljs.test/default :end-run-tests] [m]
     (when-not (test/successful? m)
       ;; Trigger non-zero `lein test-cljs` exit code for CI
       (throw (ex-info "ClojureScript tests failed" {})))))

#?(:cljs (test/run-tests))
