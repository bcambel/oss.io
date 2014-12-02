(ns hsui.data
    (:require [clj-http.client :as client]))

(defn extract 
  [data]
  (select-keys (:data data) [:url :score :title :domain :created_utc :author]))

(defn extract-links
  [data]
  (map extract data))

(defn get-reddit
  [url]
  (try 
    (-> url
      (client/get {:accept :json :as :json})
      (:body))
    (catch Throwable t 
      (do (log/error t) 
          []))))

(def ^:dynamic def-url "http://www.reddit.com/r/Python/top.json")

(defn alinks
  ([] 
     (links def-url))
  ([url]
     (let [jdata (get-reddit url)
        link-items (extract-links (get-in jdata [:data :children]))]
        (clojure.pprint/pprint link-items)
        (respond link-items))))
