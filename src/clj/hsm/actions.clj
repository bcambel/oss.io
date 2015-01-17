  (ns hsm.actions
  (:require
    [clojure.core.memoize :as memo]
    [clojure.tools.logging :as log]
    [schema.core :as s]
    [clojurewerkz.cassaforte.cql  :as cql]
    [clojurewerkz.cassaforte.query :as dbq]
    [qbits.hayt :as hayt]
    [qbits.hayt.dsl.statement :as hs]
    [hsm.utils :refer :all]
    [hsm.cache :as cache]))

(declare list-top-proj**)
(declare load-projects-by-int-id)

;; USER
(defn follow-user
  [db user current-user]
  (let [conn (:connection db)]
    (cql/atomic-batch conn
          (dbq/queries
            (hs/insert :user_follower 
                (dbq/values {:user_id user 
                             :follower_id current-user 
                             :created_at (now->ep)}))
            (hs/insert :user_following 
                (dbq/values {:user_id current-user 
                             :following_id user 
                             :created_at (now->ep)}))))))

(defn unfollow-user
  [db user current-user]
  (let [conn (:connection db)]
    (cql/atomic-batch conn
          (dbq/queries
            (hs/delete :user_follower
                (dbq/values {:user_id user
                             :follower_id current-user }))
            (hs/delete :user_following
                (dbq/values {:user_id current-user
                             :following_id user}))))))

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
    (log/warn post-info)
    (log/warn discussion-info)
    (cql/atomic-batch conn
      (dbq/queries
        (hs/insert :post (dbq/values post-info))
        (hs/insert :discussion (dbq/values discussion-info))))
    ;; add this update into the atomic-batch as well
    (cql/update conn :post_counter 
       {
        :karma (dbq/increment-by 1)
        :up_votes (dbq/increment-by 1)
        :views (dbq/increment-by 1)}
      (dbq/where {:id (:id post-info)}))
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
        (hs/update :post_counter
            (dbq/values {:karma 0 :upvotes (0) :views 1 })
            (dbq/where {:id post-id}))
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

(defn create-post
  [db post user]
  (s/validate Post post)
  (let [conn (:connection db)
        post-id (id-generate)
        post-data (merge post {:id post-id 
                               :user_id user 
                               :created_at (now->ep) })]
      ;; counter column updates cannot be batched with normal statements
      ;; we have to execute 2 queries to satisfy our req.
      ;; might throw the counter update task into a Worker Queue
      (cql/insert conn :post post-data)
      (cql/update conn :post_counter
         {:karma (dbq/increment-by 1)
          :up_votes (dbq/increment-by 1)
          :views (dbq/increment-by 1)}
        (dbq/where {:id post-id}))
    post-id
    ))

(defn edit-post [db data])

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
                        (cql/select conn :link 
                          (dbq/where [[= :id link-id]])))]
        (when-not (empty? link)
          (merge link
            (first (cql/select conn :post_counter
                      (dbq/where [[= :id link-id]]))))))))
(defn list-links 
  [db time-filter user]
  (let [conn (:connection db)]
    (cql/select conn :link)))

(defn stringify-id
  [dict]
  (assoc dict :id (str (:id dict))))



(defn load-projects*
  [db platform limit-by]
  (log/info "[LIST-PROJ] Fetching " platform limit-by)
  (let [conn (:connection db)
        limit-by (if (> limit-by 100) 100 limit-by)]
    (when-let [projects (cql/select conn :github_project
                          (dbq/limit 10000) ; place a hard limit
                           (when-not (nil? platform) (dbq/where 
                                        [[= :language platform]])))]
      projects)))

(defn load-all-projects
  [db batch-size]
  (let [batch-size (if (> batch-size 100) 100 batch-size)
        conn (:connection db)]
    (cql/iterate-table conn :github_project :full_name batch-size)))

(defn ^:private fetch-top-proj
  [db redis language size]
  (log/warn "Start REDIS TOP PROJ Fetch" language)
  (let [projects (cache/ssort-fetch redis (str "lang-" language) 0 (- size 1))
        project-ids (mapv #(Integer/parseInt (first (.split % "-"))) projects)]
    (log/warn project-ids)
    (let [found-projects (load-projects-by-int-id db project-ids)]
      ; (log/warn found-projects)
      (log/warn (count found-projects))
      found-projects)))

(defn list-top-proj*
  "List top projects; 
  Results will be fetched from redis"
  [db redis platform limit-by]
  (let [cached-projects (fetch-top-proj db redis platform limit-by)]
    (if (!!nil? cached-projects)
        cached-projects
        (list-top-proj** db platform limit-by))))

(defn list-top-proj**
  "Given platform/language returns top n projects.
  TODO: DELETE THIS"
  [db platform limit-by]
  (log/info "[LIST-TOP-PROJ] Fetching " platform limit-by)
  (let [conn (:connection db)
        limit-by (if (> limit-by 100) 100 limit-by)]
    (when-let [projects (cql/select conn :github_project
                        (dbq/limit 10000) ; place a hard limit
                          (dbq/where [[= :language platform]]))]
      (map
        stringify-id
        (take limit-by (reverse
                          (sort-by :watchers projects)))))))

(def list-top-proj (memo/ttl list-top-proj* :ttl/threshold 6000000 ))

(defn load-project
  [db proj]
  (let [conn (:connection db)]
    (cql/select conn :github_project
      (dbq/limit 1)
      (dbq/where [[= :full_name proj]]))))

(defn load-projects-by-id
  [db proj-list]
  (let [conn (:connection db)]
    (cql/select conn :github_project
      (dbq/where [[:in :full_name proj-list]]))))

(defn load-projects-by-int-id
  [db proj-list]
  (let [conn (:connection db)]
    ;; FIX THIS SHIT!@!@!@!
     (mapcat #(cql/select conn :github_project
            (dbq/where [[:= :id %]]))
      proj-list)))

(defn load-project-extras
  [db proj]
  (let [conn (:connection db)]
    (first (cql/select conn :github_project_list
      (dbq/limit 1)
      (dbq/where [[= :proj proj]])))))

(defn list-top-disc
  [db platform limit-by]
  (log/warn "[TOP-DISC] Fetching " platform limit-by)
  (let [conn (:connection db)
        limit-by (if (> limit-by 100) 100 limit-by)]
    (when-let [discussions (cql/select conn :discussion 
      (dbq/limit limit-by))]
      ; TODO: horrible to do this way. 
      ; Cache these top project IDs by platform
      ; and do a quick load
      (doall (map #(load-discussion db (:id %)) discussions)
    ))))

(defn list-top-user
  [db platform limit-by]
  (log/warn "[TOP-USER] Fetching " platform limit-by)
  (let [conn (:connection db)
        limit-by (if (> limit-by 100) 100 limit-by)]
    (cql/select conn :github_user
      (dbq/limit limit-by))
  ))

(defn load-user2
  [db user-id]
  (let [conn (:connection db)]
      (first (cql/select conn :github_user
        (dbq/where [[= :login user-id]])))))

(defn user-extras
  [db user-id]
  (let [conn (:connection db)]
      (first (cql/select conn :github_user_list
        (dbq/where [[= :user user-id]])))))

(defn top-users-in
  [users limit-by]
  (take limit-by 
    (reverse (sort-by :followers users))))

(defn top-users
  [db limit-by top-n]
  (let [conn (:connection db)
        users (cql/select conn :github_user 
                (dbq/limit limit-by)
                (dbq/columns :login :followers))]
    (top-users-in users top-n)))

(defn load-users-by-id
  [db user-ids]
  (log/warn "Fetching user-ids" user-ids)
  (let [conn (:connection db)]
    (cql/select conn :github_user
        (dbq/where [[:in :login user-ids]]))))

(defn fetch-top-users
  [db limit-by top-n]
  (let [toppers (top-users db limit-by top-n)
        user-ids (mapv :login toppers)]
    (load-users-by-id db user-ids)))

(defn load-users
  [db limit-by]
  (let [conn (:connection db)]
    (when-let [users (cql/select conn :github_user
                      (dbq/columns :login :followers :name :email :blog)
                      (dbq/limit 1000)
                      (dbq/where [[= :full_profile true]]))]
      (mapv #(select-keys % [:login :followers :name :email :blog]) 
        (top-users-in users limit-by)))))

(defn load-collections
  [db limit-by]
  (let [conn (:connection db)]
      (cql/select conn :collection)))

(defn create-collection
  [db data]
  (let [conn (:connection db)]
    (cql/insert conn
      :collection data)
    (cql/insert conn 
      :collection_list { :id (:id data)})))

(defn update-collection
  [db id items]
  (let [conn (:connection db)]
    (cql/update conn :collection 
      {:items items}
      (dbq/where [[:= :id id]])))) 

(defn get-collection
  [db id]
  (let [conn (:connection db)]
      (cql/select conn :collection
        (dbq/limit 1)
        (dbq/where [[:= :id id]]))))

(defn get-collection-extras-by-id
  [db id-list]
  (let [conn (:connection db)]
      (cql/select conn :collection_list
        (dbq/limit 1000)
        (dbq/where [[:in :id id-list]]))))

(defn get-collection-extra
  [db id]
  (let [conn (:connection db)]
      (first (cql/select conn :collection_list
        (dbq/limit 1)
        (dbq/where [[:= :id id]])))))

(defn add-collection-fork
  [db id fork-id]
  (let [conn (:connection db)]
      (cql/update conn :collection_list
        {:forks [+ #{(str fork-id)}]}
        (dbq/where [[:= :id id]]))))

(defn delete-collection
  [db id]
  (let [conn (:connection db)]
      (cql/delete conn :collection
        (dbq/where [[:= :id id]]))))

(defn star-collection
  [db id user-set]
  (let [conn (:connection db)]
      (cql/update conn :collection_list
        {:stargazers [+ user-set]}
        (dbq/where [[:= :id id]]))))
