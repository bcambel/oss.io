(ns hsm.controllers.discussion
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [cheshire.core :refer :all]
            [ring.util.response :as resp]
            [hsm.actions :as actions]
            [hsm.pipe.event :as event-pipe]
            [hsm.utils :as utils :refer [json-resp body-of host-of whois]]))

(defn get-discussion 
  [[db event-chan] id request]
  (let [discussion (actions/load-discussion db (BigInteger. id))]
    (log/info "[DISC]Loading " id discussion)
    (json-resp discussion)))

(defn ^:private following-discussion
  [f act-name [db event-chan] id request]
  (let [host  (host-of request)
        body (body-of request)
        platform 1
        user 243975551163827208
        discussion-id (BigInteger. id)]
    (let [result (f db discussion-id user)]
      (event-pipe/follow-discussion act-name event-chan {:user user :id discussion-id})
      (json-resp result))))

(def follow-discussion (partial following-discussion actions/follow-discussion :follow-discussion))
(def unfollow-discussion (partial following-discussion actions/unfollow-discussion :unfollow-discussion))


(defn get-discussion-posts
  [[db event-chan] id request]
  (let [host  (host-of request)
        body (body-of request)
        platform 1
        user 243975551163827208
        discussion-id (BigInteger. id)
        data (utils/mapkeyw body)]
    (let [result (actions/load-discussion-posts db discussion-id )]
      (json-resp result))))

(defn post-discussion
  [[db event-chan] request]
  (log/warn request)
  (let [host  (host-of request)
        body (body-of request)
        platform 1
        id (get-in request [:route-params :id])
        user 243975551163827208
        discussion-id (BigInteger. id)
        data (utils/mapkeyw body)]
        (log/warn host body)
    (let [result (actions/new-discussion-post db user discussion-id data)]
      (event-pipe/post-discussion event-chan {:post result :discussion-id discussion-id :current-user user})
      (json-resp result))))

(defn create-discussion
  [[db event-chan] request] 
  (log/warn request)
  (let [host  (host-of request)
        body (body-of request)
        platform 1
        user (whois request)
        data (utils/mapkeyw body)]
    (let [res (actions/create-discussion db platform user data)]
      (event-pipe/create-discussion event-chan {:discussion res  :current-user user})
      (json-resp res))))