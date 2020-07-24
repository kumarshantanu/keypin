;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns keypin.type.record
  (:require
    [keypin.type :as t])
  (:import
    [clojure.lang Associative IDeref ILookup IPersistentCollection IPersistentMap Named Seqable]
    [keypin.type KeyAttributes]))


(defrecord CacheState [store-data
                       cache-data])


(deftype CachingStore [state-agent  ; an agent holding current state
                       fetch-state  ; (fn ^CacheState []) to fetch state
                       ]
  IDeref
  (deref [_]      (.-store-data ^CacheState (fetch-state)))
  t/IStore
  (lookup [_ kd]  (let [^CacheState cache-state (fetch-state)
                        cache-data (.-cache-data cache-state)
                        lookup-key (.-the-key ^KeyAttributes kd)]
                    (if (contains? cache-data lookup-key)
                      (get cache-data lookup-key)
                      (let [v (t/lookup (.-store-data cache-state) kd)]
                        (send state-agent assoc-in [:cache-data lookup-key] v)
                        v))))
  ILookup
  (valAt [_ k]    (get (.-store-data ^CacheState (fetch-state)) k))
  (valAt [_ k nf] (get (.-store-data ^CacheState (fetch-state)) k nf))
  Seqable
  (seq   [_]      (seq (.-store-data ^CacheState (fetch-state))))
  IPersistentCollection
  (count [_]      (count (.-store-data ^CacheState (fetch-state))))
  (cons  [_ _]    (throw (UnsupportedOperationException. "cons is not supported on this type")))
  (empty [_]      (let [cache-data (->CacheState {} {})]
                    (CachingStore. (agent cache-data) (constantly {}))))
  (equiv [_ obj]  (.equiv ^IPersistentMap (.-store-data ^CacheState (fetch-state)) obj))
  Associative
  (containsKey  [_ k]   (contains? (.-store-data ^CacheState (fetch-state)) k))
  (entryAt      [_ k]   (.entryAt ^IPersistentMap (.-store-data ^CacheState (fetch-state)) k))
  (assoc        [_ _ _] (throw (UnsupportedOperationException. "assoc is not supported on this type"))))
