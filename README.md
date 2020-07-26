# keypin

[![Build Status](https://travis-ci.org/kumarshantanu/keypin.svg)](https://travis-ci.org/kumarshantanu/keypin)

Key lookup on steroids in Clojure.

## Why Keypin?

_**TL;DR - Simplify code by consolidating application configuration concerns!**_

**Quick pitch:** Config access code is tightly coupled to config. Why let this tight coupling spread
all over the code base? Collect config concerns in one place, without repeating yourself, so that the
rest of the code remains cleaner.

### Features

- Key lookup on any associative data structure
  - Clojure or Java maps
  - Clojure vectors
  - `java.util.Properties` instances
  - Custom (static or dynamic) config stores
  - Destructuring
- Optional value parsing
- Optional value validation
- Fail-fast error reporting
- Optional default value (when key is missing)
- Optional caching for efficiency
- Support for dynamic config store (e.g. periodic refresh)
- Reading config files
  - Out of the box support for `.edn` and `.properties` files
  - Extensible design for other config file types (JSON, YAML etc.)
  - Read config file from filesystem and classpath (in that order)
    - Freedom to have external config files, or shipped with application JAR
  - Chained config files (via parent key, child properties override parent)
  - Multi-parent chaining
  - Template value resolution in string values (environment variables override others)
  - Cascading lookup `${foo|bar|baz}` - `foo` is looked up, followed by `bar`, then `baz`
- Writing config files


## Usage

Clojars coordinates: `[keypin "0.8.0-SNAPSHOT"]`

Requires Java 7 or higher.


### Quick start

```clojure
(require '[keypin.core :refer [defkey letval] :as k])
(require '[keypin.util :as u])

;; key with constraints
(defkey
  ip   [:ip]
  port [:port #(< 1023 % 65535) "Port number" {:parser u/str->int}])

;; lookup
(ip   {:ip "0.0.0.0" :port "5000"})  ; returns "0.0.0.0"
(port {:ip "0.0.0.0" :port "5000"})  ; returns 5000
(port {:ip "0.0.0.0"})               ; throws IllegalArgumentException

;; key with default value
(defkey
  ip   [:ip]
  port-optional [:port #(< 1023 % 65535) "Port number" {:parser u/str->int :default 3000}])

;; lookup
(port-optional {:ip "0.0.0.0" :port "5000"})  ; returns 5000
(port-optional {:ip "0.0.0.0"})               ; returns 3000

;; lookup form
(letval [{:defs [ip port-optional] :as m} {:ip "0.0.0.0"}]
  [ip port-optional m])  ; returns ["0.0.0.0" 3000 {:ip "0.0.0.0"}]
```


### Documentation

[See the documentation page](doc/intro.md)


## License

Copyright © 2015-2020 Shantanu Kumar (kumar.shantanu@gmail.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
