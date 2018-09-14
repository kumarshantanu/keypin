;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns keypin.internal
  (:require
    [clojure.string :as string])
  (:import
    [java.util Collection Map]
    [clojure.lang ArityException]))


(defn illegal-arg
  [messages]
  (throw (IllegalArgumentException. ^String (if (coll? messages)
                                              (string/join \space messages)
                                              (str messages)))))


(defn expect-arg
  "Validate given value using predicate, which if returns falsy then throws IllegalArgumentException."
  [value pred messages]
  (if (pred value)
    value
    (illegal-arg messages)))


(defn expected
  "Throw illegal input exception citing `expectation` and what was `found` did not match. Optionally accept a predicate
  fn to test `found` before throwing the exception."
  ([expectation found]
    (throw (IllegalArgumentException.
             (format "Expected %s, but found (%s) %s" expectation (class found) (pr-str found)))))
  ([pred expectation found]
    (when-not (pred found)
      (expected expectation found))))


(defn expected-state
  "Throw illegal state exception citing `expectation` and what was `found` did not match. Optionally accept a predicate
  fn to test `found` before throwing the exception."
  ([expectation found]
    (throw (IllegalStateException.
             (format "Expected %s, but found (%s) %s" expectation (class found) (pr-str found)))))
  ([pred expectation found]
    (when-not (pred found)
      (expected expectation found))))


(defn identity-parser
  [_ value]
  value)


(defn bad-key-arity
  "Throw ArityException on behalf of key definition when invoked as a function."
  [^long n k]
  (throw (ArityException. n (str "key definition " (pr-str k)
                              ", allowed: [] [the-map] [the-map default-value]"))))
