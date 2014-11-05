(ns hackersome.web.utils
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.local :as l]
            [clojure.java.io :as jio]
            [clojure.string :as s]
            [taoensso.timbre :as timbre :refer [debug warn info error stacktrace]]
            [clj-time.coerce :as c]))

(defn ->int
  [s]
  (warn "Integer to" s)
  (bigint s)
  )

(defn select-values
  [m ks]
  (reduce #(if-let [v (m %2)] (conj %1 v) %1) [] ks))

(def humanly-readable-datetime-formatter (f/formatter "dd-MM-yyyy-HH-mm-ss"))
(def humanly-readable-date-formatter (f/formatter "dd-MM-yyyy"))

(def humanly-readable-fancy-date-formatter (f/formatter "dd MMM ''yy"))
(def humanly-readable-fancy-datetime-formatter (f/formatter "dd MMM ''yy HH:mm"))

(defn nice-date
  ([] (f/unparse humanly-readable-datetime-formatter (t/now)))
  ([d] (f/unparse humanly-readable-datetime-formatter d))
  ([d formatter] (f/unparse formatter d))
  ([d formatter offset] (f/unparse formatter (t/from-time-zone d (t/time-zone-for-offset offset)))))

(defn parse-interval
  "Converts
  2014-04-29T22:00:00+00:00/2014-05-29T22:00:00+00:00
  into
  29-04-2014_29-05-2014
  "
  [^String s offset]
  (let [date-parts (s/split s #"\/")
        start-date (f/parse (first date-parts))
        end-date (f/parse (last date-parts))]
    [(nice-date start-date humanly-readable-date-formatter offset)
     (nice-date (t/minus end-date (t/seconds 1)) humanly-readable-date-formatter offset)
     ]))

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