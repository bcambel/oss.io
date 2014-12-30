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
		 "dev.pythonhackers.com" 		"dev.pythonhackers.com"
		 "clojurehackers.com" 			"clojurehackers.com")

(tabular
	(fact "Empty Host Request with falsy hash-map "
		(host-of ?request ) => ?result)
		?request ?result
		 {:headers { "TEST" 1}} 		nil
		 {:headers2 "test"} 				nil
		 )

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
		{:a 1 :b 2} [:a] 		[1]
		{:a 1 :b 2} [] 			[]
		{:a 1 :b 2} nil 		[]
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

(tabular
	(fact "Convert maps into keyword keyed hashmaps"
		(mapkeyw ?hashmap) => ?result)
		?hashmap ?result
		{"a" 1 "b" 2} {:a 1 :b 2}
		{:a 1 :b 2} 	{:a 1 :b 2}
		{1 2} 				{nil 2}
		{1 2 3 4} 		{nil 4}
		{"1" 2 "3" 4} {:1 2 :3 4}
	)

(tabular 
	(fact "FQDN"
		(fqdn {:headers {"host" ?request}}) => ?result)
		?request ?result
		"dev.pythonhackers.com" "dev.pythonhackers.com"
		"pythonhackers.com" "www.pythonhackers.com"

	)

(tabular 
	(fact "Not NIL"
		(!nil? ?obj) => ?bool)
		?obj ?bool
		1			true
		nil   false
		[]		true
		[nil] true
		0			true
	)	

(tabular 
	(fact "NOT BLank" 
		(!blank? ?str) => ?bool)
		?str ?bool
		""		false
		"a"		true
		:1		(throws ClassCastException)
		1			(throws ClassCastException)
	)

(tabular
	(fact "Vecof 2"
		(vec2? ?vec) => ?bool)
		?vec ?bool
		nil 			false
		[1 2]			true
		[1 [1 2]] true
		[nil nil] true
	)

(tabular
	(fact "Vecof N"
		(nvec? ?n ?vec) => ?bool)
		?vec ?n ?bool
		nil 				1		false
		[1 2]				2	true
		[1 [1 2]] 	2 true
		[nil nil] 	2 true
		[nil nil 1] 3 true
	)

(fact "Has 1" (vec1? [nil]) => true)
(fact "Has 3" (vec3? [nil nil 0]) => true)
(fact "Not Negative Num" (!neg? 123) => true)
(fact "Not Negative Num" (!neg? -123) => false)
(fact "Not Negative Num" (!neg? 0) => true)
(fact "0 is not Positive" (pos-int? 0) => false)
(fact "1 is Positive" (pos-int? 1) => true)
(fact "Max VALUE is Positive" (pos-int? Integer/MAX_VALUE) => true)
(fact "MAX VALUE + 1 is Positive" (pos-int? (+ 1 Integer/MAX_VALUE)) => true)
(fact "MIN VALUE - 1 is Positive" (pos-int? (- 1 Integer/MIN_VALUE)) => true)

(tabular
	(fact "Vectorize"
		(vec* ?coll) => ?res)
		?coll ?res
		#{1}	[1]
		nil 		[]
		[1]			[1]
		[nil]		[nil]
		{:a 1}	[[:a 1]]
		#{:a 1} [1 :a]
	)

(tabular
	(fact "Setify"
		(set* ?coll) => ?res)
		?coll ?res
		#{1}		#{1}
		nil 		#{}
		[1]			#{1}
		[nil]		#{nil}
		{:a 1}	#{[:a 1]}
		#{:a 1} #{:a 1}
	)

(tabular
	(fact "Not NIL but Equal"
		(!nil= ?o1 ?o2) => ?res)
		?o1 			?o2 			?res
		1 				2 				false
		1 				nil 			false
		nil 			1 				false
		1 				[1]				false
		1 				1/1 			true
		[1] 			[[1]]			false
		[1] 			#{1}			false
		[]				nil 			false
		[]				#{}				false
		[] 				'()				true
		[1]				'(1)			true
		[1 [1]]		'(1 [1])	true
	)