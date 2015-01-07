(ns hsm.controllers.main
  (:require 
    [hiccup.def     :refer [defhtml]]
    [hsm.ring       :refer [html-resp]]
    [hsm.views      :refer :all]
    [hsm.utils      :refer [host-of id-of cutoff]]
    [hsm.helpers    :refer [pl->lang]]
    [hsm.conf       :refer [languages]]
    [hsm.actions    :refer [list-top-proj list-top-disc list-top-user]]))



(defn homepage
  [[db event-chan] request]
  (let [host (host-of request)]
    (html-resp 
      (layout host (languages-pane)
        ))))

(defn platform
  [[db event-chan] request]
  (let [host (host-of request)
        pl   (pl->lang (id-of request :platform))
        top-disc (list-top-disc db pl 5)
        top-members (list-top-user db pl 5)
        top-projects (list-top-proj db pl 20)]
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
