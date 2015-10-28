# Changes and Todo

## 0.1.1 / 2015-Oct-28

- Compile Java source files in Java 7 bytecode format

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

