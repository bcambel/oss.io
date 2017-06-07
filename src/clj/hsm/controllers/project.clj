(ns hsm.controllers.project
  (:require
    [taoensso.timbre :as log]
    [clojure.core.memoize :as memo]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [cheshire.core :refer :all]
    [ring.util.response :as resp]
    [ring.util.codec :as codec]
    [hiccup.core :as hic]
    [hiccup.page :as hic.pg]
    [hiccup.element :as hic.el]
    [hiccup.def         :refer [defhtml]]
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
  [{:keys [db event-chan redis else]} request]
  ; (let [session-data (:session request)])
  (let [{:keys [host id body json? user platform
                req-id limit-by url hosted-pl]} (common-of request)
        view         (get-in request [:params :view])
        view-fn     (if (= view "grid") grid-view list-view)]
    ; (log/info req-id platform hosted-pl host url)
    (when platform
      (let [;top-projects (actions/top-projects-es* else platform limit-by)
            top-projects (actions/list-top-proj platform 100)
            keyset (keys (first top-projects))]
        (if json?
          (json-resp top-projects)
          (html-resp
            (views/layout {:website host :platform platform
                           :description (format "Top %s Projects, most popular %s projects, favourite %s projects "
                                          platform platform platform)}
              [:div.row
                [:div.col-lg-3
                  (left-menu host platform "open-source")]
                [:div.col-lg-9
                  (view-fn top-projects keyset)]])))))))

(defn get-project-readme*
  "Fetch read me. Redis is used.
  Or Fetches from github.
  TODO: Post retrieved data to ElasticSearch"
  [redis proj-obj]
  (when-not (nil? proj-obj)
  (if (not (empty? (:readme proj-obj)))
    (:readme proj-obj)

  (let [proj (:full_name proj-obj)
        cache-key (str "readme-" proj)
        cached (cache/retrieve redis cache-key)]
    (if (!nil? cached)
      (do
          (actions/set-project-readme proj cached)
          (cache/delete redis cache-key)
          cached)
      (let [readme (gh/project-readme proj)]
        (try
          ; (cache/setup redis cache-key readme)
          (actions/set-project-readme (:full_name proj) readme)
          redis
          (catch Throwable t
            (do
              (log/error t)
              (actions/set-project-readme (:full_name proj) :err)))
            )
        readme
        ))))))

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

(defn transform-user
  [x]
  x)

(defhtml project-header
  [id proj admin? owner contributor-count watcher-count]
  (panel id
  [:div.row
    [:div.col-lg-2 {:style "text-align: center;padding-top:15px;"}
      [:h3 [:i.fa.fa-star]
           [:a {:href (str "/p/" id "/stargazers")}
              (:watchers proj)]]
      [:form {:action "/ajax/project/follow" :method "POST"}
        [:input {:type "hidden" :value (:id proj)}]

        [:button.btn.btn-primary {:type "submit"} "Follow"]]
        (when admin?
          [:a.btn.btn-danger.btn-sm {
            :href (format "/p/%s?force-sync=1" id)
            :rel "nofollow"} "Synchronize"])
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
   (let [{:keys [host id body json? user platform
                req-id limit-by url hosted-pl]} (common-of request)
         id (format "%s/%s" (id-of request :user) (id-of request :project))
         force-sync (is-true (get-in request [:params :force-sync]))
         related-projects []
         admin? false
         proj (first (actions/load-project db id))
        ;  _ (log/infof "Project loaded. %s" (:full_name proj))
         proj-extras (actions/load-project-extras* db id)
         watcher-count (try (count (:watchers proj-extras)) (catch Throwable t 0))
         contributor-count (try  (count (:contributors proj-extras)) (catch Throwable t 0))
         owner (first (.split id "/"))
         owner-obj (actions/load-user2 db owner)
        ;  _ (log/infof "User loaded %s-> %s" owner owner-obj)
         ]
      (if json?
        (json-resp (selector proj-extras));(assoc proj :owner owner-obj))
        (views/layout {:website host
                       :title (str (:name proj) " - " (:description proj))
                       :platform platform
                       :description (:description proj)}
          [:div.row
            [:div.col-lg-3
              (left-menu host platform (str "p/" id))]
            [:div.col-lg-9
              (project-header id proj admin? owner contributor-count watcher-count)
              (mod-fn db selector id proj proj-extras)]]))))

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
      ;  (throw (clojure.lang.ArityException. 2 "Invalid Stargazer exce" ))
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
      (->
       (resp/response "Sorry. Page not found")
       (resp/status 404)))))

(defn get-py-contribs
  [{:keys [db event-chan redis]} request]

  )
(defn get-py-proj
  "Temporary solution till there is a proper solution.
  Somehow fetch the Python Package Projects. Same will go for Clojars."
  [{:keys [db event-chan redis]} request]
  (let [{:keys [host id body json? user platform
                req-id limit-by url hosted-pl]} (common-of request)
          proj (id-of request :project)
                ]
  (layout host
    [:div.row
      [:div.col-lg-3
        (left-menu host platform "open-source")]
      [:div.col-lg-9
        [:div.bs-callout.bs-callout-danger
          [:p "Python Packages currently unavailable. Please follow the link below."]
          [:h3 [:a {:href (str "https://pypi.python.org/pypi/" proj)} (format "%s at PyPI" proj)]]]]])
  ))

(defn take-x [coll n]
  (try
    (take n coll)
    (catch Throwable t
      (do
      (log/error t)
      []))))

(defn load-peeps [proj-extras]
  (let [contributors (:contributors proj-extras)
        watchers (:watchers proj-extras)
        stargazers (:stargazers proj-extras)]
    {:contributors (take-x contributors 10)
     :stargazers (take-x stargazers 10)
     :watchers (take-x watchers 10)
    }))

(defn get-proj
  "Ugly method to respond a project query. xyz.com/p/:user/:project"
  [{:keys [db event-chan redis]} request]
  (let [session-data (:session request)])
  (let [{:keys [host id body json? user platform
                req-id limit-by url hosted-pl]} (common-of request)

        id (format "%s/%s" (id-of request :user) (id-of request :project))
        force-sync (is-true (get-in request [:params :force-sync]))
        related-projects []
        admin? false]
    (let [proj (first (actions/load-project db id))]
      ; (log/info "Project loaded" (select-keys proj [:id :name :full_name]))
      (if force-sync
        (do
          (gh/enhance-proj db id 1000)
          (redirect (str "/p/" id)))
        (let [proj-extras (actions/load-project-extras* db id)
              ; _ (log/info "Project extras loaded" (select-keys proj-extras [:proj]))
              watcher-count (try (count (or (:watchers proj-extras) 0)) (catch Throwable t 0))
              contributor-count (try (count (:contributors proj-extras)) (catch Throwable t 0))
              owner (first (.split id "/"))
              owner-obj (actions/load-user2 db owner)
              ; _ (log/infof "User loaded %s" owner)
              ]
          (if json?
            (json-resp (-> proj
                          (assoc :owner owner-obj)
                          (assoc :readme (get-project-readme* redis proj))))
            (let [{:keys [contributors watchers stargazers]} (load-peeps proj-extras)
                  people (try (vec (set (concat contributors watchers stargazers))) (catch Throwable t []))
                  users (actions/load-users-by-id db people)
                  contributors (filter #(in? contributors (:login %)) users)
                  watchers (filter #(in? watchers (:login %)) users)
                  stargazers (filter #(in? stargazers (:login %)) users)]
            (html-resp
              (views/layout {:website host :platform platform
                             :title (:description proj)
                             :description (:description proj)
                             ; replace this with a multiplication or combination
                              :keywords (format "%s project, %s %s, %s tutorial, %s documentation, %s examples"
                                          (:language proj) (:language proj) (:name proj) (:name proj)
                                          (:name proj) (:name proj) )
                           }
                [:div.row
                  [:div.col-lg-3
                    (left-menu host platform "open-source")]
                  [:div.col-lg-9
                    (project-header id proj admin? owner contributor-count watcher-count)
                    [:div.row
                      [:div.col-lg-8
                        (views/panel "READ ME"
                          (get-project-readme* redis proj))]
                      [:div.col-lg-4
                        (panel [:a {:href (str "/p/" id "/contributors")}
                               [:span [:i.fa.fa-users] " Contributors" ]]
                          (user-list contributors ))
                        (panel [:a {:href (str "/p/" id "/watchers") }
                               [:span [:i.fa.fa-users] "Watchers"]]
                          (user-list watchers))
                        (panel [:a {:href (str "/p/" id "/stargazers") }
                               [:span [:i.fa.fa-users] "Stargazers"]]
                          (user-list stargazers))
                        (panel "Related Projects"
                          [:ul
                            (for [x related-projects]
                              [:li
                                [:a {:href (str "/p/"(:full_name x))} (:full_name x)
                                [:p {:style "color:gray"} (cutoff (:description x) 50)]]
                                ]
                              )])]]]])))))))))
