(defproject keypin "0.1.0-SNAPSHOT"
  :description "Key lookup on steroids in Clojure"
  :url "https://github.com/kumarshantanu/keypin"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :java-source-paths ["java-src"]
  :global-vars {*warn-on-reflection* true
                *assert* true}
  :profiles {:dev {:dependencies [[org.clojure/tools.nrepl "0.2.10"]]}
             :c15 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :c16 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :c17 {:dependencies [[org.clojure/clojure "1.7.0"]]
                   :global-vars {*unchecked-math* :warn-on-boxed}}})
