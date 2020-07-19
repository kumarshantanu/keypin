(defproject keypin "0.8.0-SNAPSHOT"
  :description "Key lookup on steroids in Clojure"
  :url "https://github.com/kumarshantanu/keypin"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :java-source-paths ["java-src"]
  :global-vars {*warn-on-reflection* true
                *assert* true}
  :javac-options ["-target" "1.7" "-source" "1.7" "-Xlint:-options"]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :dev {:dependencies [[org.clojure/tools.nrepl "0.2.12"]]}
             :c05 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :c06 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :c07 {:dependencies [[org.clojure/clojure "1.7.0"]]
                   :global-vars {*unchecked-math* :warn-on-boxed}}
             :c08 {:dependencies [[org.clojure/clojure "1.8.0"]]
                   :global-vars {*unchecked-math* :warn-on-boxed}}
             :c09 {:dependencies [[org.clojure/clojure "1.9.0"]]
                   :global-vars {*unchecked-math* :warn-on-boxed}}
             :c10 {:dependencies [[org.clojure/clojure "1.10.1"]]
                   :global-vars {*unchecked-math* :warn-on-boxed}}
             :dln {:jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
