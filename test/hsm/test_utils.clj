(ns hsm.test-utils
	(:use midje.sweet)
	(:require		[hsm.utils :refer :all]))

(tabular 
	(fact "Domain name resolution"
		(domain-of {:headers { "host" ?host }} ) => ?result )
		?host ?result
		"dev.pythonhackers.com" 		["dev" "pythonhackers" "com"]
		"pythonhackers.com" 				["pythonhackers" "com"]
		"www.clojurehackers.com" 		["www" "clojurehackers" "com"])

(tabular 
	(fact "Find Host of the Request"
		(host-of {:headers { "host" ?host }} ) => ?result)
		?host ?result
		 "dev.pythonhackers.com" "dev.pythonhackers.com"
		 "clojurehackers.com" "clojurehackers.com")

(tabular 
	(fact "Empty Host Request with falsy hash-map "
		(host-of ?request ) => ?result)
		?request ?result
		 {:headers { "TEST" 1}} nil
		 {:headers2 "test"} nil)

; Well Token fetcher always returns the same ID for right now! Very effective!! :)
(tabular 
	(fact "Find User Token"
		(whois {:headers { "X-AUTH-TOKEN" ?token }}) => ?result)
		?token ?result
		1 243975551163827208
		)