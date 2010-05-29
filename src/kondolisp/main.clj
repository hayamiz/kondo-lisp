(ns kondolisp.main
  (:gen-class)
  (:use [clojure core]
	[clojure.contrib pprint command-line java-utils]
	[clj-match])
  (:require [kondolisp compiler serial])
  (:import (sun.misc Signal SignalHandler)
	   (java.util.concurrent LinkedBlockingQueue
				 TimeUnit CyclicBarrier)
	   (java.nio.channels Selector)
	   (javax.swing UIManager)))

(defn- sys-println [arg]
  (.println System/out arg))
(defn- sys-print [arg]
  (.println System/out arg))

(defn kondo-repl []
  (kondolisp.serial/open-serial)
  (let [output-queue (LinkedBlockingQueue. ),
	serial-output-thread
	(Thread.
	 (fn []
	   (try
	    (while (not (.isInterrupted (Thread/currentThread)))
		   (let [serial-output
			 (kondolisp.serial/read-serial 1024)]
		     (if (not (nil? serial-output))
		       (.put output-queue serial-output))))
	    (catch InterruptedException e
	      nil)
	    )))]
    (.start serial-output-thread)
    (try
     (letfn [(printer
	      [] (let [output (.take output-queue),
		       last-char (.charAt output,
					  (- (.length output) 1))]
		   (.print System/out output)
		   (if (= last-char (char 0))
		     nil
		     (do (recur))
		     )))]
       (while true
	      (while (> (.size output-queue) 0)
		     (printer)
		     (Thread/sleep 50))
	      ;;	    (while (> (.size output-queue) 0)
	      ;;		   (.print System/out (.take output-queue))
	      ;;		   (Thread/sleep 50)
	      ;;		   )
	      (.flush System/out)
	      (let [user-input (do (.print System/out "kondo-lisp> ")
				   (read))]
		(match user-input
		  :quit		(throw (RuntimeException. "quit"))
		  :help		(throw (RuntimeException. "quit"))
		  _
		  (let [byteseq (kondolisp.compiler/kondo-compile user-input)]
		    (kondolisp.serial/write-serial
		     (concat byteseq [0 0 0 0]))
		    (printer)
		    (Thread/sleep 50))))))
     (catch clojure.lang.LispReader$ReaderException e
       (sys-println ":quit"))
     (catch RuntimeException e
       (match (.getMessage e)
	 "java.lang.RuntimeException: quit"	nil
	 _		(throw e)))
     (finally
      (.interrupt serial-output-thread)
      (kondolisp.serial/close-serial)
      ))))

(defn kondo-gui []
  (try
   (UIManager/setLookAndFeel
    (UIManager/getSystemLookAndFeelClassName))
   (catch Exception _ ))
  (let [view (kondolisp.gui.kondoView.)]
    (.setVisible view true))
  )

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
    _	(do (sys-println (str argv))
	    (kondo-gui)))
  true)



