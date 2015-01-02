(ns hsm.integration.ghub
    "Fetch repository information from github"
    (:require 
      [clojure.string :as s]
      [clojure.tools.logging :as log]
      [tentacles.search :as gh-search]
      [clojurewerkz.cassaforte.cql  :as cql]
      [clojurewerkz.cassaforte.query :as dbq]
      [qbits.hayt.dsl.statement :as hs]
      [clj-http.client :as client]
      [cheshire.core :refer :all]
      [environ.core :refer [env]]
      [hsm.utils :refer [id-generate mapkeyw !nil?]]
      )
    (:use [slingshot.slingshot :only [throw+ try+]]
      [clojure.data :only [diff]]))

(def ghub-root "https://api.github.com")

(def api-params (format "client_id=%s&client_secret=%s" (env :client-id) (env :client-secret)))

(def ghub-url*
  (str ghub-root "%s/search/repositories"
    "?q=+language:%s&sort=stars&order=desc&per_page=100&" api-params))

(def ghub-url
  (str ghub-root "/repositories?per_page=100&" api-params))

(def header-settings
  {:socket-timeout 10000 :conn-timeout 10000})

(def ghub-proj-fields [:name :fork :watchers :open_issues :language :description :full_name])

(def user-fields
  [:id :login :type :name :company :blog 
  :location :email :public_repos :public_gists
  :followers :following] )

(defn get-url
  [url headers]
  (try+
    (client/get url headers)
    (catch [:status 403] {:keys [request-time headers body]}
      (log/warn "403" request-time headers))
    (catch [:status 404] {:keys [request-time headers body]}
      (log/warn "NOT FOUND" url request-time headers body))
    (catch Object _
      (log/error (:throwable &throw-context) "Unexpected Error")
      (throw+))))

(def base-user-fields ["id" "login" "type"])

(defn user-data
  [m]
  (assoc
    (select-keys (get m "owner") base-user-fields)
    :full_profile false))

(defn find-existing-users
  [conn user-list]
  (mapv :login (cql/select conn :github_user
    (dbq/columns :login)
    (dbq/where [[:in :login user-list]]))))

(defn find-users
  "Given all the projects which contains **`owner`** field, 
  extract those and construct a hash-map by login id."
  [conn coll]
  (let [users (apply merge 
                (map #(hash-map (get % "login") %) coll))
        user-list (keys users)
        existing-users (find-existing-users conn user-list)
        [_ not-in-db both-exists] (diff (set existing-users) (set user-list))]
    (log/warn "already exists" both-exists)
    (log/warn not-in-db)
    (vals (select-keys users not-in-db))
  ))

(defn insert-users
  [conn coll]
  (when-let [users (mapv #(assoc % :full_profile false) (find-users conn coll))]
    (cql/insert-batch conn :github_user users)))

(defn insert-records
  [conn coll]
  (log/info (format "Inserting %d projects" (count coll)))
  (cql/insert-batch conn :github_project 
    (doall (map (fn[item] 
                  (merge {:id (id-generate)}
                    (select-keys item 
                      (map name ghub-proj-fields)))) 
            coll)))
   (insert-users conn (map user-data coll)))

(defn find-next-url
  "Figure out the next url to call
  <https://api.github.com/search/repositories?q=...&page=2>; 
  rel=\"next\", <https://api.github.com/search/repositories?q=+...&page=34>; rel=\"last\"
  "
  [stupid-header]
  (when (!nil? stupid-header)
  (try 
    (let [[next-s last-s] (.split stupid-header ",")
          next-page (vec (.split next-s ";"))
          is-next (.contains (last next-page) "next")]
      (when is-next 
        (s/replace (subs (first next-page) 1) ">" "")))
    (catch Throwable t
      (log/warn "FUCK" t stupid-header)))))

(defn fetch-url
  [url]
  (try 
    (let [response (get-url url header-settings)] 
          ; /repositories call returns result in root
          ; /search/repositories returns under items
        (if (nil? response)
          {:success false :next-url nil :data nil :reason "Empty Response"}
          (do 
            (let [repos (parse-string (:body response))
                  next-url (find-next-url 
                      (-> response :headers (get "link")))]
              (log/debug (:headers response))
              {:success true :next-url next-url :data repos}))))
    (catch Throwable t
      (do 
        (throw+ t)
        {:success false :reason (.getMessage t) :repos [] :next-url nil }))))

(defn import-repos
  [db language max-iter]
  (let [max-iter (or max-iter 10000)
        conn (:connection db)]
    (loop [url (format ghub-url* language) 
           looped 1]
      (log/warn (format "Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            repos (get data "items")]
        (insert-records conn repos)
        (when (and next-url (< looped max-iter))
          (recur next-url (inc looped)))))
    1))

(defn expand-user
  "Fetch latest user information from github"
  [user-login]
  (let [url (format "%s/users/%s?client_id=%s&client_secret=%s" 
                     ghub-root user-login (env :client-id) (env :client-secret))
        response (get-url url header-settings)]
      (when (!nil? response)
        (let [user-data (parse-string (:body response))]
          (when-let [user-info (select-keys user-data (map name user-fields))]
            (log/warn (format "%s -> %s" user-login user-info))
            user-info)))))

(defn user-starred
  [db user-login max-iter]
  (let [conn (:connection db)
        max-iter (or max-iter 10000)
        start-url (format "%s/users/%s/starred?per_page=100&client_id=%s&client_secret=%s" 
                      ghub-root user-login (env :client-id) (env :client-secret))]

    (loop [url start-url
           looped 1]
      (log/warn (format "[STARRED]Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            repos data]
        (when (and (!nil? repos) (> (count repos) 0))
          (cql/update conn :github_user_list
            {:starred [+ (set (mapv #(get % "full_name") repos))]}
            (dbq/where [[:= :user user-login]]))
          (insert-records conn repos)
          (when (and next-url (< looped max-iter))
            (recur next-url (inc looped))))))))

(defn project-starred
  [db project-name max-iter]
  (let [conn (:connection db)
        max-iter (or max-iter 10000)
        start-url (format "%s/repos/%s/stargazers?per_page=100&client_id=%s&client_secret=%s" 
                      ghub-root project-name (env :client-id) (env :client-secret))]
    (loop [url start-url
           looped 1]
      (log/warn (format "[PROJSTARRED]Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            users data]
        (when (and (!nil? users) (> (count users) 0))
          (cql/update conn :github_project_list
            {:starred [+ (set (mapv #(get % "login") users))]}
            (dbq/where [[:= :proj project-name]]))
          (insert-users conn users)
          (when (and next-url (< looped max-iter))
            (recur next-url (inc looped))))))))

(defn project-watchers
  [db project-name max-iter]
  (let [conn (:connection db)
        max-iter (or max-iter 10000)
        start-url (format "%s/repos/%s/watchers?per_page=100&client_id=%s&client_secret=%s" 
                      ghub-root project-name (env :client-id) (env :client-secret))]
    (loop [url start-url
           looped 1]
      (log/warn (format "[PROJSTARRED]Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            users data]
        (when (and (!nil? users) (> (count users) 0))
          (cql/update conn :github_project_list
            {:watchers [+ (set (mapv #(get % "login") users))]}
            (dbq/where [[:= :proj project-name]]))
          (insert-users conn users)
          (when (and next-url (< looped max-iter))
            (recur next-url (inc looped))))))))

(defn user-followers
  [db user-login max-iter]
  (let [conn (:connection db)
        max-iter (or max-iter 10000)
        start-url (format "%s/users/%s/followers?per_page=100&client_id=%s&client_secret=%s" 
                      ghub-root user-login (env :client-id) (env :client-secret))]
    (loop [url start-url
           looped 1]
      (log/warn (format "[FOLLOWERS]Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            users (map #(select-keys % base-user-fields) data)]
        (when (and (!nil? users) (> (count users) 0))
          (cql/update conn :github_user_list
            {:followers [+ (set (mapv #(get % "login") users))]}
            (dbq/where [[:= :user user-login]]))
          (insert-users conn users)
          (when (and next-url (< looped max-iter))
            (recur next-url (inc looped))))))))

(defn user-following
  [db user-login max-iter]
  (let [conn (:connection db)
        max-iter (or max-iter 10000)
        start-url (format "%s/users/%s/following?per_page=100&client_id=%s&client_secret=%s" 
                      ghub-root user-login (env :client-id) (env :client-secret))]
    (loop [url start-url
           looped 1]
      (log/warn (format "[FOLLOWING]Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            users (map #(select-keys % base-user-fields) data)]
        (when (and (!nil? users) (> (count users) 0))
          (cql/update conn :github_user_list
            {:following [+ (set (mapv #(get % "login") users))]}
            (dbq/where [[:= :user user-login]]))
          (insert-users conn users)
          (when (and next-url (< looped max-iter))
            (recur next-url (inc looped))))))))

(defn enhance-user
  [db user-login max-iter]
  (doall (pmap #(% db user-login max-iter)
    [user-following user-followers user-starred])))

(defn find-user [user-login]
  (when-let [user-data (expand-user user-login)]
    (dissoc 
      (assoc 
        (mapkeyw user-data) 
        :full_profile true) 
      :login)))

(defn user-list
  [conn n] 
  (mapv :login
    (cql/select conn :github_user 
      (dbq/columns :login)
      (dbq/limit n) 
      (dbq/where [[:= :full_profile false]]))))

(defn find-n-update
  [db x]
    (when-let [user (find-user x)]
      (cql/update (:connection db) :github_user
        user 
        (dbq/where [[= :login x]]))
      (enhance-user db x 1000)
      ))

(defn sync-users
  [db n]
  (log/warn "Find users: " n db)
  (let [conn (:connection db)]
    (loop [users (user-list conn n)
           looped 1]
      (log/warn (format "Loop: %d Found %d users" looped (count users)))
      (doall 
        (map #(find-n-update db %) users ))
      (Thread/sleep 5000)
      (recur (user-list conn n) (inc looped)))))
