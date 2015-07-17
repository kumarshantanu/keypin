(ns keypin.core
  (:require
    [clojure.string :as string]
    [keypin.internal :as i])
  (:import
    [clojure.lang ILookup]
    [java.io FileNotFoundException]
    [java.util Map Properties]
    [keypin Logger PropertyFile]))


;; ===== validators =====


(defn any?
  "Dummy validator. Always return true."
  [_]
  true)


(defn bool?
  "Return true if argument is of boolean type, false otherwise."
  [x]
  (instance? Boolean x))


(defn deref?
  "Wrap a predicate such that it derefs the argument before applying the predicate."
  [pred]
  (fn [x]
    (pred (deref x))))


;; ===== lookup functions ====


(defn lookup-key
  "Look up a key in a map or something that implements clojure.lang.ILookup."
  [the-map the-key validator description property-parser default-value? default-value]
  (when-not (or (instance? Map the-map)
              (instance? ILookup the-map))
    (i/illegal-arg (format "Key %s looked up in a non map (or clojure.lang.ILookup) object: %s"
                     (pr-str the-key) the-map)))
  (let [value (if (contains? the-map the-key)
                (->> (get the-map the-key)
                  (property-parser the-key))
                (if default-value?
                  default-value
                  (i/illegal-arg (str "No default value is defined for non-existent key " (pr-str the-key)))))]
    (i/expect-arg value validator (format "Invalid value for key %s (description: '%s'): %s"
                                    (pr-str the-key) description (pr-str value)))))


(defn lookup-keypath
  "Look up a key path in a map or something that implements clojure.lang.ILookup."
  [the-map ks validator description property-parser default-value? default-value]
  (let [value (loop [data the-map
                     path ks]
                (when-not (or (instance? Map data)
                            (instance? ILookup data))
                  (i/illegal-arg (format "Key path %s looked up in a non map (or clojure.lang.ILookup) object: %s"
                                   (pr-str path) (pr-str data))))
                (let [k (first path)]
                  (if-not (contains? data k)
                    (if default-value?   ; has a default value?
                      default-value
                      (i/illegal-arg (str "No default value is defined for non-existent key path " (pr-str ks))))
                    (if-not (next path)  ; last key in key path?
                      (get data k)
                      (recur (get data k) (rest path))))))]
    (i/expect-arg value validator (format "Invalid value for key path %s (description: '%s'): %s"
                                    (pr-str ks) description (pr-str value)))))


;; ===== properties files =====


(defn read-properties
  "Read properties file(s) returning a java.util.Properties instance."
  (^Properties [^String config-filename]
    (read-properties config-filename {:parent-key "parent"}))
  (^Properties [^String config-filename {:keys [^String parent-key info-logger error-logger]
                                         :or {info-logger  #(println "[keypin] [info]" %)
                                              error-logger #(println "[keypin] [error]" %)}
                                         :as options}]
    (let [logger (reify Logger
                   (info [this msg] (info-logger msg))
                   (error [this msg] (error-logger msg)))]
      (if parent-key
        (PropertyFile/resolveConfig config-filename parent-key logger)
        (PropertyFile/resolveConfig config-filename logger)))))


(defn lookup-property
  "Look up property name in a java.util.Properties instance."
  [^Properties the-map ^String property-name validator description property-parser default-value? default-value]
  (when-not (instance? Properties the-map)
    (i/illegal-arg (format "Property '%s' looked up in a non java.util.Properties object: %s"
                     property-name the-map)))
  (let [value (if (.containsKey ^Properties the-map property-name)
                (->> property-name
                  (.getProperty ^Properties the-map)
                  (property-parser property-name))
                (if default-value?
                  default-value
                  (i/illegal-arg (format "No default value is defined for non-existent property '%s'" property-name))))]
    (i/expect-arg value validator (format "Invalid value for property '%s' (description: '%s'): %s"
                                    property-name description (pr-str value)))))


;; ===== key attributes primitive =====


(defrecord KeyAttributes
  [the-key validator description value-parser default-value? default-value lookup-fn]
  clojure.lang.IFn
  (invoke [this the-map]  ; behave as arity-1 fn
    (lookup-fn the-map the-key validator description value-parser default-value? default-value))
  (invoke [this the-map not-found]  ; behave as arity-2 fn (2nd arg is returned when key not found)
    (lookup-fn the-map the-key identity description value-parser true not-found))
  java.util.Map$Entry  ; key name is retrievable using clojure.core/key: (key foo)
  (getKey [this] the-key)
  ; not implementing methods (getValue [this] ..) and (setValue [this v] ..)
  Object
  (toString [this] (str the-key)))


;; ===== key definition =====


(defn make-key
  "Create a key that can be looked up in a java.util.Map/Properties or clojure.lang.ILookup (map, vector) instance. The
  following optional keys are supported:
  :lookup  - The function to look the key up,
             args: the-map, the-key, validator, description, value-parser, default-value?, default-value,
             default: ordinary key look up
  :parser  - The value parser function (args: key, value)
  :default - Default value to return if key is not found"
  [the-key validator description {:keys [lookup parser default]
                                  :or {lookup lookup-key
                                       parser i/identity-parser}
                                  :as options}]
  (->KeyAttributes
    the-key validator description parser
    (if   (contains? options :default) true false)
    (when (contains? options :default) default)
    lookup))


(defmacro defkey
  "Define one or more keys as vars using argument vectors. Every argument vector must have one of the following arities:
  [key]
  [key options]
  [key validator description]
  [key validator description options]
  See `make-key` for details. First argument to `defkey` can optionally be a base option-map for all argument vectors.
  Examples:
  (defkey
    ip   [:ip-address]
    port [:port #(< 1023 % 65535) \"Server port\" {:parser str->int :default 3000}])
  (defkey
    {:lookup lookup-property}
    ip   [\"server.ip.address\"]
    port [\"server.port\" #(< 1023 % 65535) \"Server port\" {:parser str->int :default 3000}])"
  [the-sym arg-vec & more]
  (let [options (if (odd? (count more))
                  (i/expect-arg the-sym map? ["Expected an option map, found" (pr-str the-sym)])
                  {})
        pairs (->> (if (odd? (count more))
                     (cons arg-vec more)
                     (cons the-sym (cons arg-vec more)))
                (partition 2)
                (map (fn [[each-sym each-vec]]
                       (i/expect-arg each-sym symbol? ["Expected a symbol to define var, but found" (pr-str each-sym)])
                       (i/expect-arg each-vec vector? ["Expected a vector to create key, but found" (pr-str each-vec)])
                       [each-sym (case (count each-vec)
                                   1 (conj each-vec any? "No description" options)
                                   2 (let [{:keys [pred desc]
                                            :or {pred any?
                                                 desc "No description"}
                                            :as spec-opts} (let [[each-key each-opts] each-vec]
                                                             (i/expect-arg each-opts map?
                                                               (format "Expected an option map for key %s, but found %s"
                                                                 (pr-str each-key) (pr-str each-opts)))
                                                             (merge options each-opts))]
                                       (-> (pop each-vec) ; remove options first
                                         (conj pred desc spec-opts)))
                                   3 (conj each-vec options)
                                   4 (update-in each-vec [3] (fn [each-opts]
                                                               (i/expect-arg each-opts map?
                                                                 ["Expected an option map for defkey, but found"
                                                                  (pr-str each-opts)])
                                                               (merge options each-opts)))
                                   (i/illegal-arg ["Expected 1, 2, 3 or 4 elements as arguments for defkey, but found"
                                                   (pr-str each-vec)]))]))
                (map (fn [[each-sym each-vec]]
                       `(def ~each-sym (make-key ~@each-vec)))))]
    `(do ~@pairs)))


;; ===== key lookup and value destructuring =====


(defmacro letval
  "Like let, except in which the left hand side is a destructuring map, right hand side is the argument to key finder.
  Beside symbols, the destructuring map optionally supports :defs (symbols bound to key finders) and :as keys.
  Example:
  (letval [{:defs [foo bar] ; foo, bar are key finders
            baz baz-key     ; baz-key is a key finder
            :as m} {:foo 10 :bar 20 :baz 30}]
    ;; foo, bar and baz are now bound to values looked up in the map
    ...)"
  [bindings & body]
  (i/expect-arg bindings vector? ["Expected a binding vector, but found" (pr-str bindings)])
  (i/expect-arg (count bindings) even? ["Expected even number of binding forms, but found" (pr-str bindings)])
  (let [forms (->> (partition 2 bindings)
                (mapcat
                  (fn bind [[lhs rhs]]
                    (i/expect-arg lhs map? ["Expected a map to destructure keys, but found" (pr-str lhs)])
                    (let [local (gensym)]  ; bind it to a gensym, in case we have to handle :as
                      (->> (seq lhs)
                        (mapcat
                          (fn [[sym k]]
                            (cond
                              (symbol? sym) [sym `(~k ~local)]
                              (map? sym)    (bind [sym `(~k ~local)])
                              (= sym :as)   [k local]
                              (= sym :defs) (->> k
                                              (mapcat (fn [s]
                                                        (if (symbol? s)
                                                          (let [u-sym (symbol (last (string/split (str s) #"/")))]
                                                            [u-sym `(~s ~local)])  ; use unqualified symbol to bind
                                                          (i/illegal-arg ["Expected a symbol under :defs, but found"
                                                                          (pr-str s)])))))
                              :otherwise    (i/illegal-arg ["Expected a symbol, or a map or :as/:defs, but found"
                                                            (pr-str sym)]))))
                        (into [local rhs])))))
                vec)]
    `(let [~@forms]
       ~@body)))


;; ===== value parsers =====


(defn str->bool
  [the-key ^String x]
  (if (#{"true" "false"} (string/lower-case x))
    (Boolean/valueOf x)
    (i/illegal-arg [(format "Expected 'true' or 'false' for key %s but found" (pr-str the-key))
                    (pr-str x)])))


(defn str->int
  [the-key ^String x]
  (try
    (Integer/parseInt x)
    (catch NumberFormatException _
      (i/illegal-arg [(format "Expected integer (number) string for key %s but found" (pr-str the-key))
                            (pr-str x)]))))


(defn str->long
  [the-key ^String x]
  (try
    (Long/parseLong x)
    (catch NumberFormatException _
      (i/illegal-arg [(format "Expected long (number) string for key %s but found" (pr-str the-key))
                            (pr-str x)]))))


(defn str->float
  [the-key ^String x]
  (try
    (Float/parseFloat x)
    (catch NumberFormatException _
      (i/illegal-arg [(format "Expected float (number) string for key %s but found" (pr-str the-key))
                            (pr-str x)]))))


(defn str->double
  [the-key ^String x]
  (try
    (Double/parseDouble x)
    (catch NumberFormatException _
      (i/illegal-arg [(format "Expected double (number) string for key %s but found" (pr-str the-key))
                            (pr-str x)]))))


(defn str->var
  "Given a fully qualified var name (eg. 'com.example.foo/bar'), resolve the var and return it."
  [the-key fq-var-name]
  (let [[ns-name var-name] (-> (string/split (str fq-var-name) #"/")
                             (i/expect-arg #(= 2 (count %))  ["Var name" fq-var-name "must be in 'ns/var' format"])
                             (i/expect-arg #(first (seq %))  ["Namespace is empty in" fq-var-name])
                             (i/expect-arg #(second (seq %)) ["Value is empty in" fq-var-name]))
        ;; the following step is required for `find-ns` to work
        _       (try
                  (require (symbol ns-name))
                  (catch FileNotFoundException _
                    (i/illegal-arg (format "Cannot find ns '%s' for key %s=%s"
                                     ns-name (pr-str the-key) (str fq-var-name)))))
        the-ns  (-> (find-ns (symbol ns-name))
                  (i/expect-arg identity (format "Cannot find ns '%s' for key %s=%s"
                                           ns-name (pr-str the-key) (str fq-var-name))))
        the-var (-> (ns-resolve the-ns (symbol var-name))
                  (i/expect-arg identity (format "Cannot find var '%s/%s' for key %s=%s"
                                           ns-name var-name (pr-str the-key) (str fq-var-name))))]
    ;; return the var without deref'ing
    the-var))
