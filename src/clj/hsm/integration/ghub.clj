(ns hsm.integration.ghub
    "Fetch repository information from github"
    (:require
      [clojure.string                 :as s]
      [taoensso.timbre                :as log]
      [ring.util.codec :as codec]
      [clojure.java.jdbc              :as jdbc]
      [honeysql.core                  :as sql]
      [honeysql.helpers               :as sqlh]
      [clj-http.client                :as client]
      [cheshire.core                  :refer :all]
      [environ.core                   :refer [env]]
      [hsm.utils                      :refer :all]
      [hsm.conf                       :as conf]
      [hsm.system.pg                  :refer [pg-db]]
      [hsm.actions                    :as actions]
      [hsm.integration.ghub_keys      :as ghkeys]
      [truckerpath.clj-datadog.core   :as dd]
      [taoensso.carmine :as car :refer (wcar)]
      [hsm.cache :as cache])

    (:use [slingshot.slingshot :only [throw+ try+]]
      [clojure.data :only [diff]])

    )





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


(defn rate-limit-logger
  [hd]
  (log/infof "R/L: %s RESET: %s"
           (get hd "X-RateLimit-Remaining")
           (get hd "X-RateLimit-Reset")))



(defn get-url
  [url & options]
  (let [{:keys [header safe care conf]
         :or {header header-settings safe false
              care true conf (get-config)}} options
        [client_id secret] (ghkeys/pick-key)
        ; _ (log/infof "Picked %s" client_id)
        ]
    (try+
      (dd/timed {} "github.api.call" {:service "github"}
        (let [full-url (format "%s&client_id=%s&client_secret=%s" url client_id secret)
              _ (log/infof  "Calling %s " full-url)
              response (client/get full-url header)]
          (rate-limit-logger (:headers response))
          response))
      (catch [:status 403] {:keys [request-time headers body]}
        (do
          (log/warn "403" request-time)
          (rate-limit-logger headers)))
      (catch [:status 404] {:keys [request-time headers body]}
        (do
          (log/warn "NOT FOUND" url request-time )
          (rate-limit-logger headers)
          {:error 404}
          ))
      (catch Object _
        (when care
          (log/error (:throwable &throw-context) "Unexpected Error"))
        (when-not safe
          (throw+))))))

(defn get-url*
  [url & options]
  (let [response (get-url url)]
    response
  )
  )

;; if the response is 403, capture the reset date and remove it from
;; the available items

(defn find-next-url
  "Figure out the next url to call
  <https://api.github.com/search/repositories?q=...&page=2>;
  rel=\"next\", <https://api.github.com/search/repositories?q=+...&page=34>; rel=\"last\"
  "
  [stupid-header]
  (when (!nil? stupid-header)
    (try
      (log/info stupid-header)
      (let [[next-s last-s & others] (.split stupid-header ",")
            next-page (vec (.split next-s ";"))
            is-next (.contains (last next-page) "next")]
        (when is-next
          (s/replace (subs (first next-page) 1) ">" "")))
      (catch Throwable t
        (log/error t stupid-header)))))

(defn fetch-url-and-next
  [url {:keys [get-url-fn] :or {get-url-fn get-url}}]
  (try
    (let [response (get-url-fn url :header header-settings)]
        (if (nil? response)
          {:success false :next-url nil :data nil :reason "Empty Response"}
          (do
            (let [data (parse-string-strict (:body response) true)
                  _ (log/sometimes 0.01 (log/info (:headers response) ))
                  credit-state (select-keys (:headers response) [:X-RateLimit-Reset :X-RateLimit-Remaining])
                  ; _ (log/infof "R/L %s" credit-state)
                  next-url (find-next-url
                              (or (-> response :headers :link)
                                  (-> response :headers :Link) ;; json encoder causes this when executed remotely
                              ))]
              {:success true
                :next-url next-url
                :data data
                :credits credit-state}))))
    (catch Throwable t
      (do
        (throw+ t)
        {:success false :reason (.getMessage t) :repos [] :next-url nil }))))

(defn assign-worker-bee
  [task bee param]
  (let [url (format "http://%s/%s/%s" bee task param )]
      ; (log/info url)
     (when-let [result (try
                        (dd/timed {} "worker.bee.call" {:service "oss"}
                          (-> (client/get url header-settings)
                            (get :body)
                            (parse-string true)))
                        (catch Exception ex
                          (do
                            (log/warnf "Failed in bee %s for %s %s" bee task param)
                            (log/error ex))))]
      result)))

(defn clean-next-url
  [next-url]
  (let [
        [path qs] (s/split next-url #"\?")
        params  (-> (codec/form-decode qs "UTF-8")
                    (dissoc "client_id" "client_secret"))
        next-url* (s/join "?" [path (codec/form-encode params "UTF-8")]  )]
      next-url*
  ))

(defn to-int
  [v]
  (try
    (Integer/parseInt v)
    (catch Exception ex
      nil)))
(defn epoch-sec
  []
  (int (/ (hsm.utils/now->ep) 1000)))

(defn credit-report
  [bee report]
  (when report
    (when-let [reset (to-int (:X-RateLimit-Reset report))]
    (let [now (epoch-sec)
          credits-left (to-int (:X-RateLimit-Remaining report))
          seconds-left (- reset now)
          seconds-left (if (zero? seconds-left) 1 seconds-left)
          report (merge report {:bee bee :rate (if (zero? credits-left) 0
                                                   (float (/ credits-left seconds-left )))})]
    (wcar {:pool {} :spec {:host "localhost" :port 6379}}
      (car/hset "oss.worker.bee.state" bee (generate-string report))
      (car/zadd  "oss.worker.bee.tokens" credits-left bee )
      (car/zadd  "oss.worker.bee.credit" reset bee)
      (car/zadd  "oss.worker.bee.credit.rate" (str (:rate report))  bee )
      (car/publish "oss.worker.bee.credit.line"  (generate-string report))
      )))))
;; bee has time reset time already set, so don't use it
;; until the reset has been done.

(defn pick-bee-by-random
  []
  (let [hive (wcar  {:pool {} :spec {:host "localhost" :port 6379}}
                        (car/smembers "oss.worker.bees"))]
      (rand-nth hive)
  ))

(defn bees-with-proper-state
  [states]
  (filter (fn[x] (or (> (epoch-sec) (Integer/parseInt (:X-RateLimit-Reset x)))
                            (> (Integer/parseInt (:X-RateLimit-Remaining x)) 0)))
              (map (fn [[x y]] (parse-string y true)) (partition 2 states)))
  )

(defn pick-bee-by-random-with-state []
  ; (log/info "Random bee pick")
  (let [[hive states] (wcar  {:pool {} :spec {:host "localhost" :port 6379}}
                        (car/smembers "oss.worker.bees")
                        (car/hgetall "oss.worker.bee.state"))
        possible-bees (bees-with-proper-state states)
        chosen (rand-nth possible-bees)]
        (if chosen
          (do
            ; (log/infof "Choses %s" chosen)
            (:bee chosen))
          (do
            (log/info "Rollback to random hive bee")
            (rand-nth hive)))))

(defn pick-bee-by-rank []
  (let [hive (mapv identity (partition 2 (wcar  {:pool {} :spec {:host "localhost" :port 6379}}
              (car/zrangebyscore "oss.worker.bee.tokens" 10 5000 "WITHSCORES" ))))
        selection (mapv identity (last hive))]
        (if (or (nil? selection) (empty? selection))
          (pick-bee-by-random)
          (do
            ; (log/infof "Lucky one is %s [%s]" selection hive)
            (first selection)))))

(defn pick-bee-by-rate []
  (let [hive (mapv identity (partition 2 (wcar  {:pool {} :spec {:host "localhost" :port 6379}}
              (car/zrangebyscore "oss.worker.bee.credit.rate" 0.01 5000 "WITHSCORES" ))))
        selection (mapv identity (last hive))]
        (if (or (nil? selection) (empty? selection))
          (pick-bee-by-random)
          (do
            ; (log/infof "Rate Lucky one is %s [%s]" selection hive)
            (first selection)))))

(defn pick-a-bee
  []
  (apply (rand-nth [pick-bee-by-rank
                    pick-bee-by-random-with-state
                    pick-bee-by-random-with-state
                    pick-bee-by-random
                    pick-bee-by-rate]) []))

;; if the chosen bee has the credit 0

(defn fetch-url
  "Picks up one of the worker bee as the delegate to fetch the URL and finds the
  next link. "
  [url & {:keys [bee-picker] :or {bee-picker pick-a-bee}}]
  (let [bee (bee-picker)
        url-encoded (try
                      (codec/url-encode url)
                      (catch Exception ex
                        (log/warnf "Failed to URL Encode %s" url )
                          ))]
      (when url-encoded
        (let [fetcher (fn [url & args] (assign-worker-bee "get-url" bee url-encoded))
              next-step (fetch-url-and-next url {:get-url-fn fetcher})
              next-url (:next-url next-step)
              _ (log/sometimes 0.009 (log/info bee (:credits next-step)))]
              (dd/increment {} "github.calls" 1 {:bee bee :result (not (nil? next-url))})
              (credit-report bee (:credits next-step))
          (if (nil? next-url)
            next-step
            (do
              (let [next-url* (clean-next-url next-url)
                    next-step* (assoc next-step :next-url next-url*)]
              (log/info (:next-url next-step*))
              next-step*)))))))


(defn user-data
  [m]
  (assoc
    (select-keys (get m :owner) base-user-fields)
    :full_profile false))

(defn find-existing-users
  [conn user-list]
  (map :login (jdbc/query pg-db
      (-> (sqlh/select :login)
          (sqlh/from :github_user)
          (sqlh/where [:in :login user-list])
          (sqlh/limit 1e6)
          (sql/build)
          (sql/format :quoting :ansi))))
  )

(defn find-users
  "Given all the users,
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
    (if (empty? project-list)
      []
    (let [projects (map :full_name
                      (jdbc/query pg-db
                      (->(sqlh/select :full_name)
                         (sqlh/from :github_project)
                         (sqlh/where [:in :full_name project-list])
                         (sqlh/limit 1e6)
                         (sql/build)
                         (sql/format :quoting :ansi))))]
      ; (log/warn (format "Found projects: %d" (count projects)))
      projects)))

(defn insert-users
  [conn coll]
  (when-let [users (mapv #(assoc % :full_profile false)
                      (find-users conn coll))]
    (when-not (empty? users)
      (log/infof  "Insert users %d " (count users))
      (dd/increment {} "oss.users" (count users))
      ; (log/info users)
      ; (mapv #(jdbc/insert! pg-db :github_user %) users)
      (jdbc/insert-multi! pg-db :github_user users)
      )))

(defn insert-projects
  [conn coll]
  (let [projects (doall (map (fn[item]
                          (select-keys item
                           ghub-proj-fields)) coll))
        project-ids (mapv #(get % :full_name) projects)
        existing-projects (or (find-existing-projects conn project-ids) [])]
    (let [[not-in-db _ both-exists] (diff (set project-ids) (set existing-projects))]
      ; (log/warn "NOT-DB" not-in-db)
      (let [missing-projects (filter #(in? not-in-db (get % :full_name)) projects)]
        (when (> (count missing-projects) 0)
          (dd/increment {} "oss.projects" (count missing-projects))
          (log/info (format "Insert projects %d" (count missing-projects)))
          (jdbc/insert-multi! pg-db :github_project missing-projects))))))

(defn insert-records
  [conn coll]
  (insert-projects conn coll)
  (insert-users conn (map user-data coll)))

  (defn find-existing-events
    [event-list]
      (let [events (mapv :id
                        (jdbc/query pg-db
                        (->(sqlh/select :id)
                           (sqlh/from :github_events)
                           (sqlh/where [:in :id event-list])
                           (sqlh/limit 1e6)
                           (sql/build)
                           (sql/format :quoting :ansi))))]
        (log/warn (format "Found Ids: %d" (count events)))
        events))

 (defn insert-events [events]
   (when-not (empty? events)
    (let [existing-events (find-existing-events (mapv :id events))
          remaining-events (remove #(in? existing-events (:id %)) events)]
          (log/warn (format "Remaining Ids: %d" (count remaining-events)))
      (when-not (empty? remaining-events)
        ; (log/warn "Writing" (first remaining-events))
        ; (log/warn pg-db)
        (jdbc/insert-multi! pg-db :github_events remaining-events)
        ; (log/warn "done Writing")
        true
        ))))

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
  [organization]
  (let [org-events-url (format "%s/orgs/%s/events?per_page=100" ghub-root organization)
        max-iter 100]
    (loop [url org-events-url
           looped 1
           ids []]
        (log/warn (format "Loop %d. %s %s" looped url (count ids)))
        (let [{:keys [success next-url data]} (fetch-url url)]
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
        response (fetch-url url)]
        ; (log/info response)
        ;; iff the response is {:error 404}
        (if (or (= "Empty Response" (get response :reason ))
                (= 404 (:error (:data response ))))
          :user-does-not-exist
          (let [{:keys [next-url data]} response]
            (when-let [user-info (select-keys data user-fields)]
              ; (log/warn (format "%s -> %s" user-login user-info))
              user-info)))))

(defn expand-project
  [proj]
  (let [url (format "%s/repos/%s?" ghub-root proj )
        response (get-url url :header header-settings)]
      (when (!nil? response)
        (let [proj-data (parse-string (:body response) true)]
          (when-let [proj-info (select-keys proj-data ghub-proj-fields)]
            ; (log/warn (format "%s -> %s" proj (select-keys proj-info [:id :watchers :full_name])))
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

(defn update-project-db
  [proj projx]
  (log/infof "%s| %s" proj projx)
  (let [update-query (-> (sqlh/update :github_project)
                          (sqlh/sset (dissoc projx :full_name))
                          (sqlh/where [:= :full_name proj])
                          (sql/build)
                          (sql/format :quoting :ansi))]
  ; (log/info projx)
  ; (log/warn (first update-query))
  (try
    (jdbc/execute! pg-db update-query)
    (catch Exception ex
      (log/warnf "Failed to write update for %s" proj)
      ))
  projx
  ))

(defn update-project
  [db proj]
  (when-let [projx (try
                      (expand-project proj)
                      (catch Exception ex
                        (log/error "Failed")))]
      (update-project-db proj projx)
      projx))

(defn update-project-stats
  [project]
  (when project
    (cache/hset {:pool {} :spec {:host "localhost" :port 6379}}
      (format "oss.stats_timeline_%s" (:full_name project))
                            (str (System/currentTimeMillis))
                            (:watchers project)))
    project)

(defn update-project-info
  [params]
  (-> (update-project nil params)
      (update-project-stats)))


(defn enhance-proj
  [db proj max-iter]
  (update-project db proj)
  (doall
    (pmap #(% db proj max-iter)
      [project-watchers project-stargazers project-contrib])))

(defn find-user
  [user-login]
  (let [user-info (expand-user user-login)]
    (if (= :user-does-not-exist user-info)
        user-info
      (when-let [user-data (mapkeyw user-info)]
        (-> user-data
          (assoc :image (:avatar_url user-data))
          (assoc :full_profile true)
          (dissoc :avatar_url))))))

(defn user-list
  [conn n]
  (let [random (rand-int 3e7)]
  (log/infof "Picking %d up" random)
  (mapv :login
    (jdbc/query pg-db
      (-> (sqlh/select :login)
          (sqlh/from :github_user)
          (sqlh/where [:and
                       [:= :full_profile false]
                       [:> :id random]])
          (sqlh/limit n)
          (sql/build)
          (sql/format :quoting :ansi))))))

(defn find-n-update-user
  [_ x enhance?]
    (when-let [user (find-user x)]
      (log/warn "USER:" user)
      (try
        (if (= :user-does-not-exist user)
          (do
            (log/warnf "Deleting user %s" x)
            (jdbc/delete! pg-db :github_user ["login = ?" x]))

          (jdbc/execute! pg-db
            (-> (sqlh/update :github_user)
                (sqlh/sset user)
                (sqlh/where [:= :login x])
                (sql/build)
                (sql/format :quoting :ansi))))
            (catch Exception ex
              (let [ex (.getNextException ex)]
                (log/error ex)
              )))
      (when enhance?
        (enhance-user {} x 1000))))

(defn sync-some-users
  [db n]
  (log/warn "Find users: " n db)
  (let [conn (:connection db)
        users (user-list nil n)]
    (log/warnf "Loop: %d Found users" (count users))
    (mapv #(find-n-update-user db % true) users )))


(defn sync-users-continuous
  [db n slp]
  (log/warn "Find users: " n db)
  (let [conn (:connection db)]
    (loop [users (user-list nil n)
           looped 1]
      (log/warn (format "Loop: %d Found %d users" looped (count users)))
      (doall
        (map #(find-n-update-user db % true) users ))
      (Thread/sleep slp)
      (recur (user-list conn n) (inc looped)))))


(defn update-project-remotely
  [params]
  (let [result (assign-worker-bee "update-project" params)]
    (update-project-stats result)
    (update-project-db params result)))


(defn iterate-users
  [url looped max]
    (log/warn (format "[USERSINCE]Loop %d. %s" looped url))
    (let [{:keys [success next-url data]} (fetch-url url)]
      (insert-users {}
        (mapv (fn [x]
                (select-keys x base-user-fields)) data))
      (if (>= looped max)
        :done
      (recur next-url (inc looped) max)
  )))

(defn fetch-url-with-retry
  [url]
  (let [{:keys [success next-url data] :as result}  (fetch-url url)]
    (if (nil? next-url)
      (do
        (log/infof "Retrying %s" url)
        (let [{:keys [success next-url data] :as retry-result}  (fetch-url url)]
          retry-result))
      result)))

(defn iterate-projects
  [url looped max]
    (log/warn (format "[PROJECTSINCE]Loop %d. %s" looped url))
    (let [{:keys [success next-url data]} (fetch-url-with-retry url)]
      (insert-projects {} data)
      (if (or (nil? next-url) (>= looped max))
        :done
      (recur next-url (inc looped) max)
  )))

(defn iterate-users-since
  [since]
  (iterate-users (format "https://api.github.com/users?since=%d&per_page=100" since) 0 1e4)
  )

(defn iterate-projects-since
  [since]
  (iterate-projects (format "https://api.github.com/repositories?since=%d&per_page=100" since) 0 1e4)
  )
