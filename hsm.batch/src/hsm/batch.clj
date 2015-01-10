(ns hsm.batch
  (:use cascalog.api)
  (:require 
    [taoensso.timbre :as timbre
           :refer (log  trace  debug  info  warn  error  fatal  report)]
    [clojure.string :as s]
    [cascalog.logic.ops :as c]
    [cheshire.core :refer :all]
    [hsm.batch.core :as b.core]
  )(:gen-class))


(defn load-json
  [f]
  (parse-string (slurp f) true))

(defn fill-s
  [s]
  (format "%10s" s))

 (defn mapper [source] 
    (<- [?id ?watchers ?language ?proj] 
      (source :> ?proj ?idx ?watchers ?language ?full)
      (fill-s :< ?idx :> ?id)
      (> ?watchers 50)
      ; (fill-s :< ?languagex :> ?language)
      ))

(defn top-obj
  [query field cutoff]
  (c/first-n query cutoff :sort field :reverse true))

(defn exec
  [f sorting cutoff top-n]
  (let [objects (b.core/parsevaluate (load-json f))
        objects (if (> cutoff 0) (subvec (vec objects) 0 cutoff) objects)]
    (?- (lfs-textline ".run/" :sinkmode :replace)
      (top-obj 
        (mapper objects)
         sorting top-n
        ))))

(defn execute
  [f cutoff top-n]
  (exec f ["?watchers"] cutoff top-n)
  )
 


(defn -main
  "I don't do a whole lot ... yet."
  [f cutoff]

  (execute f 0 (Integer/parseInt cutoff)))
