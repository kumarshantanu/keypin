;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns keypin.di-test
  (:require
    [clojure.test :refer :all]
    [keypin.di :as di])
  (:import
    [clojure.lang ArityException]))


(defn foo [a b c] [a b c])


(deftest test-partial
  (let [cc-p (partial foo :first)
        di-p (di/partial foo :first)]
    (with-redefs [foo (fn [a b c] {:data [a b c]})]
      (is (= (cc-p :second :third) [:first :second :third]))
      (is (= (di-p :second :third) {:data [:first :second :third]})))))


(defn sample-1
  ([^:inject a ^:inject b]
    [a b]))


(defn sample-2
  ([^:inject a ^{:inject :b} {:keys [b d]} c]
    [a [b d] c])
  ([^:inject a ^:inject b c & more]
    [a b c more]))


(deftest test-inject
  (let [f (di/inject sample-1 {:a 10 :b 20})
        g (di/inject sample-2 {:a 10 :b {:b 20 :d 40}})]
    (is (= (f) [10 20]))
    (is (thrown? ArityException (f 30)))
    (is (thrown? ArityException (g)))
    (is (= (g 30) [10 [20 40] 30]))
    (is (= (g 30 40) [10 {:b 20 :d 40} 30 '(40)]))))
