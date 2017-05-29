(ns hsm.controllers.user
  (:require [taoensso.timbre        :as log]
            [clojure.java.io              :as io]
            [clojure.core.memoize         :as memo]
            [clojure.string               :as str]
            [cheshire.core                :refer :all]
            [ring.util.response           :as resp]
            [hiccup.def                   :refer [defhtml]]
            ; [clojurewerkz.elastisch.rest.document :as esd]
            [hsm.actions                  :as actions]
            [hsm.views                    :refer [layout panel panelx render-user left-menu]]
            [hsm.ring                     :refer [json-resp html-resp redirect]]
            [hsm.integration.ghub         :as gh]
            [hsm.cache                    :as cache]
            [hsm.utils                    :refer :all]))

(defn fetch-users
  [db ppl-list]
  (let [users (actions/load-users-by-id db (vec ppl-list))]
    (reverse (sort-by :followers users))))

(defhtml render-users
  [db ppl-list]
  (let [users (actions/load-users-by-id db (vec ppl-list))]
     (for [x (reverse (sort-by :followers users))]
        [:li (render-user x)])))

; (defn get-user
;   [[db event-chan] request]
;   (let [id (id-of request)
;         user (actions/load-user db id)]
;     (json-resp user)))

(defhtml user-part
  [id user admin? c-star c-follow c-followers]
  (panel "A User" ;[:a {:href (str "/user2/" id)} (:login user)]
    ; [:img.img-responsive.img-rounded {:src (:image user)}]
    [:h3 ;[:span (:login user)]
      "A User"
      ; [:a.pad10 {:href (str "https://github.com/" (:login user))} [:i.fa.fa-github]]
      ]
    [:h5 "A User"] ;[:span (:name user)]]
    ; [:p [:a {:href (:blog user)}(:blog user)]]
    ; [:p (:company user)]
    ; [:p (:location user)]
    ; [:p (:type user)]
    ; [:a {:href (str "mailto://" (:email user))} (:email user)]
    [:hr]

    [:h3 [:a {:href (str "/user2/" id "/starred")} c-star ]" starred "]
    [:h3 [:a {:href (str "/user2/" id "/following")} c-follow ]" following "]
    [:h3 [:a {:href (str "/user2/" id "/followers")} c-followers ]" followers "]
    ))

(defhtml render-repos
  [repos]
  [:table.table
    (for [repo repos]
    [:tbody
      [:tr
        [:td {:rowspan 2} [:span (:watchers repo)]]
        [:td
          [:a {:href (str "/p/" (:full_name repo))}
          (:full_name repo)
          [:span.label (:stars repo)]
          [:span.label (:forks repo)]
          ]]]
      [:tr
          [:td
          [:p.gray (:description repo)]]]])])

(defn opt-out-list*
  []
  (let [text (vec (.split (slurp "opt-out.txt") "\n"))]
    (println "OPT-OUT USERS" text)
    text))

(def opt-out-list (memo/memo opt-out-list*))

(defn opted-out?
  [user]
  (let [opted-out-users (opt-out-list)]
    (in? opted-out-users user)))

(defn get-user2
  [{:keys [db event-chan redis conf else]} request]
  (let [{:keys [host id body json? user platform
                req-id limit-by url hosted-pl]} (common-of request)
        user (actions/load-user2 db id)
        admin? false
        force-sync (is-true (get-in request [:params :force-sync]))
        is-json (type-of request :json)]
    ; (log/info user)
    (if (opted-out? id)
      (layout {:website host :title "User does not exist"} "User opted-out.")
      (if (or force-sync (not (:full_profile user)))
        (do

          (future (gh/find-n-update-user db id conf))
          (Thread/sleep 2000)
          (if is-json
            (redirect (format "/user2/%s?json=1" id))
            (redirect (str "/user2/" id))))
          ; (json-resp {:ok 1})

        (let [user-extras (actions/user-extras db id)
              ; user-repos (reverse (sort-by :watchers (actions/load-projects-by-id db (vec (:repos user-extras)))))
              user-repos (actions/user-projects-es* id 100)
              c-star (count (:starred user-extras))
              c-follow (count (:following user-extras))
              c-followers (count (:followers user-extras))
              org? (= (:type user) "Organization")]
            ; (log/warn user-repos)
          (if is-json
            (json-resp user)
            (layout {:website host :title "A User"} ;(format "%s - %s " (:login user) (:name user))
              [:div.row
                [:div.col-lg-3
                  (left-menu host platform (str "/user2/" id))]
                [:div.col-lg-9
                [:div.alert.alert-info
                  [:p (format "The following user is not a member of the %s community.
                        Below data is part of publicly available Github API." platform)]]
                  [:div.row
                    [:div.col-lg-4
                      (user-part id user admin? c-star c-follow c-followers)
                      (when-not org?
                        (do
                          (panel [:a {:href (format "/user2/%s/following" (:login user))}
                                    (str "Following: " c-follow)]
                            [:ul.user-list
                              (render-users db (take 10 (:following user-extras)))])
                          (panel [:a {:href (format "/user2/%s/followers" (:login user))}
                                  (str "Followers: " c-followers) ]
                            [:ul.user-list
                              (render-users db (take 10(:followers user-extras)))])))]
                    [:div.col-lg-8
                      (when-not org?
                        [:div.col-lg-12
                        (panel [:a {:href (format "/user2/%s/starred" (:login user))}
                                (str "Starred " c-star) ]
                          [:ul (for [star (take 10 (:starred user-extras))]
                            [:li [:a {:href (str "/p/" star)} star]])])])
                      [:div.col-lg-12
                      (panelx "User Repos" ["no-pad"] ""
                        (render-repos user-repos)
                        )]]]]])))))))

(defn sync-user2
  [[db event-chan] request]
  (let [id (id-of request)
        host (host-of request)]
    (gh/enhance-user db id 1000)
    (json-resp (actions/load-user2 db id))
  ))

; (defn create-user
;   [[db event-chan] request]
;   (let [host  (get-in request [:headers "host"])
;         body (parse-string (body-as-string request))
;         user-data (mapkeyw body)]
;     (actions/create-user db user-data)
;     (event-pipe/create-user event-chan user-data)
;     (json-resp { :ok body })))

; (defn ^:private get-user-detail
;   [func [db event-chan] request]
;   (let [host  (host-of request)
;         body (body-of request)
;         current-user (whois request)
;         user-id (BigInteger. (id-of request))]
;         (json-resp (func db user-id))))

; (def get-user-following (partial get-user-detail actions/load-user-following))
; (def get-user-followers (partial get-user-detail actions/load-user-followers))

(defn get-user-activity
  [[db event-chan] request]
  (let [host  (host-of request)
        body (body-of request)]))

(defn user2-follower
  [{:keys [db event-chan redis]} request]
  (let [id (id-of request)
        host (host-of request)
        user (actions/load-user2 db id)
        admin? false
        user-extras (actions/user-extras db id)
        is-json (type-of request :json)
        c-star (count (:starred user-extras))
        c-follow (count (:following user-extras))
        c-followers (count (:followers user-extras))
        org? (= (:type user) "Organization")]
    (if (opted-out? id)
      (layout {:website host :title "User does not exist"} "User opted-out.")
      (if is-json
        (json-resp (:followers user-extras))
        (layout {:website host
                :title "A User" ;(format "%s - %s " (:login user) (:name user))
                ; :desription (format "%s (%s) followed by these users " (:login user) (:name user))
                ; :keywords (str/join "," [(:login user) (:name user) (format "%s followers" (:name user))])
              }
            [:div.row
              [:div.col-lg-3
                (user-part id user admin? c-star c-follow c-followers)]
              [:div.col-lg-9
              (when-not org?
                [:div.col-lg-12
                (panel [:a {:href (format "/user2/%s/starred" (:login user))} (str "Followers " c-followers) ]
                  [:div.user-list.row
                    (for [x (fetch-users db (:followers user-extras))]
                        [:div.col-lg-3.user-thumb
                          (render-user x)])])])]])))))

(defn user2-following
  [{:keys [db event-chan redis]} request]
  (let [id (id-of request)
        host (host-of request)
        user (actions/load-user2 db id)
        admin? true
        user-extras (actions/user-extras db id)
        is-json (type-of request :json)
        c-star (count (:starred user-extras))
        c-follow (count (:following user-extras))
        c-followers (count (:followers user-extras))
        org? (= (:type user) "Organization")]
    (if (opted-out? id)
      (layout {:website host :title "User does not exist"} "User opted-out.")
      (if is-json
        (json-resp (:following user-extras))
        (layout {:website host
                 :title "A User" ;(format "%s (%s) following these users " (:login user) (:name user))
                  ; :description (format "%s (%s) following these users " (:login user) (:name user))
                  ; :keywords (str/join "," [(:login user) (:name user) (format "%s following" (:name user))])
               }
            [:div.col-lg-3
              (user-part id user admin? c-star c-follow c-followers)]
            [:div.col-lg-9
            (when-not org?
              [:div.col-lg-12
              (panel [:a {:href (format "/user2/%s/starred" (:login user))} (str "Following " c-follow) ]
                [:div.user-list.row
                  (for [x (fetch-users db (:following user-extras))]
                      [:div.col-lg-3.user-thumb
                        (render-user x)])]
                )])])))))

(defn organization-events
  [{:keys [db event-chan redis]} request]
  (let [id (id-of request)
        host (host-of request)
        user (actions/load-user2 db id)]
    (when (and user (= (:type user) "Organization"))
      (gh/import-org-events id)
        )))


(defn user2-starred
  [{:keys [db event-chan redis]} request]
  (let [id (id-of request)
        host (host-of request)
        user (actions/load-user2 db id)
        user-extras (actions/user-extras db id)
        admin? true
        is-json (type-of request :json)
        c-star (count (:starred user-extras))
        c-follow (count (:following user-extras))
        c-followers (count (:followers user-extras))
        org? (= (:type user) "Organization")]
    (if (opted-out? id)
      (layout {:website host :title "User does not exist"} "User opted-out.")
      (if is-json
            (json-resp (:starred user-extras))
            (layout {:website host :title "A User"
                ; (format "%s - %s " (:login user) (:name user))
              }
                [:div.col-lg-3
                  (user-part id user admin? c-star c-follow c-followers)]
                [:div.col-lg-9
                (when-not org?
                  [:div.col-lg-10
                  (panel [:a {:href (format "/user2/%s/starred" (:login user))} (str "Starred " c-star) ]
                    (render-repos (reverse (sort-by :watchers (actions/load-projects-by-id db (vec (:starred user-extras))))))
                    )])])))))

(defn user2-contrib
  [{:keys [db event-chan redis]} request])

(defn user2-activity
  [{:keys [db event-chan redis]} request])

(defn some-user
  [{:keys [db event-chan redis else]} request]
  (let [{:keys [host id body json? user platform req-id limit-by url hosted-pl]} (common-of request)
        limit-by 100
        es-conn     (:conn else)
        search-rez [];(esd/search es-conn (:index else) "github_user" :sort [ { :followers {:order :desc}}] :size 100)
        users (map :_source (-> search-rez :hits :hits))
        is-json (type-of request :json)]
    (if is-json
      (json-resp users)
      (html-resp
        (layout {:website host :title "Top Users" :platform platform}
          [:div.row
            [:div.col-lg-3
              (left-menu host platform "open-source")]
            [:div.col-lg-9
              [:table.table.table-striped
              (for [x users]
                  [:tr
                    [:td [:h4 (:followers x)]]
                    [:td
                      ; [:img.img-rounded.img-responsive.pull-left {:src (:image x)
                      ; :style "width:64px;margin-right:10px;"}]
                      [:a {:href (str "/user2/" (:login x))} (:login x)]
                      [:br]
                      (:name x)
                      [:p
                        [:a {:href (:blog x)} (:blog x)]

                        [:br]
                        (when (:email x)[:a {:href (:email x)} (:email x)])
                        ]]])]]])))))
