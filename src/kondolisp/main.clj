(ns kondolisp.main
  (:gen-class)
  (:use [clojure core]
	[clojure.contrib pprint command-line]
	[clj-match])
  (:require [kondolisp compiler serial])
  (:import [sun.misc Signal SignalHandler]
	   [java.util.concurrent LinkedBlockingQueue TimeUnit CyclicBarrier]))

(defn kondo-repl []
  (kondolisp.serial/open-serial)
  (let [output-queue (LinkedBlockingQueue. ),
	serial-output-thread
	(Thread. (fn []
		   (while true
			  (let [serial-output (kondolisp.serial/read-until)]
			    (.put output-queue serial-output)))))]
    (.start serial-output-thread)
    (try
     (while true
	    (.print System/out (.take output-queue))
	    (while (> (.size output-queue) 0)
		   (.print System/out (.take output-queue)))
	    (.flush System/out)
	    (let [user-input (do (.print System/out "kondo-lisp> ")
				 (read)),
		  byteseq (kondolisp.compiler/kondo-compile user-input)]
	      (kondolisp.serial/write-serial
	       (concat byteseq [0 0 0 0]))
	      (Thread/sleep 500)))
     (catch Exception e
       (.println System/out (str e))
       (.println System/out
		 (format "message: %s" (.getMessage e))))
     (finally
      (kondolisp.serial/close-serial)
      (.destroy serial-output-thread)
      ))))

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



