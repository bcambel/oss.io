(ns hsm.batch
  (:use cascalog.api)
  (:require 
    [taoensso.timbre :as timbre
           :refer (log  trace  debug  info  warn  error  fatal  report)]
    [clojure.string :as s]
    [cascalog.logic.ops :as c]
  (:gen-class))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
