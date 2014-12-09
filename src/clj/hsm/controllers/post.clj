(ns hsm.controllers.post
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [cheshire.core :refer :all]
            [ring.util.response :as resp]
            [hsm.actions :as actions]
            [hsm.utils :as utils :refer [json-resp]]))

(defn whois 
  [req]
  (log/debug (get-in req [:headers "x-auth-token"]))
  243975551163827208
  )

(defn create-link
  [db request] 
  (log/warn request)
  (let [host  (get-in request [:headers "host"])
        body (parse-string (utils/body-as-string request))
        user (whois request)
        link-data (utils/mapkeyw body)]
    (actions/create-link db link-data user)
    (json-resp { :ok body })))

(defn upvote-link
  [db request]
  (let [host  (get-in request [:headers "host"])
        body (parse-string (utils/body-as-string request))
        link-id (BigInteger. (get-in request [:route-params :id]))
        user (whois request)]
        (actions/upvote-post db link-id user)
        (json-resp {:ok 1})
        ))

(defn show-link
  [db request]
  (let [host  (get-in request [:headers "host"])
        body (parse-string (utils/body-as-string request))
        link-id (BigInteger. (get-in request [:route-params :id]))
        user (whois request)]
    (json-resp (actions/get-link db link-id user))))
