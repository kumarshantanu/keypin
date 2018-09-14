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


(defrecord KeyAttributes
  [the-key validator description value-parser default-value? default-value lookup-fn container]
  clojure.lang.IFn
  (applyTo [this arglist]
    (case (count arglist)
      0 (.invoke this)
      1 (.invoke this (first arglist))
      2 (.invoke this (first arglist) (second arglist))
      (i/expect-arg arglist #(<= 1 % 2) "Allowed arities: [] [the-map] [the-map default-value]")))
  (invoke [this]  ; behave as arity-0 fn
    (if (nil? container)
      (throw (IllegalStateException. "No key/value-source was specified when creating this key"))
      (condp instance? container
        IPending (do
                   (i/expected-state realized? "key/value-source to be realized" container)
                   (lookup-fn @container the-key validator description value-parser default-value? default-value))
        IDeref   (lookup-fn @container the-key validator description value-parser default-value? default-value)
        (i/expected-state "clojure.lang.IDeref key/value-source to dereference" container))))
  (invoke [this the-map]  ; behave as arity-1 fn
    (lookup-fn the-map the-key validator description value-parser default-value? default-value))
  (invoke [this the-map not-found]  ; behave as arity-2 fn (2nd arg is returned when key not found)
    (lookup-fn the-map the-key validator description value-parser true not-found))
  java.util.Map$Entry  ; key name is retrievable using clojure.core/key: (key foo)
  (getKey [this] the-key)
  ; not implementing methods (getValue [this] ..) and (setValue [this v] ..)
  Object
  (toString [this] (str the-key)))


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
