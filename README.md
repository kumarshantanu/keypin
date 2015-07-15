# keypin

Key lookup on steroids for Clojure.

Features include:

- Key lookup on any `clojure.lang.ILookup` or `java.util.Map` type
  - Clojure maps
  - Clojure vectors
  - `java.util.Properties` instances
- Optional value parsing
- Optional value validation
- Fail-fast error reporting
- Optional default value (when key is missing)
- Reading property files
  - From filesystem and classpath (in that order)
  - Chained property files (via parent key, child properties override parent)
  - Multi-parent chaining
  - Template value resolution in property values


## Usage

Leiningen coordinates: `[keypin "0.1.0-SNAPSHOT"]`


### Quick start

```clojure
(require '[keypin.core :refer [defkey] :as k])

;; key with constraints
(defkey
  ip   [:ip]
  port [:port #(< 1023 % 65535) "Port number" {:parser k/str->int}])

;; lookup
(ip   {:ip "0.0.0.0" :port "5000"})  ; returns "0.0.0.0"
(port {:ip "0.0.0.0" :port "5000"})  ; returns 5000
(port {:ip "0.0.0.0"})               ; throws IllegalArgumentException

;; key with default value
(defkey
  ip   [:ip]
  port-optional [:port #(< 1023 % 65535) "Port number" {:parser k/str->int :default 3000}])

;; lookup
(port-optional {:ip "0.0.0.0" :port "5000"})  ; returns 5000
(port-optional {:ip "0.0.0.0"})               ; returns 3000
```


### Documentation

[See the documentation page](doc/intro.md)


## License

Copyright © 2015 Shantanu Kumar (kumar.shantanu@gmail.com, shantanu.kumar@concur.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
