;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns keypin.util
  (:require
    [clojure.string :as string]
    [keypin.internal :as i])
  (:import
    [java.io FileNotFoundException]))


;; ===== validators =====


(defn any?
  "Dummy validator. Always return true."
  [_]
  true)


(defn bool?
  "Return true if argument is of boolean type, false otherwise."
  [x]
  (instance? Boolean x))


(defn deref?
  "Wrap a predicate such that it derefs the argument before applying the predicate."
  [pred]
  (fn [x]
    (pred (deref x))))


;; ===== value parsers =====


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
  "Given a fully qualified var name (eg. 'com.example.foo/bar'), resolve the var and return it."
  [the-key fq-var-name]
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
    the-var))


(defn str->var->deref
  "Given a fully qualified var name (eg. 'com.example.foo/bar'), resolve the var, deref it and return the value."
  [the-key fq-var-name]
  (deref (str->var the-key fq-var-name)))


(defn regex->tokenizer
  "Given a regex, return a fn that tokenizes a text."
  ([token-processor regex]
    (fn [text]
      (let [tokens (string/split text regex)]
        (mapv token-processor tokens))))
  ([regex]
    (regex->tokenizer string/trim regex)))


(def comma-tokenizer (regex->tokenizer #","))
(def colon-tokenizer (regex->tokenizer #":"))


(defn str->coll
  "Given a delimited text tokenize it as a collection and process it to return the result."
  [entity-tokenizer entity-processor the-key text]
  (entity-processor (entity-tokenizer text)))


(defn str->vec
  "Given a delimited text, tokenize it and return a vector of tokens. By default, the delimiter is a comma."
  ([tokenizer the-key text]
    (str->coll tokenizer identity the-key text))
  ([the-key text]
    (str->vec comma-tokenizer the-key text)))


(defn str->map
  "Given a delimted text, where each token is a delimited pair text, tokenize it and return a map of tokens. By default,
  the pair delimiter is a comma and the key-value delimiter is a colon."
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
  vectors of tokens. By default, the outer delimiter is a comma and the inner delimiter is a colon."
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
  default, the outer delimiter is a comma and the inner delimiter is a colon."
  ([outer-tokenizer inner-tokenizer ks the-key text]
    (->> (str->nested outer-tokenizer inner-tokenizer the-key text)
      (mapv #(zipmap ks %))))
  ([ks the-key text]
    (str->tuples comma-tokenizer colon-tokenizer ks the-key text)))
