(ns hsm.cache
  (:require 
    [taoensso.carmine :as car :refer (wcar)]))


(defn retrieve
  [redis key]
  (wcar redis 
    (car/get key)))

(defn setup
  [redis key val]
  (wcar redis 
    (car/set key val)))