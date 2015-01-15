(ns hsm.fetcher
  (:require [com.climate.claypoole :as cp]
    [cheshire.core :refer :all]
    [clj-http.client :as http]
    [clojure.tools.logging :as log]) 
  (:gen-class))

(defn get-user [user-action] 
  (log/info "Download " user-action)
  (let [content (parse-string 
                  (:body (http/get 
                    (format "http://hackersome.com/user2/%s?json=1" user-action))))]
      [user-action content]
    ))

(defn -main
  [action & [thread-count]]
  (let [[act datasource] (get-user action)]
    (cp/with-shutdown! [net-pool (cp/threadpool (or (Integer/parseInt thread-count) 20))]
      (def results (cp/upmap net-pool get-user datasource))

      (doseq [[user-action content] results] 
        (log/info user-action content))
      ))
  (log/warn "DONE!")
  (System/exit 0))