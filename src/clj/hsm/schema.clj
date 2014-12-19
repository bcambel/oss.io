(ns hsm.schema
  (:require 
    [clojurewerkz.cassaforte.cql  :as cql]
    [clojurewerkz.cassaforte.query :as cq]
    [qbits.hayt.dsl.statement :as hs]))

(def table-definitions {
  :link {   :id :bigint
            :title :text
            :user-id :bigint
            :created-at :bigint
            :submit-by :bigint
            :url :text 
            :primary-key [:id]}

  :platform {
            :id     :int
            :name   :text
            :slug   :text
            :url   :text
            :primary-key [:id]}

  :channel {
            :platform-id :int
            :id :bigint
            :name :text
            :slug :text
            :primary-key [:platform-id :id] }

  ;;index on channel -> slug

  :channel-follower {
            :channel-id :bigint
            :user-id :bigint
            :created-at :bigint
            :primary-key [:channel-id :user-id]}

  :channel-timeline {
            :channel-id :bigint
            :post-id :bigint
            :primary-key [:channel-id :post-id]}

  :discussion {
            :platform-id :int
            :id :bigint
            :last-message :bigint
            :post-id :bigint
            :published-at :bigint
            :slug :text
            :title :text
            :topic-id :int
            :user-id :bigint
            :users (cq/set-type :int)
            :primary-key [:platform-id :id]}

  :discussion-counter {
            :id :bigint
            :follower-count :counter
            :message-count :counter
            :user-count :counter
            :view-count :counter
            :primary-key [:id]}

  :discussion-follower {
            :disc-id :bigint
            :user-id :bigint
            :created-at :bigint
            :primary-key [:disc-id :user-id]}

  :discussion-post {
            :disc-id :bigint
            :post-id :bigint
            :user-id :bigint
            :primary-key [:disc-id :post-id :user-id]}

  :post {
            :user-id :bigint
            :id :bigint
            :channel-id :int
            :created-at :bigint
            :deleted :boolean
            :discussion-id :bigint
            :ext-id :text
            :flagged :boolean
            :has-channel :boolean
            :has-url :boolean
            :html :text
            :reply-to-id :bigint
            :reply-to-nick :text
            :reply-to-uid :int
            :spam :boolean
            :stats (cq/map-type :ascii :int)
            :text :text
            :user-nick :text
            :primary-key [:id]}

  :post-counter {
            :id :bigint
            :down-votes :counter
            :karma :counter
            :replies :counter
            :up-votes :counter
            :views :counter
            :primary-key [:id]}

  ;; do we really need a post follower ? 
  ;; Maybe notification about replies ?
  :post-follower {
            :post-id :bigint
            :user-id :bigint
            :created-at :bigint
            :primary-key [:post-id :user-id]}

  :post-reply {
            :post-id :bigint
            :reply-post-id :bigint
            :primary-key [:post-id :reply-post-id]}

  :post-vote {
            :post-id :bigint
            :user-id :bigint
            :created-at :bigint
            :positive :boolean
            :primary-key [:post-id :user-id]}

  :project {
            :platform-id :int
            :id :int
            :name :text
            :primary-key [:platform-id :id]}

  :project-follower {
            :project-id :int
            :user-id :bigint
            :created-at :bigint
            :primary-key [:project-id :user-id]}

  :project-timeline {
            :project-id :int
            :post-id :bigint
            :primary-key [:project-id :post-id]}

  :topic {
            :platform-id :int
            :id :bigint
            :description :text
            :last-message-id :bigint
            :last-message-time :bigint
            :main-topic :boolean
            :name :text
            :parent-topic :bigint
            :slug :text
            :subtopics (cq/set-type :int)
            :primary-key [:platform-id :id]}

  :topic-counter {
            :id :bigint
            :discussions :counter
            :messages :counter
            :views :counter
            :primary-key [:id]}

  :topic-discussion {
            :topic-id :bigint
            :discussion-id :bigint
            :primary-key [:topic-id :discussion-id]}
  :user {
            :id :bigint
            :created-at :bigint
            :nick :text
            :email :text
            :password :text
            :name :text
            :followers :int
            :following :int
            :roles (cq/set-type :text)
            :picture :text
            :registered-at :bigint
            :extended (cq/map-type :text :text)
            :primary-key [:id]}

  :user-counter {
            :id :bigint
            :down-vote-given :counter
            :down-vote-taken :counter
            :follower-count :counter
            :following-count :counter
            :karma :counter
            :messages :counter
            :up-vote-given :counter
            :up-vote-received :counter
            :primary-key [:id]}

  :user-discussion {
            :user-id :bigint
            :discussion-id :bigint
            :primary-key [:user-id :discussion-id]}

  :user-follower {
            :user-id :bigint
            :follower-id :bigint
            :created-at :bigint
            :primary-key [:user-id :follower-id]}

  :user-following {
            :user-id :bigint
            :following-id :bigint
            :created-at :bigint
            :primary-key [:user-id :following-id]}

  :user-post {
            :user-id :bigint
            :post-id :bigint
            :primary-key [:user-id :post-id]}

  :user-project {
            :user-id :bigint
            :project-id :int
            :primary-key [:user-id :project-id]}

  :user-timeline {
            :user-id :bigint
            :post-id :bigint
            :primary-key [:user-id :post-id]}

  :github-project {
            :id :bigint
            :description :text
            :fork :boolean
            :forks :int
            :full-name :text
            :homepage :text
            :language :text
            :master-branch :text
            :name :text
            :network-count :int
            :open-issues :int
            :owner :text
            :url :text
            :watchers :int
            :primary-key [:id]}

  :github-user {
            :login :text
            :bio :text
            :blog :text
            :company :text
            :email :text
            :followers :int
            :following :int
            :full-profile :boolean
            :id :int
            :image :text
            :location :text
            :name :text
            :public-gists :int
            :public-repos :int
            :url :text
            :type :text
            :primary-key [:login]}

  :github-user-list {
            :user :text
            :followers (cq/set-type :text)
            :following (cq/set-type :text)
            :starred (cq/set-type :text)
            :primary-key [:user]}
})