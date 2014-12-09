(ns hsm.actions
  (:require 
    [clojure.tools.logging :as log]
    [schema.core :as s]
    [clojurewerkz.cassaforte.cql  :as cql]
    [clojurewerkz.cassaforte.query :as dbq]
    [qbits.hayt.dsl.statement :as hs]
    [hsm.utils :refer [id-generate now->ep]]))

;; USER
(defn follow-user
  [db user current-user]
  (let [conn (:connection db)]
    (cql/atomic-batch conn
          (dbq/queries
            (hs/insert :user_follower (dbq/values {:user_id user :follower_id current-user :created_at (now->ep)}))
            (hs/insert :user_following (dbq/values {:user_id current-user :following_id user :created_at (now->ep)}))))))

(defn unfollow-user
  [db user current-user]
  (let [conn (:connection db)]
    (cql/atomic-batch conn
          (dbq/queries
            (hs/delete :user_follower (dbq/values {:user_id user :follower_id current-user }))
            (hs/delete :user_following (dbq/values {:user_id current-user :following_id user }))))))

(defn load-user
  [db user-id]
  (let [conn (:connection db)]
      (cql/select conn :user
        (dbq/where [[= :nick user-id]]))))

(def User {:email s/Str :name s/Str :nick s/Str })

(defn create-user
  [db user-data]
  (s/validate User user-data)
  (let [conn (:connection db)
        dt (now->ep)
        additional {:id (id-generate) :password ""
                    :roles #{"user"} :created_at dt  :registered_at dt }
        user-info (merge additional user-data)]
    (cql/insert conn :user user-info)))

(defn load-user-activity
  [db user-id])

(defn load-user-followers
  [db user-id]
  (let [conn (:connection db)]
    (cql/select conn :user_follower
      (dbq/columns :follower_id)
      (dbq/where [[= :user_id user-id]]))))

(defn load-user-following
  [db user-id]
  (let [conn (:connection db)]
    (map :following_id (cql/select conn :user_following
      (dbq/columns :following_id)
      (dbq/where [[= :user_id user-id]])))))

(defn get-profile
  [db user visitor])

(defn get-profile-by-nick
  [nick visitor]
  (let [user nick]
    (get-profile user)))

;; DISCUSS
(def Discussion {:title s/Str :post s/Str})

(defn create-discussion
  [db platform user data]
  (s/validate Discussion data)  
  (let [conn (:connection db)
        post (:post data)
        post-info {:id (id-generate) :user_id user :text post}
        additional {:id (id-generate) :published_at (now->ep) 
                    :user_id user :platform_id platform
                    :post_id (:id post-info)}
        
        discussion-info (merge additional (dissoc data :post))]
    (cql/atomic-batch conn 
      (dbq/queries
        (hs/insert :post (dbq/values post-info))
        (hs/insert :discussion (dbq/values discussion-info))))
    (cql/update conn :post_counter (dbq/values {
                              :karma (dbq/increment-by 1)
                              :upvotes (dbq/increment-by 1)
                              :views (dbq/increment-by 1)} ) (dbq/where {:id (:id post-info)}))
    additional))

(def Post {:text s/Str})

(defn new-discussion-post
  [db user disc-id post]
  (let [conn (:connection db)
        post-id (id-generate)
        post-data (merge post {:id post-id :user_id user })]
        (log/warn post post-data)
    (cql/atomic-batch conn
      (dbq/queries
        (hs/insert :post (dbq/values post-data))
        (hs/update :post_counter (dbq/values {:karma 0 :upvotes (0) :views 1 }) (dbq/where {:id post-id}))
        (hs/insert :discussion_post
          (dbq/values { :post_id post-id
                        :user_id user
                        :disc_id disc-id }))))
    ; (cql/update conn :post_counter {:id post-id
    ;                           ; :karma 1;(dbq/increment-by 1)
    ;                           ; :upvotes 1;(dbq/increment-by 1)
    ;                           ; :views 1;(dbq/increment-by 1)
    ;                           })
    post-id))

(defn load-post
  [db post-id]
  (let [conn (:connection db)]
    (first (or (cql/select conn :post (dbq/where [[= :id post-id]])) []))))

(defn load-discussion
  [db disc-id]
  (let [conn (:connection db)]
    (when-let [discussion (first (cql/select conn :discussion
                    (dbq/where [[= :id disc-id] [= :platform_id 1]])))]
      (log/warn discussion)
      (let [post-id (:post_id discussion)]
        (assoc discussion :post (load-post db post-id))))))

(defn follow-discussion
  [db disc-id user-id]
  (let [conn (:connection db)]
    (cql/atomic-batch conn
      (dbq/queries
        ;; TODO: Add UserDiscussion table
        (hs/insert :discussion_follower
          (dbq/values { :created_at (now->ep)
                        :user_id user-id
                        :disc_id disc-id }))))))

(defn unfollow-discussion
  [db disc-id user-id]
  (let [conn (:connection db)]
    (cql/delete conn
      :discussion_follower
      {:user_id user-id
        :disc_id disc-id})))

(defn load-discussion-posts 
  [db disc-id]
  (let [conn (:connection db)]
    (when-let [post-ids (mapv :post_id 
                          (cql/select conn :discussion_post 
                            (dbq/where [[= :disc_id disc-id]])))]
    (log/warn "Found " post-ids)
      (cql/select conn :post (dbq/where [[:in :id post-ids]])))))

(defn delete-discussion [id])

;; POST Related 

(defn new-post [data])

(defn edit-post [data])

(defn upvote-post [db post user]
  (let [conn (:connection db)]
    (cql/insert conn :post_vote { 
      :post_id post 
      :user_id user
      :created_at (now->ep) 
      :positive true})))

(defn delete-post [post user])

(defn create-link
  [db link-data user]
  (let [conn (:connection db)]
    (cql/insert conn :link
      (merge link-data {
        :id (id-generate)
        :submit_by user
        :created_at (now->ep)}))))

(defn get-link 
  [db link-id user]
    (let [conn (:connection db)]
      (when-let [link (first 
                        (cql/select conn :link (dbq/where [[= :id link-id]])))]
        (when-not (empty? link)
          (merge link 
            (first (cql/select conn :post_counter 
                      (dbq/where [[= :id link-id]]))))))))
