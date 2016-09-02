(ns hsm.controllers.main
  (:require
    [clojure.tools.logging        :as log]
    [clojure.string :as str]
    [hiccup.def                   :refer [defhtml]]
    [markdown.core :refer [md-to-html-string]]
    ; [clojurewerkz.elastisch.rest.document :as esd]
    [ring.util.codec :as codec]
    ; [clojurewerkz.elastisch.query :as q]
    ; [clojurewerkz.elastisch.rest.response :as esrsp]
    [hsm.ring                     :refer [html-resp json-resp]]
    [hsm.views                    :refer :all]
    [hsm.utils                    :refer [host-of id-of cutoff pl->lang common-of]]
    [hsm.conf                     :refer [languages]]
    ; [hsm.actions                  :refer [top-projects-es]]
    ))

(defn homepage
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
              [:h1 "Community for " platform " developers"]
              [:hr]
              [:p "Currently under high development. We will keep you updated if you "
                [:a {:href "#mc_embed_signup" :data-toggle :modal} "subscribe now!"]]
              [:a.btn.btn-success.btn-lg {:href "#mc_embed_signup" :data-toggle :modal} "Subscribe free"]
              [:h2 "Pssst, also check out these"
                [:a {:href "/open-source/?utm_source=main_page_link"} " Top Projects"] " or "
                [:a.green {:href "/users?utm_source=main_page_link"} " Top Users"]
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
        top-projects [] ; (top-projects-es else pl 100)
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

; (defn tutorial
;   [{:keys [db event-chan redis else]} request]
;   (let [{:keys [host id body json? user platform
;                 req-id limit-by url hosted-pl]} (common-of request)
;         user (id-of request :user)
;         orig-slug (id-of request :slug)
;         slug  (format "%s-%s" user orig-slug)
;         es-conn     (:conn else)]
;     (log/warn user (codec/url-encode slug))
;     (let [res (esd/search es-conn (:index else) "tutorial"
;               :query  (q/term :owner (codec/url-encode user)))
;           n (esrsp/total-hits res)
;           pre-hits (esrsp/hits-from res) hits []]
;
;       (log/warn slug "=>" (map :slug (map :_source pre-hits)))
;       (let [filtered  (filter (fn[x] (= (:slug x) slug)) (map :_source pre-hits))]
;
;         (if json?
;           (json-resp filtered)
;           (html-resp
;             (layout {:website host
;                       :title (str platform " " (:title (first filtered)) " Tutorial")
;                       :description (format "%s Tutorial" (:title (first filtered)))
;                       :keywords (str (:keywords (first filtered)) "," platform " tutorial")
;                       :platform platform}
;               [:div.row
;               [:div.col-lg-3
;                 (left-menu host platform (str "/tutorial/"user"/"orig-slug))]
;               [:div.col-lg-9
;                 [:h4 [:a {:href "/tutorial/"} "< All Tutorials"]]
;                 [:hr]
;                 (for [data filtered]
;                   (panel
;                     [:h1 (:title data)]
;                     [:div (md-to-html-string (:content data))]))]])))))))

; (defn all-tutorial
;   [{:keys [db event-chan redis else]} request]
;   (let [{:keys [host id body json? user platform
;                 req-id limit-by url hosted-pl]} (common-of request)
;         user (or (id-of request :user) "bcambel")
;         slug  (format "%s-%s" user (id-of request :slug))
;         es-conn     (:conn else)]
;     (let [res (esd/search es-conn (:index else) "tutorial"
;               :query  (q/term :owner (codec/url-encode user)))
;           n (esrsp/total-hits res)
;           pre-hits (esrsp/hits-from res) hits []]
;       (let [filtered (mapv :_source pre-hits)]
;         (log/warn (keys (first filtered)))
;         (if json?
;           (json-resp filtered)
;           (html-resp
;             (layout {:website host
;                      :title (str platform " " (:title (first filtered)) " Tutorial")
;                      :platform platform}
;               [:div.row
;               [:div.col-lg-3
;                 (left-menu host platform "tutorial")]
;               [:div.col-lg-9
;                 (for [data filtered]
;
;                   [:div
;                     [:h3 [:a {:href (format "/tutorial/%s/%s"
;                                     (:owner data)
;                                     (str/join "-" (rest (vec (.split (:slug data) "-")))))} (:title data)] ]
;                     ; [:div (md-to-html-string (:content data))]
;                     ])]])))))))
