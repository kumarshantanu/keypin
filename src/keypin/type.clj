;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns keypin.type
  (:require
    [keypin.internal :as i])
  (:import
    [java.util.concurrent TimeUnit]
    [clojure.lang IDeref IPending]))


(defprotocol IStore
  (lookup [this keydef] "Lookup in the store using given keypin.type/KeyAttributes instance."))


(defrecord KeyAttributes
  [the-key validator description value-parser default-value? default-value lookup-fn container]
  clojure.lang.IDeref
  (deref [this] (.invoke this))
  clojure.lang.IFn
  (applyTo [this arglist]
    (case (count arglist)
      0 (.invoke this)
      1 (.invoke this (first arglist))
      2 (.invoke this (first arglist) (second arglist))
      (i/bad-key-arity (count arglist) the-key)))
  (invoke [this]  ; behave as arity-0 fn
    (if (nil? container)
      (throw (IllegalStateException. "No key/value source was specified when creating this key"))
      (condp instance? container
        IPending (do
                   (i/expected-state realized? "key/value source to be realized" container)
                   (lookup-fn @container the-key validator description value-parser default-value? default-value))
        IDeref   (lookup-fn @container the-key validator description value-parser default-value? default-value)
        (i/expected-state "clojure.lang.IDeref key/value source to dereference" container))))
  (invoke [this store]  ; behave as arity-1 fn
    (lookup store this))
  (invoke [this store not-found]  ; behave as arity-2 fn (2nd arg is returned when key not found)
    (lookup store (assoc this
                    :default-value? true
                    :default-value not-found)))
  (invoke [this _ _ _] (i/bad-key-arity 3 the-key))
  (invoke [this _ _ _ _] (i/bad-key-arity 4 the-key))
  (invoke [this _ _ _ _ _] (i/bad-key-arity 5 the-key))
  (invoke [this _ _ _ _ _ _] (i/bad-key-arity 6 the-key))
  (invoke [this _ _ _ _ _ _ _] (i/bad-key-arity 7 the-key))
  (invoke [this _ _ _ _ _ _ _ _] (i/bad-key-arity 8 the-key))
  (invoke [this _ _ _ _ _ _ _ _ _] (i/bad-key-arity 9 the-key))
  (invoke [this _ _ _ _ _ _ _ _ _ _] (i/bad-key-arity 10 the-key))
  (invoke [this _ _ _ _ _ _ _ _ _ _ _] (i/bad-key-arity 11 the-key))
  (invoke [this _ _ _ _ _ _ _ _ _ _ _ _] (i/bad-key-arity 12 the-key))
  (invoke [this _ _ _ _ _ _ _ _ _ _ _ _ _] (i/bad-key-arity 13 the-key))
  (invoke [this _ _ _ _ _ _ _ _ _ _ _ _ _ _] (i/bad-key-arity 14 the-key))
  (invoke [this _ _ _ _ _ _ _ _ _ _ _ _ _ _ _] (i/bad-key-arity 15 the-key))
  (invoke [this _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _] (i/bad-key-arity 16 the-key))
  (invoke [this _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _] (i/bad-key-arity 17 the-key))
  (invoke [this _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _] (i/bad-key-arity 18 the-key))
  (invoke [this _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _] (i/bad-key-arity 19 the-key))
  (invoke [this _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _] (i/bad-key-arity 20 the-key))
  (invoke [this _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ a] (i/bad-key-arity (unchecked-add 20 (alength a)) the-key))
  java.util.Map$Entry
  (getKey   [this] the-key)        ; key name is retrievable using clojure.core/key: (key foo)
  (getValue [this] (.invoke this)) ; value is retrievable using clojure.core/val: (val foo) or (foo)
  (setValue [this _] (throw (UnsupportedOperationException. "Setting value is not supported.")))
  Object
  (toString [this] (str the-key)))


(defn- call-lookup-fn
  [databag ^KeyAttributes keydef]
  (let [lookup-fn      (.-lookup-fn      keydef)
        the-key        (.-the-key        keydef)
        validator      (.-validator      keydef)
        description    (.-description    keydef)
        value-parser   (.-value-parser   keydef)
        default-value? (.-default-value? keydef)
        default-value  (.-default-value  keydef)]
    (lookup-fn databag the-key validator description value-parser default-value? default-value)))


(extend-protocol IStore
  java.util.Map
  (lookup [the-map ^KeyAttributes keydef] (call-lookup-fn the-map keydef))
  clojure.lang.IPersistentVector
  (lookup [databag ^KeyAttributes keydef] (call-lookup-fn databag keydef)))


(defprotocol IDuration
  (^boolean  duration? [this] "Return true if valid duration, false otherwise")
  (^long     dur-time  [this] "Return the duration time")
  (^TimeUnit dur-unit  [this] "Return the duration time unit")
  (^long     days      [this] "Convert duration to number of days")
  (^long     hours     [this] "Convert duration to number of hours")
  (^long     minutes   [this] "Convert duration to number of minutes")
  (^long     seconds   [this] "Convert duration to number of seconds")
  (^long     millis    [this] "Convert duration to number of milliseconds")
  (^long     micros    [this] "Convert duration to number of micros")
  (^long     nanos     [this] "Convert duration to number of nanoseconds"))


(defrecord Duration
  [^long time ^TimeUnit unit]
  IDuration
  (duration? [this] true)
  (dur-time  [this] time)
  (dur-unit  [this] unit)
  (days      [this] (.toDays    unit time))
  (hours     [this] (.toHours   unit time))
  (minutes   [this] (.toMinutes unit time))
  (seconds   [this] (.toSeconds unit time))
  (millis    [this] (.toMillis  unit time))
  (micros    [this] (.toMicros  unit time))
  (nanos     [this] (.toNanos   unit time)))


(defrecord Ref [path required?])


(defn ref?
  "Return `true` if argument is of `keypin.type/Ref` type, `false` otherwise."
  [x]
  (instance? Ref x))
