(ns hsm.controllers.project
  (:require 
    [clojure.tools.logging :as log]
    [clojure.core.memoize :as memo]
    [clojure.java.io :as io]
    [cheshire.core :refer :all]
    [ring.util.response :as resp]
    [hiccup.core :as hic]
    [hiccup.page :as hic.pg]
    [hiccup.element :as hic.el]
    [hiccup.def         :refer [defhtml]]
    [clojurewerkz.elastisch.rest :as esr]
    [clojurewerkz.elastisch.rest.index :as esi]
    [clojurewerkz.elastisch.rest.document :as esd]
    [clojurewerkz.elastisch.rest.response :as esrsp]
    [clojurewerkz.elastisch.query :as q]
    [hsm.actions :as actions]
    [hsm.ring :refer [json-resp html-resp redirect]]
    [hsm.views :as views :refer [layout panel panelx render-user left-menu]]
    [hsm.integration.ghub :as gh]
    [hsm.cache :as cache]
    [hsm.utils :as utils :refer :all]))

(def platforms {
  "pythonhackers.com" {:lang "Python" :id 1}
  "clojurehackers.com" {:lang "Clojure" :id 2} })

(defn grid-view
  [top-projects keyset]
  [:table {:class "table table-striped table-hover"}
    [:thead 
      [:tr 
        (for [header keyset] 
        [:th header])]]
    [:tbody
      (for [x top-projects]
        [:tr (for [ky (keys x)]
          [:td (get x ky)])])]])

(defhtml list-view
  [top-projects keyset]
  [:div.col-lg-9
  (panel "Top Projects"
  [:div.btn-toolbar
  [:div.btn-group.pull-right
    [:a.btn.btn-info {:href "?limit=20"} "20"]
    [:a.btn.btn-info {:href "?limit=50"} "50"]
    [:a.btn.btn-info {:href "?limit=100"} "100"]]]
  [:table.table
    [:tbody
      (for [x top-projects]
        [:tr
          [:td (get x :watchers)]
          [:td  
            [:a { :href (format "/p/%s" (get x :full_name))} (get x :full_name)
            [:p {:style "color:gray"} (get x :description)]]]
        ])]])])

(defn list-top-proj
  [{:keys [db event-chan redis]} request]
  (let [{:keys [host id body json? user platform req-id limit-by url hosted-pl]} (common-of request)
        view         (get-in request [:params :view])
        view-fn     (if (= view "grid") grid-view list-view)]
    (log/info req-id platform hosted-pl host url)
    (when platform
      (let [top-projects (actions/list-top-proj db redis platform limit-by)
            keyset (keys (first top-projects))]
        ; (log/warn cached-projects)
        (if json?
          (json-resp top-projects)
          (html-resp
            (views/layout host
              [:div.row 
                [:div.col-lg-3
                  (left-menu host platform "open-source")]
                [:div.col-lg-9
                  (view-fn top-projects keyset)]]
              )))))))

(defn get-project-readme*
  "Fetch read me. Redis is used.
  Or Fetches from github.
  TODO: Post retrieved data to ElasticSearch"
  [redis proj]
  (let [cache-key (str "readme-" proj)
        cached (cache/retrieve redis cache-key)]
    (if (!nil? cached)
      cached
      (let [readme (gh/project-readme proj)]
        (try 
          (cache/setup redis cache-key readme)
          (catch Throwable t (log/error t)))
        readme
        ))))

(defn get-project-readme
  "Memoized Project README.
  Might take a while if Github is reached."
  [redis proj] 
  (memo/memo 
    (fn[] 
      (get-project-readme redis proj))))

(defn transform-project
  [x]
  (let [data (select-keys x [:description :name :language :id :watchers :homepage :full_name])]
    (-> data
      (assoc :owner (first (vec (.split (:full_name data) "/")))))))

(defn update-search
  [{:keys [db event-chan redis else]} request]
  (let [index-name  (:index else)
        es-conn     (:conn else)]
    (if (nil? es-conn)
      (json-resp {:ok false :reason "Conn failure..."})
      (do
        (when (esi/exists? es-conn index-name)
          (esi/flush es-conn index-name :refresh true))
        (mapv #(esd/create es-conn index-name "github_project" (transform-project %))
          (actions/load-all-projects db 200))
        (json-resp {:ok 1})))))

(defn search
  "Temporary horrible searching logic.
  Will be replaced with proper ElasticSearch Solution"
  [{:keys [db event-chan redis else]} request]
  (let [host        (host-of request)
        is-json     (type-of request :json)
        term        (get-in request [:query-params "q"])
        hosted-pl   (host->pl->lang host)
        platform    (or hosted-pl (pl->lang (id-of request :platform)))
        index-name  (:index else)
        es-conn     (:conn else)]
    (log/warn "TERML:" term)
    (let [res (esd/search es-conn index-name "github_project"
                :query (q/query-string :query term))
          n (esrsp/total-hits res)
          hits (esrsp/hits-from res)]
      (json-resp (map :_source hits)))))
;(map #(assoc % :full_name (format "%s - %s" (:watchers %) (:full_name %)))

(defhtml project-header
  [id proj admin? owner contributor-count watcher-count]
  (panel id
  [:div.row
    [:div.col-lg-2 {:style "text-align: center;padding-top:15px;"}
      [:h3 [:i.fa.fa-star] [:a {:href (str "/p/" id "/stargazers")}(:watchers proj)]]
      [:form {:action "/ajax/project/follow" :method "POST"}
        [:input {:type "hidden" :value (:id proj)}]
          
        [:button.btn.btn-primary {:type "submit"} "Love It"]]
        (when admin?
          [:a.btn.btn-danger.btn-sm {:href (format "/p/%s?force-sync=1" id) :rel "nofollow"} "Synchronize"])
        ]
    [:div.col-lg-8
      [:h3
        [:a {:href (str "/user2/" owner)} owner]
        [:span " / "]
        [:a {:href (str "/p/" id)} [:span  (:name proj)]]
        
        [:a.pad10 {
            :href (str "https://github.com/" (:full_name proj))
            :title "View on Github"}
          [:i.fa.fa-github]]]
      [:a {:href (:homepage proj)}]
      [:div.icons 
        [:a {:href (str "/p/" id "/watchers")} 
          [:span [:i.fa.fa-bullhorn] watcher-count]]
        [:a {:href (str "/p/" id "/contributors")} 
          [:span [:i.fa.fa-users] contributor-count]]]
      [:span.label.label-warning (:language proj)]
      [:p.lead (:description proj)]]])
      [:hr])

(defn get-project-*
  [mod-fn selector {:keys [db event-chan redis]} request]
   (let [host  (host-of request)
         is-json (type-of request :json)
         id (format "%s/%s" (id-of request :user) (id-of request :project))
         force-sync (is-true (get-in request [:params :force-sync]))
         related-projects []
         admin? true
         proj (first (actions/load-project db id))
         proj-extras (actions/load-project-extras db id)
         watcher-count (count (:watchers proj-extras))
         contributor-count (count (:contributors proj-extras))
         owner (first (.split id "/"))
         owner-obj (actions/load-user2 db owner)]
      (if is-json
        (json-resp (selector proj-extras));(assoc proj :owner owner-obj))
        (views/layout host
          (project-header id proj admin? owner contributor-count watcher-count)
          (mod-fn db selector id proj proj-extras)))))

(defhtml contribs
  [db selector id proj proj-extras]
  (let [ppl-list (selector proj-extras)
        users (actions/load-users-by-id db (vec ppl-list))]
    (panel [:span [:i.fa.fa-users] " Contributors" ]
      [:div.row.user-list 
        (for [x (reverse (sort-by :followers users))]
          [:div.col-lg-3.user-thumb
            (render-user x)])])))

(defhtml watchers
  [db selector id proj proj-extras]
  (let [ppl-list (selector proj-extras)
        users (actions/load-users-by-id db (vec ppl-list))]
    (panel "Watchers"
      [:div.row.user-list 
        (for [x (reverse (sort-by :followers users))]
          [:div.col-lg-3.user-thumb
            (render-user x)])])))

(defhtml stargazers
  [db selector id proj proj-extras]
  (let [ppl-list (selector proj-extras)
        users (actions/load-users-by-id db (vec ppl-list))]
    (panel "Star gazers"
      [:div.row.user-list
        (for [x (reverse (sort-by :followers users))]
          [:div.col-lg-3.user-thumb
            (render-user x)])])))

(def get-project-contrib (partial get-project-* contribs :contributors))
(def get-project-stargazers (partial get-project-* stargazers :stargazers))
(def get-project-watchers (partial get-project-* watchers :watchers))


(defhtml user-list
  [users]
  [:ul (for [x (reverse (sort-by :followers users))]
    [:li.user-thumb 
    (render-user x)])])

(defn get-proj-module
  [spec request]
  (let [module (id-of request :mod)]
    (condp = module
      "stargazers" (get-project-stargazers spec request)
      "watchers"    (get-project-watchers spec request)
      "contributors" (get-project-contrib spec request)
      (resp/status 404))))

(defn get-py-contribs
  [{:keys [db event-chan redis]} request]

  )
(defn get-py-proj
  "Temporary solution till there is a proper solution.
  Somehow fetch the Python Package Projects. Same will go for Clojars."
  [{:keys [db event-chan redis]} request]
  (let [{:keys [host id body json? user platform 
                req-id limit-by url hosted-pl]} (common-of request)]
  (layout host "Project")
  ))
(defn get-proj
  [{:keys [db event-chan redis]} request]
  (let [host  (host-of request)
        is-json (type-of request :json)
        id (format "%s/%s" (id-of request :user) (id-of request :project))
        force-sync (is-true (get-in request [:params :force-sync]))
        related-projects []
        admin? true]
    (let [proj (first (actions/load-project db id))]
      (if force-sync
        (do
          (gh/enhance-proj db id 1000)
          (redirect (str "/p/" id)))
        (let [proj-extras (actions/load-project-extras db id)
              watcher-count (count (:watchers proj-extras))
              contributor-count (count (:contributors proj-extras))
              owner (first (.split id "/"))
              owner-obj (actions/load-user2 db owner)]
          (if is-json
            (json-resp (assoc proj :owner owner-obj))
            (let [contributors (take 10 (:contributors proj-extras))
                  watchers (take 10 (:watchers proj-extras))
                  stargazers (take 10 (:stargazers proj-extras))
                  people (vec (set (concat contributors watchers stargazers)))
                  users (actions/load-users-by-id db people)
                  contributors (filter #(in? contributors (:login %)) users)
                  watchers (filter #(in? watchers (:login %)) users)
                  stargazers (filter #(in? stargazers (:login %)) users)
                  ]
            (html-resp
              (views/layout host
                (project-header id proj admin? owner contributor-count watcher-count)
                [:div.row
                  [:div.col-lg-9
                    (views/panel "READ ME"
                      (get-project-readme* redis id))]
                  [:div.col-lg-3
                    (panel [:a {:href (str "/p/" id "/contributors")} [:span [:i.fa.fa-users] " Contributors" ]]
                      (user-list contributors ))
                    (panel [:a {:href (str "/p/" id "/watchers") } [:span [:i.fa.fa-users] "Watchers"]]
                      (user-list watchers))
                    (panel [:a {:href (str "/p/" id "/stargazers") } [:span [:i.fa.fa-users] "Stargazers"]]
                      (user-list stargazers))                 
                    (panel "Related Projects"
                      [:ul
                        (for [x related-projects]
                          [:li
                            [:a {:href (str "/p/"(:full_name x))} (:full_name x)
                            [:p {:style "color:gray"} (cutoff (:description x) 50)]]
                            ]
                          )])]])))))))))