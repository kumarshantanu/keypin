;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns keypin.core-test
  (:require
    [clojure.test :refer :all]
    [keypin.core :refer :all]
    [keypin.util :as ku]
    [keypin.test-helper :as th])
  (:import
    [java.io File]
    [java.util Map Properties]
    [java.util.concurrent TimeUnit]))


(deftest test-config-file-reader
  (testing "Non-hierarchical"
    (doseq [config-fileset [["test-config/myconf.properties"]
                            ["test-config/myconf.edn"]]]
      (println "----------")
      (let [props (read-config ["test-config/myconf.properties"])]
        (is (instance? Map props))
        (is (= "new-version" (get props "service.version")))
        (is (nil? (get props "app.version")))
        (is (nil? (get props "service.name")))
        (is (= "identity-not-mentioned" (get props "app.identity"))))))
  (testing "Hierarchical"
    (doseq [config-fileset [["test-config/myconf.properties"]
                            ["test-config/myconf.edn"]]]
      (println "----------")
      (let [parent-key "parent-config"
            props (read-config config-fileset {:parent-key parent-key})]
        (is (instance? Map props))
        (is (not (.containsKey props parent-key)) "hierarchical config resolution should eliminate parent key")
        (is (= "new-version" (get props "service.version")))
        (is (= "2.3.6" (get props "app.version")))
        (is (= "fooapp-new-version" (get props "service.name")) "overidden property in template")
        (is (= "identity-not-mentioned" (get props "app.identity")) "variable substitution")
        (is (= "unicorn" (get props "some.var"))))))
  (testing "Hierarchical with missing parent"
    (doseq [config-fileset [["test-config/errconf.properties"]
                            ["test-config/errconf.edn"]]]
      (println "----------")
      (is (thrown? IllegalArgumentException
            (read-config config-fileset {:parent-key "parent"}))))))


(deftest test-config-file-writer
  (doseq [[config-fileset output-extension] [[["test-config/myconf.properties"] ".properties"]
                                             [["test-config/myconf.edn"] ".edn"]]]
    (let [parent-key "parent-config"
          props (read-config config-fileset {:parent-key parent-key})
          tfile (File/createTempFile "auto-generated-" output-extension)
          tname (.getAbsolutePath tfile)]
      (.deleteOnExit tfile)
      (println "Writing properties to" tname)
      (write-config tname props)
      (let [fresh-props (read-config [tname])]
        (is (= props fresh-props) "Generated properties should be the same as what we read afresh")))))


(deftest test-property-file-reader
  (testing "Non-hierarchical"
    (println "----------")
    (let [props (read-properties ["test-config/myconf.properties"])]
      (is (instance? Properties props))
      (is (= "new-version" (.getProperty props "service.version")))
      (is (nil? (.getProperty props "app.version")))
      (is (nil? (.getProperty props "service.name")))
      (is (= "identity-not-mentioned" (.getProperty props "app.identity")))))
  (testing "Hierarchical"
    (println "----------")
    (let [parent-key "parent-config"
          props (read-properties ["test-config/myconf.properties"] {:parent-key parent-key})]
      (is (instance? Properties props))
      (is (not (.containsKey props parent-key)) "hierarchical config resolution should eliminate parent key")
      (is (= "new-version" (.getProperty props "service.version")))
      (is (= "2.3.6" (.getProperty props "app.version")))
      (is (= "fooapp-new-version" (.getProperty props "service.name")) "overidden property in template")
      (is (= "unicorn" (.getProperty props "some.var")))))
  (testing "Hierarchical with missing parent"
    (println "----------")
    (is (thrown? IllegalArgumentException
          (read-properties ["test-config/errconf.properties"] {:parent-key "parent"})))))


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
  baz ["baz" #(< 0 % 100) "Thread-pool size (1-99)" {:parser ku/str->int}]
  qux ["qux" ku/bool? "Flag: Whether enable runtime tracing?" {:parser ku/str->bool :default true}]
  esr ["qux" {:pred ku/bool?
              :desc "Flag: Whether enable runtime tracing?"
              :parser ku/str->bool
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
      (is (thrown? IllegalArgumentException (baz th/props)))
      (is (thrown? IllegalArgumentException (qux bad-props)))
      (is (thrown? IllegalArgumentException (esr bad-props))))
    (testing "Minimal definition"
      (is (= "hello" (foo  min-props)))
      (is (= "bar"   (foo  th/props))))
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
  kbaz ["baz" #(< 0 % 100) "Thread-pool size (1-99)" {:parser ku/str->int}]
  kqux ["qux" ku/bool? "Flag: Whether enable runtime tracing?" {:parser ku/str->bool :default true}]
  kesr ["qux" {:pred ku/bool? :desc "Flag: Whether enable runtime tracing?"
               :parser ku/str->bool :default true}])


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
  vbaz [2 #(< 0 % 100) "Thread-pool size (1-99)" {:parser ku/str->int}]
  vqux [3 ku/bool? "Flag: Whether enable runtime tracing?" {:parser ku/str->bool :default true}]
  vesr [3 {:pred ku/bool? :desc "Flag: Whether enable runtime tracing?"
           :parser ku/str->bool :default true}])


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
  (is (= "bar" (ku/identity-parser "foo" "bar")))
  (is (= [:bar] ((ku/comp-parser (fn [_ v] [v]) (fn [_ v] (keyword v))) "foo" "bar")))
  (is (true? (ku/str->bool "foo" "true")))
  (is (thrown? IllegalArgumentException
        (ku/str->bool "foo" "notboolean")))
  (is (integer? (ku/str->int "foo" "10")))
  (is (integer? (ku/str->long "foo" "10")))
  (is (float? (ku/str->float "foo" "10.23")))
  (is (float? (ku/str->double "foo" "10.23")))
  (is (var? (ku/str->var "foo" "keypin.test-sample/hello")))
  (is ((ku/deref? string?) (ku/str->var "foo" "keypin.test-sample/hello")))
  (is (var? (ku/str->var "foo" "keypin.test-sample/hola")))
  (is ((ku/deref? fn?) (ku/str->var "foo" "keypin.test-sample/hola")))
  (is (thrown? IllegalArgumentException
        (ku/str->var "foo" "keypin.test-samplex")) "Bad var format")
  (is (thrown? IllegalArgumentException
        (ku/str->var "foo" "keypin.test-samples/hellow")) "Bad ns")
  (is (thrown? IllegalArgumentException
        (ku/str->var "foo" "keypin.test-sample/hellow")) "Bad var")
  (is (string? (ku/str->var->deref "foo" "keypin.test-sample/hello")))
  (is (fn?     (ku/str->var->deref "foo" "keypin.test-sample/hola")))
  ;; duration parsers
  (is (= TimeUnit/MILLISECONDS (ku/str->time-unit "foo" "millis")))
  (is (ku/duration? (ku/str->duration "foo" "34ms")))
  (is (= [34 TimeUnit/MILLISECONDS] (ku/str->duration "foo" "34 millis")) "Optional whitespace between duration tokens")
  (is (= [34 TimeUnit/MILLISECONDS] (ku/str->duration "foo" "34ms")))
  ;; collection parsers
  (is (= ["foo" "bar" "baz"]
        (ku/str->vec "foo" "foo, bar, baz")))
  (is (= {"foo" "10" "bar" "20" "baz" "30"}
        (ku/str->map "foo" "foo: 10, bar: 20, baz: 30")))
  (is (= [["foo" "10"]
          ["bar" "20" "22"]
          ["baz" "30" "32" "34" "36"]]
        (ku/str->nested "foo" "foo: 10, bar: 20: 22, baz: 30: 32: 34: 36")))
  (is (= [{:a "foo" :b "10"}
          {:a "bar" :b "20" :c "22"}
          {:a "baz" :b "30" :c "32" :d "34"}]
        (ku/str->tuples [:a :b :c :d] "foo" "foo: 10, bar: 20: 22, baz: 30: 32: 34: 36")))
  (is (= [:foo 100]
        (ku/str->edn "foo" "[:foo 100]")))
  (is (thrown? IllegalArgumentException
        (ku/str->edn map? "a map" "foo" "[:foo 100]")))
  (is (= {:foo 100}
        (ku/str->edn map? "a map" "foo" "{:foo 100}"))))


(deftest test-optional-parsers
  (is (true? (ku/any->bool "foo" true)))
  (is (true? (ku/any->bool "foo" "true")))
  (is (thrown? IllegalArgumentException
        (ku/str->bool "foo" "notboolean")))
  (is (integer? (ku/any->int "foo" 10)))
  (is (integer? (ku/any->int "foo" "10")))
  (is (integer? (ku/any->long "foo" 10)))
  (is (integer? (ku/any->long "foo" "10")))
  (is (float?   (ku/any->float "foo" 10.23)))
  (is (float?   (ku/any->float "foo" "10.23")))
  (is (float?   (ku/any->double "foo" 10.23)))
  (is (float?   (ku/any->double "foo" "10.23")))
  (is (var?     (ku/any->var "foo" #'props)))
  (is (var?     (ku/any->var "foo" "keypin.test-sample/hello"))) ; this also loads the namespace
  (is ((ku/deref? fn?)     (ku/any->var "foo" #'props)))
  (is ((ku/deref? string?) (ku/any->var "foo" "keypin.test-sample/hello"))) ; expects the namespace to be aliased
  (is ((ku/deref? fn?)     (ku/any->var "foo" "keypin.test-sample/hola")))
  (is (thrown? IllegalArgumentException
        (ku/any->var "foo" "keypin.test-samplex")) "Bad var format")
  (is (thrown? IllegalArgumentException
        (ku/any->var "foo" "keypin.test-samples/hellow")) "Bad ns")
  (is (thrown? IllegalArgumentException
        (ku/any->var "foo" "keypin.test-sample/hellow")) "Bad var")
  (is (fn?     (ku/any->var->deref "foo" #'props)))
  (is (string? (ku/any->var->deref "foo" "keypin.test-sample/hello")))
  (is (fn?     (ku/any->var->deref "foo" "keypin.test-sample/hola")))
  ;; duration parsers
  (is (= TimeUnit/MILLISECONDS (ku/any->time-unit "foo" "millis")))
  (is (= TimeUnit/MILLISECONDS (ku/any->time-unit "foo" :millis)))
  (is (ku/duration? (ku/any->duration "foo" "34ms")))
  (is (= [34 TimeUnit/MILLISECONDS] (ku/any->duration "foo" "34ms")))
  (is (ku/duration? (ku/any->duration "foo" [34 :ms])))
  (is (= [34 TimeUnit/MILLISECONDS] (ku/any->duration "foo" [34 :ms])))
  ;; collection parsers
  (is (= ["foo" "bar" "baz"]
        (ku/any->vec "foo" ["foo" "bar" "baz"])))
  (is (= ["foo" "bar" "baz"]
        (ku/any->vec "foo" "foo, bar, baz")))
  (is (= {"foo" "10" "bar" "20" "baz" "30"}
        (ku/any->map "foo" {"foo" "10" "bar" "20" "baz" "30"})))
  (is (= {"foo" "10" "bar" "20" "baz" "30"}
        (ku/any->map "foo" "foo: 10, bar: 20, baz: 30")))
  (is (= [["foo" "10"]
          ["bar" "20" "22"]
          ["baz" "30" "32" "34" "36"]]
        (ku/any->nested "foo" [["foo" "10"]
                               ["bar" "20" "22"]
                               ["baz" "30" "32" "34" "36"]])))
  (is (= [["foo" "10"]
          ["bar" "20" "22"]
          ["baz" "30" "32" "34" "36"]]
        (ku/any->nested "foo" "foo: 10, bar: 20: 22, baz: 30: 32: 34: 36")))
  (is (= [{:a "foo" :b "10"}
          {:a "bar" :b "20" :c "22"}
          {:a "baz" :b "30" :c "32" :d "34"}]
        (ku/any->tuples [:a :b :c :d] "foo" [{:a "foo" :b "10"}
                                             {:a "bar" :b "20" :c "22"}
                                             {:a "baz" :b "30" :c "32" :d "34"}])))
  (is (= [{:a "foo" :b "10"}
          {:a "bar" :b "20" :c "22"}
          {:a "baz" :b "30" :c "32" :d "34"}]
        (ku/any->tuples [:a :b :c :d] "foo" "foo: 10, bar: 20: 22, baz: 30: 32: 34: 36")))
  (is (= [:foo 100]
        (ku/any->edn "foo" "[:foo 100]")))
  (is (= [:foo 100]
        (ku/any->edn "foo" [:foo 100])))
  (is (thrown? IllegalArgumentException
        (ku/any->edn map? "a map" "foo" [:foo 100])))
  (is (= {:foo 100}
        (ku/any->edn map? "a map" "foo" "{:foo 100}"))))


(defkey
  bat [:bat]
  cow [:cow]
  dog [:dog {:default 100}]
  rat [:rat])


(deftest test-letval
  (testing "Happy lookup"
    (let [ran? (atom false)
          data {:cow 20 :dog "canine"}]
      (letval [{x cow ^String y dog :as m} data]
        (reset! ran? true)
        (is (= 20 x))
        (is (= "canine" y))
        (is (= \c (.charAt y 0)))  ; .charAt should NOT cause reflection warning
        (is (= data m)))
      (is @ran?)))
  (testing "Happy :defs lookup"
    (let [ran? (atom false)
          data {:cow 20 :dog 30}]
      (letval [{:defs [cow dog] :as m} data]
        (reset! ran? true)
        (is (= 20 cow))
        (is (= 30 dog))
        (is (= data m)))
      (is @ran?)))
  (testing "Happy :defs lookup with default"
    (let [ran? (atom false)
          data {:cow 20}]
      (letval [{:defs [cow dog] :as m} data]
        (reset! ran? true)
        (is (= 100 dog))
        (is (= data m)))
      (is @ran?)))
  (testing "Happy :defs lookup with qualified symbol"
    (let [ran? (atom false)
          data {:namaste "hello"}]
      (letval [{:defs [^String th/namaste] :as m} data]
        (reset! ran? true)
        (is (= "hello" namaste))
        (is (= \h (.charAt namaste 0))))  ; .charAt should NOT cause reflection warning
      (is @ran?)))
  (testing "Happy nested map lookup"
    (let [ran? (atom false)
          data {:bat 10 :cow {:dog 20 :rat 30}}]
      (letval [{:defs [bat] {:defs [dog rat]} cow :as m} data]
        (reset! ran? true)
        (is (= 10 bat))
        (is (= 20 dog))
        (is (= 30 rat))
        (is (= data m)))
      (is @ran?)))
  (testing "Missing key"
    (let [ran? (atom false)
          data {:cow 20 :dog 30}]
      (is (thrown? IllegalArgumentException
            (letval [{z rat :as m} data]
              (reset! ran? true))))
      (is (not @ran?)))))
