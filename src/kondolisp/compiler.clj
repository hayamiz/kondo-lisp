;; -*- indent-tabs-mode: nil; mode: clojure  -*-

(ns kondolisp.compiler
  (:use [clojure core]
        [clojure.contrib str-utils pprint seq-utils]
	[clj-match])
  (:require [clojure.walk]
	    [clojure.contrib str-utils2])
  )

(def *inst-width* 4)

(def *vm-inst-list*
     '[(:VM_DUMMY nil)

       ;; basic stack and register operation
       (:VM_IVAL lispval)
       (:VM_IVAL_PUSH lispval)
       (:VM_VREF_PUSH lispval-symbol)
       (:VM_PUSH nil)
       (:VM_POP nil)

       ;; control flow
       (:VM_JMP integer)
       (:VM_BIF integer)
       (:VM_BIFN integer)
       (:VM_PUSH_FRAME nil)
       (:VM_FUNCALL lispval)

       ;; lisp namespace operation
       (:VM_BIND lispval-symbol)	;; bind stack-top value to symbol(operand)
       (:VM_UNBIND integer) ;; unbind symbol(operand)
       (:VM_VREF lispval-symbol)	  ;; refer value of symbol(operand)
       (:VM_VSET lispval-symbol)	  ;; overwrite symbol(operand) by %val

       ;; basic arithmetic operation
       (:VM_LT nil)	;; (< <%val> <stack-top>)
       (:VM_GT nil)	;; (> <%val> <stack-top>)
       (:VM_LE nil)	;; (<= <%val> <stack-top>)
       (:VM_GE nil)	;; (>= <%val> <stack-top>)
       (:VM_EQ nil)	;; (= <%val> <stack-top>)
       (:VM_PLUS nil)	;; (+ <stack-top> <%val>)
       (:VM_MINUS nil)	;; (- <stack-top> <%val>)

       (:VM_IVAL_LT lispval-integer)
       (:VM_IVAL_GT lispval-integer)
       (:VM_IVAL_LE lispval-integer)
       (:VM_IVAL_GE lispval-integer)
       (:VM_IVAL_EQ lispval-integer)
       (:VM_IVAL_PLUS lispval-integer)
       (:VM_IVAL_MINUS lispval-integer)

       ;; lisp data operation
       (:VM_CONS nil)	      ;; (cons <stack-top> <%val>)
       (:VM_IVAL_CONS lispval) ;; (cons <stack-top> <immval>)
       ])

(def *vm-builtin-funs*
     '[(print-char "print_char")
       (write "write")
       (+ "plus")
       (- "minus")
       (* "times")
       (/ "divide")
       (% "remainder")
       (eq "eq")
       (equal "equal")
       (cons "cons")
       (list "list")

       (debug-mode "debug_mode")

       ;; arduino builtin function wrappers
       (pin-mode "pin_mode")
       (delay "delay")
       (digital-write "digital_write")
       (digital-read "digital_read")
       (analog-reference "analog_reference")
       (analog-read "analog_read")
       (analog-write "analog_write")
       ])

(defn #^{:doc "Convert Short to 2 Bytes (big endian)"}
  short-to-byte [#^Short short]
  [(byte (bit-and short 0xFF))
   (byte (bit-and (bit-shift-right short 8) 0xFF))])

(defn index [pred coll]
  (defn inner-index [idx pred coll]
    (if (empty? coll)
      nil
      (if (pred (first coll))
	idx
	(recur (+ idx 1) pred (rest coll)))))
  (inner-index 0 pred coll))

(defn #^{:doc "Encode a VM instruction to bytes"}
  encode-inst [inst]
  (let [inst-name (first inst),
	inst-operand (or (second inst) 0),
	inst-spec (some (fn [inst-spec] (= inst-name (first inst-spec)))
			*vm-inst-list*)]
    (when inst-spec
      (concat (short-to-byte (index (fn [inst-spec] (= inst-name (first inst-spec)))
				    *vm-inst-list*))
	      (short-to-byte inst-operand)))))

(defn
  bytes-to-short [b1 b2]
  (bit-or b1 (bit-shift-left b2 8)))

(defn
  decode-inst [bytes]
  (if (not (= *inst-width* (count bytes)))
    nil
    (let [inst-opcode (bytes-to-short (nth bytes 0) (nth bytes 1)),
	  inst-operand (bytes-to-short (nth bytes 2) (nth bytes 3))]
      (if (>= inst-opcode (count *vm-inst-list*))
	nil
	(let [inst-spec (nth *vm-inst-list* inst-opcode),
	      inst-name (first inst-spec)]
	  (if (second inst-spec)
	    `(~inst-name ~inst-operand)
	    `(~inst-name)))))))

(defn assemble [insts]
  (apply concat (map encode-inst insts)))

(defn disassemble [bytecode]
  (if (not (= 0 (rem (count bytecode) *inst-width*)))
    nil
    (map decode-inst (partition *inst-width* bytecode))))

(defn make-nil []
  0)

(defn make-t []
  (bit-or (bit-shift-left 1 13) 1))

(defn encode-char [c]
  (- (int c) (int \!)))

(defn combine-chars [c1 c2]
  (let [c1 (encode-char c1),
	c2 (encode-char c2)]
    (bit-or
     (bit-shift-left
      (bit-or (bit-shift-left c1 6) c2) 1)
     0x4001)))

(defn make-sym [sym]
  (cond
   (and (string? sym)
	(= 2 (count sym)))
   (let [sym (clojure.contrib.str-utils2/upper-case sym)]
     (combine-chars (nth sym 0)
		    (nth sym 1)))
   ;;
   (symbol? sym)	(recur (.toString sym))
   true
   (throw (Exception. (str "cannot make sym from " sym)))))

(defn make-num [x]
  (cond
   (and (integer? x) (>= x 0))	(bit-or (bit-shift-left x 1) 0x8001)
   ;; 
   (and (integer? x) (< x 0))	(bit-or (bit-shift-left x 1) 0xc001)
   true		(throw (Exception. (str "cannot make num from " x)))))

(defn decode-num [x]
  (bit-or
   (bit-shift-right (bit-and x 0x7ffe) 1)
   (if (not (= 0 (bit-and x (bit-shift-left 1 14))))
     (bit-shift-left 7 13)
     0)))

(defn decode-sym [x]
  (let [c1 (byte (+ (int \!) (bit-shift-right (bit-and 0x3ffe x) 7)))
	c2 (byte (+ (int \!) (bit-shift-right (bit-and 0x7e x) 1)))
	buf (make-array (. Byte TYPE) 2)]
    (aset buf 0 c1)
    (aset buf 1 c2)
    (String. buf, 0, 2)))

(defn lispval-str [val]
  (cond
   (= val (make-nil)) "NIL"
   (= val (make-t)) "T"
   (= (bit-and 0x8001 val) 0x8001) (format "%d" (decode-num val))
   (= (bit-and 0xc001 val) 0x4001) (format "'%s" (decode-sym val))))

(defn pp-program [insts]
  (dotimes [i (count insts)]
    (let [inst (nth insts i),
	  inst-name (first inst)
	  inst-spec (some (fn [spec] (and (= inst-name (first spec)) spec))
			  *vm-inst-list*)
	  inst-operand (second inst)
	  inst-operand-type (second inst-spec)]
      (print (format "%d: (%s" i inst-name))
      (condp = inst-operand-type
	    nil (println ")")
	    'lispval-symbol (println (format " '%s)" (decode-sym inst-operand)))
            'lispval-integer (println (format " '%d)" (decode-num inst-operand)))
	    'lispval (println (format " %s)" (lispval-str inst-operand)))
	    inst-operand-type (println (format " %d)" inst-operand))
	    )
      ))
  insts)

(declare compile-pass1)

(defn compile-quote [exp]
  (cond
   (or (and (coll? exp)
	    (empty? exp))
       (= 'nil exp))
   `((:VM_IVAL ~(make-nil)))
   ;;
   (or (= 't exp) (= 'T exp))		`((:VM_IVAL ~(make-t)))
   (symbol? exp)			`((:VM_IVAL ~(make-sym exp)))
   (number? exp)			`((:VM_IVAL ~(make-num exp)))
   (coll? exp)				`(~@(compile-quote (first exp))
					  (:VM_PUSH)
					  ~@(compile-quote (rest exp))
					  (:VM_CONS))
   ))

(defn compile-let [bindings body]
  `(~@(apply concat
       (map (fn [binding]
	      `(~@(compile-pass1 (second binding))
		(:VM_BIND ~(make-sym (first binding)))))
	    bindings))
    ~@(apply concat
	     (map compile-pass1 body))
    (:VM_UNBIND ~(count bindings))))

(defn builtin-fun-id [fname]
  (index (fn [pair] (= (first pair) fname))
	 *vm-builtin-funs*))

(defn compile-funcall [fname args]
  `((:VM_PUSH_FRAME)
    ~@(apply concat
       (map (fn [arg] (concat (compile-pass1 arg)
			      `((:VM_PUSH))))
	    (reverse args)))
    (:VM_FUNCALL ~(if (some (fn [fun-spec] (= (first fun-spec) fname))
			   *vm-builtin-funs*)
		   (make-num (builtin-fun-id fname))
		   (make-sym fname)))))

(declare compile-pass1 compile-compact
         compile-resolve-labels assemble)

(defn kondo-compile [exp]
  (assemble
   (pp-program
    (compile-resolve-labels
     (compile-compact
      (compile-pass1 exp))))))

(defn compile-pass1 [exp]
  (cond
   (or (nil? exp)
       (and (coll? exp) (empty? exp)))
   `((:VM_IVAL ~(make-nil)))
   ;;
   (or (= 't exp) (= 't exp))
   `((:VM_IVAL ~(make-t)))
   ;;
   (symbol? exp)
   `((:VM_VREF ~(make-sym exp)))
   ;;
   (number? exp)
   `((:VM_IVAL ~(make-num exp)))
   ;;
   (coll? exp)
   (match exp
	  ('quote x)	(compile-quote x)
	  ;;
	  ('progn & body)	(if (empty? body)
			    `((:VM_IVAL ~(make-nil)))
			    (apply concat (map compile-pass1 body)))
	  ;;
	  ('let bindings & body)	(compile-let bindings body)
	  ;;
	  ('setq sym val)	`(~@(compile-pass1 val)
				  (:VM_VSET ~(make-sym sym)))
	  ;;
	  ('if pred then-body else-body)
	  (let [end-label (gensym),
		else-label (gensym)]
	    `(~@(compile-pass1 pred)
	       (:VM_BIFN ~else-label)
	       ~@(compile-pass1 then-body)
	       (:VM_JMP ~end-label)
	       ~else-label
	       ~@(compile-pass1 else-body)
	       ~end-label))
	  ;;
	  ('while pred & body)	(let [retry-label (gensym),
				      end-label (gensym)]
				  `((:VM_IVAL ~(make-nil))
                                    ~retry-label
                                    (:VM_PUSH)
				    ~@(compile-pass1 pred)
				    (:VM_BIFN ~end-label)
                                    (:VM_POP)
				    ~@(apply concat (map compile-pass1 body))
				    (:VM_JMP ~retry-label)
				    ~end-label
                                    (:VM_POP)))
	  ;;
	  ('dotimes (var num) & body)
	  (let [end-label (gensym),
		retry-label (gensym)]
	    `((:VM_IVAL ~(make-num 0))
	      (:VM_BIND ~(make-sym var))
	      ~retry-label
	      ~@(apply concat (map compile-pass1 body))
              (:VM_PUSH)
	      (:VM_VREF ~(make-sym var))
	      (:VM_PUSH)
	      (:VM_IVAL ~(make-num 1))
	      (:VM_PLUS)
	      (:VM_VSET ~(make-sym var))
	      (:VM_VREF ~(make-sym var))
	      (:VM_PUSH)
	      (:VM_IVAL ~(make-num num))
	      (:VM_LE)
	      (:VM_BIF ~end-label)
              (:VM_POP)
	      (:VM_JMP ~retry-label)
	      ~end-label
              (:VM_POP)
	      (:VM_UNBIND 1)))
	  ;;
	  _	(compile-funcall (first exp) (rest exp)))
   ;;
   true
   (throw (Exception. (str "Compile error: " exp)))
   ))

(defn compile-compact [program]
  (match program
    ((:VM_IVAL x) (:VM_PUSH) & rest)
    `((:VM_IVAL_PUSH ~x) ~@(compile-compact rest))
    ;;
    ((:VM_IVAL x) (:VM_LE) & rest)
    `((:VM_IVAL_LE ~x) ~@(compile-compact rest))
    ;;
    ((:VM_IVAL x) (:VM_GE) & rest)
    `((:VM_IVAL_GE ~x) ~@(compile-compact rest))
    ;;
    ((:VM_IVAL x) (:VM_LT) & rest)
    `((:VM_IVAL_LT ~x) ~@(compile-compact rest))
    ;;
    ((:VM_IVAL x) (:VM_GT) & rest)
    `((:VM_IVAL_GT ~x) ~@(compile-compact rest))
    ;;
    ((:VM_IVAL x) (:VM_EQ) & rest)
    `((:VM_IVAL_EQ ~x) ~@(compile-compact rest))
    ;;
    ((:VM_IVAL x) (:VM_PLUS) & rest)
    `((:VM_IVAL_PLUS ~x) ~@(compile-compact rest))
    ;;
    ((:VM_IVAL x) (:VM_MINUS) & rest)
    `((:VM_IVAL_MINUS ~x) ~@(compile-compact rest))
    ;;
    ((:VM_VREF x) (:VM_PUSH) & rest)
    `((:VM_VREF_PUSH ~x) ~@(compile-compact rest))
    ;;
    (x & xs)	`(~x ~@(compile-compact xs))
    ;;
    ()	program
  ))

(defn compile-resolve-labels [program]
  (let [[_ labels]
        (reduce (fn [[idx labels] inst]
                  (if (symbol? inst)
                    [idx (assoc labels inst idx)]
                    [(+ 1 idx) labels]))
                [0 {}] program)]
    (map (fn [inst]
           (if (empty? (rest inst))
             inst
             (let [[inst-name inst-operand] inst]
               (if (symbol? inst-operand)
                 `(~inst-name ~(inst-operand labels))
                 inst))))
         (filter #(not (symbol? %)) program))))
