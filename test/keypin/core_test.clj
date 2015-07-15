(ns keypin.core-test
  (:require
    [clojure.test :refer :all]
    [keypin.core :refer :all])
  (:import
    [java.util Map Properties]))


(deftest test-property-file-reader
  (testing "Non-hierarchical"
    (println "----------")
    (let [props (read-properties "test-config/myconf.properties")]
      (is (instance? Properties props))
      (is (= "new-version" (.getProperty props "service.version")))
      (is (nil? (.getProperty props "app.version")))
      (is (nil? (.getProperty props "service.name")))))
  (testing "Hierarchical"
    (println "----------")
    (let [props (read-properties "test-config/myconf.properties" {:parent-key "parent-config"})]
      (is (instance? Properties props))
      (is (= "new-version" (.getProperty props "service.version")))
      (is (= "2.3.6" (.getProperty props "app.version")))
      (is (= "fooapp-new-version" (.getProperty props "service.name")) "overidden property in template")))
  (testing "Hierarchical with missing parent"
    (println "----------")
    (is (thrown? IllegalArgumentException
          (read-properties "test-config/errconf.properties" {:parent-key "parent"})))))


(defprop sample-prop "sample")
(defkey sample-key :sample)
(defkey sample-nkey 3)


(deftest test-key
  (is (= "sample"  (key sample-prop)))
  (is (= "sample"  (str sample-prop)))
  (is (= :sample   (key sample-key)))
  (is (= ":sample" (str sample-key)))
  (is (= 3         (key sample-nkey)))
  (is (= "3"       (str sample-nkey))))


(defprop foo "foo")
(defprop bar "bar" string? "Expected string")
(defprop baz "baz" #(< 0 % 100) "Thread-pool size (1-99)" str->int)
(defprop qux "qux" bool? "Flag: Whether enable runtime tracing?" str->bool true)


(defmanyprops
  mfoo ["foo"]
  mbar ["bar" string? "Expected string"]
  mbaz ["baz" #(< 0 % 100) "Thread-pool size (1-99)" str->int]
  mqux ["qux" bool? "Flag: Whether enable runtime tracing?" str->bool true])


(defn props
  [^Map m]
  (doto (Properties.)
    (.putAll m)))


(deftest test-defprop
  (let [no-props (props {})
        min-props (props {"foo" "hello"})
        mod-props (props {"foo" "hello"
                          "bar" "hola"})
        max-props (props {"foo" "hello"
                          "bar" "hola"
                          "baz" "34"
                          "qux" "false"})
        bad-props (props {"foo" "hello"
                          "bar" "hola"
                          "baz" "true"
                          "qux" "78"})
        bad-props2 (props {"foo" "hello"
                           "bar" "hola"
                           "baz" "150"
                           "qux" "None"})]
    (testing "Failures"
      (is (thrown? IllegalArgumentException (foo no-props)))
      (is (thrown? IllegalArgumentException (mfoo no-props)))
      (is (thrown? IllegalArgumentException (baz bad-props)))
      (is (thrown? IllegalArgumentException (mbaz bad-props)))
      (is (thrown? IllegalArgumentException (baz bad-props2)))
      (is (thrown? IllegalArgumentException (mbaz bad-props2)))
      (is (thrown? IllegalArgumentException (qux bad-props)))
      (is (thrown? IllegalArgumentException (mqux bad-props))))
    (testing "Minimal definition"
      (is (= "hello" (foo  min-props)))
      (is (= "hello" (mfoo  min-props))))
    (testing "Definition with validator/description"
      (is (= 34 (baz max-props)))
      (is (= 34 (mbaz max-props)))
      (is (= "hola" (bar mod-props)))
      (is (= "hola" (mbar mod-props)))
      (is (true? (qux min-props)))
      (is (true? (mqux min-props)))
      (is (false? (qux max-props)))
      (is (false? (mqux max-props))))))


(defkey kfoo "foo")
(defkey kbar "bar" string? "Expected string")
(defkey kbaz "baz" #(< 0 % 100) "Thread-pool size (1-99)" str->int)
(defkey kqux "qux" bool? "Flag: Whether enable runtime tracing?" str->bool true)


(defmanykeys
  mkfoo ["foo"]
  mkbar ["bar" string? "Expected string"]
  mkbaz ["baz" #(< 0 % 100) "Thread-pool size (1-99)" str->int]
  mkqux ["qux" bool? "Flag: Whether enable runtime tracing?" str->bool true])


(deftest test-defkey-map
  (let [no-keys {}
        min-keys {"foo" "hello"}
        mod-keys {"foo" "hello"
                   "bar" "hola"}
        max-keys {"foo" "hello"
                   "bar" "hola"
                   "baz" "34"
                   "qux" "false"}
        bad-keys {"foo" "hello"
                   "bar" "hola"
                   "baz" "true"
                   "qux" "78"}
        bad-keys2 {"foo" "hello"
                    "bar" "hola"
                    "baz" "150"
                    "qux" "None"}]
    (testing "Failures"
      (is (thrown? IllegalArgumentException (kfoo no-keys)))
      (is (thrown? IllegalArgumentException (mkfoo no-keys)))
      (is (thrown? IllegalArgumentException (kbaz bad-keys)))
      (is (thrown? IllegalArgumentException (mkbaz bad-keys)))
      (is (thrown? IllegalArgumentException (kbaz bad-keys2)))
      (is (thrown? IllegalArgumentException (mkbaz bad-keys2)))
      (is (thrown? IllegalArgumentException (kqux bad-keys)))
      (is (thrown? IllegalArgumentException (mkqux bad-keys))))
    (testing "Minimal definition"
      (is (= "hello" (kfoo  min-keys)))
      (is (= "hello" (mkfoo  min-keys))))
    (testing "Definition with validator/description"
      (is (= 34 (kbaz max-keys)))
      (is (= 34 (mkbaz max-keys)))
      (is (= "hola" (kbar mod-keys)))
      (is (= "hola" (mkbar mod-keys)))
      (is (true? (kqux min-keys)))
      (is (true? (mkqux min-keys)))
      (is (false? (kqux max-keys)))
      (is (false? (mkqux max-keys))))))


(defkey vfoo 0)
(defkey vbar 1 string? "Expected string")
(defkey vbaz 2 #(< 0 % 100) "Thread-pool size (1-99)" str->int)
(defkey vqux 3 bool? "Flag: Whether enable runtime tracing?" str->bool true)


(defmanykeys
  mvfoo [0]
  mvbar [1 string? "Expected string"]
  mvbaz [2 #(< 0 % 100) "Thread-pool size (1-99)" str->int]
  mvqux [3 bool? "Flag: Whether enable runtime tracing?" str->bool true])


(deftest test-defkey-vec
 (let [no-keys []
       min-keys ["hello"]
       mod-keys ["hello" "hola"]
       max-keys ["hello" "hola" "34" "false"] 
       bad-keys ["hello" "hola" "true" "78"]
       bad-keys2 ["hello" "hola" "150" "None"]]
   (testing "Failures"
     (is (thrown? IllegalArgumentException (vfoo no-keys)))
     (is (thrown? IllegalArgumentException (mvfoo no-keys)))
     (is (thrown? IllegalArgumentException (vbaz bad-keys)))
     (is (thrown? IllegalArgumentException (mvbaz bad-keys)))
     (is (thrown? IllegalArgumentException (vbaz bad-keys2)))
     (is (thrown? IllegalArgumentException (mvbaz bad-keys2)))
     (is (thrown? IllegalArgumentException (vqux bad-keys)))
     (is (thrown? IllegalArgumentException (mvqux bad-keys))))
   (testing "Minimal definition"
     (is (= "hello" (vfoo  min-keys)))
     (is (= "hello" (mvfoo  min-keys))))
   (testing "Definition with validator/description"
     (is (= 34 (vbaz max-keys)))
     (is (= 34 (mvbaz max-keys)))
     (is (= "hola" (vbar mod-keys)))
     (is (= "hola" (mvbar mod-keys)))
     (is (true? (vqux min-keys)))
     (is (true? (mvqux min-keys)))
     (is (false? (vqux max-keys)))
     (is (false? (mvqux max-keys))))))


(deftest test-parsers
  (is (true? (str->bool "foo" "true")))
  (is (thrown? IllegalArgumentException
        (str->bool "foo" "notboolean")))
  (is (integer? (str->int "foo" "10")))
  (is (integer? (str->long "foo" "10")))
  (is (float? (str->float "foo" "10.23")))
  (is (float? (str->double "foo" "10.23")))
  (is (var? (str->var "foo" "keypin.test-sample/hello")))
  (is ((deref? string?) (str->var "foo" "keypin.test-sample/hello")))
  (is (var? (str->var "foo" "keypin.test-sample/hola")))
  (is ((deref? fn?) (str->var "foo" "keypin.test-sample/hola")))
  (is (thrown? IllegalArgumentException
        (str->var "foo" "keypin.test-samplex")) "Bad var format")
  (is (thrown? IllegalArgumentException
        (str->var "foo" "keypin.test-samples/hellow")) "Bad ns")
  (is (thrown? IllegalArgumentException
        (str->var "foo" "keypin.test-sample/hellow")) "Bad var"))
