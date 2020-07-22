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
    [keypin.store :as ks])
  (:import
    [java.util.concurrent TimeoutException]))


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
    ;; TODO
    ))


(deftest caching-store-test
  (testing "No regression"
    (let [cs (ks/make-caching-store {:foo 10})]
     (is (contains? cs :foo))
     (is (= 10 (get cs :foo)))))
  (testing "Lookup actually caches"
    ;; TODO
    )
  (testing "Cache busts on underlying store changes"
    ;; TODO
    ))
