(ns hsm.controllers.main
  (:require 
    [clojure.tools.logging        :as log]
    [clojure.string :as str]
    [hiccup.def                   :refer [defhtml]]
    [markdown.core :refer [md-to-html-string]]
    [clojurewerkz.elastisch.rest.document :as esd]
    [clojurewerkz.elastisch.query :as q]
    [clojurewerkz.elastisch.rest.response :as esrsp]
    [hsm.ring                     :refer [html-resp json-resp]]
    [hsm.views                    :refer :all]
    [hsm.utils                    :refer [host-of id-of cutoff pl->lang common-of]]
    [hsm.conf                     :refer [languages]]
    [hsm.actions                  :refer [list-top-proj list-top-disc list-top-user top-projects-es]]))



(defn homepage
  [[db event-chan] request]
  (let [host (host-of request)]
    (html-resp 
      (layout host (languages-pane)
        ))))

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
        slug (format "%s/%s" user (id-of request :slug))
        es-conn     (:conn else)]
    (log/warn user slug)
    (let [res (esd/search es-conn (:index else) "tutorial"
              :query  (q/filtered 
                    :filter   (q/term :owner (str/lower-case user))))
          n (esrsp/total-hits res)
          hits (esrsp/hits-from res)]
      (if json?
        (json-resp (map :_source hits))
        (html-resp
              (layout host 
                [:div.row 
                [:div.col-lg-3
                  (left-menu host platform "open-source")]
                [:div.col-lg-9
                  (for [data (map :_source hits)]
                    [:div (md-to-html-string (:content data))]
                    )
                  
                  ]]

    ))))))
