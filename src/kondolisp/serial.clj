;; -*- indent-tabs-mode: nil; mode: clojure  -*-

(ns kondolisp.serial
  (:use [clojure core pprint])
  (:require [clojure.contrib seq-utils])
  (:import (gnu.io CommPort
                   CommPortIdentifier
                   SerialPort
                   SerialPortEvent
                   SerialPortEventListener)
           (java.io FileDescriptor
                    IOException
                    InputStream
                    OutputStream)
           (java.nio ByteBuffer)
           (java.nio.channels Selector Channels)
           (kondolisp.util RingByteBuffer
                           RingByteBuffer$UnderflowException)))

(def *serial* (ref nil))
(def *serial-config* (ref {:port-name nil,
                           :baud-rate 9600,
                           :app-name "kondolisp"}))
(def *serial-buffer* (RingByteBuffer.))

(defn set-serial-config [config]
  (dosync
   (ref-set *serial-config* (merge @*serial-config* config))))

(defn open-serial []
  (let [serial-config @*serial-config*,
        ident (CommPortIdentifier/getPortIdentifier (:port-name serial-config))]
    (when (.isCurrentlyOwned ident)
      (throw (IOException. (format "%s is busy" (:port-name serial-config)))))
    (let [comm-port (.open ident (:app-name serial-config) 3000)]
      (when (not (instance? SerialPort comm-port))
        (throw (IOException. (format "%s is not a serial port" (:port-name serial-config)))))
      (.setSerialPortParams comm-port,
                            (:baud-rate serial-config),
                            SerialPort/DATABITS_8,
                            SerialPort/STOPBITS_1,
                            SerialPort/PARITY_NONE)
      (.clear *serial-buffer*)
      (.addEventListener
       comm-port
       (proxy [SerialPortEventListener] []
         (serialEvent
          [evt]
          (condp = (.getEventType evt)
            SerialPortEvent/DATA_AVAILABLE
            (let [in (.getInputStream comm-port),
                  buf (make-array (. Byte TYPE) 1024),
                  len (.read in buf)]
              (dotimes [idx len]
                (.put *serial-buffer* (byte (aget buf idx)))))
            ;;
            (.getEventType evt)	nil))))
      (.notifyOnDataAvailable comm-port true)
      (dosync (ref-set *serial* comm-port)))))

(defn serial-opened? []
  (not (nil? @*serial*)))

(defn serial-config [key]
  (key @*serial-config*))

(defn opened-port-name []
  (if (serial-opened?)
    (.getName @*serial*)
    nil))

(defn close-serial []
  (let [serial @*serial*]
    (when serial
      (.close serial)
      (dosync (ref-set *serial* nil)))))

;; returns String read from Serial port
(defn read-serial []
  (let [serial @*serial*]
    (when serial
      (let [data (take-while
                  #(and (not (nil? %))
                        (not (= (byte 0) %)))
                  (repeatedly
                   (fn []
                     (try
                       (.get *serial-buffer*)
                       (catch RingByteBuffer$UnderflowException e
                         nil)))))]
        (if (> (count data) 0)
          (let [buf (make-array (. Byte TYPE) (count data))]
            (doseq [[idx b] (clojure.contrib.seq-utils/indexed data)]
              (aset buf idx b))
            (String. buf))
          nil)))))

(defn get-serial-ports []
  (let [enum (CommPortIdentifier/getPortIdentifiers),
        ports (take-while #(not (nil? %))
                          (iterate (fn [_] (.nextElement enum))
                                   (.nextElement enum))),
        serial-ports (filter (fn [port-ident]
                               (= (.getPortType port-ident)
                                  CommPortIdentifier/PORT_SERIAL))
                             ports)
        port-names (map #(.getName %) ports)]
    (if (serial-opened?)
      (cons (opened-port-name) port-names)
      port-names)
    ))

(defn to-byte-array [coll]
  (let [bytes (map byte coll)
        ret (make-array (. Byte TYPE) (count coll))]
    (dotimes [i (count coll)]
      (aset ret i (nth bytes i)))
    ret))

(defn write-serial [bytes]
  (let [serial @*serial*]
    (when serial
      (let [out (.getOutputStream serial),
            bytes-blocks (partition-all 32 bytes)]
        (doseq [bytes bytes-blocks]
          (.write out (to-byte-array bytes))
          (Thread/sleep 50))))))
