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
    [hsm.utils :as utils :refer [host-of body-of whois id-of]]))

(def platforms {
	"pythonhackers.com" {:lang "Python" :id 1}
	"clojurehackers.com" {:lang "Clojure" :id 2} })

(defn list-top-proj
	[[db event-chan] request]
	(log/warn request)
	(let [host  (host-of request)
        body (body-of request)
        user (whois request)
        data (utils/mapkeyw body)
        platform (get-in request [:route-params :platform])
        is-json false
        limit-by (or (Integer/parseInt (get-in request [:params :limit])) 20)]
    (when-let [top-projects (actions/list-top-proj db platform limit-by)]
			(if is-json
				(json-resp top-projects)
				(let [keyset (keys (first top-projects))]
					(html-resp
						(hic/html
							[:table
								[:thead [:tr (for [header keyset] [:th header])]]
								[:tbody
									(for [x top-projects]
										[:tr (for [ky (keys x)]
											[:td (get x ky)])])]])))))))