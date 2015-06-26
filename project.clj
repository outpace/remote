(defproject com.outpace/remote "0.3.0"
  :description "A Clojure(Script) library and DSL for building client to remote service APIs."

  :url "http://github.com/outpace/remote"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}

  :source-paths ["target/classes" "src"]

  :dependencies
  [[org.clojure/clojure "1.6.0"]
   [org.clojure/clojurescript "0.0-2268" :scope "provided"]
   [org.clojure/core.async "0.1.319.0-6b1aca-alpha"]
   [cljs-http "0.1.29"]
   [clj-http "0.9.2"]]

  :test-paths ["target/test-classes"]

  :jar-exclusions [#"\.cljx"]

  :cljx {:builds [{:source-paths ["src"]
                   :output-path "target/classes"
                   :rules :clj}
                  {:source-paths ["src"]
                   :output-path "target/classes"
                   :rules :cljs}
                  {:source-paths ["test"]
                   :output-path "target/test-classes"
                   :rules :clj}
                  {:source-paths ["test"]
                   :output-path "target/test-classes"
                   :rules :cljs}]}

  :cljsbuild
  {:test-commands {"unit" ["phantomjs" :runner "target/main.js"]}
   :builds [{:source-paths ["target/classes" "target/test-classes"]
             :compiler {:output-to "target/main.js"
                        :optimizations :simple
                        :pretty-print true}}]}

  :profiles
  {:dev {:plugins [[lein-cljsbuild "1.0.3"]
                   [com.keminglabs/cljx "0.6.0"]
                   [com.cemerick/clojurescript.test "0.3.1"]]
         :prep-tasks [["cljx" "once"] "javac" "compile"]
         :aliases {"deploy" ["do" "clean," "cljx" "once," "deploy" "clojars"]
                   "cleantest" ["do" "clean," "cljx" "once," "test," "cljsbuild" "test"]}}})
