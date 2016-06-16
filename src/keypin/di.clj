;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns keypin.di
  (:refer-clojure :exclude [partial])
  (:require
    [keypin.internal :as i]))


(defmacro partial
  "Same as clojure.core/partial, except it does not lose associativity with the var."
  [f & args]
  `(fn [& args#]
     (apply ~f ~@args args#)))


(defn inject*
  "Given a var defined with defn and an argument map, look up the :inject metadata on arguments and return a partially
  applied function."
  ([v args {:keys [inject-meta-key]
            :or {inject-meta-key :inject}}]
    (when-not (and (var? v)
                (contains? (meta v) :arglists))
      (i/expected "a var created with clojure.core/defn" v))
    (when-not (map? args)
      (i/expected "a map" args))
    (let [{name-sym :name
           arglists :arglists} (meta v)
          prepared (map (partial i/inject-prepare inject-meta-key v) arglists)
          bindings (->> prepared
                     (mapcat first)
                     (mapcat (fn [{:keys [sym inject-key]}]
                               [sym `(i/get-val ~args ~inject-key)]))
                     vec)
          body-exp (map second prepared)]
      (eval `(let ~bindings
               (fn ~@body-exp)))))
  ([v args]
    (inject* v args {})))


(defmacro inject
  "Given a symbol representing a var defined using defn and an argument map, return a function after injecting the
  annotated arguments."
  [defn-sym args]
  (i/expected symbol? "a symbol" defn-sym)
  `(inject* (var ~defn-sym) ~args))
