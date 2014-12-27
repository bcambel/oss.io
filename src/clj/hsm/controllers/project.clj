(ns hsm.controllers.project
	(:require 
		[clojure.tools.logging :as log]
    [clojure.java.io :as io]
    [cheshire.core :refer :all]
    [ring.util.response :as resp]
    [hsm.actions :as actions]
    [hsm.utils :as utils :refer [host-of body-of whois id-of]]))

(defn list-top-proj
	[[db event-chan] request]
	(let [host  (host-of request)
        body (body-of request)
        user (whois request)
        data (utils/mapkeyw body)
        platform 1]
	(actions/list-top-proj db platform )
	))