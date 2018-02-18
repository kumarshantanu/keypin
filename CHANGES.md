# keypin Changes and Todo

## TODO

- [TODO] Base `keypin.util/atom?` on `clojure.lang.IAtom` interface (requires Clojure 1.7)
- [TODO] Pre-lookup and post-lookup fallbacks for key definitions
- [TODO] Mechanism to enforce EDN parsing on a string (possibly containing variable) in an EDN file
- [Idea] EDN parser support for invocation
  - [Idea] Invoke fns/constructor/static-method: `(target arg1 arg2)`
  - [Idea] Mechanism to reference config keys as invocation args
  - [Idea] Built-in parser functions
    - For parsing symbol with `/` as fully qualified varname
    - For parsing a list of vars
- [Idea] Mechanism for partial/delta/path override in child config files using type hints
  - (Lookup) `^refer` - Refer another key in config
  - (Vec) `^insert` or `^precat` - Insert before
  - (Vec) `^append` or `^concat` - Insert after
  - (Any) `^replace` - Replace
  - (Map) `^merge`   - Merge


## 0.7.2 / 2018-February-18

- Add utility fn `keypin.util/clojurize-subst` for symbol/keyword variable substitution in EDN data
  - Symbol variable example: $foo   (looked up as string `"foo"`)
  - Keyword variable example: :$bar (looked up as keyword `:bar`)
- Fix issue: If argument to `keypin.util/str->var` is a var, then return as it is
- Add utility fn `keypin.util/symstr->any` to allow symbol or string interchangeably
  - Fix issue: Allow symbols for specifying fully qualified var names in `any->var` and `any->var->deref`


## 0.7.1 / 2017-July-31

- Fix issue: Validator is not applied when (keydef config not-found) arity is used
- Fix issue: Value parser is not applied in `lookup-keypath`


## 0.7.0 / 2017-July-30

- Add media reader support
  - Extensible via protocol (Java interface `keypin.MediaReader`)
  - Implementation for filesystem
  - Implementation for classpath
  - [BREAKING CHANGE] Update Java config reading API to use the protocol
- Config I/O protocol
  - [BREAKING CHANGE] Add support for named config reader/writer
- Refactor logger argument passing
  - For `keypin.core` functions: `read-config`, `realize-config` and `write-config`
  - [BREAKING CHANGE] Drop optional arguments `:info-logger` and `:error-logger`
  - Accept optional argument `:logger` that defaults to printing to `*err*`
  - Utility functions in `keypin.util` ns: `make-logger` and `default-logger`
- Overhaul duration abstraction
  - Allow runtime resolution of data-driven duration, eg. for `[1 :second]`
  - Update `duration?` to detect a duration dynamically
  - Fix silent wrong parsing by `any->duration` of the EDN form [time unit-keyword]
  - [BREAKING CHANGE] Add `duration?`, `dur-time` and `dur-unit` fns to `keypin.type/IDuration` protocol
- Add `atom?` validator function for Clojure atoms


## 0.6.0 / 2017-June-02

- Config file
  - [BREAKING CHANGE] Change default parent key to `"parent.filenames"`
  - Fix issue where when reading a config file without a parent leads to argument passing error
  - Fix issue where the parent option key is lost in the second arity of `keypin.core/read-config`
- Config I/O protocol
  - [BREAKING CHANGE] ConfigIO support for writing to `java.io.Writer` (besides `java.io.OutputStream`)
- Lookup function
  - [BREAKING CHANGE] Accept additional argument to handle not-found keys
- Key definition
  - Allow value override via system property with option `:sysprop`
  - Allow value override via environment variable with option `:envvar`
- Predicate/validator functions
  - `fqvn?` - Fully qualified var name
  - `vec?`  - Vector of predicate-validated elements


## 0.5.0 / 2017-January-31

- Deprecation
  - [BREAKING CHANGE] Remove deprecated class `keypin.PropertyFile`
  - [BREAKING CHANGE] Remove deprecated fns `keypin.core/read-properties` and `keypin.core/lookup-property`
- Support for variable escaping during reading and writing configuration
- Generic "duration" abstraction with conversion helpers
- Config reading `keypin.core/read-config` may now optionally skip variable substitution using kwarg `:realize?`
- Additional fn `keypin.core/realize-config` for explicitly applying variable substitution
- Variable substitution now distinguishes between random-access-list, sequential-access-list, set and collection
- Value parsers
  - Identity parser
  - Function for parser composition
  - Parser functions for time-unit and time-duration
  - Parser helper `clojurize-data` to transform Java-based data structures to Clojure equivalent
  - EDN parser `any->edn` now additionally transforms Java-based data structures to Clojure equivalent


## 0.4.2 / 2016-July-15

- Config files support
  - Throw exception with suitable message when EDN content being read is not a map


## 0.4.1 / 2016-June-24

- Value parsers
  - Make EDN value parser optionally validate the EDN value


## 0.4.0 / 2016-June-21

- Config files support
  - Support for reading config from EDN files
  - Unified interface for resolving config from various kinds of files (JSON, YAML etc.)
    - Hierarchical config resolution support for all types of config files
  - Support for writing config files
- Deprecated API
  - Deprecate class `keypin.PropertyFile`
  - Deprecate `keypin.core/read-properties` and `keypin.core/lookup-property`


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
