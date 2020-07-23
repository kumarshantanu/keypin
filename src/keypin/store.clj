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
    [keypin.type     :as t])
  (:import
    [java.util Date]
    [java.text SimpleDateFormat]
    [java.util.concurrent TimeoutException]
    [clojure.lang Associative IDeref ILookup IPersistentCollection IPersistentMap Named Seqable]))


(defrecord DynamicStore
  [^IPersistentMap kvdata
   ^String         name
   ^long           tstamp])


(defn fetch-every?
  "Given duration in milliseconds, return a fetch decider (fn fetch? [last-fetch-time-millis]) that returns true if
  it is time to fetch data, false otherwise."
  [^long duration-millis]
  (fn [^DynamicStore dynamic-store]
    (>= (i/now-millis (.-tstamp dynamic-store))
      duration-millis)))


(defn fetch-if-error?
  [err-ts-key ^long millis-since-error]
  (fn [^DynamicStore dynamic-store]
    (if-let [err-ts (get dynamic-store err-ts-key)]
      (>= (i/now-millis (long err-ts))
        millis-since-error)
      true)))


(defn wait-if-stale
  [^long stale-millis ^long timeout-millis]
  (fn [container]
    (let [^DynamicStore dynamic-store @container
          tstamp (.-tstamp dynamic-store)]
      (when (>= (i/now-millis tstamp) stale-millis)  ; stale data?
        (let [until-millis (unchecked-add (i/now-millis) timeout-millis)]
          (loop []
            (if (< (i/now-millis) until-millis)  ; not timed out yet waiting for stale->refresh
              (when (= tstamp (.-tstamp ^DynamicStore @container))  ; not updated?
                (try (-> until-millis
                       (unchecked-subtract (i/now-millis))
                       (min 10) ; max 10ms sleep window
                       (max 0)  ; guard against negative duration
                       (Thread/sleep))
                  (catch InterruptedException _))
                (recur))
              (throw (TimeoutException. (format "Timed out waiting for stale dynamic store %s to be refreshed"
                                          (.-name dynamic-store)))))))))))


(defn make-dynamic-store
  "Given a fetch function (fn [old-data])->new-data that fetches a map instance, and initial data (`nil`: initialized
  asynchronously in another thread), create a dynamic store that refreshes itself.

  ### Options

  | Kwarg          | Type/format                   | Description                 | Default |
  |----------------|-------------------------------|-----------------------------|---------|
  | :name          | stringable                    | Name of the config store    | Auto generated |
  | :fetch?        | (fn [^DynamicStore ds])->bool | Return true for to re-fetch | Fetch at 1 sec interval |
  | :verify-sanity | (fn [DynamicStore-holder])    | Verify store sanity         | Wait max 1 sec for 5+ sec old data |
  | :error-handler | (fn [DynamicStore-holder ex]) | respond to async fetch error| Prints the error |

  You may deref DynamicStore-holder to access its contents.

  ### Examples

  (make-dynamic-store f nil)  ; async initialization, refresh interval 1 second
  (make-dynamic-store f (f))  ; upfront initialization, refresh interval 1 second"
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
          data-holder (agent (map->DynamicStore {:kvdata init
                                                 :name   name-string
                                                 :tstamp (if (nil? init)
                                                           0
                                                           (i/now-millis))})
                        :error-handler error-handler)
          start-fetch (fn [] (send-off data-holder (fn [^DynamicStore dynamic-store]
                                                     (if (fetch? dynamic-store)
                                                       (map->DynamicStore {:kvdata (f (.-kvdata dynamic-store))
                                                                           :name   name-string
                                                                           :tstamp (i/now-millis)})
                                                       dynamic-store))))
          update-data (fn []
                        (let [^DynamicStore dynamic-store @data-holder
                              kvdata (.-kvdata dynamic-store)]
                          (when (fetch? dynamic-store)
                            (start-fetch))
                          (verify-sanity data-holder)
                          (when (nil? kvdata)
                            (throw (IllegalStateException. (format "Dynamic store %s is not yet initialized"
                                                             name-string))))
                          kvdata))]
      (when (nil? init)
        (start-fetch))
      (reify
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
        (empty [_]      (make-dynamic-store (constantly false) {}))
        (equiv [_ obj]  (.equiv ^IPersistentMap (update-data) obj))
        Associative
        (containsKey  [_ k]   (contains? (update-data) k))
        (entryAt      [_ k]   (.entryAt ^IPersistentMap (update-data) k))
        (assoc        [_ _ _] (throw (UnsupportedOperationException. "assoc is not supported on this type")))
        Named
        (getNamespace [_]  (when (instance? Named name) (namespace name)))
        (getName      [_]  (if (instance? Named name)
                             (clojure.core/name name)
                             name-string))))))


(defn make-caching-store
  "Wrap a given store (map, vector or keypin.type/IStore instance) such that the key-definition lookups are cached.
  For dynamic stores (that implement clojure.lang.IDeref) the cache is valid as long as the underlying store data
  doesn't change."
  [store]
  (i/expected #(satisfies? t/IStore %) "an instance of keypin.type/IStore protocol" store)
  (let [data? (not (instance? IDeref store))
        state (agent {:kvdata (when data?
                                store)
                      :cache  {}}
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
                    (if (identical? store-data (:kvdata state-data))
                      state-data
                      (let [new-state {:kvdata store-data
                                       :cache {}}]
                        (send state conj new-state)
                        new-state)))))
        sdata (fn []
                (:kvdata (fetch)))]
    (reify
      IDeref
      (deref [_]      (sdata))
      t/IStore
      (lookup [_ kd]  (let [{:keys [kvdata cache]} (fetch)
                            l-key (:the-key kd)]
                        (if (contains? cache l-key)
                          (get cache l-key)
                          (let [v (t/lookup kvdata kd)]
                            (send state assoc-in [:cache l-key] v)
                            v))))
      ILookup
      (valAt [_ k]    (get (sdata) k))
      (valAt [_ k nf] (get (sdata) k nf))
      Seqable
      (seq   [_]      (seq (sdata)))
      IPersistentCollection
      (count [_]      (count (sdata)))
      (cons  [_ _]    (throw (UnsupportedOperationException. "cons is not supported on this type")))
      (empty [_]      (make-caching-store {}))
      (equiv [_ obj]  (.equiv ^IPersistentMap (sdata) obj))
      Associative
      (containsKey  [_ k]   (contains? (sdata) k))
      (entryAt      [_ k]   (.entryAt ^IPersistentMap (sdata) k))
      (assoc        [_ _ _] (throw (UnsupportedOperationException. "assoc is not supported on this type"))))))
