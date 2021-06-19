;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns keypin.util-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [keypin.util :as ku])
  (:import (clojure.lang ExceptionInfo)))


(deftest test-data-readers
  (testing "env"
    (is (= {:foo nil}
          (edn/read-string {:readers ku/data-readers}
            "{:foo #env foo}")))
    (is (string?
          (-> {:readers ku/data-readers}
            (edn/read-string "{:foo #env LEIN_VERSION}")
            (get :foo)))))
  (testing "env!"
    (is (thrown-with-msg?
          ExceptionInfo
          #"Environment variable `foo` is not set"
          (edn/read-string {:readers ku/data-readers}
            "{:foo #env! foo}")))
    (is (string?
          (-> {:readers ku/data-readers}
            (edn/read-string "{:foo #env LEIN_VERSION}")
            (get :foo)))))
  (testing "join"
    (is (= {:foo ":foo45"}
          (edn/read-string {:readers ku/data-readers}
            "{:foo #join [:foo 45]}"))))
  (testing "some"
    (is (nil?
          (-> {:readers ku/data-readers}
            (edn/read-string "{:foo #some [#env foo #env bar]}")
            (get :foo))))
    (is (= :bar
          (-> {:readers ku/data-readers}
            (edn/read-string "{:foo #some [#env foo :bar]}")
            (get :foo))))
    (is (string?
          (-> {:readers ku/data-readers}
            (edn/read-string "{:foo #some [#env foo #env LEIN_VERSION]}")
            (get :foo))))))
