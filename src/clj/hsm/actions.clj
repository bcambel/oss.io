(ns hsm.actions
  (:require [clojurewerkz.cassaforte.cql  :as cql]))

;; USER

(defn follow-user
    [userA userB])

(defn unfollow-user
    [userA userB])

(defn load-user
    [user-id])

(defn load-user-activity [user-id])
(defn load-user-followers [user-id])
(defn load-user-following [user-id])

(defn get-profile
    [user visitor])

(defn get-profile-by-nick
    [nick visitor]
    (let [user nick]
        (get-profile user)
        ))



(defn create-user
    [db user-data]
    (let [conn (:connection db)]
      (cql/insert conn :user user-data)))

;; DISCUSS

(defn create-discussion
  [user data]
  )

(defn new-discussion-post
  [discussion post]
  )

(defn load-discussion [id])

(defn follow-discussion [discussion user])

(defn unfollow-discussion [discussion user])

(defn load-discussion-posts [id])

(defn delete-discussion [id])

;; POST Related 

(defn load-posts [])

(defn new-post [data])

(defn edit-post [data])

(defn upvote-post [post user])

(defn delete-post [post user])