(ns hsm.cache
  (:require
    [taoensso.carmine :as car :refer (wcar)]))

(defn ssort-fetch
  [redis k start end]
  (wcar redis
    (car/zrevrange k start end))
  )

(defn retrieve
  [redis key]
  (wcar redis
    (car/get key)))

(defn setup
  [redis key val]
  (wcar redis
    (car/set key val)))

(defn delete
  [redis key]
  (wcar redis
    (car/del key))
  )

(defn hset
  [redis key el val]
  (wcar redis
    (car/hset key el val))
  )
