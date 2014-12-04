(ns hsm.utils
  (:require 
    [clojure.java.io :as io]
    [cheshire.core :refer :all]
    [ring.util.response :as resp]
    [clj-time.core :as t]
    [clj-time.format :as f]
    [clj-time.local :as l]
    [clj-time.coerce :as c]))

(defn select-values
  [m ks]
  (reduce #(if-let [v (m %2)] (conj %1 v) %1) [] ks))

(defn epoch
  "Returns the millisecond representation the given datetime as epoch"
  ([d]
   (c/to-long d))
  ([d format]
   (if (= format :second)
     (/ (c/to-long d) 1000)
     (epoch d))))

(defn now->ep
  "Returns the microsecond representation of epoch of now."
  []
  (epoch (t/now)))


(defn body-as-string
  [ctx]
  (if-let [body (:body ctx)]
    (condp instance? body
      java.lang.String body
      (slurp (io/reader body)))))

(defn mapkeyw
  [data]
  (apply merge (map #(hash-map (keyword %) (get data %)) (keys data))))

(def zero (fn[& args] 0))
(def idseq (atom 0))
(def start (hsm.utils/epoch (t/date-time 2014 1 1)))

(defn id-generate
  "Generate a new ID. Very raw right now.
  TODO: Sequences must be reset in some interval"
  [& args]
  (let [time (-> (- (hsm.utils/now->ep) start) (bit-shift-left 23))
        worker (-> 1 (bit-shift-left 10))
        sequence (swap! idseq inc)]
        (if (> sequence 4095) (swap! idseq zero))
        (bit-or time worker sequence)))


(defn json-resp
  [data]
  (-> (generate-string data)
        (resp/response)
        (resp/status 200)))
