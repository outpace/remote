(defproject com.outpace/remote "0.4.3"
  :description "A Clojure(Script) library and DSL for building client to remote service APIs."

  :url "http://github.com/outpace/remote"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}

  :dependencies
  [[org.clojure/clojure "1.8.0"]
   [org.clojure/clojurescript "1.7.228" :scope "provided"]
   [org.clojure/core.async "0.2.374"]
   [cljs-http "0.1.39"]
   [clj-http "2.1.0"]]

  :cljsbuild
  {:test-commands {"unit" ["phantomjs" :runner "target/main.js"]}
   :builds [{:source-paths ["test"]
             :compiler {:output-to "target/main.js"
                        :optimizations :simple
                        :pretty-print true}}]}

  :profiles
  {:dev {:plugins [[lein-cljsbuild "1.1.3"]
                   [com.cemerick/clojurescript.test "0.3.3"]]
         :auto-clean false
         :aliases {"deploy" ["do" "clean," "deploy" "clojars"]
                   "cleantest" ["do" "clean," "test" "outpace.remote.service-test," "cljsbuild" "test"]}}})
