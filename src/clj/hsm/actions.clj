  (ns hsm.actions
  (:require
    [clojure.core.memoize :as memo]
    [clojure.tools.logging :as log]
    [clojure.string :as str]
    [schema.core :as s]
    [clojurewerkz.cassaforte.cql  :as cql]
    [clojurewerkz.cassaforte.query :as dbq]
    [qbits.hayt :as hayt]
    [qbits.hayt.dsl.statement :as hs]
    [clojurewerkz.elastisch.rest :as esr]
    [clojurewerkz.elastisch.rest.index :as esi]
    [clojurewerkz.elastisch.rest.document :as esd]
    [clojurewerkz.elastisch.rest.response :as esrsp]
    [clojurewerkz.elastisch.query :as q]
    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]
    [honeysql.helpers :refer :all]
    [clj-kryo.core :as kryo]
    [hsm.utils :refer :all]
    [hsm.cache :as cache]
    [hsm.system.pg :refer [pg-db]]))

; (defn load-user
;   [db user-id]
;   (let [conn (:connection db)]
;       (cql/select conn :user
;         (dbq/where [[= :nick user-id]]))))

(def User {:email s/Str :name s/Str :nick s/Str })

; (defn create-user
;   [db user-data]
;   (s/validate User user-data)
;   (let [conn (:connection db)
;         dt (now->ep)
;         additional {:id (id-generate) :password ""
;                     :roles #{"user"} :created_at dt  :registered_at dt }
;         user-info (merge additional user-data)]
;     (cql/insert conn :user user-info)))

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
  (let [conn (:connection db)]
     (jdbc/query pg-db
      (-> (select :full_name)
         (from :github_project)
         (where [:in :full_name proj-list])
         (limit 1e3)
         (sql/build)
         (sql/format :quoting :ansi)))))

; (defn load-project-extras
;   [db proj]
;   (let [conn (:connection db)]
;     (first (cql/select conn :github_project_list
;       (dbq/limit 1)
;       (dbq/where [[= :proj proj]])))))

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
  "Atomically update the field of a table
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

(defn load-project-extras*
  [db proj & fields]
  (let [fields-to-select (if (empty? fields) [:*] fields)
        q (-> (apply (partial select) fields-to-select)
             (from :github_project_list)
             (where [:= :proj proj])
             (limit 1)
             (sql/build)
             (sql/format :quoting :ansi))
        _ (log/info q)
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

; (defn list-top-user
;   [db platform limit-by]
;   (log/warn "[TOP-USER] Fetching " platform limit-by)
;   (let [conn (:connection db)
;         limit-by (if (> limit-by 100) 100 limit-by)]
;     (cql/select conn :github_user
;       (dbq/limit limit-by))
;   ))

(defn load-user2
  [db user-id]
  (let [conn (:connection db)]
      (->
      (jdbc/query pg-db (-> (select :*)
                       (from :github_user)
                       (where [:= :login user-id])
                       (limit 1)
                       (sql/build)
                       (sql/format :quoting :ansi)))
      first)))

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

(defn top-users
  [db limit-by top-n]
  (let [conn (:connection db)
        users (cql/select conn :github_user
                (dbq/limit limit-by)
                (dbq/columns :login :followers))]
    (top-users-in users top-n)))

(defn load-users-by-id
  [db user-ids]
  (when-not (empty? user-ids)
  (let [user-ids (max-element user-ids 100)
        conn (:connection db)]
    (log/warn "Fetching user-ids" user-ids)
    (jdbc/query pg-db
      (->
        (select :*)
        (from :github_user)
        (limit 100)
        (where [:in :login user-ids])
        (sql/format :quoting :ansi))))))

; (defn fetch-top-users
;   [db limit-by top-n]
;   (let [toppers (top-users db limit-by top-n)
;         user-ids (mapv :login toppers)]
;     (load-users-by-id db user-ids)))

; (defn load-users
;   [db limit-by]
;   (let [conn (:connection db)]
;     (when-let [users (cql/select conn :github_user
;                       (dbq/columns :login :followers :name :email :blog)
;                       (dbq/limit 1000)
;                       (dbq/where [[= :full_profile true]]))]
;       (mapv #(select-keys % [:login :followers :name :email :blog])
;         (top-users-in users limit-by)))))


(defn user-projects-es*
  [else user limit]
  (log/warn "[ES_PROJ]" user )
  (let [res (esd/search (:conn else) (:index else) "github_project"
                 :sort [ { :watchers {:order :desc}}]
                 :size limit
                  :query (q/filtered
                          :filter   (q/term
                                        :owner (str/lower-case user))))
          n (esrsp/total-hits res)
          hits (esrsp/hits-from res)]
    (map :_source hits)))

(defn top-projects-es*
  [else platform limit]
  (let [res (esd/search (:conn else) (:index else) "github_project"
                 :sort [ { :watchers {:order :desc}}]
                 :size limit
                  :query (q/filtered
                          :filter   (q/term
                                        :language (str/lower-case platform))))
          n (esrsp/total-hits res)
          hits (esrsp/hits-from res)]
    (map :_source hits)))

(def top-projects-es (memo/ttl top-projects-es* :ttl/threshold 6000000 ))
