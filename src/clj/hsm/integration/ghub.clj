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
    (:use [slingshot.slingshot :only [throw+ try+]]))

(defn find-users
  "Given all the projects which contains **`owner`** field, 
  extract those and construct a hash-map by login id."
  [coll]
  (vals (apply merge 
          (map #(hash-map (get % "login") %)  
                (map #(select-keys (get % "owner") ["id" "login" "type"]) coll)))))

(def ghub-fields [:name :fork :watchers :open_issues :language :description :full_name])

(defn insert-records
  [conn coll]
  (log/info (format "Inserting %d projects" (count coll)))
  ;; extend the owner field into proper structure :owner => login_name
  (cql/insert-batch conn :github_project 
    (doall (map (fn[item] 
                  (merge {:id (id-generate)}
                    (select-keys item 
                      (map name ghub-fields)))) 
            coll)))
  (cql/insert-batch conn :github_user
    (find-users coll)))

(defn find-next-url
  "Figure out the next url to call
  <https://api.github.com/search/repositories?q=...&page=2>; 
  rel=\"next\", <https://api.github.com/search/repositories?q=+...&page=34>; rel=\"last\"
  "
  [stupid-header]
  (let [[next-s last-s] (.split stupid-header ",")
        next-page (vec (.split next-s ";"))
        is-next (.contains (last next-page) "next")]
    (when is-next 
      (s/replace (subs (first next-page) 1) ">" ""))))

(defn fetch-url
  [url]
  (try 
    (let [response (client/get url {:socket-timeout 10000 :conn-timeout 10000}) 
          ; /repositories call returns result in root
          ; /search/repositories returns under items
          repos (get (parse-string (:body response)) "items")
          next-url (find-next-url 
                      (-> response :headers (get "link")))]
      (log/debug (:headers response))
      {:success true :next-url next-url :repos repos})
    (catch Throwable t
      (do 
        (log/error t)
        {:success false :reason (.getMessage t) :repos [] :next-url nil }))))

(def ghub-url*
  (str "https://api.github.com/search/repositories"
    "?q=+language:%s&sort=stars&order=desc&per_page=100&"
    (format "client_id=%s&client_secret=%s" (env :client-id) (env :client-secret))))

(def ghub-url
  (str "https://api.github.com/repositories"
    "?per_page=100&"
    (format "client_id=%s&client_secret=%s" (env :client-id) (env :client-secret))))


(defn import-repos
  [db language max-iter]
  (let [max-iter (or max-iter 10000)
        conn (:connection db)]
    (loop [url (format ghub-url* language) 
           looped 1]
      (log/warn (format "Loop %d. %s" looped url))
      (let [{:keys [success next-url repos]} (fetch-url url)]
        (insert-records conn repos)
        (when (and next-url (< looped max-iter))
          (recur next-url (inc looped)))))
    1))

(def user-fields
  [:id :login :type :name :company :blog :location :email :public_repos :public_gists
  :followers :following] )

(def header-settings
  {:socket-timeout 10000 :conn-timeout 10000})

(defn expand-user
  "Fetch latest user information from github"
  [user-login]
  (let [url (format "https://api.github.com/users/%s?client_id=%s&client_secret=%s" 
                      user-login (env :client-id) (env :client-secret))
        response (try+ 
                    (client/get url header-settings) 
                    (catch [:status 403] {:keys [request-time headers body]}
                      (log/warn "403" request-time headers))
                    (catch [:status 404] {:keys [request-time headers body]}
                      (log/warn "NOT FOUND" user-login request-time headers body))
                    (catch Object _
                      (log/error (:throwable &throw-context) "unexpected error")
                      (throw+)))]
      (when (!nil? response)
        (let [user-data (parse-string (:body response))]
          (when-let [user-info (select-keys user-data (map name user-fields))]
            (log/warn (format "%s -> %s" user-login user-info))
            user-info)))))


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
  [conn x]
    (when-let [user (find-user x)]
      (cql/update conn :github_user
        user 
        (dbq/where [[= :login x]]))))

(defn sync-users
  [db n]
  (log/warn "Find users: " n db)
  (let [conn (:connection db)
    users (user-list conn n)]
    (log/warn (format "Found %d users" (count users)))
    (doall 
      (map #(find-n-update conn %) users ))))
