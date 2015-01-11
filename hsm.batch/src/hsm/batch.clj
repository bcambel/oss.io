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

(defn all-projects
  [source]
  (<- [?id ?watchers ?language ?proj] 
    (source :> ?proj ?idx ?watchers ?language ?full)
    (fill-s :< ?idx :> ?id)
    (> ?watchers 50)
    ; (fill-s :< ?languagex :> ?language)
    ))

(defn top-obj
  [query field cutoff]
  (c/first-n query cutoff :sort field :reverse true))

(defn top-projects
  [out-tap query top-n]
  (?- out-tap 
    (top-obj query ["?watchers"] top-n)))

(defn process
  [files cutoff top-n]
  (let [sorting ["?watchers"]]
    (info files)))

(defn extract
  [f cutoff]
  (let [part (last (vec (.split f "/")))
        objects (b.core/parsevaluate (load-json f))
        objects (if (> cutoff 0) (subvec (vec objects) 0 cutoff) objects)]
    (?- (lfs-textline (str ".run/" part "/") :sinkmode :replace)
       (all-projects objects))))

(defn -main
  "I don't do a whole lot ... yet."
  [mode f cutoff & args]
  (info mode f cutoff args)
  (let [cut (Integer/parseInt cutoff)]
  (condp = mode
    "e" (extract f cut)
    "g" (process (vec args) 0 cut)
    )))
