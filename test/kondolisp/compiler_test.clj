(ns kondolisp.compiler-test
  (:use [kondolisp.compiler] :reload-all)
  (:use [clojure.test]))

(deftest test-make-num
  (is (= 0x8001 (make-num 0))))

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

(deftest test-make-t
  (is (= 0x2001
         (make-t))))

(deftest test-make-nil
  (is (= 0
         (make-nil))))

(deftest test-make-sym
  (is (= 0x4001
         (make-sym "!!")))

  (is (thrown? RuntimeException
               (make-sym "hoge"))))

(deftest test-compile-pass1-immediate-values
  (is (= [[:VM_IVAL (make-num 1)]]
         (compile-pass1 1)))

  (is (= [[:VM_IVAL (make-num 1)]]
         (compile-pass1 '(quote 1))))

  (is (= [[:VM_IVAL (make-sym "aa")]]
         (compile-pass1 ''aa)))

  (is (= [[:VM_IVAL (make-t)]]
         (compile-pass1 't)))

  (is (= [[:VM_IVAL (make-t)]]
         (compile-pass1 ''t)))

  (is (= [[:VM_IVAL (make-nil)]]
         (compile-pass1 'nil))))

(deftest test-compile-pass1-quote
  (is (= [[:VM_IVAL (make-num 1)]
          [:VM_PUSH]
          [:VM_IVAL (make-num 2)]
          [:VM_PUSH]
          [:VM_IVAL (make-nil)]
          [:VM_CONS]
          [:VM_CONS]]
         (compile-pass1 '(quote (1 2)))))
  )
