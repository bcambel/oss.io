(ns hsm.integration.ghub
    "Fetch repository information from github"
    (:require
      [clojure.string                 :as s]
      [taoensso.timbre                :as log]
      [clojure.java.jdbc              :as jdbc]
      [honeysql.core                  :as sql]
      [honeysql.helpers               :refer :all]
      ; [clojurewerkz.cassaforte.cql    :as cql]
      ; [clojurewerkz.cassaforte.query  :as dbq]
      ; [qbits.hayt.dsl.statement       :as hs]
      [clj-http.client                :as client]
      [cheshire.core                  :refer :all]
      [environ.core                   :refer [env]]
      [hsm.utils                      :refer :all]
      [hsm.conf                       :as conf]
      [hsm.system.pg                  :refer [pg-db]]
      [hsm.actions                    :as actions]
      )

    (:use [slingshot.slingshot :only [throw+ try+]]
      [clojure.data :only [diff]])

    (:import [org.postgresql.util PGobject]))


(defn pg-json [value]
  (doto (PGobject.)
    (.setType "json")
    (.setValue value)))


(def ghub-root "https://api.github.com")

(def api-params (format "client_id=%s&client_secret=%s"
                  (env :client-id)
                  (env :client-secret)))

(def ghub-url*
  (str ghub-root "/search/repositories"
    "?q=+language:%s&sort=stars&order=desc&per_page=100&"))

(def ghub-url
  (str ghub-root "/repositories?per_page=100&"))

(def header-settings
  {:socket-timeout 10000 :conn-timeout 10000})

(def base-user-fields [:id :login :type])

(def ghub-proj-fields
  [:id :name :fork :watchers :open_issues
   :language :description :full_name :homepage])

(def user-fields
  [:id :login :type :name :company :blog
  :location :email :public_repos :public_gists
  :followers :following :avatar_url] )

(defn get-config
  []
  (:data @conf/app-conf))

(defn get-url
  [url & options]
  (let [{:keys [header safe care conf]
         :or {header header-settings safe false
              care true conf (get-config)}} options
              end-point (format "%s&client_id=%s&client_secret=%s" url
                            (env :client-id)
                            (env :client-secret)) ]
    (log/info header)
    (log/info end-point)
    (try+
      (client/get end-point header)
      (catch [:status 403] {:keys [request-time headers body]}
        (log/warn "403" request-time headers))
      (catch [:status 404] {:keys [request-time headers body]}
        (log/warn "NOT FOUND" url request-time headers body))
      (catch Object _
        (when care
          (log/error (:throwable &throw-context) "Unexpected Error"))
        (when-not safe
          (throw+))))))

(defn get-url2
  [url & options]
  (let [{:keys [header safe care conf]
         :or {header header-settings safe false
              care true conf (get-config)}} options]
    (log/info header)
    (try+
      (client/get (format "%s&client_id=%s&client_secret=%s" url
                    (:github-client conf) (:github-secret conf))
        header)
      (catch [:status 403] {:keys [request-time headers body]}
        (log/warn "403" request-time headers))
      (catch [:status 404] {:keys [request-time headers body]}
        (log/warn "NOT FOUND" url request-time headers body))
      (catch Object _
        (when care
          (log/error (:throwable &throw-context) "Unexpected Error"))
        (when-not safe
          (throw+))))))

(defn find-next-url
  "Figure out the next url to call
  <https://api.github.com/search/repositories?q=...&page=2>;
  rel=\"next\", <https://api.github.com/search/repositories?q=+...&page=34>; rel=\"last\"
  "
  [stupid-header]
  (when (!nil? stupid-header)
    (try
      (log/debug stupid-header)
      (let [[next-s last-s & others] (.split stupid-header ",")
            next-page (vec (.split next-s ";"))
            is-next (.contains (last next-page) "next")]
          (log/debug next-s last-s)
          (log/info "Next UP" next-page)
        (when is-next
          (s/replace (subs (first next-page) 1) ">" "")))
      (catch Throwable t
        (log/error t stupid-header)))))

(defn fetch-url
  [url]
  (try
    (let [response (get-url url :header header-settings)]
        (if (nil? response)
          {:success false :next-url nil :data nil :reason "Empty Response"}
          (do
            (let [repos (parse-string (:body response) true)
                  next-url (find-next-url
                      (-> response :headers :link))]
              (log/debug (:headers response))
              {:success true :next-url next-url :data repos}))))
    (catch Throwable t
      (do
        (throw+ t)
        {:success false :reason (.getMessage t) :repos [] :next-url nil }))))


(defn user-data
  [m]
  (assoc
    (select-keys (get m :owner) base-user-fields)
    :full_profile false))

(defn find-existing-users
  [conn user-list]
  ; (mapv :login (cql/select conn :github_user
  ;   (dbq/columns :login)
  ;   (dbq/where [[:in :login user-list]])
  ;   )
  ; )
  (map :login (jdbc/query pg-db
    (->(select :login)
       (from :github_user)
       (where [:in :login user-list])
       (limit 1e6)
       (sql/build)
       (sql/format :quoting :ansi))))
  )

(defn find-users
  "Given all the projects which contains **`owner`** field,
  extract those and construct a hash-map by login id."
  [conn coll]
  (let [users (apply merge
                (map #(hash-map (get % :login) %) coll))
        user-list (keys users)
        existing-users (find-existing-users conn user-list)
        [_ not-in-db both-exists] (diff (set existing-users)
                                        (set user-list))]
    ; (log/warn "already exists" both-exists)
    (when-not (empty? not-in-db)
      (log/warn not-in-db))
    (vals (select-keys users not-in-db))))

(defn find-existing-projects
  [conn project-list]
    (let [projects (map :full_name
                      (jdbc/query pg-db
                      (->(select :full_name)
                         (from :github_project)
                         (where [:in :full_name project-list])
                         (limit 1e6)
                         (sql/build)
                         (sql/format :quoting :ansi))))]
      (log/warn (format "Found projects: %d" (count projects)))
      projects))

(defn insert-users
  [conn coll]
  (when-let [users (mapv #(assoc % :full_profile false)
                      (find-users conn coll))]
    (when-not (empty? users)
      (log/info "INSERT USERS" users)
      (apply (partial jdbc/insert! pg-db :github_user) users)
      ; (cql/insert-batch conn :github_user users)

      )))

(defn insert-projects
  [conn coll]
  (let [projects (doall (map (fn[item]
                          (select-keys item
                           ghub-proj-fields)) coll))
        project-ids (mapv #(get % :full_name) projects)
        existing-projects (or (find-existing-projects conn project-ids) [])]
        ; (log/warn "EXISTING" existing-projects project-ids)
    (let [[not-in-db _ both-exists] (diff (set project-ids) (set existing-projects))]
      (log/warn "NOT-DB" not-in-db)
      (let [missing-projects (filter #(in? not-in-db (get % :full_name)) projects)]
        (when (> (count missing-projects) 0)
          (log/info (format "Inserting %d projects" (count missing-projects)))
          ; (cql/insert-batch conn :github_project missing-projects)
          (apply (partial jdbc/insert! pg-db :github_project) missing-projects))))))

(defn insert-records
  [conn coll]
  (insert-projects conn coll)
  (insert-users conn (map user-data coll)))


 (defn insert-events [events] (when-not (empty? events) (apply (partial jdbc/insert! pg-db :github_events) events)))

(defn import-repos
  [db language max-iter]
  (let [max-iter (or max-iter 10000)
        conn (:connection db)]
    (loop [url (format ghub-url* language)
           looped 1]
      (log/warn (format "Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            repos (get data :items)]
        (insert-records conn repos)
        (when (and next-url (< looped max-iter))
          (recur next-url (inc looped)))))
    1))

(defn import-org-events
  [organization ]
  (let [org-events-url (format "%s/orgs/%s/events?per_page=100" ghub-root organization)
        max-iter 100]
    (loop [url org-events-url
           looped 1
           ids []]
        (log/warn (format "Loop %d. %s \n %s" looped url (count ids)))
        (let [{:keys [success next-url data]} (fetch-url url)]
          (log/info next-url url)
          (insert-events (mapv (fn[x] {:id (Long/parseLong (:id x))
                                        :event_type (:type x)
                                        :payload (pg-json (generate-string x))})
                            (remove #(in? ids (:id %)) data)))

            (when (and next-url

                    (< looped max-iter))
              (recur next-url (inc looped)
                (concat ids (mapv :id data)) ))))))

; (def facebook-events (fetch-url (events-url "facebook")))
; (defn org-events-url [organization] (format "%s/orgs/%s/events?per_page=100" ghub-root organization))


(defn expand-user
  "Fetch latest user information from github"
  [user-login]
  (let [url (format "%s/users/%s?"
                     ghub-root user-login (env :client-id) (env :client-secret))
        response (get-url url :header header-settings)]
      (when (!nil? response)
        (let [user-data (parse-string (:body response) true)]
          (when-let [user-info (select-keys user-data user-fields)]
            (log/warn (format "%s -> %s" user-login user-info))
            user-info)))))

(defn expand-project
  [proj]
  (let [url (format "%s/repos/%s?"
                     ghub-root proj (env :client-id) (env :client-secret))
        response (get-url url :header header-settings)]
       (log/info (:headers response))
      (when (!nil? response)
        (let [proj-data (parse-string (:body response) true)]
          (when-let [proj-info (select-keys proj-data ghub-proj-fields)]
            (log/warn (format "%s -> %s" proj proj-info))
            proj-info)))))

(defn user-starred
  [db user-login max-iter]
  (let [conn (:connection db)
        max-iter (or max-iter 10000)
        start-url (format "%s/users/%s/starred?per_page=100&"
                      ghub-root user-login)]
    (loop [url start-url
           looped 1]
      (log/warn (format "[STARRED]Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            repos data]
        (when-not (empty? repos)
          ;; INSERT
          ; (cql/update conn :github_user_list
          ;   {:starred [+ (set (mapv #(get % "full_name") repos))]}
          ;   (dbq/where [[:= :user user-login]]))
          (let [user-extra (actions/user-extras nil user-login :starred)
              new-repos (set (map :full_name repos))
              all-repos (set (concat (or (:starred user-extra) #{}) new-repos))]
            (actions/update-table-kryo-field :github_user_list :login user-login
                :starred all-repos))

          (insert-records conn repos)
          (when (and next-url (< looped max-iter))
            (recur next-url (inc looped))))))))

(defn user-repos
  [db user-login max-iter]
  (let [conn (:connection db)
        max-iter (or max-iter 10000)
        start-url (format "%s/users/%s/repos?per_page=100&"
                      ghub-root user-login (env :client-id) (env :client-secret))]
    (loop [url start-url
           looped 1]
      (log/warn (format "[USER-REPOS]Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            repos data]
        (when-not (empty? repos)
          ;; INSERT
          ; (cql/update conn :github_user_list
          ;   {:repos [+ (set (mapv #(get % "full_name") repos))]}
          ;   (dbq/where [[:= :user user-login]]))
          (let [user-extra (actions/user-extras nil user-login :repos)
              new-repos (set (map :full_name repos))
              all-repos (set (concat (or (:repos user-extra) #{}) new-repos))]
            (actions/update-table-kryo-field :github_user_list :login user-login
                :repos all-repos))


          (insert-records conn repos)
          (when (and next-url (< looped max-iter))
            (recur next-url (inc looped))))))))

(defn project-stargazers
  [db project-name max-iter]
  (let [conn (:connection db)
        max-iter (or max-iter 1e3)
        start-url (format "%s/repos/%s/stargazers?per_page=100"
                      ghub-root project-name (env :client-id) (env :client-secret))]
    (loop [url start-url
           looped 1]
      (log/warn (format "[PROJSTARRED]Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            users (map #(select-keys % base-user-fields) data)]
        (when-not (empty? users)
         (let [proj-extra (actions/load-project-extras* nil project-name :stargazers)
              new-users (set (map :login users))
              all-stargazers (set (concat (or (:stargazers proj-extra) #{}) new-users))]
          (actions/update-table-kryo-field :github_project_list :proj project-name
                :stargazers all-stargazers))

          (insert-users conn users)
          (when (and next-url (< looped max-iter))
            (recur next-url (inc looped))))))))

(defn project-watchers
  [db project-name max-iter]
  (let [conn (:connection db)
        max-iter (or max-iter 10000)
        start-url (format "%s/repos/%s/subscribers?per_page=100"
                      ghub-root project-name (env :client-id) (env :client-secret))]
    (actions/ensure-table-extras :github_project_list :proj project-name)
    (loop [url start-url
           looped 1]
      (log/warn (format "[PROJSTARRED]Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            users (map #(select-keys % base-user-fields) data)]
        (when-not (empty? users)
          ;; INSERT
          (let [proj-extra (actions/load-project-extras* nil project-name :watchers)
                new-users (set (map :login users))
                all-watchers (set (concat (or (:watchers proj-extra) #{}) new-users))]
          (actions/update-table-kryo-field :github_project_list :proj project-name
                :watchers all-watchers))

          (insert-users conn users)
          (when (and next-url (< looped max-iter))
            (recur next-url (inc looped))))))))

(defn project-contrib
  [db project-name max-iter]
  (let [conn (:connection db)
        max-iter (or max-iter 10000)
        start-url (format "%s/repos/%s/contributors?per_page=100"
                      ghub-root project-name (env :client-id) (env :client-secret))]
    (loop [url start-url
           looped 1]
      (log/warn (format "[PROJSTARRED]Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            users (map #(select-keys % base-user-fields) data)]
        (when-not (empty? users)

          (let [proj-extra (actions/load-project-extras* nil project-name :contributors)
              new-users (set (map :login users))
              all-contributors (set (concat (or (:contributors proj-extra) #{}) new-users))]
            (actions/update-table-kryo-field :github_project_list :proj project-name
                  :contributors all-contributors))

          (insert-users conn users)
          (when (and next-url (< looped max-iter))
            (recur next-url (inc looped))))))))

(defn org-members
  [db org max-iter]
  (let [conn (:connection db)
        max-iter (or max-iter 10000)
        start-url (format "%s/orgs/%s/members?per_page=100"
                      ghub-root org (env :client-id) (env :client-secret))]
    (loop [url start-url
           looped 1]
      (log/warn (format "[ORG-MEMBER]Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            users (map #(select-keys % base-user-fields) data)]
        (when-not (empty? users)
          ;; INSERT
          ; (cql/update conn :github_org_members
          ;   {:members [+ (set (mapv #(get % "login") users))]}
          ;   (dbq/where [[:= :org org]]))

          (insert-users conn users)
          (when (and next-url (< looped max-iter))
            (recur next-url (inc looped))))))))

(defn user-followers
  [db user-login max-iter]
  (let [conn (:connection db)
        max-iter (or max-iter 10000)
        start-url (format "%s/users/%s/followers?per_page=100"
                      ghub-root user-login (env :client-id) (env :client-secret))]
    (loop [url start-url
           looped 1]
      (log/warn (format "[FOLLOWERS]Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            users (map #(select-keys % base-user-fields) data)]
        (when-not (empty? users)
          ;; INSERT
          ; (cql/update conn :github_user_list
          ;   {:followers [+ (set (mapv #(get % "login") users))]}
          ;   (dbq/where [[:= :user user-login]]))
          (let [user-extra (actions/user-extras nil user-login :followers)
              new-users (set (map :login users))
              all-users (set (concat (or (:followers user-extra) #{}) new-users))]
            (actions/update-table-kryo-field :github_user_list :login user-login
                :followers all-users))

          (insert-users conn users)
          (when (and next-url (< looped max-iter))
            (recur next-url (inc looped))))))))

(defn project-readme
  [proj]
  (let [url (format "%s/repos/%s/readme?"
                      ghub-root proj (env :client-id) (env :client-secret))
        req-header (merge {:accept "application/vnd.github.VERSION.html"} header-settings)]
    (when-let [resp (get-url url :header req-header)]
      ; (log/warn resp)
      (:body resp))))

(defn user-following
  [db user-login max-iter]
  (let [conn (:connection db)
        max-iter (or max-iter 10000)
        start-url (format "%s/users/%s/following?per_page=100"
                      ghub-root user-login (env :client-id) (env :client-secret))]
    (loop [url start-url
           looped 1]
      (log/warn (format "[FOLLOWING]Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            users (map #(select-keys % base-user-fields) data)]
        (when-not (empty? users)
          ;; INSERT
          ; (cql/update conn :github_user_list
          ;   {:following [+ (set (mapv #(get % "login") users))]}
          ;   (dbq/where [[:= :user user-login]]))
          (let [user-extra (actions/user-extras nil user-login :following)
              new-users (set (map :login users))
              all-users (set (concat (or (:following user-extra) #{}) new-users))]
            (actions/update-table-kryo-field :github_user_list :login user-login
                :following all-users))

          (insert-users conn users)
          (when (and next-url (< looped max-iter))
            (recur next-url (inc looped))))))))

(defn enhance-user
  [db user-login max-iter]
  (doall
    (map #(% db user-login max-iter)
      [user-following user-followers user-starred user-repos])))

(defn update-project
  [db proj]
  (when-let [projx (expand-project proj)]
    (let [update-query (-> (update :github_project)
                            (sset (dissoc projx :full_name))
                            (where [:= :full_name proj])
                            (sql/build)
                            (sql/format :quoting :ansi))]
    (log/info projx)
    (log/warn (first update-query))
    (jdbc/execute! pg-db update-query))))

(defn enhance-proj
  [db proj max-iter]
  (update-project db proj)
  (doall
    (map #(% db proj max-iter)
      [project-watchers project-stargazers project-contrib])))

(defn find-user
  [user-login]
  (when-let [user-data (mapkeyw (expand-user user-login))]
    (-> user-data
      (assoc :image (:avatar_url user-data))
      (assoc :full_profile true)
      (dissoc :avatar_url))))

(defn user-list
  [conn n]
  ; (mapv :login
  ;   (cql/select conn :github_user
  ;     (dbq/columns :login)
  ;     (dbq/limit n)
  ;     (dbq/where [[:= :full_profile false]])))
  )

(defn find-n-update-user
  [db x enhance?]
    (when-let [user (find-user x)]
      (log/warn "USER:" user)
      (jdbc/execute! pg-db
          (-> (update :github_user)
              (sset user)
              (where [:= :login x])
              (sql/format :quoting :ansi)))
      (when enhance?
        (enhance-user db x 1000)
      )))



(defn sync-users
  [db n]
  (log/warn "Find users: " n db)
  (let [conn (:connection db)]
    (loop [users (user-list conn n)
           looped 1]
      (log/warn (format "Loop: %d Found %d users" looped (count users)))
      (doall
        (map #(find-n-update-user db % true) users ))
      (Thread/sleep 5000)
      (recur (user-list conn n) (inc looped)))))
