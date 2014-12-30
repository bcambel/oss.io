(ns hsm.controllers.main
	(:require 
		[hsm.ring 		:refer [html-resp]]
		[hsm.views 		:refer :all]
		[hsm.utils 		:refer [host-of id-of]]))

(defn homepage
	[[db event-chan] request]
	(let [host (host-of request)]
		(html-resp 
			(layout host 
				[:div 
					[:a {:href "/Clojure/top-projects"} "Clojure"]
					[:a {:href "/Python/top-projects"} "Python"]]
					))))


(defn platform
	[[db event-chan] request]
	(let [host (host-of request)
				pl   (id-of request :platform)]
		(html-resp 
			(layout host
				[:div 
					[:p (str "Welcome to " pl)]
					[:a {:href "/Clojure/top-projects"} "Clojure"]
					[:a {:href "/Python/top-projects"} "Python"]]
					))))