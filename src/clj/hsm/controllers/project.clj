(ns hsm.controllers.project
	(:require 
		[clojure.tools.logging :as log]
    [clojure.java.io :as io]
    [cheshire.core :refer :all]
    [ring.util.response :as resp]
    [hsm.actions :as actions]
    [hsm.ring :refer [json-resp]]
    [hsm.utils :as utils :refer [host-of body-of whois id-of]]))

(defn list-top-proj
	[[db event-chan] request]
	(log/warn request)
	(let [host  (host-of request)
        body (body-of request)
        user (whois request)
        data (utils/mapkeyw body)
        platform (get-in request [:route-params :platform])
        limit-by (or (Integer/parseInt (get-in request [:params :limit])) 20)]
		(json-resp
			(actions/list-top-proj db platform limit-by))))