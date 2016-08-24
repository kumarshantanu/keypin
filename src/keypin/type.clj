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
    [java.util.concurrent TimeUnit]))


(defrecord KeyAttributes
  [the-key validator description value-parser default-value? default-value lookup-fn]
  clojure.lang.IFn
  (applyTo [this arglist]
    (case (count arglist)
      1 (.invoke this (first arglist))
      2 (.invoke this (first arglist) (second arglist))
      (i/expect-arg arglist #(<= 1 % 2) "Allowed arities: (the-map) (the-map default-value)")))
  (invoke [this the-map]  ; behave as arity-1 fn
    (lookup-fn the-map the-key validator description value-parser default-value? default-value))
  (invoke [this the-map not-found]  ; behave as arity-2 fn (2nd arg is returned when key not found)
    (lookup-fn the-map the-key identity description value-parser true not-found))
  java.util.Map$Entry  ; key name is retrievable using clojure.core/key: (key foo)
  (getKey [this] the-key)
  ; not implementing methods (getValue [this] ..) and (setValue [this v] ..)
  Object
  (toString [this] (str the-key)))


(defprotocol IDuration
  (days    [this] "Convert duration to number of days")
  (hours   [this] "Convert duration to number of hours")
  (minutes [this] "Convert duration to number of minutes")
  (seconds [this] "Convert duration to number of seconds")
  (millis  [this] "Convert duration to number of milliseconds")
  (micros  [this] "Convert duration to number of micros")
  (nanos   [this] "Convert duration to number of nanoseconds"))


(defrecord Duration
  [^long time ^TimeUnit unit]
  IDuration
  (days    [this] (.toDays    unit time))
  (hours   [this] (.toHours   unit time))
  (minutes [this] (.toMinutes unit time))
  (seconds [this] (.toSeconds unit time))
  (millis  [this] (.toMillis  unit time))
  (micros  [this] (.toMicros  unit time))
  (nanos   [this] (.toNanos   unit time)))


(extend-protocol IDuration
  java.util.List
  (days    [this] (.toDays    ^TimeUnit (.get this 1) ^long (.get this 0)))
  (hours   [this] (.toHours   ^TimeUnit (.get this 1) ^long (.get this 0)))
  (minutes [this] (.toMinutes ^TimeUnit (.get this 1) ^long (.get this 0)))
  (seconds [this] (.toSeconds ^TimeUnit (.get this 1) ^long (.get this 0)))
  (millis  [this] (.toMillis  ^TimeUnit (.get this 1) ^long (.get this 0)))
  (micros  [this] (.toMicros  ^TimeUnit (.get this 1) ^long (.get this 0)))
  (nanos   [this] (.toNanos   ^TimeUnit (.get this 1) ^long (.get this 0)))
  java.util.Map
  (days    [this] (.toDays    ^TimeUnit (.get this :unit) ^long (.get this :time)))
  (hours   [this] (.toHours   ^TimeUnit (.get this :unit) ^long (.get this :time)))
  (minutes [this] (.toMinutes ^TimeUnit (.get this :unit) ^long (.get this :time)))
  (seconds [this] (.toSeconds ^TimeUnit (.get this :unit) ^long (.get this :time)))
  (millis  [this] (.toMillis  ^TimeUnit (.get this :unit) ^long (.get this :time)))
  (micros  [this] (.toMicros  ^TimeUnit (.get this :unit) ^long (.get this :time)))
  (nanos   [this] (.toNanos   ^TimeUnit (.get this :unit) ^long (.get this :time))))
