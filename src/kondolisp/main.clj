(ns kondolisp.main
  (:gen-class)
  (:use [clojure core]
	[clojure.contrib pprint command-line]
	[clj-match])
  (:require [kondolisp compiler serial])
  (:import [sun.misc Signal SignalHandler]))

(defn kondo-repl []
  (kondolisp.serial/open-serial)
  (try
   (while true
	  (let [serial-output (kondolisp.serial/read-until)]
	    (.print System/out serial-output)
	    (.flush System/out)
	    (let [user-input (do (.print System/out "kondo-lisp> ")
				 (read)),
		  byteseq (kondolisp.compiler/kondo-compile user-input)]
	      (kondolisp.serial/write-serial
	       (concat byteseq [0 0 0 0])))))
   (catch Exception e
     (.println System/out (str e))
     (.println System/out
	       (format "message: %s" (.getMessage e))))
   (finally
    (kondolisp.serial/close-serial))))

(defn -main [& argv]
  (match argv
    ("header" "vminst" & rest)
    (kondolisp.compiler/generate-vminst-header)
    ;;
    ("header" "builtin" & rest)
    (kondolisp.compiler/generate-builtin-header)
    ;;
    ("repl" & rest)
    (kondo-repl)
    ;;
    _	(.println System/out (str argv)))
  true)



