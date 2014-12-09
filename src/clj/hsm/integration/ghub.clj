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
      [hsm.utils :refer [id-generate]]
      ))

(defn find-users
  [coll]
  (vals (apply merge 
          (map #(hash-map (get % "login") %)  
                (map #(select-keys (get % "owner") ["id" "login" "type"]) coll)))))

(defn insert-records
  [conn coll]
  (log/info (format "Inserting %d projects" (count coll)))
  (cql/insert-batch conn :github_project 
    (doall (map (fn[item] (merge {:id (id-generate)}
      (select-keys item 
        (map name [:name :fork :watchers :open_issues :language :description :full_name])))) coll)))
  (cql/insert-batch conn :github_user
    (find-users coll)))

(defn find-next-url
  "Figure out the next url to call
  <https://api.github.com/search/repositories?q=...&page=2>; rel=\"next\", <https://api.github.com/search/repositories?q=+...&page=34>; rel=\"last\"
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
    (let [response (client/get url) 
          ; /repositories call returns result in root
          ; /search/repositories returns under items
          repos (parse-string (:body response)) ;(get  "items")
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
  [db language]
  (let [conn (:connection db)]
    (loop [url (format ghub-url language)]
      (log/warn url)
      (let [{:keys [success next-url repos]} (fetch-url url)]
        (insert-records conn repos)
        (when next-url
          (recur next-url))))))

; (defn import-repos*
;   [db]
;   (let [repos (gh-search/search-repos "a" 
;                 {:language "clojure"} 
;                 {:sort "stars" :order "desc"})
;         conn (:connection db)]
;             (doall (map #(partial insert-project conn %) repos ))))