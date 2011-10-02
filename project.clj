;; -*- indent-tabs-mode: nil; mode: clojure  -*-

(defproject kondolisp "0.0.2-SNAPSHOT"
  :main kondolisp.main
  :description "A framework for dynamic prototyping on Arduino"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [clj-match "0.0.4-SNAPSHOT"]]
  :dev-dependencies [[swank-clojure "1.3.3-SNAPSHOT"]]
  :keep-non-project-classes true)
