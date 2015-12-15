# Changes and Todo


## 0.2.2 / 2015-December-15

- Key lookup
  - Support for defining the target map as a var


## 0.2.1 / 2015-December-02

- Reading property files
  - Support for pipe-delimited cascading environment variables/properties: `${foo|bar|baz}`


## 0.2.0 / 2015-Nov-02

- Reading property files
  - Template resolution looks up environment variables before other property values


## 0.1.1 / 2015-Oct-28

- Compile Java source files in Java 7 bytecode format ([Vignesh Sarma K](vigneshsarma))


## 0.1.0 / 2015-July-17

- Key lookup on any clojure.lang.ILookup or java.util.Map type
  - Clojure maps
  - Clojure vectors
  - java.util.Properties instances
  - Destructuring
- Optional value parsing
- Optional value validation
- Fail-fast error reporting
- Optional default value (when key is missing)
- Reading property files
  - From filesystem and classpath (in that order)
  - Chained property files (via parent key, child properties override parent)
  - Multi-parent chaining
  - Template value resolution in property values

