(ns hsm.fetcher
  (:require [com.climate.claypoole :as cp]
    [cheshire.core :refer :all]
    [clj-http.client :as http]
    [taoensso.timbre :as log]) 
  (:gen-class))

(defn safe-parse
  [body]
  (try
    (parse-string body)
    (catch Throwable t
      (log/error t body))))

(defn safe-get
  [url]
  (try
    (let [response (http/get url)]
      (:body response))
    (catch Throwable t
      (log/warn t))))

(defn get-user [user-action] 
  (log/info "Download " user-action)
  (when-let [body (safe-get (format "http://hackersome.com/%s?json=1" user-action))]
    (let [content (safe-parse body)]
      [user-action content])))

(defn -main
  [action & [thread-count]]
  (let [[act datasource] (get-user action)]
    (cp/with-shutdown! [net-pool (cp/threadpool (or (Integer/parseInt thread-count) 20))]
      (def results (cp/upmap net-pool get-user (map #(str "/user2/" %) datasource)))

      (doseq [[user-action content] results] 
        (log/info user-action content))
      ))
  (log/warn "DONE!")
  (System/exit 0))