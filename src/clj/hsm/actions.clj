(ns hsm.actions
  (:require
    [clojure.core.memoize :as memo]
    [taoensso.timbre :as log]
    [clojure.string :as str]
    [schema.core :as s]
    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]
    [honeysql.helpers :refer :all]
    [clj-kryo.core :as kryo]
    [hsm.utils :refer :all]
    [hsm.cache :as cache]
    [hsm.system.pg :refer [pg-db]]))


(def User {:email s/Str :name s/Str :nick s/Str })

;; DISCUSS
(def Discussion {:title s/Str :post s/Str})

(def Post {:text s/Str})

(defn stringify-id
  [dict]
  (assoc dict :id (str (:id dict))))

(defn load-all-users
  [db batch-size]
  (let [batch-size (if (> batch-size 1000) 1000 batch-size)
        conn (:connection db)]
    ; (cql/iterate-table conn :github_user :login batch-size)

    ))

(defn load-project
  [db proj]
  (let [conn (:connection db)]
    (jdbc/query pg-db (-> (select :*)
                       (from :github_project)
                       (where [:= :full_name proj])
                       (limit 1)
                       (sql/build)
                       (sql/format :quoting :ansi)))))

(defn load-projects-by-id
  [db proj-list]
  (when-not (empty? proj-list)
  (let [conn (:connection db)]
     (jdbc/query pg-db
      (-> (select :full_name)
         (from :github_project)
         (where [:in :full_name proj-list])
         (limit 1e3)
         (sql/build)
         (sql/format :quoting :ansi))))))

(defn ensure-table-extras
  [table pk-field pk-val]
  (let [cnt (count (jdbc/query pg-db (->
                     (select pk-field)
                     (from table)
                     (where [:= pk-field pk-val])
                     (limit 1)
                     (sql/build)
                     (sql/format :quoting :ansi))))]
    (when (zero? cnt)
      (jdbc/insert! pg-db table {pk-field pk-val}))))

(defn update-table-kryo-field
  "Atomically update the kryo field of a table
  Usage: (update-table-kryo-field
              :github_project :full_name \"bcambel/hackersome\"
              :watchers [:me :you :him] )
  "
  [table pk-field pk-val field value]
  (let [stmt (-> (update table)
                  (sset {field (kryo/serialize value)})
                  (where [:= pk-field pk-val])
                  (sql/build)
                  (sql/format :quoting :ansi)) ]
    (log/info stmt)
    (jdbc/execute! pg-db stmt)
  ))

(defn update-table-field
  "Atomically update the field of a table
  Usage: (update-table-kryo-field
              :github_project :full_name \"bcambel/hackersome\"
              :watchers [:me :you :him] )
  "
  [table pk-field pk-val field value]
  (let [stmt (-> (update table)
                  (sset {field value})
                  (where [:= pk-field pk-val])
                  (sql/build)
                  (sql/format :quoting :ansi)) ]
    (try
      (jdbc/execute! pg-db stmt)
      (catch Throwable t
        (do (log/error t)
        false)))))

(defn set-project-readme
  [proj readme]
  (update-table-field :github_project :full_name proj :readme readme))

(defn load-project-extras*
  [db proj & fields]
  (let [fields-to-select (if (empty? fields) [:*] fields)
        q (-> (apply (partial select) fields-to-select)
             (from :github_project_list)
             (where [:= :proj proj])
             (limit 1)
             (sql/build)
             (sql/format :quoting :ansi))
        ; _ (log/info q)
        proj-extras (first (jdbc/query pg-db q))]
    (merge proj-extras
      { :watchers (if-not (nil? (:watchers proj-extras))
                      (-> (:watchers proj-extras) (kryo/deserialize))
                        #{})
        :contributors (if-not (nil? (:contributors proj-extras))
                        (-> (:contributors proj-extras) (kryo/deserialize))
                        #{})
        :stargazers (if-not (nil? (:stargazers proj-extras))
                      (-> (:stargazers proj-extras) (kryo/deserialize))
                      #{})
        })))

(defn load-user2
  [db user-id]
  ; (log/infof "Loading user[%s] from DB..." user-id)

  (let [conn (:connection db)
        user (->
                (jdbc/query pg-db (-> (select :*)
                                 (from :github_user)
                                 (where [:= :login user-id])
                                 (limit 1)
                                 (sql/build)
                                 (sql/format :quoting :ansi)))
                first)]
      ; (log/debug "User loaded" user)
      user

      ))

(defn user-extras
  [db user-id & fields ]
  (let [fields-to-select (if (empty? fields) [:*] fields)
        q (-> (apply (partial select) fields-to-select)
               (from :github_user_list)
               (where [:= :login user-id])
               (limit 1)
               (sql/build)
               (sql/format :quoting :ansi))
        user-extras (first (jdbc/query pg-db q))
        deserialize (fn[x] (if-not (nil? (x user-extras))
                                  (-> (x user-extras) (kryo/deserialize))
                                  #{}))]
      (merge user-extras
        { :stargazers (deserialize :stargazers)
          :followers (deserialize :followers)
          :following (deserialize :following)
          :starred (deserialize :starred)
          :repos (deserialize :repos)})))

(defn top-users-in
  [users limit-by]
  (take limit-by
    (reverse (sort-by :followers users))))

(defn load-users-by-id
  [db user-ids]
  ; (log/info "Loading users..# " (count user-ids))
  (if (empty? user-ids)
    []
    (let [user-ids (max-element user-ids 100)
          conn (:connection db)]
      ; (log/warn "Fetching user-ids" user-ids)
      (jdbc/query pg-db
        (->
          (select :*)
          (from :github_user)
          (limit 100)
          (where [:in :login user-ids])
          (sql/format :quoting :ansi))))))

(defn user-projects-es*
  [id maximum]
  ; (log/info id)
  (jdbc/query pg-db
    (->
      (select :*)
      (from :github_project)
      (limit maximum)
      (where [:= :owner id])
      (order-by [:watchers :desc])
      (sql/format :quoting :ansi))))


(defn list-top-proj*
  [lang num]
  (log/info "Querying Top projects for " lang)

  (jdbc/query pg-db
    (->
      (select :*)
      (from :github_project)
      (limit num)
      (where [:= :language lang])
      (order-by [:watchers :desc])
      (sql/format :quoting :ansi))))

(def list-top-proj
  (memo/ttl list-top-proj* :ttl/threshold 6000000 ))
