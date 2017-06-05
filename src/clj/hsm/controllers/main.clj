(ns hsm.controllers.main
  (:require
    [taoensso.timbre        :as log]
    [clojure.string :as str]
    [hiccup.def                   :refer [defhtml]]
    [markdown.core :refer [md-to-html-string]]
    [ring.util.codec :as codec]
    [hsm.ring                     :refer [html-resp json-resp]]
    [hsm.views                    :refer :all]
    [hsm.utils                    :refer [host-of id-of cutoff pl->lang common-of]]
    [hsm.conf                     :refer [languages]]
    [hsm.actions                  :refer [list-top-proj]]
    ))

(defn homepage
  [{:keys [db event-chan redis else]} request]
  (let [{:keys [host id body json? user platform
                req-id limit-by url hosted-pl]} (common-of request)]
    (html-resp
      (layout {:website host :title "Community for developers"
                :keywords "Developer Community, Top Projects," }
        [:div.row
          [:div.col-lg-2 ]
          [:div.col-lg-10
            [:div.jumbotron
              [:h1 "Community for developers"]
              [:hr]
              ; [:p "Currently under high development. We will keep you updated if you "
              ;   [:a {:href "#mc_embed_signup" :data-toggle :modal} "subscribe now!"]]
              [:a.btn.btn-success.btn-lg {:href "/register" } "Join free"]
              [:h2 "Pssst, also check out these"
                [:a {:href "/open-source/?utm_source=main_page_link"} " Top Projects"]
                ]
              [:hr]
              [:a.twitter-follow-button {:href "https://twitter.com/pythonhackers"
                                        :data-show-count true :data-size :large }]
        ]]]))))

(defn about
  [{:keys [db event-chan redis else]} request]
  (let [{:keys [host id body json? user platform
                req-id limit-by url hosted-pl]} (common-of request)]
    (html-resp
      (layout {:website host :title (format "Community for %s developers" platform)
               :keywords "Developer Community, Top Projects," }
        [:div.row
          [:div.col-lg-2 ]
          [:div.col-lg-10
            [:div.jumbotron
              [:h1 "About Hackersome"] [:h3 platform " hackers platform"]
              [:hr ]
              [:a.btn.btn-success.btn-lg {:href "#mc_embed_signup" :data-toggle :modal} "Subscribe"]
              [:hr]
              [:h2 "Pssst, also check out these" [:a {:href "/open-source/?utm_source=about_page_link"} " Top Projects"]]
              [:hr]
              [:a.twitter-follow-button {:href "https://twitter.com/pythonhackers"
                                          :data-show-count true :data-size :large }]
        ]]]))))


(defn platform
  [{:keys [db event-chan redis else]} request]
  (let [host (host-of request)
        pl   (pl->lang (id-of request :platform))
        top-members []
        top-projects (list-top-proj pl 100)
        ]
    (html-resp
      (layout {:website host
              :title (format "Top %s Project index" pl)
              :description (format "Top %s Project index, %s discussions, %s users" pl pl pl)
              :platform pl}
        [:div
          [:h1 (str "Welcome to " pl)]
          [:div.row
            [:div.col-lg-4
              (panel [:a {:href (format "/%s/members" pl)} "Members"]
                [:ul {:style "list-style-type:none;padding-left:1px;" }
                  (for [x top-members]
                    [:li [:a {:href (str "/user2/"(:login x))} (:login x)]])])]
            [:div.col-lg-4
              (panel [:a {:href (format "/%s/top-projects" pl)} "Top Projects"]
                [:ul {:style "list-style-type:none;padding-left:1px;" }
                  (for [x top-projects]
                    [:li
                      [:a {:href (str "/p/"(:full_name x))} (:full_name x)
                      [:p {:style "color:gray"} (cutoff (:description x) 50)]]
                      ]
                    )])]]]))))
