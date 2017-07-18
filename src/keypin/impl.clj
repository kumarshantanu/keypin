;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns keypin.impl
  (:require
    [keypin.type :as t])
  (:import
    [java.util.concurrent TimeUnit]
    [clojure.lang Named]))


(def time-units
  "A map of string time unit to Java equivalent."
  (array-map
    ;; days
    "d"            TimeUnit/DAYS
    "day"          TimeUnit/DAYS
    "days"         TimeUnit/DAYS
    ;; hours
    "h"            TimeUnit/HOURS
    "hr"           TimeUnit/HOURS
    "hour"         TimeUnit/HOURS
    "hours"        TimeUnit/HOURS
    ;; minutes
    "m"            TimeUnit/MINUTES
    "min"          TimeUnit/MINUTES
    "minute"       TimeUnit/MINUTES
    "minutes"      TimeUnit/MINUTES
    ;; seconds
    "s"            TimeUnit/SECONDS
    "sec"          TimeUnit/SECONDS
    "second"       TimeUnit/SECONDS
    "seconds"      TimeUnit/SECONDS
    ;; milliseconds
    "ms"           TimeUnit/MILLISECONDS
    "millis"       TimeUnit/MILLISECONDS
    "millisecond"  TimeUnit/MILLISECONDS
    "milliseconds" TimeUnit/MILLISECONDS
    ;; microseconds
    "us"           TimeUnit/MICROSECONDS
    "μs"           TimeUnit/MICROSECONDS
    "micros"       TimeUnit/MICROSECONDS
    "microsecond"  TimeUnit/MICROSECONDS
    "microseconds" TimeUnit/MICROSECONDS
    ;; nanoseconds
    "ns"           TimeUnit/NANOSECONDS
    "nanos"        TimeUnit/NANOSECONDS
    "nanosecond"   TimeUnit/NANOSECONDS
    "nanoseconds"  TimeUnit/NANOSECONDS))


(defn resolve-time-unit
  "Resolve given argument as java.util.concurrent.TimeUnit, or nil."
  ^TimeUnit
  [x]
  (cond
    (instance? TimeUnit x) x
    (instance? String x)   (get time-units (.toLowerCase ^String x))
    (instance? Named x)    (when (nil? (.getNamespace ^Named x))  ; must be unqualified
                             (get time-units (.toLowerCase (.getName ^Named x))))))


(extend-protocol t/IDuration
  java.util.List
  (isDuration [this] (and (= 2 (.size this))
                       (resolve-time-unit (.get this 1))
                       (integer? (.get this 0))))
  (days       [this] (.toDays    ^TimeUnit (resolve-time-unit (.get this 1)) ^long (.get this 0)))
  (hours      [this] (.toHours   ^TimeUnit (resolve-time-unit (.get this 1)) ^long (.get this 0)))
  (minutes    [this] (.toMinutes ^TimeUnit (resolve-time-unit (.get this 1)) ^long (.get this 0)))
  (seconds    [this] (.toSeconds ^TimeUnit (resolve-time-unit (.get this 1)) ^long (.get this 0)))
  (millis     [this] (.toMillis  ^TimeUnit (resolve-time-unit (.get this 1)) ^long (.get this 0)))
  (micros     [this] (.toMicros  ^TimeUnit (resolve-time-unit (.get this 1)) ^long (.get this 0)))
  (nanos      [this] (.toNanos   ^TimeUnit (resolve-time-unit (.get this 1)) ^long (.get this 0)))
  java.util.Map
  (isDuration [this] (and (resolve-time-unit (.get this :unit))
                       (integer? (.get this :time))))
  (days       [this] (.toDays    ^TimeUnit (resolve-time-unit (.get this :unit)) ^long (.get this :time)))
  (hours      [this] (.toHours   ^TimeUnit (resolve-time-unit (.get this :unit)) ^long (.get this :time)))
  (minutes    [this] (.toMinutes ^TimeUnit (resolve-time-unit (.get this :unit)) ^long (.get this :time)))
  (seconds    [this] (.toSeconds ^TimeUnit (resolve-time-unit (.get this :unit)) ^long (.get this :time)))
  (millis     [this] (.toMillis  ^TimeUnit (resolve-time-unit (.get this :unit)) ^long (.get this :time)))
  (micros     [this] (.toMicros  ^TimeUnit (resolve-time-unit (.get this :unit)) ^long (.get this :time)))
  (nanos      [this] (.toNanos   ^TimeUnit (resolve-time-unit (.get this :unit)) ^long (.get this :time))))
