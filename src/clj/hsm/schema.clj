; (ns hsm.schema
;   (:require
;     [taoensso.timbre :as log]
;     [clojurewerkz.cassaforte.cql  :as cql]
;     [clojurewerkz.cassaforte.query :as cq]
;     [qbits.hayt.dsl.statement :as hs]))
;
; (def table-definitions {
;   :link {   :id :bigint
;             :title :text
;             :user_id :bigint
;             :created_at :bigint
;             :submit_by :bigint
;             :url :text
;             :domain :text
;             :primary-key [:id]}
;
;   :platform {
;             :id     :int
;             :name   :text
;             :slug   :text
;             :url   :text
;             :primary-key [:id]}
;
;   :channel {
;             :platform_id :int
;             :id :bigint
;             :name :text
;             :slug :text
;             :primary-key [:platform_id :id] }
;
;   ;;index on channel _> slug
;
;   :channel_follower {
;             :channel_id :bigint
;             :user_id :bigint
;             :created_at :bigint
;             :primary-key [:channel_id :user_id]}
;
;   :channel_timeline {
;             :channel_id :bigint
;             :post_id :bigint
;             :primary-key [:channel_id :post_id]}
;
;   :discussion {
;             :platform_id :int
;             :id :bigint
;             :last_message :bigint
;             :post_id :bigint
;             :published_at :bigint
;             :slug :text
;             :title :text
;             :topic_id :int
;             :user_id :bigint
;             :users (cq/set-type :int)
;             :primary-key [:platform_id :id]}
;
;   :discussion_counter {
;             :id :bigint
;             :follower_count :counter
;             :message_count :counter
;             :user_count :counter
;             :view_count :counter
;             :primary-key [:id]}
;
;   :discussion_follower {
;             :disc_id :bigint
;             :user_id :bigint
;             :created_at :bigint
;             :primary-key [:disc_id :user_id]}
;
;   :discussion_post {
;             :disc_id :bigint
;             :post_id :bigint
;             :user_id :bigint
;             :primary-key [:disc_id :post_id :user_id]}
;
;   :post {
;             :user_id :bigint
;             :id :bigint
;             :channel_id :int
;             :created_at :bigint
;             :deleted :boolean
;             :discussion_id :bigint
;             :ext_id :text
;             :flagged :boolean
;             :has_channel :boolean
;             :has_url :boolean
;             :html :text
;             :reply_to_id :bigint
;             :reply_to_nick :text
;             :reply_to_uid :int
;             :spam :boolean
;             :stats (cq/map-type :ascii :int)
;             :text :text
;             :user_nick :text
;             :primary-key [:id]}
;
;   :post_counter {
;             :id :bigint
;             :down_votes :counter
;             :karma :counter
;             :replies :counter
;             :up_votes :counter
;             :views :counter
;             :primary-key [:id]}
;
;   ;; do we really need a post follower ?
;   ;; Maybe notification about replies ?
;   :post_follower {
;             :post_id :bigint
;             :user_id :bigint
;             :created_at :bigint
;             :primary-key [:post_id :user_id]}
;
;   :post_reply {
;             :post_id :bigint
;             :reply_post_id :bigint
;             :primary-key [:post_id :reply_post_id]}
;
;   :post_vote {
;             :post_id :bigint
;             :user_id :bigint
;             :created_at :bigint
;             :positive :boolean
;             :primary-key [:post_id :user_id]}
;
;   :project {
;             :platform_id :int
;             :id :int
;             :name :text
;             :primary-key [:platform_id :id]}
;
;   :project_follower {
;             :project_id :int
;             :user_id :bigint
;             :created_at :bigint
;             :primary-key [:project_id :user_id]}
;
;   :project_timeline {
;             :project_id :int
;             :post_id :bigint
;             :primary-key [:project_id :post_id]}
;
;   :topic {
;             :platform_id :int
;             :id :bigint
;             :description :text
;             :last_message_id :bigint
;             :last_message_time :bigint
;             :main_topic :boolean
;             :name :text
;             :parent_topic :bigint
;             :slug :text
;             :subtopics (cq/set-type :int)
;             :primary-key [:platform_id :id]}
;
;   :topic_counter {
;             :id :bigint
;             :discussions :counter
;             :messages :counter
;             :views :counter
;             :primary-key [:id]}
;
;   :topic_discussion {
;             :topic_id :bigint
;             :discussion_id :bigint
;             :primary-key [:topic_id :discussion_id]}
;   :user {
;             :id :bigint
;             :created_at :bigint
;             :nick :text
;             :email :text
;             :password :text
;             :name :text
;             :followers :int
;             :following :int
;             :roles (cq/set-type :text)
;             :picture :text
;             :registered_at :bigint
;             :extended (cq/map-type :text :text)
;             :primary-key [:id]}
;
;   :user_counter {
;             :id :bigint
;             :down_vote_given :counter
;             :down_vote_taken :counter
;             :follower_count :counter
;             :following_count :counter
;             :karma :counter
;             :messages :counter
;             :up_vote_given :counter
;             :up_vote_received :counter
;             :primary-key [:id]}
;
;   :user_discussion {
;             :user_id :bigint
;             :discussion_id :bigint
;             :primary-key [:user_id :discussion_id]}
;
;   :user_follower {
;             :user_id :bigint
;             :follower_id :bigint
;             :created_at :bigint
;             :primary-key [:user_id :follower_id]}
;
;   :user_following {
;             :user_id :bigint
;             :following_id :bigint
;             :created_at :bigint
;             :primary-key [:user_id :following_id]}
;
;   :user_post {
;             :user_id :bigint
;             :post_id :bigint
;             :primary-key [:user_id :post_id]}
;
;   :user_project {
;             :user_id :bigint
;             :project_id :int
;             :primary-key [:user_id :project_id]}
;
;   :user_timeline {
;             :user_id :bigint
;             :post_id :bigint
;             :primary-key [:user_id :post_id]}
;
;   :collection {
;             :id           :bigint
;             :platform     :int
;             :user_id      :bigint
;             :name         :varchar
;             :description  :text
;             :items        (cq/map-type :varchar :varchar)
;             :updated      :bigint
;             :primary-key  [:id]
;   }
;   :collection_list {
;             :id           :bigint
;             :stargazers   (cq/set-type :text) ;user-ids/logins
;             :forks        (cq/set-type :text) ;collection-id
;             :primary-key  [:id]
;   }
;
;   :github_project {
;             :id :bigint
;             :description :text
;             :fork :boolean
;             :forks :int
;             :full_name :text
;             :homepage :text
;             :language :text
;             :master_branch :text
;             :name :text
;             :network_count :int
;             :open_issues :int
;             :owner :text
;             :url :text
;             :watchers :int
;             :primary-key [:full_name]}
;
;   :github_user {
;             :login :text
;             :bio :text
;             :blog :text
;             :company :text
;             :email :text
;             :followers :int
;             :following :int
;             :full_profile :boolean
;             :id :int
;             :image :text
;             :location :text
;             :name :text
;             :public_gists :int
;             :public_repos :int
;             :url :text
;             :type :text
;             :primary-key [:login]}
;
;   :github_user_list {
;             :user :text
;             :followers (cq/set-type :text)
;             :following (cq/set-type :text)
;             :starred (cq/set-type :text)
;             :repos (cq/set-type :text)
;             :primary-key [:user]}
;
;   :github_project_list {
;             :proj :text
;             :watchers (cq/set-type :text)
;             :stargazers (cq/set-type :text)
;             :contributors (cq/set-type :text)
;             :primary-key [:proj]
;   }
;
;   :github_org_members {
;             :org      :text
;             :members (cq/set-type :text)
;             :primary-key [:org]
;   }
; })
;
; (defn create-or-use-keyspace
;   "Given connection"
;   [conn keyspace]
;   (try
;     ;; in production, should never create keyspace like this!
;     (cql/create-keyspace conn (keyword keyspace)
;              (cq/with {:replication
;                     {:class "SimpleStrategy"
;                      :replication_factor 1}}))
;     (catch Throwable t
;       (log/warn t)))
;   (cql/use-keyspace conn keyspace))
;
; (defn create-db-space
;   "Given db connection creates all the necessary tables"
;   [db keyspace]
;   (let [conn (-> db :connection)]
;     (cql/use-keyspace conn keyspace)
;     (map
;       (fn [table]
;         (cql/create-table conn
;           (name table)
;           (cq/column-definitions (table table-definitions))))
;       (keys table-definitions))))
;
; (defn create-table
;   [db keyspace table table-definition]
;    (let [conn (-> db :connection)]
;     (cql/use-keyspace conn keyspace)
;     (cql/create-table conn table
;       (cq/column-definitions table-definition))))
