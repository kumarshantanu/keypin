;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns keypin.util
  "Common utility functions."
  (:refer-clojure :exclude [any?])
  (:require
    [clojure.edn     :as edn]
    [clojure.string  :as string]
    [keypin.internal :as i]
    [keypin.impl     :as impl]
    [keypin.type     :as t])
  (:import
    [java.io              FileNotFoundException]
    [java.util            Collection List Map Properties RandomAccess Set]
    [java.util.concurrent TimeUnit]
    [clojure.lang         IAtom Named]
    [keypin               Logger]))


;; ===== logger helpers =====


(defn make-logger
  "Make a logger instance from info-logger `(fn [info-msg])` and error-logger `(fn [error-msg])`."
  [info-logger error-logger]
  (reify Logger
    (info [this msg] (info-logger msg))
    (error [this msg] (error-logger msg))))


(def default-logger (make-logger
                      #(binding [*out* *err*] (println "[keypin] [info]" %))
                      #(binding [*out* *err*] (println "[keypin] [error]" %))))


;; ===== validators =====


(defn any?
  "Dummy validator. Always return `true`. Same as `clojure.core/any?` in Clojure 1.9+."
  [_]
  true)


(defn bool?
  "Return `true` if the argument is of boolean type, `false` otherwise.
  Same as `clojure.core/boolean?` in Clojure 1.9+."
  [x]
  (instance? Boolean x))


(defn fqvn?
  "Return `true` if the argument has the 'syntax' of a fully qualified var name, `false` otherwise.
  Similar (except string) to `clojure.core/qualified-symbol?` in Clojure 1.9+."
  [x]
  (and (or (symbol? x)
         (string? x))
    (as-> (str x) <>
      (string/split <> #"/")
      (map string/trim <>)
      (remove empty? <>)
      (count <>)
      (= 2 <>))))


(defn deref?
  "Wrap a predicate such that it derefs the argument before applying the predicate."
  [pred]
  (fn [x]
    (pred (deref x))))


(defn vec?
  "Wrap a predicate to verify the argument as a vector before applying the predicate to all elements in it."
  [pred]
  (fn [x]
    (and (vector? x)
      (every? pred x))))


(defn duration?
  "Return `true` if the argument is a duration, `false` otherwise."
  [x]
  (and (satisfies? t/IDuration x)
    (t/duration? x)))


(defn atom?
  "Return `true` if argument is a Clojure atom, false otherwise."
  [x]
  (instance? IAtom x))


(defn network-port?
  "Return `true` if argument is an integer in valid network port number range, `false` otherwise."
  [n]
  (and (integer? n) (<= 0 n 65535)))


;; ===== parsing helpers =====


(defn clojurize-data
  "Process a data structure recursively passing each element through specified fn. Turn Java oriented data structures
  into Clojure equivalent."
  ([data]
    (clojurize-data identity data))
  ([f data]
    (let [g (comp f (partial clojurize-data f))]
      (cond
        (nil? data)                      nil
        (= "us.bpsm.edn.Keyword"
          (.getName (class data)))       (keyword (subs (str data) 1))  ; support for EDN Java implementation
        (= "us.bpsm.edn.Symbol"
          (.getName (class data)))       (symbol (str data))            ; support for EDN Java implementation
        (map? data)                      (zipmap (map g (keys data)) (map g (vals data)))
        (instance? Map data)             (zipmap (map g (keys data)) (map g (vals data)))
        (set? data)                      (set (map g data))
        (instance? Set data)             (set (map g data))
        (vector? data)                   (vec (map g data))
        (and (instance? List data)
          (instance? RandomAccess data)) (vec (map g data))
        (coll? data)                     (list* (map g data))
        (instance? Collection data)      (list* (map g data))
        :otherwise                       (f data)))))


(defn clojurize-subst
  "Variable substitution for EDN data. Symbols and keywords starting with `$` (e.g. `$foo.bar` or `:$foo.bar`) are
  looked up and substituted by their respective values as follows:

  |Variable    |Description     | Lookup as          |
  |------------|----------------|--------------------|
  |`$foo.bar`  |Symbol variable |`\"foo.bar\"`       |
  |`$:foo.bar` |Keyword variable|`:foo.bar`          |
  |`:$foo.bar` |Keyword variable|`:foo.bar`          |
  |`$$foo.bar` |Escaped variable|-- (not substituted)|
  |`:$$foo.bar`|Escaped variable|-- (not substituted)|

  Note:
  1. Missing variables cause `IllegalArgumentException` to be thrown.
  2. You can escape the variable marker `$` using `$$` (interpreted as `$`) to avoid substitution."
  ([data]
    (clojurize-subst data data))
  ([lookup data]
    (let [nname (fn [^Named named] (if-let [tns (namespace named)]
                                     (str tns \/ (name named))
                                     (name named)))
          swapv (fn [needle named]
                  (if (contains? lookup needle)
                    (->> needle
                      (get lookup)
                      (clojurize-subst lookup))
                    (throw (IllegalArgumentException. (format "Variable '%s' has no defined value" named)))))
          subst (fn [named] (let [kname (nname named)]
                              (cond
                                ;; '$' as first character of name implies a variable, '$$' is escaped '$'
                                (= "$" kname)     (i/expected "valid variable name starting with '$'" named)
                                (= '$$ named)     '$  ; escape request for '$
                                (= :$$ named)     :$  ; escape request for :$
                                (= [\$ \$]            ; escape request for entire named entity
                                  (take 2 kname)) (condp apply [named]
                                                    symbol?  (symbol (subs kname 1))
                                                    keyword? (keyword (subs kname 1))
                                                    named)
                                (= \$
                                  (first kname))  (let [kname (subs kname 1)]
                                                    (condp apply [named]
                                                      symbol?  (swapv (if (= \: (first kname))   ; symbol of form $:foo
                                                                        (keyword (subs kname 1)) ; treat as keyword
                                                                        kname)
                                                                 named)
                                                      keyword? (swapv (keyword kname) named)
                                                      named))
                                :otherwise        named)))]
      (cond
        (map?     data) (reduce-kv (fn [m k v] (assoc m
                                                 (clojurize-subst lookup k)
                                                 (clojurize-subst lookup v))) {} data)
        (vector?  data) (mapv #(clojurize-subst lookup %) data)
        (set?     data) (set   (map #(clojurize-subst lookup %) data))
        (coll?    data) (list* (map #(clojurize-subst lookup %) data))
        (symbol?  data) (subst data)
        (keyword? data) (subst data)
        :otherwise      data))))


;; ===== value parsers =====


(defn identity-parser
  "Return the value to be parsed without doing any actual parsing."
  [the-key parseable-val]
  parseable-val)


(defn comp-parser
  "Compose multiple parsers (where parser is `(fn [key parseable-value]) -> parsed-value`) into one. Composition is
  applied right-to-left, as in `clojure.core/comp`."
  [& parsers]
  (if (seq parsers)
    (fn [the-key parseable-val]
      (as-> parsers $
        (map partial $ (repeat the-key))
        (apply comp $)
        ($ parseable-val)))
    identity-parser))


;; ----- string value parsers -----


(defn str->bool
  "Given a boolean value in string form, parse and return the boolean value."
  [the-key ^String x]
  (if (#{"true" "false"} (string/lower-case x))
    (Boolean/valueOf x)
    (i/illegal-arg [(format "Expected 'true' or 'false' for key %s but found" (pr-str the-key))
                    (pr-str x)])))


(defn str->int
  "Given an integer value in string form, parse and return the integer value."
  [the-key ^String x]
  (try
    (Integer/parseInt x)
    (catch NumberFormatException _
      (i/illegal-arg [(format "Expected integer (number) string for key %s but found" (pr-str the-key))
                            (pr-str x)]))))


(defn str->long
  "Given a long int value in string form, parse and return the long int value."
  [the-key ^String x]
  (try
    (Long/parseLong x)
    (catch NumberFormatException _
      (i/illegal-arg [(format "Expected long (number) string for key %s but found" (pr-str the-key))
                            (pr-str x)]))))


(defn str->float
  "Given a floating point value in string form, parse and return the floating point value."
  [the-key ^String x]
  (try
    (Float/parseFloat x)
    (catch NumberFormatException _
      (i/illegal-arg [(format "Expected float (number) string for key %s but found" (pr-str the-key))
                            (pr-str x)]))))


(defn str->double
  "Given a double precision value in string form, parse and return the double precision value."
  [the-key ^String x]
  (try
    (Double/parseDouble x)
    (catch NumberFormatException _
      (i/illegal-arg [(format "Expected double (number) string for key %s but found" (pr-str the-key))
                            (pr-str x)]))))


(defn str->var
  "Given a fully qualified var name (eg. `com.example.foo/bar`), resolve the var and return it."
  [the-key fq-var-name]
  (if (var? fq-var-name)
    fq-var-name
    (let [[ns-name var-name] (-> (string/split (str fq-var-name) #"/")
                               (i/expect-arg #(= 2 (count %))  ["Var name" fq-var-name "must be in 'ns/var' format"])
                               (i/expect-arg #(first (seq %))  ["Namespace is empty in" fq-var-name])
                               (i/expect-arg #(second (seq %)) ["Value is empty in" fq-var-name]))
          ;; the following step is required for `find-ns` to work
          _       (try
                    (require (symbol ns-name))
                    (catch FileNotFoundException _
                      (i/illegal-arg (format "Cannot find ns '%s' for key %s=%s"
                                       ns-name (pr-str the-key) (str fq-var-name)))))
          the-ns  (-> (find-ns (symbol ns-name))
                    (i/expect-arg identity (format "Cannot find ns '%s' for key %s=%s"
                                             ns-name (pr-str the-key) (str fq-var-name))))
          the-var (-> (ns-resolve the-ns (symbol var-name))
                    (i/expect-arg identity (format "Cannot find var '%s/%s' for key %s=%s"
                                             ns-name var-name (pr-str the-key) (str fq-var-name))))]
      ;; return the var without deref'ing
      the-var)))


(defn str->var->deref
  "Given a fully qualified var name (eg. `com.example.foo/bar`), resolve the var, deref it and return the value."
  [the-key fq-var-name]
  (deref (str->var the-key fq-var-name)))


(defn str->fn
  "Given a fully qualified var name (eg. `com.example.foo/add-item`), resolve the var, deref it and return the value
  assuming it is a function."
  [the-key fq-var-name]
  (cond
    (fn? fq-var-name)  fq-var-name
    (var? fq-var-name) (deref fq-var-name)
    :otherwise         (str->var->deref the-key fq-var-name)))


(defn str->time-unit
  "Given a time unit string, resolve it as `java.util.concurrent.TimeUnit` instance."
  ^TimeUnit [the-key unit-str]
  (or (impl/resolve-time-unit unit-str)
    (i/expected
      (format
        (str "time unit to be either of "
          (vec (keys impl/time-units))
          " for key %s")
        (pr-str the-key))
      unit-str)))


(defn str->duration
  "Given a duration string, parse it as a vector `[long java.util.concurrent.TimeUnit]` and return it."
  [the-key duration-str]
  (if-let [[_ strnum unit] (re-matches #"([0-9]+)\s*([a-zA-Z]+)" duration-str)]
    (try
      [(Long/parseLong strnum) (str->time-unit the-key unit)]
      (catch NumberFormatException e
        (i/expected (format "duration to be a long int (followed by time unit) for key %s" (pr-str the-key)) strnum)))
    (i/expected "duration expressed as <NNNss> (e.g. 83ns, 103us, 239ms, 4s, 2m, 1h, 6d, 13w etc.)" duration-str)))


(defn regex->tokenizer
  "Given a regex, return a `(fn [text]) -> tokens` that tokenizes a text. Each token can be processed using an optional
  `(fn [token])`, which by default trims the tokens."
  ([token-processor regex]
    (fn [text]
      (let [tokens (string/split text regex)]
        (mapv token-processor tokens))))
  ([regex]
    (regex->tokenizer string/trim regex)))


(def comma-tokenizer (regex->tokenizer #","))
(def colon-tokenizer (regex->tokenizer #":"))


(defn str->coll
  "Given a delimited text tokenize it (using an arity-1 fn) as a collection and process it (using an arity-1 fn) to
  return the result."
  [entity-tokenizer entity-processor the-key text]
  (entity-processor (entity-tokenizer text)))


(defn str->vec
  "Given a delimited text, tokenize it and return a vector of tokens. By default, the delimiter is a comma.
  Example:
  ```
  => (str->vec :foo \"a, b, c\")
  [\"a\" \"b\" \"c\"]
  ```"
  ([tokenizer the-key text]
    (str->coll tokenizer identity the-key text))
  ([the-key text]
    (str->vec comma-tokenizer the-key text)))


(defn str->map
  "Given a delimted text, where each token is a delimited pair text, tokenize it and return a map of tokens. By default,
  the pair delimiter is a comma and the key-value delimiter is a colon.
  Example:
  ```
  => (str->map :foo \"a: 10, b: 20, c: 30\")
  {\"a\" \"10\" \"b\" \"20\" \"c\" \"30\"}
  ```"
  ([pair-tokenizer kv-tokenizer the-key text]
    (str->coll
      pair-tokenizer
      (fn [pair-tokens]
        (->> pair-tokens
          (mapv kv-tokenizer)
          (reduce (fn [m pair]
                    (try
                      (conj m pair)
                      (catch Exception e
                        (throw (IllegalArgumentException.
                                 (format "Expected a 2-element vector as a key/value pair for key %s, but found %s"
                                   (pr-str the-key) (pr-str pair))
                                 e)))))
            {})))
      the-key
      text))
  ([the-key text]
    (str->map comma-tokenizer colon-tokenizer the-key text)))


(defn str->nested
  "Given a delimited text, where each token is again a delimited text, tokenize it and return a vector of nested
  vectors of tokens. By default, the outer delimiter is a comma and the inner delimiter is a colon.
  Example:
  ```
  => (str->nested :foo \"joe: 30: male, sue: 35: female, max: 40: male\")
  [[\"joe\" \"30\" \"male\"]
   [\"sue\" \"35\" \"female\"]
   [\"max\" \"40\" \"male\"]]
  ```"
  ([outer-tokenizer inner-tokenizer the-key text]
    (str->coll
      outer-tokenizer
      #(mapv inner-tokenizer %)
      the-key
      text))
  ([the-key text]
    (str->nested comma-tokenizer colon-tokenizer the-key text)))


(defn str->tuples
  "Given a delimited text, where each token is again a delimited text, tokenize it and return a vector of maps. By
  default, the outer delimiter is a comma and the inner delimiter is a colon.
  Example:
  ```
  => (str->tuples [:name :age :gender] :foo \"joe: 30: male, sue: 35: female, max: 40: male\")
  [{:name \"joe\" :age \"30\" :gender \"male\"}
   {:name \"sue\" :age \"35\" :gender \"female\"}
   {:name \"max\" :age \"40\" :gender \"male\"}]
  ```"
  ([outer-tokenizer inner-tokenizer ks the-key text]
    (->> (str->nested outer-tokenizer inner-tokenizer the-key text)
      (mapv #(zipmap ks %))))
  ([ks the-key text]
    (str->tuples comma-tokenizer colon-tokenizer ks the-key text)))


(defn str->edn
  "Given a string representation of EDN, parse it as EDN and return it."
  ([the-key text]
    (try
      (edn/read-string text)
      (catch Exception e
        (throw (IllegalArgumentException.
                 (format "Expected a valid EDN string for key %s but found %s" (pr-str the-key) (pr-str text))
                 e)))))
  ([pred expectation the-key text]
    (let [v (str->edn the-key text)]
      (when-not (pred v)
        (i/expected pred (str expectation " for key " (pr-str the-key)) v))
      v)))


;; ----- optional (only when parsing needed) parsers -----


(defn str->any
  "Given a predicate fn and a string parser fn, return a parser fn that parses the value only when the predicate fn
  returns `false` and the value is a string."
  [pred str-parser expected-msg]
  (fn [the-key x]
    (cond
      (pred x) x
      (string? x) (str-parser the-key ^String x)
      :otherwise  (i/illegal-arg [(format "Expected %s for key %s but found %s"
                                    expected-msg (pr-str the-key) (pr-str x))]))))


(defn symstr->any
  "Given a predicate fn and a string parser fn, return a parser fn that parses the value only when the predicate fn
  returns `false` and the value is a string or symbol."
  [pred str-parser expected-msg]
  (fn [the-key x]
    (cond
      (pred x) x
      (string? x) (str-parser the-key ^String x)
      (symbol? x) (str-parser the-key ^String (str x))
      :otherwise  (i/illegal-arg [(format "Expected %s for key %s but found %s"
                                    expected-msg (pr-str the-key) (pr-str x))]))))


(def any->bool
  "Like [[str->bool]], except parsing is avoided if value is already a boolean."
  (str->any bool?    str->bool   "a boolean or a parseable string (as boolean)"))


(def any->int
  "Like [[str->int]], except parsing is avoided if value is already an integer."
  (str->any integer? str->int    "an integer or a parseable string (as integer)"))


(def any->long
  "Like [[str->long]], except parsing is avoided if value is already a long integer."
  (str->any integer? str->long   "a long int or a parseable string (as long int)"))


(def any->float
  "Like [[str->float]], except parsing is avoided if value is already a floating point number."
  (str->any float?   str->float  "a float or a parseable string (as float)"))


(def any->double
  "Like [[str->double]], except parsing is avoided if value is already a double precision number."
  (str->any float?   str->double "a double precision or a parseable string (as double precision)"))


(def any->var
  "Like [[str->var]], except parsing is avoided if value is already a var."
  (symstr->any var?  str->var    "a var or a fully qualified var name in format foo.bar/baz"))


(def any->var->deref
  "Like [[str->var->deref]], except parsing is avoided if value is already a var (which is deref'ed before returning)."
  (comp deref (symstr->any var? str->var "a var or a fully qualified var name in format foo.bar/baz")))


(defn any->time-unit
  "Like [[str->time-unit]], except it accepts `java.util.concurrent.TimeUnit`/string/keyword as time-unit."
  [the-key time-unit]
  (cond
    (instance? TimeUnit
      time-unit)         time-unit
    (string? time-unit)  (str->time-unit the-key time-unit)
    (keyword? time-unit) (str->time-unit the-key (name time-unit))
    :otherwise           (i/expected
                           (format "time unit as string, keyword, or java.util.concurrent.TimeUnit instance for key %s"
                             (pr-str the-key))
                           time-unit)))


(defn any->duration
  "Like [[str->duration]], except it accepts `[long-int java.util.concurrent.TimeUnit/string/keyword]` too."
  [the-key duration]
  (if (string? duration)
    (str->duration the-key duration)
    (if (and (vector? duration)
          (= (count duration) 2)
          (integer? (first duration)))
      [(first duration) (any->time-unit the-key (second duration))]
      (i/expected
        (format "duration as a vector [long-int java.util.concurrent.TimeUnit/string/keyword] for key %s"
          (pr-str the-key))
        duration))))


(def any->vec
  "Like [[str->vec]], except parsing is avoided if value is already a vector."
  (str->any vector?  str->vec    "a vector or a comma delimited string"))


(def any->map
  "Like [[str->map]], except parsing is avoided if value is already a map."
  (str->any map?     str->map "a map or a comma delimited string (each token colon-delimited pair)"))


(def any->nested
  "Like [[str->nested]], except parsing is avoided if value is already a vector of nested vectors."
  (str->any #(and (vector? %) (every? vector? %))
    str->nested "a vector of vectors, or comma delimited string (each token colon-delimited text)"))


(defn any->tuples
  "Like [[str->tuples]], except parsing is avoided if value is already tuples."
  [ks the-key value]
  (cond
    (and (vector? value)
      (every? map? value)) value
    (string? value)        (str->tuples ks the-key value)
    :otherwise         (i/illegal-arg [(format "Expected a valid or parseable-string value for key %s but found %s"
                                         (pr-str the-key) (pr-str value))])))


(defn any->edn
  "Like [[str->edn]], except parsing is avoided if value is already non-string."
  ([the-key value]
    (if (string? value)
      (str->edn the-key value)
      (clojurize-data value)))
  ([pred expectation the-key value]
    (let [v (any->edn the-key value)]
      (when-not (pred v)
        (i/expected pred (str expectation " for key " (pr-str the-key)) v))
      v)))
