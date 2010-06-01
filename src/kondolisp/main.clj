(ns kondolisp.main
  (:gen-class)
  (:use [clojure core]
	[clojure.contrib pprint command-line java-utils]
	[clj-match]
	[kondolisp  serial])
  (:require [kondolisp compiler]
	    [clojure.contrib str-utils2])
  (:import (sun.misc Signal SignalHandler)
	   (java.awt.event ActionListener WindowListener ItemListener ItemEvent)
	   (java.util.concurrent LinkedBlockingQueue
				 TimeUnit CyclicBarrier)
	   (java.nio.channels Selector)
	   (javax.swing UIManager JOptionPane JRadioButtonMenuItem
			ButtonGroup JSeparator JMenuItem)
	   (javax.swing.event MenuListener)))

(defn- sys-println [arg]
  (.println System/out arg))
(defn- sys-print [arg]
  (.println System/out arg))

(defn- except-simple-msg [exception]
  (clojure.contrib.str-utils2/replace
   (.getMessage exception)
   #"(\w+\.)+(\w+)?Exception:[ \t]*" ""))

(defn kondo-repl []
  (open-serial)
  (let [output-queue (LinkedBlockingQueue. ),
	serial-output-thread
	(Thread.
	 (fn []
	   (try
	    (while (not (.isInterrupted (Thread/currentThread)))
		   (let [serial-output
			 (read-serial 1024)]
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
		    (write-serial
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
      (close-serial)
      ))))

(defn set-status [view str]
  (.setText (.. view gStatusLabel) str))

(defn setup-serial-menu [view]
  (let [item-listener
	(proxy [ItemListener] []
	  (itemStateChanged
	   [evt]
	   (when (= ItemEvent/SELECTED (.getStateChange evt))
	     (let [selected-port (.. evt getItemSelectable getText)]
	       (when (not (and (serial-opened?)
			       (= selected-port
				  (opened-port-name))))
		 (close-serial)
		 (set-serial-config
		  {:port-name selected-port})
		 (open-serial)
		 (set-status view
			     (str "Serial port " selected-port " opened"))
		 )))))]
    (letfn [(make-serial-port-menu
	     []
	     (.. view gSerialPortMenu removeAll)
	     (let [group (ButtonGroup.),
		   menu-items (map (fn [port-name]
				     (let [item (JRadioButtonMenuItem. port-name)]
				       (.addItemListener item item-listener)
				       item))
				   (get-serial-ports))]
	       (if (> (count menu-items) 0)
		 (do
		   (doseq [menu-item menu-items]
		     (.add group menu-item)
		     (.add (.. view gSerialPortMenu) menu-item))
		   (when (serial-opened?)
		     (let [selected-port (opened-port-name),
			   selected-item (some #(and (= selected-port (.getText %)) %)
					       menu-items)]
		       (if (not (nil? selected-item))
			 (.setSelected group, (.getModel selected-item), true))))
		   (let [sep (JSeparator.),
			 close-serial-item (JMenuItem. "Close serial port")]
		     (.addActionListener
		      close-serial-item
		      (proxy [ActionListener] []
			(actionPerformed
			 [evt]
			 (close-serial)
			 (set-serial-config {:port-name nil})
			 (.clearSelection group)
			 (set-status view (str "Closed " (serial-config :port-name))))))
		     (.add (.. view gSerialPortMenu) sep)
		     (.add (.. view gSerialPortMenu) close-serial-item)
		     ))
		 (.add (.. view gSerialPortMenu)
		       (JMenuItem. "No serial port available")))
		 ))]
      (let [menu-listener
	    (proxy [MenuListener] []
	      (menuSelected
	       [evt]
	       (make-serial-port-menu))
	      (menuDeselected [_] nil)
	      (menuCanceled [_] nil))]
	(.addMenuListener (.. view gSerialPortMenu) menu-listener)))))

(defn kondo-setup-view [view]
  (setup-serial-menu view)
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
				    (write-serial
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
		  (if (serial-opened?)
		    (let [serial-output (read-serial),
			  text-area (.. view gSerialOutputTextArea)]
		      (if (nil? serial-output)
			(Thread/sleep 1000)
			(do
			  (.append text-area serial-output)
			  (.setCaretPosition text-area
					     (.. text-area getDocument getLength)))))
		    (Thread/sleep 1000)))))]
    (.start serial-printer)
    (.addWindowListener
     view
     (proxy [WindowListener] []
       (windowActivated [evt]
			nil)
       (windowClosed [evt]
		     (.interrupt serial-printer)
		     (close-serial))
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
     (kondo-setup-view view)
     (.setVisible view true)
     (catch Exception e
       (sys-println (str e))
       (sys-println (.getMessage e))
       (dorun (map (fn [st] (sys-println (str st)))
		   (seq (.getStackTrace e)))))
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



