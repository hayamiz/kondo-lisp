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
(def *serial-config* (ref {:port-name "/dev/ttyUSB0",
                           :baud-rate 9600,
                           :app-name "kondolisp"}))

(defn set-serial-config [config]
  (dosync
   (ref-set *serial-config* (merge config *serial-config*))))

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

(defn read-until [& rest]
  (let [serial @*serial*,
        term-byte (if (nil? rest)
                    (byte 0)
                    (byte (first rest)))]
    (when serial
      (let [in (.getInputStream serial)
            ret (StringBuffer.)
            buf (make-array (. Byte TYPE) 1024)]
        (dorun
         (take-while
          #(not (nil? %))
          (iterate
           (fn [accum-len]
             (let [len (.read in buf)]
               (cond
                (= len -1)	nil
                (= len 0)	accum-len
                true		(if (.equals term-byte (aget buf (- len 1)))
                                  (do (.append ret (String. buf, 0, (- len 1)))
                                      nil)
                                  (do (.append ret (String. buf, 0, len))
                                      (+ accum-len len))))))
           0)))
        (.toString ret)))))

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
