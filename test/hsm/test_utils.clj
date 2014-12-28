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

(tabular
	(fact "Select Values of a Hash-Map"
		(select-values ?hashmap ?keys) => ?result)
		?hashmap ?keys ?result
		{:a 1 :b 2} [:a :b] [1 2]
		{:a 1 :b 2} [:a] [1]
		{:a 1 :b 2} [] []
		{:a 1 :b 2} nil []
		)

(let [temp-file (java.io.File/createTempFile (str "body-test" (now->ep)) ".txt")]
	(doto (clojure.java.io/writer temp-file)
		(.write "testing")
		(.close))
	(tabular
		(fact "Body Slurping"
			(body-as-string {:body ?body}) => ?result)
			?body ?result
			"test" "test"
			temp-file "testing")
	(.delete temp-file))