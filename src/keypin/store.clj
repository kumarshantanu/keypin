;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns keypin.store
  "Config store functionality and implementation.

  See: [[make-dynamic-store]], [[make-caching-store]]"
  (:require
    [clojure.stacktrace :as cs]
    [keypin.internal :as i]
    [keypin.type     :as t])
  (:import
    [java.util Date Map]
    [java.text SimpleDateFormat]
    [clojure.lang Associative IDeref ILookup IPersistentCollection IPersistentMap Seqable]
    [keypin.type KeyAttributes]))


;; ----- dynamic store -----


(defrecord StoreState [^Map    store-data
                       ^String store-name
                       ^long   updated-at
                       ])


(deftype DynamicStore [state-agent  ; an agent holding current state
                       update-data  ; (fn ^StoreState []) to potentially update state
                       ]
  IDeref
  (deref [_]      (update-data))
  t/IStore
  (lookup [_ kd]  (t/lookup (update-data) kd))
  ILookup
  (valAt [_ k]    (get (update-data) k))
  (valAt [_ k nf] (get (update-data) k nf))
  Seqable
  (seq   [_]      (seq (update-data)))
  IPersistentCollection
  (count [_]      (count (update-data)))
  (cons  [_ _]    (throw (UnsupportedOperationException. "cons is not supported on this type")))
  (empty [_]      (let [ss (->StoreState {} "empty" (i/now-millis))]
                    (DynamicStore. (agent ss) (constantly ss))))
  (equiv [_ obj]  (.equiv ^IPersistentMap (update-data) obj))
  Associative
  (containsKey  [_ k]   (contains? (update-data) k))
  (entryAt      [_ k]   (.entryAt ^IPersistentMap (update-data) k))
  (assoc        [_ _ _] (throw (UnsupportedOperationException. "assoc is not supported on this type"))))


(defn fetch-every?
  "Given duration in milliseconds, return a fetch decider (predicate) function

  ```
  (fn [^keypin.type.StoreState store-state]) -> boolean
  ```

  that returns `true` if the duration has elapsed, `false` otherwise.

  See: [[make-dynamic-store]]"
  [^long duration-millis]
  (fn [^StoreState store-state]
    (>= (i/now-millis (.-updated-at store-state))
      duration-millis)))


(defn fetch-if-error?
  "Given error-timestamp key and duration since error, return a fetch decider (predicate) function

  ```
  (fn [^keypin.type.StoreState store-state]) -> boolean
  ```

  that returns `true` if error happened + duration elapsed,
  `false` if error happened + duration NOT elapsed, `true` otherwise.

  |Error occured?|Duration elapsed?|Return|
  |--------------|-----------------|------|
  |      Yes     |       Yes       | True |
  |              |        No       | False|
  |       No     |                 | True |

  See: [[make-dynamic-store]]"
  [err-ts-key ^long millis-since-error]
  (fn [^StoreState store-state]
    (if-let [err-ts (get store-state err-ts-key)]
      (>= (i/now-millis (long err-ts))
        millis-since-error)
      true)))


(defn wait-if-stale
  "Given staleness duration and refresh-wait timeout in milliseconds, return a function `(fn [state-agent])` that
  detects stale store and waits until timeout for a refresh - prints to `*err*` by default on timeout.

  See: [[make-dynamic-store]]"
  ([^long stale-millis ^long timeout-millis]
    (wait-if-stale stale-millis timeout-millis {}))
  ([^long stale-millis ^long timeout-millis
    {:keys [stale-timeout-handler]
     :or {stale-timeout-handler (fn [^StoreState store-state]
                                  (binding [*out* *err*]
                                    (println (format "Timed out waiting for stale dynamic store %s to be refreshed"
                                               (.-store-name store-state)))
                                    (flush)))}
     :as options}]
    (fn [state-agent]
      (let [^StoreState store-state @state-agent
            tstamp (.-updated-at store-state)]
        (when (>= (i/now-millis tstamp) stale-millis)  ; stale data?
          (let [until-millis (unchecked-add (i/now-millis) timeout-millis)]
            (loop []
              (if (< (i/now-millis) until-millis)  ; not timed out yet waiting for stale->refresh
                (when (= tstamp (.-updated-at ^StoreState @state-agent))  ; not updated?
                  (try (-> until-millis
                         (unchecked-subtract (i/now-millis))
                         (min 10) ; max 10ms sleep window
                         (max 0)  ; guard against negative duration
                         (Thread/sleep))
                    (catch InterruptedException _))
                  (recur))
                (stale-timeout-handler store-state)))))))))


(defn make-dynamic-store-options
  "Given following options, use pre-configured utility functions to build options for [[make-dynamic-store]].

  | Kwarg                  | Description                                        | Default     |
  |------------------------|----------------------------------------------------|-------------|
  |`:fetch-interval-millis`|Milliseconds to wait since last fetch to fetch again|`1000`       |
  |`:err-tstamp-millis-key`|Key for error timestamp (millis) in `StoreState`    |`:err-ts`    |
  |`:fetch-backoff-millis` |Milliseconds to wait to fetch since last error      |`1000`       |
  |`:stale-duration-millis`|Store-data older than this duration(millis) is stale|`5000`       |
  |`:stale-timeout-millis` |Wait max this duration(millis) to refresh stale data|`1000`       |
  |`:stale-timeout-handler`|`(fn [^StoreState store-state])` to call on timeout |STDERR output|

  See: [[fetch-every?]], [[fetch-if-error?]], [[wait-if-stale]]"
  ([]
    (make-dynamic-store-options {}))
  ([{:keys [name
            fetch-interval-millis
            err-tstamp-millis-key
            fetch-backoff-millis
            stale-duration-millis
            stale-timeout-millis]
     :or {name                  (gensym "dynamic-store:")
          fetch-interval-millis 1000  ; fetch every 1 second
          err-tstamp-millis-key :err-ts
          fetch-backoff-millis  1000  ; fetch after minimum 1 second since error happened
          stale-duration-millis 5000  ; older than 5 seconds store data is considered stale
          stale-timeout-millis  1000  ; wait max 1 second for stale data to be refreshed
          }
     :as options}]
    {:name          name
     :fetch?        (let [f? (fetch-every? fetch-interval-millis)
                          e? (fetch-if-error?
                               err-tstamp-millis-key fetch-backoff-millis)]
                      (fn [db] (and (f? db) (e? db))))
     :verify-sanity (wait-if-stale stale-duration-millis stale-timeout-millis options)
     :error-handler (fn [data-holder ^Throwable ex]
                      (let [err-ts (i/now-millis)]
                        (binding [*out* *err*]
                          (printf "Error refreshing dynamic store %s at %s\n"
                            (i/as-str name)
                            (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSSXXX") (Date. err-ts)))
                          (cs/print-stack-trace ex)
                          (flush))
                        (send data-holder update err-tstamp-millis-key (fn [old-err-ts]
                                                                         (max (long (or old-err-ts 0)) err-ts)))))}))


(defn make-dynamic-store
  "Given a fetch function `(fn [old-data]) -> new-data` that fetches a map instance, create a dynamic store that
  refreshes itself.

  ### Options

  | Kwarg          | Type/format                 | Description                 | Default |
  |----------------|-----------------------------|-----------------------------|---------|
  |`:name`         | stringable                  | Name of the config store    | Auto generated |
  |`:init`         | map                         | Initial data                | `nil`: Initialized asynchronously |
  |`:fetch?`       |`(fn [^StoreState ss])->bool`| Return true to re-fetch now | Fetch at 1 sec interval |
  |`:verify-sanity`|`(fn [StoreState-holder])`   | Verify store sanity         | Wait max 1 sec for 5+ sec old data |
  |`:error-handler`|`(fn [StoreState-holder ex])`| respond to async fetch error| Prints the error |

  You may deref StoreState-holder to access its contents.

  ### Examples

  ```
  (make-dynamic-store f)  ; async initialization, refresh interval 1 second
  (make-dynamic-store f {:init (f)})  ; upfront initialization, refresh interval 1 second
  ```

  See: [[make-dynamic-store-options]]"
  ([f]
    (make-dynamic-store f {}))
  ([f options]
    (let [{:keys [name
                  init
                  fetch?
                  verify-sanity
                  error-handler]} (conj (make-dynamic-store-options) options)
          name-string (i/as-str name)
          data-holder (agent (map->StoreState {:store-data init
                                               :store-name name-string
                                               :updated-at (if (nil? init)
                                                             0
                                                             (i/now-millis))})
                        :error-handler error-handler)
          start-fetch (fn [] (send-off data-holder (fn [^StoreState store-state]
                                                     (if (fetch? store-state)
                                                       (map->StoreState {:store-data (f (.-store-data store-state))
                                                                         :store-name name-string
                                                                         :updated-at (i/now-millis)})
                                                       store-state))))
          update-data (fn []
                        (when (fetch? @data-holder)
                          (start-fetch))
                        (verify-sanity data-holder)
                        (let [^StoreState store-state @data-holder
                              store-data (.-store-data store-state)]
                          (when (nil? store-data)
                            (throw (IllegalStateException. (format "Dynamic store %s is not yet initialized"
                                                             name-string))))
                          store-data))]
      (when (nil? init)
        (start-fetch))
      (->DynamicStore data-holder update-data))))


;; ----- caching store -----


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


(defn make-caching-store
  "Wrap a given store (map, vector or keypin.type/IStore instance) such that the key-definition lookups are cached.
  For dynamic stores (that implement `clojure.lang.IDeref`) the cache is valid as long as the underlying store data
  doesn't change."
  [store]
  (i/expected #(satisfies? t/IStore %) "an instance of keypin.type/IStore protocol" store)
  (let [data? (not (instance? IDeref store))
        state (agent (map->CacheState {:store-data (when data? store)
                                       :cache-data {}})
                :error-handler (fn [state ^Throwable ex]
                                 (let [err-ts (i/now-millis)]
                                   (binding [*out* *err*]
                                     (printf "Error updating caching store for %s at %s\n"
                                       (i/as-str store)
                                       (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSSXXX") (Date. err-ts)))
                                     (cs/print-stack-trace ex)
                                     (flush)))))
        fetch (if data?
                (fn []
                  @state)
                (fn []
                  (let [store-data @store
                        state-data @state]
                    (if (identical? store-data (:store-data state-data))
                      state-data
                      (let [new-state (map->CacheState {:store-data store-data
                                                        :cache-data {}})]
                        (send state conj new-state)
                        new-state)))))]
    (->CachingStore state fetch)))
