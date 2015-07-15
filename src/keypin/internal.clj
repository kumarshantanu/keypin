(ns keypin.internal
  (:require
    [clojure.string :as string])
  (:import
    [clojure.lang ILookup]
    [java.util Map Properties]))


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


(defn identity-parser
  [_ value]
  value)


(defmacro defmany
  [macro-sym def-sym arg-vec & more]
  (let [exprs (->> (concat [def-sym arg-vec] more)
                (partition 2)
                (map (fn [[sym args]]
                       `(~macro-sym ~sym ~@args))))]
    `(do ~@exprs)))


(defn lookup-property
  "Look up property name in a java.util.Properties instance."
  [^Properties the-map ^String property-name validator description property-parser default-value? default-value]
  (when-not (instance? Properties the-map)
    (illegal-arg (format "Property '%s' looked up in a non java.util.Properties object: %s"
                   property-name the-map)))
  (let [value (if (.containsKey ^Properties the-map property-name)
                (->> property-name
                  (.getProperty ^Properties the-map)
                  (property-parser property-name))
                (if default-value?
                  default-value
                  (illegal-arg (format "No default value is defined for non-existent property '%s'" property-name))))]
    (expect-arg value validator (format "Invalid value for property '%s' (description: '%s'): %s"
                                  property-name description (pr-str value)))))


(defn lookup-key
  "Look up a key in a map or something that implements clojure.lang.ILookup."
  [the-map the-key validator description property-parser default-value? default-value]
  (when-not (or (instance? Map the-map)
              (instance? ILookup the-map))
    (illegal-arg (format "Key %s looked up in a non map (or clojure.lang.ILookup) object: %s"
                   (pr-str the-key) the-map)))
  (let [value (if (contains? the-map the-key)
                (->> (get the-map the-key)
                  (property-parser the-key))
                (if default-value?
                  default-value
                  (illegal-arg (str "No default value is defined for non-existent key " (pr-str the-key)))))]
    (expect-arg value validator (format "Invalid value for key %s (description: '%s'): %s"
                                  (pr-str the-key) description (pr-str value)))))


(defn lookup-keypath
  "Look up a key path in a map or something that implements clojure.lang.ILookup."
  [the-map ks validator description property-parser default-value? default-value]
  (let [value (loop [data the-map
                     path ks]
                (when-not (or (instance? Map data)
                            (instance? ILookup data))
                  (illegal-arg (format "Key path %s looked up in a non map (or clojure.lang.ILookup) object: %s"
                                 (pr-str path) (pr-str data))))
                (let [k (first path)]
                  (if-not (contains? data k)
                    (if default-value?   ; has a default value?
                      default-value
                      (illegal-arg (str "No default value is defined for non-existent key path " (pr-str ks))))
                    (if-not (next path)  ; last key in key path?
                      (get data k)
                      (recur (get data k) (rest path))))))]
    (expect-arg value validator (format "Invalid value for key path %s (description: '%s'): %s"
                                  (pr-str ks) description (pr-str value)))))
