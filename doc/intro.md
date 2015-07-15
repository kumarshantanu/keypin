# Introduction to keypin


## Requiring namespace

```clojure
(require '[keypin.core :refer [defkey defmanykeys defprop defmanyprops] :as k])
```


## Defining keys

You define key finders with some meta data as follows:


### Simple key finder

```clojure
(defkey foo :foo)

;; lookup
(foo {:foo 20 :bar 30})  ; returns 20
(foo {:bar 30 :baz 40})  ; throws IllegalArgumentException
```


### Complex key finders

```clojure
;; key with constraints
(defkey port :port #(< 1023 % 65535) "Port number" k/str->int)

;; lookup
(port {:ip "0.0.0.0" :port "5000"})  ; returns 5000
(port {:ip "0.0.0.0"})               ; throws IllegalArgumentException

;; key with default value
(defkey port-optional :port #(< 1023 % 65535) "Port number" k/str->int 3000)

;; lookup
(port-optional {:ip "0.0.0.0" :port "5000"})  ; returns 5000
(port-optional {:ip "0.0.0.0"})               ; returns 3000
```


### Accessing the key

```clojure
(defkey port :port #(< 1023 % 65535) "Port number" k/str->int)

;; access the key
(key port)  ; returns :port
```


### Key finder meta data

Defining property finders is quite straightforward. The argument format is:
`key` `validator` `description` `value-parser` `default-value`

Only `key` is required, rest of the positional arguments are optional.

| Argument         | Description                           | Default           |
|------------------|---------------------------------------|-------------------|
| `key` (required) | the key to look up                    | No default        |
| `validator`      | predicate fn to validate parsed value | `identity`        |
| `description`    | key description                       | "No description"  |
| `value-parser`   | value parser function, takes two args | `identity` parser |
| `default-value`  | default value for unspecified values  | No default        |


### Defining multiple keys at once

```clojure
(defmanykeys
  ip-address    [:ip string? "Server IP"]
  port-optional [:port #(< 1023 % 65535) "Server port" k/str->int 3000]
  username      [:username string? "User name"]
  password      [:password string? "User password"])

;; lookup
(ip-address {:ip "12.34.56.78" :username "testbot" :password "s3cr3t"})
```


### Defining property lookup

You can define property lookup usinf `defprop` (very much like `defkey`):

```clojure
(defprop app-name  "app.name"     string?      "Expected string")
(defprop pool-size "pool.size"    #(< 0 % 100) "Thread-pool size (1-99)" k/str->int)
(defprop trace?    "enable.trace" k/bool?      "Flag: Whether enable runtime tracing?" k/str->bool true)

;; Alternatively, they can be defined as follows:
(defmanyprops
  app-name  ["app.name"     string?      "Expected string"]
  pool-size ["pool.size"    #(< 0 % 100) "Thread-pool size (1-99)" k/str->int]
  trace?    ["enable.trace" k/bool?      "Flag: Whether enable runtime tracing?" k/str->bool true])

;; lookup
(app-name ^java.util.Properties props)  ; returns whatever is defined in the properties file
```


## Reading property files

Given a simple property file:

```properties
service.name=login-service
service.version=v1
uri.prefix=${service.name}-${service.version}
```

A simple property file may be read simply like this:

```clojure
(def ^java.util.Properties props (k/read-properties "config/my-conf.properties"))
```


### Chained property files

Chained property files need to mention a parent key (property name):

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

In config file `config/my-conf.properties` (see the parent key `parent`):

```properties
parent-config=base.properties, dev.properties
service.version=v2wip
log.level=trace
```

Now, read it:

```clojure
(def ^java.util.Properties props (k/read-properties "config/my-conf.properties"
                                   {:parent-key "parent-config"}))
```

The files `base.properties` and `dev.properties` will  be looked up in file system first, then in classpath.
