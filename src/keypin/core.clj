;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns keypin.core
  "Public API for the core functionality."
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
  "Look up a key in an associative data structure - a map or something that implements `clojure.lang.Associative`."
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
  "Look up a key path in an associative data structure - a map or something that implements `clojure.lang.Associative`."
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


(defn make-config-io
  "Given the following arguments, create a config I/O codec.

  | Argument       | Description |
  |----------------|-------------|
  |`name`          |A string name for the codec, e.g. \"EDN\", \"YAML\" etc.               |
  |`file-extns`    |A vector of file extensions, e.g. [\".edn\"], [\".yaml\" \".yml\"] etc.|
  |`string-decoder`|A `(fn [string-payload]) -> map` to decode a string payload into a map |
  |`string-encoder`|A `(fn [map-data]) -> string-payload` to encode map data as a string   |

  See: [[edn-file-io]]"
  [name file-extns string-decoder string-encoder]
  (let [config-str (fn [config escape?] (->> config
                                          pr-str
                                          (u/clojurize-data (if escape?
                                                              (fn [x] (if (string? x)
                                                                        (Config/escape (Config/escape x))
                                                                        x))
                                                              identity))))
        low-fextns (mapv string/lower-case file-extns)
        ext-match? (fn [filename]
                     (let [lcase-filename (string/lower-case filename)]
                       (some #(.endsWith lcase-filename %) low-fextns)))]
    (reify ConfigIO
      (getName     [this]             name)
      (canRead     [this filename]    (ext-match? filename))
      (readConfig  [this in]          (let [m (string-decoder (slurp in))]
                                        (if (map? m)
                                          m
                                          (throw (->> (pr-str (class m))
                                                   (format "Expected %s content to be a map, but found %s" name)
                                                   IllegalArgumentException.)))))
      (canWrite    [this filename]    (ext-match? filename))
      (^void
       writeConfig [this
                    ^OutputStream out
                    ^Map config
                    ^boolean escape?] (spit out (config-str config escape?)))
      (^void
       writeConfig [this ^Writer out
                    ^Map config
                    ^boolean escape?] (spit out (config-str config escape?))))))


(def property-file-io
  "Reader/writer for properties files."
  (PropertyConfigIO.))


(def edn-file-io
  "Reader/writer for EDN files."
  (make-config-io "EDN" [".edn"] edn/read-string pr-str))


(def default-config-io-codecs
  "Default collection of `keypin.ConfigIO` codecs.

  See: [[read-config]], [[write-config]]"
  [property-file-io edn-file-io])


(defn realize-config
  "Realize config by applying variable substitution, if any.

  ### Options

  | Kwarg          | Type   | Description                 | Default                 |
  |----------------|--------|-----------------------------|-------------------------|
  |`:logger`       | object | instance of `keypin.Logger` | prints to `*err*`       |
  |`:config-mapper`| object | instance of `keypin.Mapper` | `keypin.Mapper/DEFAULT` |"
  ([config]
    (realize-config config {}))
  ([config {:keys [logger config-mapper]
            :or {logger        u/default-logger
                 config-mapper Mapper/DEFAULT}
            :as options}]
    (Config/realize config config-mapper logger)))


(defn read-config
  "Read config file(s) returning a `java.util.Map` instance.

  ### Options

  | Kwarg           | Type     | Description                                  | Default                         |
  |-----------------|----------|----------------------------------------------|---------------------------------|
  |`:parent-key`    | string   | key to identify the parent config filenames  | `\"parent.filenames\"`          |
  |`:logger`        | object   | instance of `keypin.Logger`                  | prints to `*err*`               |
  |`:config-readers`| list/vec | collection of `keypin.ConfigIO` instances    | for `.properties`, `.edn` files |
  |`:media-readers` | list/vec | collection of `keypin.MediaReader` instances | for filesystem and classpath    |
  |`:realize?`      | boolean  | whether realize template variables in string | `true`                          |"
  (^Map [config-filenames]
    (read-config config-filenames {}))
  (^Map [config-filenames {:keys [^String parent-key logger config-readers media-readers realize?]
                           :or {parent-key     "parent.filenames"
                                logger         u/default-logger
                                config-readers default-config-io-codecs
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

  ### Options

  | Kwarg           | Type     | Description                               | Default                         |
  |-----------------|----------|-------------------------------------------|---------------------------------|
  |`:logger`        | object   | instance of `keypin.Logger`               | prints to `*err*`               |
  |`:config-writers`| list/vec | collection of `keypin.ConfigIO` instances | for `.properties`, `.edn` files |
  |`:escape?`       | boolean  | whether escape values when writing        | `true`                          |"
  ([config-filename config]
    (write-config config-filename config {}))
  ([config-filename config {:keys [logger config-writers escape?]
                            :or {logger         u/default-logger
                                 config-writers default-config-io-codecs
                                 escape?        true}
                            :as options}]
    (Config/writeConfig config-writers config-filename config escape? logger)))


;; ===== key definition =====


(defn make-key
  "Create a key that can be looked up in a config store (`keypin.type/IStore`, `java.util.Map`, `java.util.Properties`
  or map/vector) instance.

  ### Options

  | Kwarg    | Description                 |
  |----------|-----------------------------|
  |`:the-key`|(Required) Key to be used for looking up value                                |
  |`:pred`   |Validator function `(fn [value]) -> boolean` - default fn just returns true   |
  |`:desc`   |Description string for the config - default: `\"No description\"`             |
  |`:lookup` |Function to look the key up (details below) - default: [[lookup-key]] - ordinary key look up |
  |`:parser` |The value parser fn `(fn [key raw-value]) -> parsed-value`, e.g. [[keypin.util/str->long]]   |
  |`:default`|Default value when key is not found (subject to validator, unspecified => no default value)  |
  |`:sysprop`|System property name that can override the config value (before parsing)                     |
  |`:envvar` |Environment variable that can override the config value and system property (before parsing) |
  |`:source` |Source or container (of reference type, e.g. atom/agent/promise etc.) of key/value pairs     |

  ### Lookup function

  ```
  (fn [store
       key
       validator
       description
       value-parser
       default-value?
       default-value
       not-found-handler]) -> value

  ;; validator is a predicate function: (fn [parsed-value]) -> boolean
  ;; not-found-handler is (fn [not-found-message]), called when the store does not have the key
  ```

  See: [[lookup-key]], [[lookup-keypath]]"
  [{:keys [the-key  ; required
           pred     ; validator
           desc     ; description
           lookup
           parser
           default
           sysprop
           envvar
           source]
    :or {pred   i/return-true
         desc   "No description"
         lookup lookup-key
         parser i/identity-parser}
    :as options}]
  (let [validator   pred
        description desc]
    (i/expected some?   "Non-nil key for lookup (option :the-key)" the-key)
    (i/expected fn?     "Validator function under (option :pred)"  validator)
    (i/expected string? "Config description string (option :desc)" description)
    (i/expected fn?     "Lookup function (option :lookup)"         lookup)
    (i/expected fn?     "Parser function (option :parser)"         parser)
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
          source)))))


(defmacro defkey
  "Define one or more keys as `defn` vars using argument vectors, which could be invoked as `(keydef store)` to
  fetch the value of the key. Every argument vector must have one of the following arities:

  ```
  [key]
  [key options]
  [key validator description]
  [key validator description options]
  ```

  ### Options

  | Kwarg       | Description |
  |-------------|-------------|
  |`:pred`      |Validator function `(fn [value]) -> boolean` - default fn just returns `true`  |
  |`:desc`      |Description string for the config - default: `\"No description\"`              |
  |`:lookup`    |Function to look the key up (details below) - default: [[lookup-key]] - ordinary key look up |
  |`:parser`    |The value parser fn `(fn [key raw-value]) -> parsed-value`, e.g. [[keypin.util/str->long]]   |
  |`:default`   |Default value when key is not found (subject to validator, unspecified => no default value)  |
  |`:sysprop`   |System property name that can override the config value (before parsing)                     |
  |`:envvar`    |Environment variable that can override the config value and system property (before parsing) |
  |`:source`    |Source or container (of reference type, e.g. atom/agent/promise etc.) of key/value pairs     |
  |`:pre-xform` |Middleware function `(fn [option-map]) -> option-map` used before key definition is created  |
  |`:post-xform`|Middleware function `(fn [keypin.type.KeyAttributes]) -> keypin.type.KeyAttributes`          |

  The `validator` is a predicate `(fn [parsed-value]) -> boolean` that returns `true` for valid values,
  `false` otherwise.

  First argument to `defkey` can optionally be a base option-map for all argument vectors.

  ### Examples

  ```
  (defkey
    ip   [:ip-address]
    port [:port #(< 1023 % 65535) \"Server port\" {:parser keypin.util/str->int :default 3000}])

  (port {:port \"3009\"})  ; returns 3009
  (port {})              ; returns 3000

  (defkey
    {:lookup lookup-key}
    ip   [\"server.ip.address\"]
    port [\"server.port\" #(< 1023 % 65535) \"Server port\" {:parser keypin.util/str->int :default 3000}])
  ```

  See: [[make-key]]"
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
                                   1 (conj each-vec i/return-true "No description" options)
                                   2 (let [{:keys [pred desc]
                                            :or {pred i/return-true
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
                (map (fn [[each-sym [the-key validator descrip options :as each-vec]]]
                       (let [;; descrip (nth each-vec 2 "No description")
                             ;; options (nth each-vec 3 {})  ; 4th element is option map
                             arities (if (:source options)
                                       ''([] [config-map] [config-map not-found])
                                       ''([config-map] [config-map not-found]))
                             meta-fn (partial merge {:arglists arities :doc descrip})
                             def-sym (vary-meta each-sym meta-fn)
                             arg-map (conj (get each-vec 3 {})
                                       {:the-key (get each-vec 0)
                                        :pred    (get each-vec 1)
                                        :desc    (get each-vec 2)})
                             pre-xform (:pre-xform options identity)
                             post-xform (:post-xform options identity)]
                         `(def ~def-sym (~post-xform (make-key (~pre-xform ~arg-map))))))))]
    `(do ~@pairs)))


;; ===== key lookup and value destructuring =====


(defmacro letval
  "Like `let`, except in which the left hand side is a destructuring map, right hand side is the argument to key finder.
  Beside symbols, the destructuring map optionally supports `:defs` (symbols bound to key finders) and `:as` keys.

  ### Example

  ```
  (letval [{:defs [foo bar] ; foo, bar are key finders
            baz baz-key     ; baz-key is a key finder
            :as m} {:foo 10 :bar 20 :baz 30}]
    ;; foo, bar and baz are now bound to values looked up in the map
    ...)
  ```"
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
