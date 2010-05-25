;; -*- indent-tabs-mode: nil; mode: clojure  -*-

(defproject kondo-lisp "0.0.1-SNAPSHOT"
  :main kondo-lisp.main
  :description "A framework for dynamic prototyping on Arduino"
  :dependencies [[org.clojure/clojure "1.1.0"]
                 [org.clojure/clojure-contrib "1.1.0"]
                 [clj-match "0.0.1-SNAPSHOT"]]
  :dev-dependencies [[leiningen/lein-swank "1.1.0"]])
