(ns hsm.batch
  (:use cascalog.api)
  (:require 
    [taoensso.timbre :as timbre
           :refer (log  trace  debug  info  warn  error  fatal  report)]
    [clojure.string :as s]
    [cascalog.logic.ops :as c]
    [cheshire.core :refer :all]
    [hsm.batch.core :as b.core]
    [cascalog.more-taps :as c.more]
  )(:gen-class))

(defn integer[s] (Integer/parseInt s))

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
    (> ?watchers 5)
    ; (fill-s :< ?languagex :> ?language)
    ))

(defn top-obj
  [query field cutoff]
  (c/first-n query cutoff :sort field :reverse true))

(defn top-projects
  [out-tap query top-n]
  (?- out-tap
    (top-obj query ["?watchers"] top-n)))

(defn final-set
  [data-source]
  (<- [?id ?watch ?lang ?name]
    (data-source :> ?id ?w ?lang ?name)
    (integer ?w :> ?watch)))

(defn language-filtered
  [lang data-source]
  (<- [?id ?watch ?lang ?name] 
    (data-source :> ?id ?watch ?lang ?name)
    (= lang ?lang)))

(deffilterfn is-lang
  "NULL Languages exported as 54abc by either Hadoop or Cassandra.
  So filter here by checking if text contains 54"
  [l]
  (not (.contains l "54")))

(defn languages
  [data-source]
  (<- [?lang]
    (data-source :> ?id ?watch ?lang ?name)
    (:distinct true)
    (is-lang ?lang)))

(defn process
  [file cutoff top-n output]
  (info "Processing" file)
  (let [sorting ["?watch"]
        data-source (c.more/lfs-delimited file)
        max-items 1000
        all-languages (first (??- (languages (final-set data-source))))]
    (info all-languages)
    (?- (stdout)
      (top-obj (final-set data-source) sorting 1000))
    (doseq [[lang] all-languages]
      (info lang)
      (?- (lfs-textline (str output "lang/" lang))
        (top-obj (language-filtered lang (final-set data-source)) ["?watch"] max-items)))))

(defn extract
  [f cutoff output]
  (warn "EXTRACT " f cutoff)
  (let [part (last (vec (.split f "/")))
        objects (b.core/parsevaluate (load-json f))
        objects (if (> cutoff 0) (subvec (vec objects) 0 cutoff) objects)]
    (?- (lfs-textline (str output part "/") :sinkmode :replace)
       (all-projects objects))))

(defn -main
  "I don't do a whole lot ... yet."
  [mode output cutoff & args]
  (info mode cutoff args)
  (let [files args
    cut (Integer/parseInt cutoff)]
  (condp = mode
    "e" (mapv #(extract % cut output) files)
    "g" (process (first files) 0 cut output))
  (info "DONE!")))
