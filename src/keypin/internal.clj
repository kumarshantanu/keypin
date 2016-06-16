;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


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


(defn expected
  "Throw illegal input exception citing `expectation` and what was `found` did not match. Optionally accept a predicate
  fn to test `found` before throwing the exception."
  ([expectation found]
    (throw (IllegalArgumentException.
             (format "Expected %s, but found (%s) %s" expectation (class found) (pr-str found)))))
  ([pred expectation found]
    (when-not (pred found)
      (expected expectation found))))


(defn identity-parser
  [_ value]
  value)


(defn inject-prepare
  [inject-meta-key name-sym arglist]
  (let [var-args?   (some #(= % '&) arglist)
        var-argsym  (when var-args? (gensym "more-"))
        arg-info    (fn [idx arg]
                      (let [inject-key (when-let [inject-name (get (meta arg) inject-meta-key)]
                                         (if (true? inject-name)
                                           (if (symbol? arg)
                                             (keyword arg)
                                             (throw (ex-info (str "Cannot infer inject key for argument " (pr-str arg))
                                                      {:argument arg})))
                                           inject-name))]
                        {:arg   arg
                         :index idx
                         :sym   (gensym (if inject-key "inject-" "arg-"))
                         ;; non nil inject-key implies injectable dependency
                         :inject-key inject-key}))
        fixed-args  (->> arglist
                      (take-while #(not= % '&))   ; fixed args only
                      (map-indexed arg-info)
                      vec)
        inject-args (->> fixed-args
                      (filter :inject-key))
        expose-syms (as-> fixed-args $
                      (remove :inject-key $)
                      (mapv :sym $)
                      (concat $ (when var-args? ['& var-argsym]))
                      (vec $))
        invoke-syms (as-> fixed-args $
                      (map :sym $)
                      (concat $ (when var-args? [var-argsym])))]
    [inject-args (if var-args?
                   `(~expose-syms (apply ~name-sym ~@invoke-syms))
                   `(~expose-syms (~name-sym ~@invoke-syms)))]))


(defn get-val
  "Given map m, find and return the value of key k. Throw IllegalArgumentException when specified key is not found."
  [m k]
  (if (contains? m k)
    (get m k)
    (throw (IllegalArgumentException.
             (format "Key %s not found in keys %s" k (try (sort (keys m))
                                                       (catch ClassCastException _ (keys m))))))))
