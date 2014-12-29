(ns hsm.controllers.project
  (:require 
    [clojure.tools.logging :as log]
    [clojure.java.io :as io]
    [cheshire.core :refer :all]
    [ring.util.response :as resp]
    [hiccup.core :as hic]
    [hiccup.page :as hic.pg]
    [hiccup.element :as hic.el]
    [hsm.actions :as actions]
    [hsm.ring :refer [json-resp html-resp]]
    [hsm.views :as views]
    [hsm.utils :as utils :refer [host-of body-of whois id-of]]))

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

(defn list-view
	[top-projects keyset]
	[:table.table
		[:tbody
			(for [x top-projects]
				[:tr
					[:td (get x :watchers)]
					[:td  
						[:a {:target "_blank" :href (format "https://github.com/%s" (get x :full_name))} (get x :full_name)
						[:p {:style "color:gray"} (get x :description)]]]
				])]])

(defn list-top-proj
  [[db event-chan] request]
  (log/warn request)
  (let [host  (host-of request)
        body (body-of request)
        user (whois request)
        data (utils/mapkeyw body)
        platform (get-in request [:route-params :platform])
        is-json false
        view (get-in request [:params :view])
        view-fn (if (= view "grid") grid-view list-view)
        limit-by (or (Integer/parseInt (get-in request [:params :limit])) 20)]
    (let [top-projects (actions/list-top-proj db platform limit-by)
    			keyset (keys (first top-projects))]
      (if is-json
        (json-resp top-projects)
        (html-resp
          (views/layout host
            (view-fn top-projects keyset)))))))