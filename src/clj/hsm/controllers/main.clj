(ns hsm.controllers.main
  (:require 
    [clojure.tools.logging        :as log]
    [clojure.string :as str]
    [hiccup.def                   :refer [defhtml]]
    [markdown.core :refer [md-to-html-string]]
    [clojurewerkz.elastisch.rest.document :as esd]
    [ring.util.codec :as codec]
    [clojurewerkz.elastisch.query :as q]
    [clojurewerkz.elastisch.rest.response :as esrsp]
    [hsm.ring                     :refer [html-resp json-resp]]
    [hsm.views                    :refer :all]
    [hsm.utils                    :refer [host-of id-of cutoff pl->lang common-of]]
    [hsm.conf                     :refer [languages]]
    [hsm.actions                  :refer [list-top-proj list-top-disc list-top-user top-projects-es]]))

(defn homepage
  [[db event-chan redis else] request]
  (let [{:keys [host id body json? user platform 
                req-id limit-by url hosted-pl]} (common-of request)]
    (html-resp 
      (layout host 
        [:div.row
          [:div.col-lg-2 ]
          [:div.col-lg-10
            [:div.jumbotron
              [:h1 "Community for " platform " developers"]
              [:a.btn.btn-success.btn-lg {:href "#mc_embed_signup" :data-toggle :modal} "Subscribe"]
              [:h2 "Pssst, also check out these" [:a {:href "/open-source/"} " Top Projects"]]
        ]]]))))

(defn platform
  [{:keys [db event-chan redis else]} request]
  (let [host (host-of request)
        pl   (pl->lang (id-of request :platform))
        top-disc (list-top-disc db pl 5)
        top-members (list-top-user db pl 5)
        top-projects (top-projects-es else pl 100)]
    (html-resp 
      (layout host
        [:div 
          [:h1 (str "Welcome to " pl)]
          [:div.row
            [:div.col-lg-4
              (panel [:a {:href (format "/%s/discussions" pl)} "Discussions"]
                [:ul {:style "list-style-type:none;padding-left:1px;" }
                  (for [x top-disc]
                    [:li 
                      [:a {:href (str "/discussion/" (:id x))} (:title x) 
                        [:p {:style "color:gray" } (get-in x [:post :text])]]])])]
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

(defn tutorial
  [{:keys [db event-chan redis else]} request]
  (let [{:keys [host id body json? user platform 
                req-id limit-by url hosted-pl]} (common-of request)
        user (id-of request :user)
        slug  (format "%s-%s" user (id-of request :slug))
        es-conn     (:conn else)]
    (log/warn user (codec/url-encode slug))
    (let [res (esd/search es-conn (:index else) "tutorial"
              :query  (q/term :owner (codec/url-encode user)))
          n (esrsp/total-hits res)
          pre-hits (esrsp/hits-from res) hits []]

      (log/warn slug "=>" (map :slug (map :_source pre-hits)))
      (let [filtered  (filter (fn[x] (println (:slug x) slug) (= (:slug x) slug)) (map :_source pre-hits))]
      (if json?
        (json-resp filtered)
        (html-resp
              (layout host 
                [:div.row 
                [:div.col-lg-3
                  (left-menu host platform "open-source")]
                [:div.col-lg-9
                  (for [data filtered]
                    [:div (md-to-html-string (:content data))]
                    )
                  
                  ]]

    )))))))
