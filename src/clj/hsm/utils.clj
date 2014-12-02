(ns hsm.utils
  (:require [clj-time.core :as t]
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