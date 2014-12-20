(ns hsm.pipe.event
	(:require 
		[clojure.core.async 			:as async	:refer [go chan >!]]
		[cheshire.core 						:refer :all]))

(def ^:private event-types 
	"A Map containing any extra op that needs to be performed beforehand"
	{
		:create-user identity
		:follow-user identity

		:create-discussion
		:follow-discussion
	})


(defn create-event
	[event-type channel user-data]
	(go 
		(>! channel 
			["test" (generate-string {:type :create-user 
																:data user-data})])))

(def create-user (partial create-event :create-user))
(def follow-user (partial create-event :follow-user))
(def unfollow-user (partial create-event :unfollow-user))

(def create-discussion (partial create-event :create-discussion))
(def follow-discussion (partial create-event :follow-discussion))