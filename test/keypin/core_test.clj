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
    [keypin.type :as kt]
    [keypin.util :as ku]
    [keypin.test-helper :as th])
  (:import
    [java.io File]
    [java.util Map Properties]
    [java.util.concurrent TimeUnit]
    [keypin Config Logger Mapper]
    [keypin.type KeyAttributes]))


(deftest test-predicate
  (testing "bool?"
    (is (ku/bool? true))
    (is (ku/bool? false))
    (is (not (ku/bool? nil)))
    (is (not (ku/bool? "foo"))))
  (testing "fqvn?"
    (is (ku/fqvn? "foo/bar"))
    (is (ku/fqvn? 'foo/bar))
    (is (not (ku/fqvn? "foo")))
    (is (not (ku/fqvn? "/foo"))))
  (testing "vec?"
    (is ((ku/vec? integer?) [10 20 30 40]))
    (is (not ((ku/vec? integer?) "foo")))
    (is (not ((ku/vec? integer?) '(10 20 30 40))))
    (is (not ((ku/vec? integer?) [10 20 :foo :bar 30 40]))))
  (testing "duration?"
    (is (ku/duration? (kt/->Duration 10 TimeUnit/MILLISECONDS)))
    (is (ku/duration? [10 TimeUnit/MILLISECONDS]))
    (is (ku/duration? [10 :millis]))
    (is (ku/duration? {:time 10 :unit TimeUnit/MILLISECONDS}))
    (is (ku/duration? {:time 10 :unit :millis}))
    (is (= 10 (kt/dur-time (kt/->Duration 10 TimeUnit/MILLISECONDS))))
    (is (= 10 (kt/dur-time [10 :millis])))
    (is (= 10 (kt/dur-time {:time 10 :unit :millis})))
    (is (= TimeUnit/MILLISECONDS (kt/dur-unit (kt/->Duration 10 TimeUnit/MILLISECONDS))))
    (is (= TimeUnit/MILLISECONDS (kt/dur-unit [10 :millis])))
    (is (= TimeUnit/MILLISECONDS (kt/dur-unit {:time 10 :unit :millis})))
    (is (= (kt/millis (kt/->Duration 10 TimeUnit/MILLISECONDS))
          (kt/millis [10 TimeUnit/MILLISECONDS])
          (kt/millis [10 :millis])
          (kt/millis {:time 10 :unit TimeUnit/MILLISECONDS})
          (kt/millis {:time 10 :unit :millis}))))
  (testing "atom?"
    (is (ku/atom? (atom nil)))
    (is (ku/atom? (atom :re)))))


(deftest test-config-file-reader
  (testing "Non-hierarchical"
    (doseq [config-fileset [["test-config/myconf.properties"]
                            ["test-config/myconf.edn"]]]
      (println "----------")
      (let [props (read-config config-fileset)]
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
        (is (= "identity-${app.id|default.app.id}" (get props "app.non.identity")) "escaped variable")
        (is (= "unicorn" (get props "some.var"))))))
  (testing "Injected variables"
    (let [props  {"bar" "baz"}
          logger (reify Logger
                   (info  [this msg] (println "[keypin-test] [info]" msg))
                   (error [this msg] (println "[keypin-test] [error]" msg)))]
      (is (= "baz" (get (-> props
                          (assoc "foo" "${bar}")
                          (Config/realize Mapper/DEFAULT logger))
                     "foo")) "injected variable substitution")
      (is (= "${bar}" (get (-> props
                             (assoc "foo" (Config/escape "${bar}"))
                             (Config/realize Mapper/DEFAULT logger))
                        "foo")) "injected escaped-variable")))
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
      (println "Writing config to" tname)
      (write-config tname props)
      (let [fresh-props (read-config [tname])]
        (is (= props fresh-props) "Generated properties should be the same as what we read afresh")))))


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


(defkey
  override ["override" string? "Override test" {:sysprop "override.sysprop"}])


(deftest test-defkey-override
  (let [^String sp "override.sysprop"
        props (doto (Properties.)
                (.setProperty "override" "val"))
        edncf {"override" "val"}]
    (testing "config values before override"
      (is (= "val" (override props)))
      (is (= "val" (override edncf))))
    (testing "config values upon override"
      (try
        (System/setProperty sp "newval")
        (is (= "newval" (override props)))
        (is (= "newval" (override edncf)))
        (finally
          (System/clearProperty sp))))))


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


(defn dummy-fn [])


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
  (is (var?     (ku/any->var "foo" #'dummy-fn)))
  (is (var?     (ku/any->var "foo" "keypin.test-sample/hello"))) ; this also loads the namespace
  (is ((ku/deref? fn?)     (ku/any->var "foo" #'dummy-fn)))
  (is ((ku/deref? string?) (ku/any->var "foo" "keypin.test-sample/hello"))) ; expects the namespace to be aliased
  (is ((ku/deref? fn?)     (ku/any->var "foo" "keypin.test-sample/hola")))
  (is (thrown? IllegalArgumentException
        (ku/any->var "foo" "keypin.test-samplex")) "Bad var format")
  (is (thrown? IllegalArgumentException
        (ku/any->var "foo" "keypin.test-samples/hellow")) "Bad ns")
  (is (thrown? IllegalArgumentException
        (ku/any->var "foo" "keypin.test-sample/hellow")) "Bad var")
  (is (fn?     (ku/any->var->deref "foo" #'dummy-fn)))
  (is (string? (ku/any->var->deref "foo" "keypin.test-sample/hello")))
  (is (fn?     (ku/any->var->deref "foo" "keypin.test-sample/hola")))
  ;; duration parsers
  (is (= TimeUnit/MILLISECONDS (ku/any->time-unit "foo" "millis")))
  (is (= TimeUnit/MILLISECONDS (ku/any->time-unit "foo" :millis)))
  (is (ku/duration? (ku/any->duration "foo" "34ms")))
  (is (= [34 TimeUnit/MILLISECONDS] (ku/any->duration "foo" "34ms")))
  (is (ku/duration? (ku/any->duration "foo" [34 :ms])))
  (is (= [34 TimeUnit/MILLISECONDS] (ku/any->duration "foo" [34 :ms])))
  (is (= [34 TimeUnit/MILLISECONDS] (ku/any->duration "foo" [34 :milliseconds])))
  (is (= [34 TimeUnit/MILLISECONDS] (ku/any->duration "foo" [34 :millis])))
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


(defkey
  {:lookup lookup-keypath}
  kp-foo-bar [[:foo :bar] integer? "Value at [:foo :bar]" {:parser ku/any->int}]
  kp-bar-baz [[:bar :baz] pos?     "Value at [:bar :baz]" {:parser ku/any->int}])


(deftest test-keypath
  (is (= 12345 (kp-foo-bar {:foo {:bar 12345}}))      "validator is applied")
  (is (= 12345 (kp-foo-bar {:foo {:bar "12345"}}))    "both parser and validator are applied")
  (is (thrown? IllegalArgumentException
        (kp-foo-bar {:bar {:baz -123}}))              "validator is applied")
  (is (= 12345 (kp-foo-bar {:foo {:bar 12345}} :zap)) "value exists")
  (is (thrown? IllegalArgumentException
        (kp-bar-baz {:bar {:baz -1234}} :zap))        "invalid value exists")
  (is (= 2345  (kp-foo-bar {} 2345))                  "value absent, but default specified ar argument"))


(def source (atom {}))


(defkey
  {:source source}
  src-foo-bar [:foo integer? "Value at :foo" {:parser ku/any->int}]
  src-bar-baz [:bar pos?     "Value at :bar" {:parser ku/any->int}])


(defkey
  nosrc-foo-bar [:foo integer? "Value at :foo" {:parser ku/any->int}]
  nosrc-bar-baz [:bar pos?     "Value at :bar" {:parser ku/any->int}])


(deftest test-source
  (testing "Source is specified"
    (reset! source {:foo -99
                    :bar 100})
    (is (= -99 (src-foo-bar) @src-foo-bar))
    (is (= 100 (src-bar-baz) @src-bar-baz))
    (is (= '([] [config-map] [config-map not-found]) (:arglists (meta #'src-foo-bar)))))
  (testing "Source not specified"
    (is (thrown? IllegalStateException (nosrc-foo-bar)))
    (is (thrown? IllegalStateException @nosrc-foo-bar))
    (is (thrown? IllegalStateException (nosrc-bar-baz)))
    (is (thrown? IllegalStateException @nosrc-bar-baz))
    (is (= '([config-map] [config-map not-found]) (:arglists (meta #'nosrc-foo-bar))))))


(defkey
 {:cmarg-meta {:inject :app-config}
  :nfarg-meta {:foo :bar}
  :dkvar-meta {:baz 1000}
  :pre-xform  (fn [options] (assoc options :default 100))
  :post-xform (fn [^KeyAttributes ka]
                (assoc ka :description "New description"))}
 ^{:qux 20} mw-foo [:foo integer? "Value at :foo"])


(deftest test-middleware
  (is (= 20 (mw-foo {:foo 20})) "normal case, i.e. no impact by pre/post processors")
  (is (= 100 (mw-foo {}))       "default is added by pre-processor")
  (is (= "New description"
        (:description mw-foo))  "description added by post-processor")
  (is (= 'config-map
        (ffirst (:arglists (meta #'mw-foo)))) "ensure config-map argument")
  (is (= {:inject :app-config}
        (meta (ffirst (:arglists (meta #'mw-foo))))) "config-map meta works")
  (is (= 'not-found
        (second (fnext (:arglists (meta #'mw-foo))))) "ensure not-found argument")
  (is (= {:foo :bar}
        (meta (second (fnext (:arglists (meta #'mw-foo)))))) "not-found meta works")
  (is (= 1000
        (:baz (meta #'mw-foo))) "specified defkey var meta works - added")
  (is (= 20
        (:qux (meta #'mw-foo))) "normal defkey var meta works - not displaced"))
