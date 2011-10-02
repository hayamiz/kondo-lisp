(ns kondolisp.compiler-test
  (:use [kondolisp.compiler] :reload-all)
  (:use [clojure.test]))

(deftest test-make-num
  (is (= 0x8001 (make-num 0))))

(deftest test-compile-pass1
  (is (= [[:VM_IVAL (make-num 1)]]
         (compile-pass1 1))))

(deftest test-ubyte-to-sbyte
  (is (= 0 (ubyte-to-sbyte 0)))

  (is (= 1 (ubyte-to-sbyte 1)))

  (is (= -128 (ubyte-to-sbyte 128))))

(deftest test-short-to-byte
  (is (= [0 0]
         (short-to-byte 0)))

  (is (= [1 0]
         (short-to-byte 1)))

  (is (= [-128 0]
         (short-to-byte 128)))

  (is (= [0 1]
         (short-to-byte 256)))

  (is (= [0 -128]
         (short-to-byte 0x8000)))
  )