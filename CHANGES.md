# Changes and Todo

## TODO

- [TODO] Support for reading config from EDN files
- [TODO] Uniform interface for resolving config from various kinds of files (JSON, YAML etc.)
- [TODO] Hierarchical config resolution support for all types of config files


## 0.3.2 / 2016-June-03

- Reading property files
  - Remove parent config filename property after the hierarchical resolution is complete


## 0.3.1 / 2016-May-17

- Value parsers
  - Add EDN value parser
- Reading property files
  - Require property file names as `Iterable<String>` instead of `List<String>`


## 0.3.0 / 2016-May-16

- Value parsers
  - Add collection parsers
  - Add optional parsers (useful for supporting EDN/other config file types)
- Refactor validator and parser functions to `keypin.util` namespace
- Reading property files
  - Support for resolving one or more property files instead of just one


## 0.2.2 / 2015-December-15

- Key lookup
  - Support for defining the target map (lookup map) as a var


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

