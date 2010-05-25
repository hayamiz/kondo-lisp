(ns kondo-lisp.main
  (:gen-class)
  (:use [clojure core]
	[clojure.contrib pprint]))

(defn -main [& argv]
  (pprint argv))



