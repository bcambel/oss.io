(ns hsm.controllers.discussion
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [cheshire.core :refer :all]
            [ring.util.response :as resp]
            [hsm.actions :as actions]
            [hsm.utils :as utils]))

(defn json-resp 
  [data]
  (-> (generate-string data)
        (resp/response)
        (resp/status 200)))

(defn get-discussion 
  [db id request]
  (let [discussion (actions/load-discussion db (BigInteger. id))]
    (log/info "[DISC]Loading " id discussion)
    (json-resp discussion)))


(defn post-discussion
  [db id request]
  (let [host  (get-in request [:headers "host"])
    body (parse-string (utils/body-as-string request))
    platform 1
    user 243975551163827208
    discussion-id (BigInteger. id)
    data (utils/mapkeyw body)]
    (let [result (actions/new-discussion-post db user discussion-id data)]
      (json-resp result))))

(defn create-discussion
  [db request] 
  (log/warn request)
  (let [host  (get-in request [:headers "host"])
    body (parse-string (utils/body-as-string request))
    platform 1
    user 243975551163827208
    data (utils/mapkeyw body)]
    (let [res (actions/create-discussion db platform user data)]
      (json-resp res))))