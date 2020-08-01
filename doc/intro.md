# Introduction to keypin

* [Requiring namespace](#requiring-namespace)
* [Defining keys](#defining-keys)
   * [Simple key finder](#simple-key-finder)
   * [Complex key finders](#complex-key-finders)
   * [Accessing the key](#accessing-the-key)
   * [Key finder meta data](#key-finder-meta-data)
   * [Specifying a key/value source](#specifying-a-keyvalue-source)
   * [Defining key-path lookup](#defining-key-path-lookup)
* [Reading config files](#reading-config-files)
   * [EDN config files](#edn-config-files)
   * [Chained config files](#chained-config-files)
* [Dynamic config stores](#dynamic-config-stores)
* [Caching config stores](#caching-config-stores)


## Requiring namespace

```clojure
(:require
  [keypin.core :refer [defkey letval] :as k]
  [keypin.store :as s]
  [keypin.util :as u])
```


## Defining keys

You define key finders with some meta data as follows:


### Simple key finder

```clojure
(defkey foo [:foo])

;; lookup
(foo {:foo 20 :bar 30})  ; returns 20
(foo {:bar 30 :baz 40})  ; throws IllegalArgumentException

(letval [{x foo} {:foo 20 :bar 30}]
  x)  ; returns 20
```


### Complex key finders

```clojure
;; key with constraints
(defkey
  ip   [:ip]
  port [:port #(< 1023 % 65535) "Port number" {:parser u/str->int}])

;; lookup
(port {:ip "0.0.0.0" :port "5000"})  ; returns 5000
(port {:ip "0.0.0.0"})               ; throws IllegalArgumentException

;; key with default value
(defkey port-optional [:port #(< 1023 % 65535) "Port number" {:parser u/str->int
                                                              :default 3000}])

;; lookup
(port-optional {:ip "0.0.0.0" :port "5000"})  ; returns 5000
(port-optional {:ip "0.0.0.0"})               ; returns 3000

;; lookup form
(letval [{:defs [ip port-optional] :as m} {:ip "0.0.0.0"}]
  [ip port-optional m])  ; returns ["0.0.0.0" 3000 {:ip "0.0.0.0"}]
```

Another example of multiple key finders:

```clojure
(defkey
  ip-address    [:ip string? "Server IP"]
  port-optional [:port #(< 1023 % 65535) "Server port" {:parser u/str->int
                                                        :default 3000
                                                        ;; environment variable "PORT" overrides config
                                                        :envvar "PORT"
                                                        ;; system property "server.port" overrides config
                                                        :sysprop "server.port"}]
  username      [:username string? "User name"]
  password      [:password string? "User password"])

;; lookup
(ip-address {:ip "12.34.56.78" :username "testbot" :password "s3cr3t"})
```


### Accessing the key

```clojure
(defkey port [:port #(< 1023 % 65535) "Port number" {:parser u/str->int}])

;; access the key
(key port)  ; returns :port
```


### Key finder meta data

Defining property finders is quite straightforward. The argument vector format is either of the following:

```clojure
[key]
[key options]
[key validator description]
[key validator description options]
```

| Argument      | Description                           | Default            |
|---------------|---------------------------------------|--------------------|
| `key`         | the key to look up                    | No default         |
| `validator`   | predicate fn to validate parsed value | `u/any?`           |
| `description` | key description                       | `"No description"` |
| `options`     | option map with following keys        | `{}`               |
|               | `:pred` (validator, in arity-2 only)  | `u/any?`           |
|               | `:desc` (description, in arity-2 only)| `"No description"` |
|               | `:parser`  (value parser fn, arity-2) | Identity parser    |
|               | `:default` (value when key not found) | No default         |
|               | `:lookup`  (lookup function)          | `k/lookup-key`     |
|               | `:envvar`  (environment var to lookup)| No default         |
|               | `:sysprop` (system prop to lookup)    | No default         |
|               | `:source`  (key/value source to deref)| No default         |

**Note:** Resolution order for all lookups is `envvar` (when defined), `sysprop` (when defined), lookup map.
Responsibility of parsing string value from environment variable or system property lies with the parser.


### Specifying a key/value source

You may specify a key/value source of a reference type when defining keys, which could be implicitly used to retrieve
values.

```clojure
;; source must be a reference type (atom, promise, delay, ref etc.)
(def config-holder (atom {:foo 10 :bar :something}))

(defkey
  {:source config-holder}
  foo [:foo integer? "an int"]
  bar [:bar keyword? "a keyword"])

;; retrieve values of the key definitions
(foo)
(val foo)   ; same as above
(deref foo) ; same as above
@foo        ; same as above
```

### Defining key-path lookup

```clojure
(defkey
  {:lookup k/lookup-keypath}
  app-name  [[:app :name]     string?      "Expected string"]
  pool-size [[:pool :size]    #(< 0 % 100) "Thread-pool size (1-99)" {:parser u/str->int}]
  trace?    [["enable.trace"] u/bool?      "Flag: Enable runtime tracing?" {:parser u/str->bool
                                                                            :default true}])

;; lookup
(app-name config)  ; for config={:app {:name "the name"}} it returns "the name"
```


## Reading config files

Given a simple property file:

```properties
service.name=login-service
service.version=v1
uri.prefix=${service.name}-${service.version}
```

or

a simple EDN file:

```edn
{"service.name" "login-service"
 "service.version" "v1"
 "uri.prefix" "${service.name}-${service.version}"}
```

A config file may be read simply like this:

```clojure
(def ^java.util.Map config (k/read-config ["config/my-conf.properties"]))
;; or
(def ^java.util.Map config (k/read-config ["config/my-conf.edn"]))
```

### EDN config files

The config values read from the EDN files may not be Clojure data structures
(e.g. vector, set etc.) It can be easily fixed as follows:

```clojure
(def config (-> ["config/my-conf.edn"]
              k/read-config
              u/clojurize-data))
```

Keypin also supports variable substitution for EDN config files, which must be
explicitly used. Consider the following config file:

```clojure
{"foo" 10
 :bar  20
 "baz" [$foo :$bar]}
```

This config file may be read as follows to utilize variable substitution:

```clojure
(def config (-> ["config/my-conf.edn"]
              k/read-config
              u/clojurize-data
              u/clojurize-subst))
```

Here, the value of `"baz"` becomes `[10 20]` by substituting the values of `$foo`
(looks up key `"foo"`) and `:$bar` (looks up key `:bar`).


### Chained config files

Chained config files need to mention a parent key:

In parent file `base.properties`

```properties
server.bind.ip=0.0.0.0
server.bind.port=3000
server.queue.size=300
service.name=login-service
service.version=v1
uri.prefix=${service.name}-${service.version}
log.level=error
```

In parent file `dev.properties`

```properties
log.level=debug
ring.error.stacktrace=true
```

In config file `config/my-conf.properties` (see the parent key `parent-config`):

```properties
parent-config=base.properties, dev.properties
service.version=v2wip
log.level=trace
```

Now, read it:

```clojure
;; the default value for :parent-key is "parent.filenames"
(def ^java.util.Map config (k/read-config ["config/my-conf.properties"]
                             {:parent-key "parent-config"}))
```

The files `base.properties` and `dev.properties` will  be looked up in file system first, then in classpath.


## Dynamic config stores

Keypin supports dynamic config stores, wherein a fetch function is used to fetch and re-fetch the
config map.

```
(defn fetch-config-from-redis
  "Fetch config from Redis and return as map."
  [old-config]
  ...)

;; create a dynamic config store that refreshes every second
;; (see keypin.store/make-dynamic-store for details)
;;
(def redis-config-store (s/make-dynamic-store
                          fetch-config-from-redis))

(k-foo redis-config-store)  ; lookup key definition on the dynamic store
```

Whenever the underlying config changes, the `k-foo` call would return the latest value.


## Caching config stores

Whenever you use a key definition to lookup a key in a confg store, it is parsed and validated every time before returning the value. This unnecessary repetition is easily avoided using caching stores.

```
(def config (k/read-config ["config/my-conf.edn"]))  ; regular (static) config store
(def caching-config (s/make-caching-store config))   ; caching config

(k-foo caching-config)  ; read always from the caching config (faster)
```

You may create a caching store out of both static and dynamic stores.
