(defproject org.clojars.nikonyrh.channels-clj "0.1.0"
  :description "core.async-based utilities for creating data sources, pipes and sinks."
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clojure.java-time "0.2.2"]
                 [org.clojure/core.async "0.3.443"]
                 [org.clojars.nikonyrh.utilities-clj "1.0.0"]
                 [com.taoensso/carmine "2.16.0"]]
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
  :aot [channels-clj.core]
  :main channels-clj.core)
