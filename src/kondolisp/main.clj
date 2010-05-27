(ns kondolisp.main
  (:gen-class)
  (:use [clojure core]
	[clojure.contrib pprint command-line]
	[clj-match])
  (:require [kondolisp compiler]))

(defn -main [& argv]
  (match argv
    ("header" "vminst" & rest)
    (kondolisp.compiler/generate-vminst-header)
    ;;
    ("header" "builtin" & rest)
    (kondolisp.compiler/generate-builtin-header)
    ;;
    _	(.println System/out (str argv)))
  true)



