(ns hsm.controllers.main
	(:require 
		[hsm.ring 		:refer [html-resp]]
		[hsm.views 		:refer :all]
		[hsm.utils 		:refer [host-of id-of cutoff]]
		[hsm.helpers  :refer [pl->lang]]
		[hsm.actions 	:refer [list-top-proj]]))

(defn homepage
	[[db event-chan] request]
	(let [host (host-of request)]
		(html-resp 
			(layout host 
				[:div 
					[:a {:href "/Clojure/top-projects"} "Clojure"]
					[:a {:href "/Python/top-projects"} "Python"]
					[:a {:href "/Erlang/top-projects"} "Erlang"]
					[:a {:href "/JavaScript/top-projects"} "Javascript"]
					[:a {:href "/C/top-projects"} "C"]
					[:a {:href "/Rust/top-projects"} "Rust"]
					[:a {:href "/Lisp/top-projects"} "LISP"]
					]
					))))


(defn platform
	[[db event-chan] request]
	(let [host (host-of request)
				pl   (pl->lang (id-of request :platform))
				top-projects (list-top-proj db pl 10)]
		(html-resp 
			(layout host
				[:div 
					[:h1 (str "Welcome to " pl)]
					[:div.row
						[:div.col-lg-4
							[:div.panel.panel-primary
								[:div.panel-heading "Latest discussions"]
								[:div.panel-body 
									[:ul
										(for [x (range 10)]
											[:li (str "Discussion " x)]
											)]]]]
						[:div.col-lg-4
							[:div.panel.panel-warning
								[:div.panel-heading "Latest members"]
								[:div.panel-body 
									[:ul
										(for [x (range 10)]
											[:li (str "Member " x)]
											)]]]]
						[:div.col-lg-4
							[:div.panel.panel-default
								[:div.panel-heading 
									[:a {:href (format "/%s/top-projects" pl)} "Top Projects"]]
								[:div.panel-body 
									[:ul {:style "list-style-type:none;padding-left:1px;" }
										(for [x top-projects]
											[:li 
												[:a {:href (str "/p/"(:full_name x))} (:full_name x)
												[:p {:style "color:gray"} (cutoff (:description x) 50)]]
												]
											)]]]]]]))))
