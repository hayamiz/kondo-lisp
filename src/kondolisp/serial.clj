;; -*- indent-tabs-mode: nil; mode: clojure  -*-

(ns kondolisp.serial
  (:use [clojure core]
        [clojure.contrib str-utils java-utils pprint seq-utils])
  (:import (gnu.io CommPort
                   CommPortIdentifier
                   SerialPort)
           (java.io FileDescriptor
                    IOException
                    InputStream
                    OutputStream)
           (java.nio ByteBuffer)
           (java.nio.channels Selector Channels)))

(def *serial* (ref nil))
(def *serial-config* (ref {:port-name nil,
                           :baud-rate 9600,
                           :app-name "kondolisp"}))

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
      (.enableReceiveTimeout comm-port 1000)
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

(defn read-serial [nbytes]
  (let [serial @*serial*]
    (when serial
      (let [in (.getInputStream serial),
            buf (make-array (. Byte TYPE) nbytes),
            len (.read in buf)]
        (if (> len 0)
          (String. buf, 0, len)
          nil)))))

(defn get-serial-ports []
  (let [enum (CommPortIdentifier/getPortIdentifiers),
        ports (take-while #(not (nil? %))
                          (iterate (fn [_] (.nextElement enum))
                                   (.nextElement enum))),
        serial-ports (filter (fn [port-ident]
                               (= (.getPortType port-ident)
                                  CommPortIdentifier/PORT_SERIAL))
                             ports)]
    (map #(.getName %) ports)))

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
