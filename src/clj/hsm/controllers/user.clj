(ns hsm.controllers.user
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [cheshire.core :refer :all]
            [ring.util.response :as resp]
            [hsm.actions :as actions]
            [hsm.pipe.event :as event-pipe]
            [hsm.ring :refer [json-resp]]
            [hsm.utils :as utils :refer [body-of host-of whois]]))

(defn get-user
  [db id request] 
  (let [user (actions/load-user db id)]
    (json-resp user)))

(defn create-user
  [[db event-chan] request] 
  (log/warn request)
  (let [host  (get-in request [:headers "host"])
    body (parse-string (utils/body-as-string request))
    user-data (utils/mapkeyw body)]
    (actions/create-user db user-data)
    (event-pipe/create-user event-chan user-data)
    (json-resp { :ok body })))

(defn ^:private follow-user-actions
  [func act-name [db event-chan] request]
  (let [host  (host-of request)
        body (body-of request)
        current-user 243975551163827208
        id (BigInteger. (get-in request [:route-params :id]))]
    (func db id current-user)
    (event-pipe/follow-user-event act-name event-chan {:current-user current-user :user id})
    (json-resp {:ok 1})))

(def follow-user (partial follow-user-actions actions/follow-user :follow-user))
(def unfollow-user (partial follow-user-actions actions/unfollow-user :unfollow-user))

(defn ^:private get-user-detail
  [func [db event-chan] request]
  (let [host  (host-of request)
        body (body-of request)
        current-user (whois request)
        user-id (BigInteger. (get-in request [:route-params :id]))]
        (json-resp (func db user-id))))

(def get-user-following (partial get-user-detail actions/load-user-following))
(def get-user-followers (partial get-user-detail actions/load-user-followers))

(defn get-user-activity
  [[db event-chan] request]
  (let [host  (host-of request)
        body (body-of request)]

        )
  )