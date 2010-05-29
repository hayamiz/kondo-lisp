(ns kondolisp.main
  (:gen-class)
  (:use [clojure core]
	[clojure.contrib pprint command-line java-utils]
	[clj-match])
  (:require [kondolisp compiler serial]
	    [clojure.contrib str-utils2])
  (:import (sun.misc Signal SignalHandler)
	   (java.awt.event ActionListener WindowListener)
	   (java.util.concurrent LinkedBlockingQueue
				 TimeUnit CyclicBarrier)
	   (java.nio.channels Selector)
	   (javax.swing UIManager)))

(defn- sys-println [arg]
  (.println System/out arg))
(defn- sys-print [arg]
  (.println System/out arg))

(defn- except-simple-msg [exception]
  (clojure.contrib.str-utils2/replace
   (.getMessage exception)
   #"(\w+\.)+(\w+)?Exception:[ \t]*" ""))

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

(defn set-status [view str]
  (.setText (.. view gStatusLabel) str))

(defn kondo-setup-view [view]
  (let [serial-ports (kondolisp.serial/get-serial-ports)]
    (if (> (count serial-ports) 0)
      (.setSerialPortMenu
       view
       (into-array (kondolisp.serial/get-serial-ports)))))
  (.addActionListener
   (.gCommandRunButton view)
   (proxy [ActionListener] []
     (actionPerformed [evt]
		      (let [cmd-str (.. view gCommandInputTextArea getText)]
			(try
			 (with-in-str cmd-str
			   (while true
				  (while ;; discard white spaces
				   (let [c (.read *in*)]
				     (match c
				       -1 (throw
					   (RuntimeException. "Evaluation finished"))
				       9  true ;; tab
				       10 true ;; newline
				       12 true ;; formfeed
				       13 true ;; return
				       32 true ;; space
				       _	(do (.unread *in* c)
						    false))))
				  (let [cmd (read),
					byteseq
					(kondolisp.compiler/kondo-compile cmd)]
				    (kondolisp.serial/write-serial
				     (concat byteseq [0 0 0 0])))))
			 (catch clojure.lang.LispReader$ReaderException e
			   (match (except-simple-msg e)
			     "EOF while reading"
			     (set-status view "Error: EOF while reading")
			     ;;
			     msg	(do (sys-println msg)
					    (set-status view (str "Read error: " msg)))
			     ))
			 (catch RuntimeException e
			   (match (except-simple-msg e)
			     "Evaluation finished"
			     (set-status view (str "Evaluation finished: " cmd-str))
			     ;;
			     msg	(set-status view msg))
			   ))))
     ))

  (let [serial-printer
	(Thread.
	 (fn []
	   (while (not (.isInterrupted (Thread/currentThread)))
		  (if (kondolisp.serial/serial-opened?)
		    (let [serial-output (kondolisp.serial/read-serial 1024),
			  text-area (.. view gSerialOutputTextArea)]
		      (.append text-area serial-output)
		      (.setCaretPosition text-area (.. text-area getDocument getLength)))
		    (Thread/sleep 1000)))))]
    (.start serial-printer)
    (.addWindowListener
     view
     (proxy [WindowListener] []
       (windowActivated [evt]
			nil)
       (windowClosed [evt]
		     (.interrupt serial-printer)
		     (kondolisp.serial/close-serial))
       (windowClosing [evt]
		      nil)
       (windowDeactivated [evt]
			  nil)
       (windowDeiconified [evt]
			  nil)
       (windowIconified [evt]
			nil)
       (windowOpened [evt]
		     nil)))))

(defn kondo-gui []
  (try
   (UIManager/setLookAndFeel
    (UIManager/getSystemLookAndFeelClassName))
   (catch Exception _ ))
  (let [view (kondolisp.gui.kondoView.)]
    (try
     (kondolisp.serial/open-serial)
     (kondo-setup-view view)
     (.setVisible view true)
     (catch Exception e
       (sys-println (str e))
       (sys-println (.getMessage e))
       (sys-println (str (.getStackTrace e))))
     (finally
      )))
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



