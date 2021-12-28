(defproject keypin "0.8.3-SNAPSHOT"
  :description "Key lookup on steroids in Clojure"
  :url "https://github.com/kumarshantanu/keypin"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :java-source-paths ["java-src"]
  :global-vars {*warn-on-reflection* true
                *assert* true
                *unchecked-math* :warn-on-boxed}
  :javac-options ["-target" "1.7" "-source" "1.7" "-Xlint:-options"]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :dev {:dependencies [[org.clojure/tools.nrepl "0.2.12"]]}
             :c07 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :c08 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :c09 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :c10 {:dependencies [[org.clojure/clojure "1.10.3"]]}
             :dln {:jvm-opts ["-Dclojure.compiler.direct-linking=true"]}}
  :aliases {"clj-test" ["with-profile" "c07:c08:c09:c10" "test"]})
