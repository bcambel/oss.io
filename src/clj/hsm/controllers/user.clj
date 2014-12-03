(ns hsm.controllers.user
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [cheshire.core :refer :all]
            [ring.util.response :as resp]
            [hsm.actions :as actions]
            [hsm.utils :as utils]))

(defn get-user
  [db id request] 
  (let [user (actions/load-user db id)]
    (->
      (resp/response (generate-string user))
      (resp/status 200))))

(defn create-user
  [db request] 
  (log/warn request)
  (let [host  (get-in request [:headers "host"])
    body (parse-string (utils/body-as-string request))
    user-data (utils/mapkeyw body)]
    (actions/create-user db user-data)
    (->
     (resp/response (generate-string {:ok body}))
     (resp/status 200))))