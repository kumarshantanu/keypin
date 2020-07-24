;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns keypin.store-test
  (:require
    [clojure.test :refer :all]
    [keypin.core  :as kc]
    [keypin.store :as ks]
    [keypin.util  :as ku])
  (:import
    [java.util.concurrent TimeoutException]
    [keypin.type.record CachingStore]))


(deftest dynamic-store-test
  (testing "Fixed store, initialized with fixed data"
    (let [fetch-fixed (constantly {:foo 10})
          fixed-store (ks/make-dynamic-store fetch-fixed (fetch-fixed))]
      (is (= 10 (get fixed-store :foo)))))
  ;;
  (testing "Fixed store, not initialized"
    (let [fetch-fixed (constantly {:foo 10})
          fixed-store (ks/make-dynamic-store fetch-fixed nil)]
      (Thread/sleep 100)
      (is (= 10 (get fixed-store :foo)) "recovers waiting to initialize store")
      (try (Thread/sleep 1000) (catch InterruptedException _))
      (is (= 10 (get fixed-store :foo)) "timed out waiting to update stale data")))
  ;;
  (testing "Fixed store, not initialized, disabled `fetch?` fn"
    (let [fetch-fixed (constantly {:foo 10})
          fixed-store (ks/make-dynamic-store fetch-fixed nil {:fetch? (constantly false)})]
      (is (thrown? TimeoutException
            (contains? fixed-store :foo)) "timed out waiting to initialize store")
      (try (Thread/sleep 1000) (catch InterruptedException _))
      (is (thrown? TimeoutException
            (contains? fixed-store :foo)) "timed out waiting to update stale data")))
  ;;
  (testing "Fixed store, not initialized, fetch fn throws"
    (let [fetch-never (fn [_] (throw (Exception. "Fetch error")))
          fixed-store (ks/make-dynamic-store fetch-never nil)]
      (is (thrown? TimeoutException
            (contains? fixed-store :foo)) "recovers waiting to initialize store")
      (try (Thread/sleep 1000) (catch InterruptedException _))
      (is (thrown? TimeoutException
            (contains? fixed-store :foo)) "timed out waiting to update stale data")))
  (testing "Actualy dynamically fetched config"
    (let [ds (ks/make-dynamic-store (fn [_] {:foo 10}) {:foo 20})]
      (is (= 20 (get ds :foo)))
      (Thread/sleep 1100)  ; wait for 1s refresh window to elapse
      (get ds :foo)        ; trigger re-fetch
      (Thread/sleep 100)   ; wait for fetch to happen
      (is (= 10 (get ds :foo))))))


(defmacro nanos
  "Evaluate body of code and return elapsed time in nanoseconds."
  [& body]
  `(let [start# (System/nanoTime)]
     (try
       ~@body
       (unchecked-subtract (System/nanoTime) start#)
       (catch Exception e#
         (unchecked-subtract (System/nanoTime) start#)))))


(kc/defkey kfoo [:foo pos? "positive integer" {:parser ku/str->long}])


(deftest caching-store-test
  (testing "No regression"
    (let [cs (ks/make-caching-store {:foo "10"})]
     (is (contains? cs :foo))
     (is (= "10" (get cs :foo)))
     (is (= 10 (kfoo cs)))))
  (testing "Lookup actually caches"
    (let [cs (ks/make-caching-store {:foo "10"})
          t1 (nanos (kfoo cs))]
      (Thread/sleep 50)
      (dotimes [_ 5000] (kfoo cs))  ; warmup
      (is (> t1 (nanos (kfoo cs))))))
  (testing "Cache busts on underlying store changes"
    (let [ds (ks/make-dynamic-store (fn [_] {:foo "10"}) {:foo "20"})
          cs (ks/make-caching-store ds)
          gc (fn [] (:cache-data @(.-state-agent ^CachingStore cs)))]
      (is (= 20 (kfoo ds)))
      (is (= {} (gc)) "before cache build up")
      (is (= 20 (kfoo cs)))
      (is (= {:foo 20} (gc)) "after cache populating")
      (Thread/sleep 1000)  ; wait for dynamic store 01 second refresh window to elapse
      (is (= 10 (kfoo cs)))
      (is (= {} (gc)) "busted cache")
      (Thread/sleep 100)
      (is (= 10 (kfoo cs)))
      (is (= {:foo 10} (gc)) "re-populated cache"))))
