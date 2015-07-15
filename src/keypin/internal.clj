(ns keypin.internal
  (:require
    [clojure.string :as string]))


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


(defn identity-parser
  [_ value]
  value)
