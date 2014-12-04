(ns hsm.controllers.user
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [cheshire.core :refer :all]
            [ring.util.response :as resp]
            [hsm.actions :as actions]
            [hsm.utils :as utils :refer [json-resp]]))

(defn get-user
  [db id request] 
  (let [user (actions/load-user db id)]
    (json-resp user)))

(defn create-user
  [db request] 
  (log/warn request)
  (let [host  (get-in request [:headers "host"])
    body (parse-string (utils/body-as-string request))
    user-data (utils/mapkeyw body)]
    (actions/create-user db user-data)
    (json-resp { :ok body })))

(defn ^:private follow-user-actions
  [func db request]
  (let [host  (get-in request [:headers "host"])
    body (parse-string (utils/body-as-string request))
    current-user 243975551163827208
    id (BigInteger. (get-in request [:route-params :id]))]
    (func db id current-user)
    (json-resp {:ok 1})
  ))

(def follow-user (partial follow-user-actions actions/follow-user))
(def unfollow-user (partial follow-user-actions actions/unfollow-user))