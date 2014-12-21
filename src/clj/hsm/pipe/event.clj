(ns hsm.pipe.event
	(:require 
		[clojure.core.async 			:as async	:refer [go chan >!]]
		[cheshire.core 						:refer :all]))

(def ^:private event-types 
	"A Map containing any extra op that needs to be performed beforehand"
	{
		:create-user identity
		:follow-user identity
		:create-discussion identity
		:follow-discussion identity
	})


(defn create-event
	[event-type channel user-data]
	(go 
		(>! channel 
			["test" (generate-string {:type event-type
																:data user-data})])))

(def create-user (partial create-event :create-user))

(defn follow-user-event 
	"Beware! Used both for follow and unfollow user actions."
	[act-name event-chan data] 
	(create-event act-name event-chan data))

; (def unfollow-user (partial create-event :unfollow-user))

(def create-discussion (partial create-event :create-discussion))
(def post-discussion (partial create-event :post-discussion))
(def follow-discussion (partial create-event))