;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns keypin.core
  (:require
    [clojure.edn     :as edn]
    [clojure.string  :as string]
    [keypin.internal :as i]
    [keypin.type     :as t]
    [keypin.util     :as u])
  (:import
    [clojure.lang Associative IDeref]
    [java.io OutputStream Writer]
    [java.util Map Properties]
    [keypin Config ConfigIO Logger Mapper PropertyConfigIO]))


;; ===== lookup functions ====


(defn lookup-key
  "Look up a key in an associative data structure - Java map or something that implements clojure.lang.Associative."
  [the-map the-key validator description value-parser default-value? default-value not-found]
  (when-not (or (instance? Map the-map)
              (instance? Associative the-map))
    (i/illegal-arg (format "Key %s looked up in a non map (or clojure.lang.Associative) object: %s"
                     (pr-str the-key) the-map)))
  (let [value (if (contains? the-map the-key)
                (->> (get the-map the-key)
                  (value-parser the-key))
                (if default-value?
                  default-value
                  (i/illegal-arg (not-found
                                   (str "No default value is defined for non-existent key " (pr-str the-key))))))]
    (i/expect-arg value validator (format "Invalid value for key %s (description: '%s'): %s"
                                    (pr-str the-key) description (pr-str value)))))


(defn lookup-keypath
  "Look up a key path in an associative data structure - Java map or something that implements clojure.lang.Associative."
  [the-map ks validator description value-parser default-value? default-value not-found]
  (let [value (loop [data the-map
                     path ks]
                (when-not (or (instance? Map data)
                            (instance? Associative data))
                  (i/illegal-arg (format "Key path %s looked up in a non map (or clojure.lang.Associative) object: %s"
                                   (pr-str path) (pr-str data))))
                (let [k (first path)]
                  (if-not (contains? data k)
                    (if default-value?   ; has a default value?
                      default-value
                      (i/illegal-arg (not-found
                                       (str "No default value is defined for non-existent key path " (pr-str ks)))))
                    (if-not (next path)  ; last key in key path?
                      (->> (get data k)
                        (value-parser ks))
                      (recur (get data k) (rest path))))))]
    (i/expect-arg value validator (format "Invalid value for key path %s (description: '%s'): %s"
                                    (pr-str ks) description (pr-str value)))))


;; ===== reading config files =====


(def property-file-io
  "Reader/writer for properties files."
  (PropertyConfigIO.))


(def edn-file-io
  "Reader/writer for EDN files."
  (let [config-str (fn [config escape?] (->> config
                                          pr-str
                                          (u/clojurize-data (if escape?
                                                              (fn [x] (if (string? x)
                                                                        (Config/escape (Config/escape x))
                                                                        x))
                                                              identity))))]
    (reify ConfigIO
      (getName     [this]             "EDN")
      (canRead     [this filename]    (.endsWith (string/lower-case filename) ".edn"))
      (readConfig  [this in]          (let [m (edn/read-string (slurp in))]
                                        (if (map? m)
                                          m
                                          (throw (->> (pr-str (class m))
                                                   (str "Expected EDN content to be a map, but found ")
                                                   IllegalArgumentException.)))))
      (canWrite    [this filename]    (.endsWith (string/lower-case filename) ".edn"))
      (^void
       writeConfig [this
                    ^OutputStream out
                    ^Map config
                    ^boolean escape?] (spit out (config-str config escape?)))
      (^void
       writeConfig [this ^Writer out
                    ^Map config
                    ^boolean escape?] (spit out (config-str config escape?))))))


(defn realize-config
  "Realize config by applying variable substitution, if any.
  Options:
  :logger        (object) instance of keypin.Logger, defaults to printing to *err*
  :config-mapper (object) instance of keypin.Mapper, default: keypin.Mapper/DEFAULT"
  ([config]
    (realize-config config {}))
  ([config {:keys [logger config-mapper]
            :or {logger        u/default-logger
                 config-mapper Mapper/DEFAULT}
            :as options}]
    (Config/realize config config-mapper logger)))


(defn read-config
  "Read config file(s) returning a java.util.Map instance.
  Options:
  :parent-key     (string)   key to identify the parent filenames having K/V pairs, default: \"parent.filenames\"
  :logger         (object)   instance of keypin.Logger, defaults to printing to *err*
  :config-readers (list/vec) collection of keypin.ConfigIO instances, default: for Properties and EDN files
  :media-readers  (list/vec) collection of keypin.MediaReader instances, default: for Filesystem and Classpath
  :realize?       (boolean)  whether realize the template variables in the string, default: true"
  (^Map [config-filenames]
    (read-config config-filenames {}))
  (^Map [config-filenames {:keys [^String parent-key logger config-readers media-readers realize?]
                           :or {parent-key     "parent.filenames"
                                logger         u/default-logger
                                config-readers [property-file-io edn-file-io]
                                realize?       true}
                           :as options}]
    (let [mediar (or media-readers [(Config/createFilesystemReader logger)
                                    (Config/createClasspathReader  logger)])
          config (if parent-key
                   (Config/readCascadingConfig config-readers mediar config-filenames parent-key logger)
                   (reduce (fn [m filename]
                             (merge m (Config/readConfig config-readers mediar filename logger)))
                     {} config-filenames))]
      (if realize?
        (realize-config config options)
        config))))


(defn write-config
  "Write config to a specified file.
  Options:
  :logger         (object)   instance of keypin.Logger, defaults to printing to *err*
  :config-writers (list/vec) collection of keypin.ConfigIO instances, default: for Properties and EDN files
  :escape?        (boolean)  whether escape values when writing, default: true"
  ([config-filename config]
    (write-config config-filename config {}))
  ([config-filename config {:keys [logger config-writers escape?]
                            :or {logger         u/default-logger
                                 config-writers [property-file-io edn-file-io]
                                 escape?        true}
                            :as options}]
    (Config/writeConfig config-writers config-filename config escape? logger)))


;; ===== key definition =====


(defn make-key
  "Create a key that can be looked up in a config store (keypin.type/IStore, java.util.Map/Properties or map/vector)
  instance. The following optional keys are supported:
  :lookup  - The function to look the key up:
             (fn [the-map the-key validator description value-parser default-value? default-value
                  (fn not-found-fn [not-found-message])])
             default: ordinary key look up
  :parser  - The value parser function (args: key, value)
  :default - Default value to return if key is not found
  :sysprop - System property name that can override the config value (before parsing)
  :envvar  - Environment variable that can override the config value and system property (before parsing)
  :source  - Source or container (of reference type, e.g. atom/promise etc.) of key/value pairs"
  [the-key validator description {:keys [lookup parser default sysprop envvar source]
                                  :or {lookup lookup-key
                                       parser i/identity-parser}
                                  :as options}]
  (t/->KeyAttributes
    the-key validator description parser
    (if   (contains? options :default) true false)
    (when (contains? options :default) default)
    (fn [the-map the-key validator description value-parser default-value? default-value]
      (cond
        (and envvar  (System/getenv envvar))       (value-parser the-key (System/getenv envvar))
        (and sysprop (System/getProperty sysprop)) (value-parser the-key (System/getProperty sysprop))
        :otherwise (lookup the-map the-key validator description value-parser default-value? default-value
                     (if (or envvar sysprop)
                       (fn [message] (str
                                       (when-not envvar  (format "Environment variable '%s' is not defined. " envvar))
                                       (when-not sysprop (format "System property '%s' is not defined. " sysprop))
                                       message))
                       identity))))
    (if (nil? source)
      nil
      (do
        (i/expected #(instance? IDeref %) "a key/value-source of type clojure.lang.IDeref (atom/promise etc.)" source)
        source))))


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
    {:lookup lookup-key}
    ip   [\"server.ip.address\"]
    port [\"server.port\" #(< 1023 % 65535) \"Server port\" {:parser str->int :default 3000}])
  See: make-key"
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
                                   1 (conj each-vec (constantly true) "No description" options)
                                   2 (let [{:keys [pred desc]
                                            :or {pred (constantly true)
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
                       (let [descrip (nth each-vec 2 "No description")
                             options (nth each-vec 3 {})  ; 4th element is option map
                             arities (if (:source options)
                                       ''([] [config-map] [config-map not-found])
                                       ''([config-map] [config-map not-found]))
                             meta-fn (partial merge {:arglists arities :doc descrip})
                             def-sym (vary-meta each-sym meta-fn)]
                         `(def ~def-sym (make-key ~@each-vec))))))]
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
