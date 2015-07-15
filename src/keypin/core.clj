(ns keypin.core
  (:require
    [clojure.string :as string]
    [keypin.internal :as i])
  (:import
    [java.io FileNotFoundException]
    [java.util Properties]
    [keypin Logger PropertyFile]))


;; ===== Key attributes primitive =====


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


(defmacro defkey
  "Define a generic key that can be looked up in a map."
  ([the-sym the-name]
    (i/expect-arg the-sym  symbol? ["Expected a symbol to define var, but found " (pr-str the-sym)])
    `(def ~the-sym
       (->KeyAttributes
         ~the-name identity "No description"
         i/identity-parser
         false
         nil
         i/lookup-key)))
  ([the-sym the-name validator]
    (i/expect-arg the-sym  symbol? ["Expected a symbol to define var, but found " (pr-str the-sym)])
    `(def ~the-sym
       (->KeyAttributes
         ~the-name (or ~validator identity) "No description"
         i/identity-parser
         false
         nil
         i/lookup-key)))
  ([the-sym the-name validator description]
    (i/expect-arg the-sym  symbol? ["Expected a symbol to define var, but found " (pr-str the-sym)])
    `(def ~the-sym
       (->KeyAttributes
         ~the-name (or ~validator identity) ~description
         i/identity-parser
         false
         nil
         i/lookup-key)))
  ([the-sym the-name validator description value-parser]
    (i/expect-arg the-sym  symbol? ["Expected a symbol to define var, but found " (pr-str the-sym)])
    `(def ~the-sym
       (->KeyAttributes
         ~the-name (or ~validator identity) ~description
         (or ~value-parser i/identity-parser)
         false
         nil
         i/lookup-key)))
  ([the-sym the-name validator description value-parser default-value]
    (i/expect-arg the-sym  symbol? ["Expected a symbol to define var, but found " (pr-str the-sym)])
    `(def ~the-sym
       (->KeyAttributes
         ~the-name (or ~validator identity) ~description
         (or ~value-parser i/identity-parser)
         true
         ~default-value
         i/lookup-key))))


(defmacro defmanykeys
  "Define many keys at once using argument vectors. See `defkey` for details."
  [the-sym arg-vec & more]
  (i/expect-arg (count more) even? ["Expected even number of var args, but found " more])
  `(i/defmany defkey ~the-sym ~arg-vec ~@more))


;; ===== properties files =====


(defmacro defprop
  "Define a property finder (key) that can be looked up in a java.util.Properties instance."
  ([the-sym the-name]
    (i/expect-arg the-sym  symbol? ["Expected a symbol to define var, but found " (pr-str the-sym)])
    (i/expect-arg the-name string? ["Expected a string property name, but found " (pr-str the-name)])
    `(def ~the-sym
       (->KeyAttributes
         ~the-name identity "No description"
         i/identity-parser
         false
         nil
         i/lookup-property)))
  ([the-sym the-name validator]
    (i/expect-arg the-sym  symbol? ["Expected a symbol to define var, but found " (pr-str the-sym)])
    (i/expect-arg the-name string? ["Expected a string property name, but found " (pr-str the-name)])
    `(def ~the-sym
       (->KeyAttributes
         ~the-name (or ~validator identity) "No description"
         i/identity-parser
         false
         nil
         i/lookup-property)))
  ([the-sym the-name validator description]
    (i/expect-arg the-sym  symbol? ["Expected a symbol to define var, but found " (pr-str the-sym)])
    (i/expect-arg the-name string? ["Expected a string property name, but found " (pr-str the-name)])
    `(def ~the-sym
       (->KeyAttributes
         ~the-name (or ~validator identity) ~description
         i/identity-parser
         false
         nil
         i/lookup-property)))
  ([the-sym the-name validator description property-parser]
    (i/expect-arg the-sym  symbol? ["Expected a symbol to define var, but found " (pr-str the-sym)])
    (i/expect-arg the-name string? ["Expected a string property name, but found " (pr-str the-name)])
    `(def ~the-sym
       (->KeyAttributes
         ~the-name (or ~validator identity) ~description
         (or ~property-parser i/identity-parser)
         false
         nil
         i/lookup-property)))
  ([the-sym the-name validator description property-parser default-value]
    (i/expect-arg the-sym  symbol? ["Expected a symbol to define var, but found " (pr-str the-sym)])
    (i/expect-arg the-name string? ["Expected a string property name, but found " (pr-str the-name)])
    `(def ~the-sym
       (->KeyAttributes
         ~the-name (or ~validator identity) ~description
         (or ~property-parser i/identity-parser)
         true
         ~default-value
         i/lookup-property))))


(defmacro defmanyprops
  "Define many property keys at once using argument vectors. See `defprop` for details."
  [the-sym arg-vec & more]
  (i/expect-arg (count more) even? ["Expected even number of var args, but found " more])
  `(i/defmany defprop ~the-sym ~arg-vec ~@more))


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


;; ===== value parsers =====


(defn bool?
  "Return is x is a boolean, false otherwise."
  [x]
  (instance? Boolean x))


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


(defn deref?
  "Wrap a predicate argument such that it derefs the argument before applying the predicate."
  [pred]
  (fn [x]
    (pred (deref x))))
