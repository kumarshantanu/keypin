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


(defkey
  {:lookup lookup-property}
  sample-prop ["sample"]
  sample-key  [:sample]
  sample-nkey [3])


(deftest test-key
  (is (= "sample"  (key sample-prop)))
  (is (= "sample"  (str sample-prop)))
  (is (= :sample   (key sample-key)))
  (is (= ":sample" (str sample-key)))
  (is (= 3         (key sample-nkey)))
  (is (= "3"       (str sample-nkey))))


(defkey
  {:lookup lookup-property}
  foo ["foo"]
  bar ["bar" string? "Expected string"]
  baz ["baz" #(< 0 % 100) "Thread-pool size (1-99)" {:parser str->int}]
  qux ["qux" bool? "Flag: Whether enable runtime tracing?" {:parser str->bool :default true}]
  esr ["qux" {:pred bool?
              :desc "Flag: Whether enable runtime tracing?"
              :parser str->bool
              :default true}])


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
      (is (thrown? IllegalArgumentException (baz bad-props)))
      (is (thrown? IllegalArgumentException (baz bad-props2)))
      (is (thrown? IllegalArgumentException (qux bad-props)))
      (is (thrown? IllegalArgumentException (esr bad-props))))
    (testing "Minimal definition"
      (is (= "hello" (foo  min-props))))
    (testing "Definition with validator/description"
      (is (= 34     (baz max-props)))
      (is (= "hola" (bar mod-props)))
      (is (true?    (qux min-props)))
      (is (false?   (qux max-props)))
      (is (true?    (esr min-props)))
      (is (false?   (esr max-props))))))


(defkey
  kfoo ["foo"]
  kbar ["bar" string? "Expected string"]
  kbaz ["baz" #(< 0 % 100) "Thread-pool size (1-99)" {:parser str->int}]
  kqux ["qux" bool? "Flag: Whether enable runtime tracing?" {:parser str->bool :default true}]
  kesr ["qux" {:pred bool? :desc "Flag: Whether enable runtime tracing?"
               :parser str->bool :default true}])


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
      (is (thrown? IllegalArgumentException (kbaz bad-keys)))
      (is (thrown? IllegalArgumentException (kbaz bad-keys2)))
      (is (thrown? IllegalArgumentException (kqux bad-keys)))
      (is (thrown? IllegalArgumentException (kesr bad-keys))))
    (testing "Minimal definition"
      (is (= "hello" (kfoo  min-keys))))
    (testing "Definition with validator/description"
      (is (= 34     (kbaz max-keys)))
      (is (= "hola" (kbar mod-keys)))
      (is (true?    (kqux min-keys)))
      (is (false?   (kqux max-keys)))
      (is (true?    (kesr min-keys)))
      (is (false?   (kesr max-keys))))))


(defkey
  vfoo [0]
  vbar [1 string? "Expected string"]
  vbaz [2 #(< 0 % 100) "Thread-pool size (1-99)" {:parser str->int}]
  vqux [3 bool? "Flag: Whether enable runtime tracing?" {:parser str->bool :default true}]
  vesr [3 {:pred bool? :desc "Flag: Whether enable runtime tracing?"
           :parser str->bool :default true}])


(deftest test-defkey-vec
 (let [no-keys []
       min-keys ["hello"]
       mod-keys ["hello" "hola"]
       max-keys ["hello" "hola" "34" "false"] 
       bad-keys ["hello" "hola" "true" "78"]
       bad-keys2 ["hello" "hola" "150" "None"]]
   (testing "Failures"
     (is (thrown? IllegalArgumentException (vfoo no-keys)))
     (is (thrown? IllegalArgumentException (vbaz bad-keys)))
     (is (thrown? IllegalArgumentException (vbaz bad-keys2)))
     (is (thrown? IllegalArgumentException (vqux bad-keys)))
     (is (thrown? IllegalArgumentException (vesr bad-keys))))
   (testing "Minimal definition"
     (is (= "hello" (vfoo  min-keys))))
   (testing "Definition with validator/description"
     (is (= 34     (vbaz max-keys)))
     (is (= "hola" (vbar mod-keys)))
     (is (true?  (vqux min-keys)))
     (is (false? (vqux max-keys)))
     (is (true?  (vesr min-keys)))
     (is (false? (vesr max-keys))))))


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


(defkey
  bat [:bat]
  cat [:cat]
  dog [:dog {:default 100}]
  rat [:rat])


(deftest test-letval
  (testing "Happy lookup"
    (let [ran? (atom false)
          data {:cat 20 :dog 30}]
      (letval [{x cat y dog :as m} data]
        (reset! ran? true)
        (is (= 20 x))
        (is (= 30 y))
        (is (= data m)))
      (is @ran?)))
  (testing "Happy :defs lookup"
    (let [ran? (atom false)
          data {:cat 20 :dog 30}]
      (letval [{:defs [cat dog] :as m} data]
        (reset! ran? true)
        (is (= 20 cat))
        (is (= 30 dog))
        (is (= data m)))
      (is @ran?)))
  (testing "Happy :defs lookup with default"
    (let [ran? (atom false)
          data {:cat 20}]
      (letval [{:defs [cat dog] :as m} data]
        (reset! ran? true)
        (is (= 100 dog))
        (is (= data m)))
      (is @ran?)))
  (testing "Happy nested map lookup"
    (let [ran? (atom false)
          data {:bat 10 :cat {:dog 20 :rat 30}}]
      (letval [{:defs [bat] {:defs [dog rat]} cat :as m} data]
        (reset! ran? true)
        (is (= 10 bat))
        (is (= 20 dog))
        (is (= 30 rat))
        (is (= data m)))
      (is @ran?)))
  (testing "Missing key"
    (let [ran? (atom false)
          data {:cat 20 :dog 30}]
      (is (thrown? IllegalArgumentException
            (letval [{z rat :as m} data]
              (reset! ran? true))))
      (is (not @ran?)))))
