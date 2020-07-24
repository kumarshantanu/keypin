;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns keypin.store
  (:require
    [clojure.stacktrace :as cs]
    [keypin.internal :as i]
    [keypin.type     :as t]
    [keypin.type.record :as r])
  (:import
    [java.util Date]
    [java.text SimpleDateFormat]
    [java.util.concurrent TimeoutException]
    [clojure.lang IDeref]
    [keypin.type.record StoreState]))


;; ----- dynamic store -----


(defn fetch-every?
  "Given duration in milliseconds, return a fetch decider (predicate) function

  ```
  (fn [^keypin.type.record.StoreState store-state]) -> boolean
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
  (fn [^keypin.type.record.StoreState store-state]) -> boolean
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
  detects stale store and waits until timeout for a refresh.

  See: [[make-dynamic-store]]"
  [^long stale-millis ^long timeout-millis]
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
              (throw (TimeoutException. (format "Timed out waiting for stale dynamic store %s to be refreshed"
                                          (.-store-name store-state)))))))))))


(defn make-dynamic-store
  "Given a fetch function `(fn [old-data]) -> new-data` that fetches a map instance, and initial data
  (`nil`: initialized asynchronously in another thread), create a dynamic store that refreshes itself.

  ### Options

  | Kwarg          | Type/format                 | Description                 | Default |
  |----------------|-----------------------------|-----------------------------|---------|
  |`:name`         | stringable                  | Name of the config store    | Auto generated |
  |`:fetch?`       |`(fn [^StoreState ss])->bool`| Return true to re-fetch now | Fetch at 1 sec interval |
  |`:verify-sanity`|`(fn [StoreState-holder])`   | Verify store sanity         | Wait max 1 sec for 5+ sec old data |
  |`:error-handler`|`(fn [StoreState-holder ex])`| respond to async fetch error| Prints the error |

  You may deref StoreState-holder to access its contents.

  ### Examples

  ```
  (make-dynamic-store f nil)  ; async initialization, refresh interval 1 second
  (make-dynamic-store f (f))  ; upfront initialization, refresh interval 1 second
  ```"
  ([f init]
    (make-dynamic-store f init {}))
  ([f init {:keys [name
                   fetch?
                   verify-sanity
                   error-handler]
            :or {name   (gensym "dynamic-store:")
                 fetch? (let [f? (fetch-every? 1000)  ; fetch every 1 second
                              e? (fetch-if-error?     ; fetch after minimum 1 second if error happened
                                   :err-ts 1000)]
                          (fn [db] (and (f? db) (e? db))))
                 verify-sanity (wait-if-stale 5000 1000)
                 error-handler (fn [data-holder ^Throwable ex]
                                 (let [err-ts (i/now-millis)]
                                   (binding [*out* *err*]
                                     (printf "Error refreshing dynamic store %s at %s\n"
                                       (i/as-str name)
                                       (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSSXXX") (Date. err-ts)))
                                     (cs/print-stack-trace ex)
                                     (flush))
                                   (send data-holder update :err-ts (fn [old-err-ts]
                                                                      (max (long (or old-err-ts 0)) err-ts)))))}
            :as options}]
    (let [name-string (i/as-str name)
          data-holder (agent (r/map->StoreState {:store-data init
                                                 :store-name name-string
                                                 :updated-at (if (nil? init)
                                                               0
                                                               (i/now-millis))})
                        :error-handler error-handler)
          start-fetch (fn [] (send-off data-holder (fn [^StoreState store-state]
                                                     (if (fetch? store-state)
                                                       (r/map->StoreState {:store-data (f (.-store-data store-state))
                                                                           :store-name name-string
                                                                           :updated-at (i/now-millis)})
                                                       store-state))))
          update-data (fn []
                        (let [^StoreState store-state @data-holder
                              store-data (.-store-data store-state)]
                          (when (fetch? store-state)
                            (start-fetch))
                          (verify-sanity data-holder)
                          (when (nil? store-data)
                            (throw (IllegalStateException. (format "Dynamic store %s is not yet initialized"
                                                             name-string))))
                          store-data))]
      (when (nil? init)
        (start-fetch))
      (r/->DynamicStore data-holder update-data))))


;; ----- caching store -----


(defn make-caching-store
  "Wrap a given store (map, vector or keypin.type/IStore instance) such that the key-definition lookups are cached.
  For dynamic stores (that implement clojure.lang.IDeref) the cache is valid as long as the underlying store data
  doesn't change."
  [store]
  (i/expected #(satisfies? t/IStore %) "an instance of keypin.type/IStore protocol" store)
  (let [data? (not (instance? IDeref store))
        state (agent (r/map->CacheState {:store-data (when data? store)
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
                      (let [new-state (r/map->CacheState {:store-data store-data
                                                          :cache-data {}})]
                        (send state conj new-state)
                        new-state)))))]
    (r/->CachingStore state fetch)))
