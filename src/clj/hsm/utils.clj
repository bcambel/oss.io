(ns hsm.utils
  "Utility functions. 
  TODO: 
  - Separate HTTP related helpers into separate namespace
  - Write proper extensive tests which would make it easier to understand
  and would be pretty much self documenting."
  (:require 
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [cheshire.core :refer :all]
    [ring.util.response :as resp]
    [clj-time.core :as t]
    [clj-time.format :as f]
    [clj-time.local :as l]
    [clj-time.coerce :as c])
  (:import [com.google.common.net InternetDomainName]))

(defn select-values
  "clojure.core contains select-keys 
  but not select-values."
  [m ks]
  (reduce 
    #(if-let [v (m %2)] 
        (conj %1 v) %1) 
    [] ks))

(defn epoch
  "Returns the millisecond representation 
  the given datetime as epoch"
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
  "In a given request context (or hash-map contains the body key), 
  returns the body if string, else tries to read input 
  string using Java.io and slurp"
  [ctx]
  (if-let [body (:body ctx)]
    (condp instance? body
      java.lang.String body
      (slurp (io/reader body)))))

(defn mapkeyw
  [data]
  (apply merge
    (map
      #(hash-map (keyword %) (get data %))
      (keys data))))

(def zero (fn[& args] 0))
(def idseq (atom 0))
(def start (hsm.utils/epoch (t/date-time 2014 1 1)))

(defn id-generate
  "Generate a new ID. Very raw right now.
  TODO: Replace with a proper ID generator. 
  Like Twitter Snowflake or simirlar"
  [& args]
  (let [time (-> (- (hsm.utils/now->ep) start) (bit-shift-left 23))
        worker (-> 1 (bit-shift-left 10))
        sequence (swap! idseq inc)]
        (if (> sequence 4095) (swap! idseq zero))
        (bit-or time worker sequence)))



(defn host-of
  "Finds the host header of the request"
  [request]
  (get-in request [:headers "host"]))

;; TODO: must be placed into a config file.
(def domains ["pythonhackers.com" "hackersome.com" "sweet.io"])

(defn domain-of
  [request]
  (let [domain (InternetDomainName/from (host-of request))]
    (.parts domain)))

(defn ^:private connect-parts
  [parts]
  (clojure.string/join "." parts)
  )

(defn fqdn
  "Fully Qualified Domain Name; Parts of domain as vec
  (fqdn \"www.pythonhackers.com\") => 
    [\"www\" \"pythonhackers\" \"com\"]
  Adds www. if necessary
  TODO: Normally www.domain.com is not equal to 
  domain.com but in our scenario, its same. Find a 
  better name!"
  [request]
  (let [parts (domain-of request)]
    (if (= (count parts) 3)
      (connect-parts parts)    
      (connect-parts (concat ["www"] (vec parts))))))

(defn id-of
  "Finds the ID of the request. E.g
  - /link/:id
  - /post/:id
  - /user/:id"
  ([request] (id-of request :id))
  ([request kw]
    (get-in request [:route-params kw])))

(defn body-of
  "Reads the JSON body of the request"
  [request]
  (parse-string 
    (body-as-string request)))

(def mime-types
  {:json "application/json" 
   :html "text/html"})

(defn type-of
  "Request Type check"
  [request mode]
  (log/warn request)
  (or
    (is-true (get-in request [:params mode]))
    (= (name mode) (get-in request [:params :format]))
    (= (get mime-types mode)
      (get-in request [:headers "accept"]))))

(defn whois
  "Temporary user finder.. Returns a static User ID"
  [request]
  (log/debug (get-in request [:headers "x-auth-token"]))
  243975551163827208)

(defn byte-array->str
  "Convert byte array into String"
  [bytes]
  (apply str (map char bytes)))

(defn cutoff
  [s maxlen] 
  (if (> (count s) maxlen) 
    (str (subs s 0 maxlen) "...")
    s))

(defn in? 
  "true if seq contains elm"
  [seq el]  
  (some #(= el %) seq))

; implement a multi-method for different types
(defn is-true
  [val]
  (condp instance? val
    java.lang.String (or (in? ["true" "1"] val)
                          (not (in? ["false" "0"] val)))
    false))

;; Following functions borrowed+modified slightly from 
;; Peter Taoussanis <https://www.taoensso.com>
;; https://github.com/ptaoussanis/encore
(defn !nil? [x] (not (nil? x)))
(defn   !blank? [x] (not (str/blank? x)))
(defn     !neg? [x] (not (neg? x)))
(defn  pos-int? [x] (and (integer? x) (pos? x)))
(defn !neg-int? [x] (and (integer? x) (!neg? x)))
(defn   nvec? [n x] (and (vector?  x) (= (count x) n)))
(def vec1? (partial nvec? 1))
(defn vec2?
  "Vector of 2 or not. Based on (nvec? n)"
  [x]
  (nvec? 2 x))
(defn vec3? [x] (nvec? 3 x))
(defn !nil=
  ([x y]        (and (!nil? x) (= x y)))
  ([x y & more] (and (!nil? x) (apply = x y more))))

(defn vec* [x] (if (vector? x) x (vec x)))
(defn set* [x] (if (set?    x) x (set x)))
(defn !nil-set  [x] (disj (set* x) nil))
(defn conj-some [coll ?x] (if (!nil? ?x) (conj coll ?x) coll))