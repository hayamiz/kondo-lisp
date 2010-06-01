#!/usr/bin/env gosh
;; -*- coding: utf-8 -*-

(use srfi-1)  ;; List library
(use srfi-13) ;; String library

(define (main args)
  (with-input-from-file "./project.clj"
    (lambda ()
      (let [[project (read)]]
	(display (caddr project))))))
