(ns hsm.views
	(:require 
		[hiccup.core :refer [html]]
    [hiccup.page :refer [doctype include-css include-js]]
		[hiccup.def  :refer [defhtml]]))

(defhtml row-fluid
  [& content]
  [:div.row-fluid
   content])

(defhtml container-fluid
  [& content]
  [:div.container-fluid
  	content])

(defhtml layout
  [website & content]
  (:html5 doctype)
  [:html {:lang "en-US"}
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:content "IE=edge,chrome=1"
            :http-equiv "X-UA-Compatible"}]
    [:title "Hackersome"]
    (include-css "//maxcdn.bootstrapcdn.com/bootswatch/3.3.1/sandstone/bootstrap.min.css")
   ]
   [:body
   	[:div.nav.navbar-default
   		[:div.navbar-header
   			[:a.navbar-brand {:href (format "http://%s" website)} "Hackersome"]]
   		[:form.navbar-form.navbar-left [:input {:type "text" :class "form-control col-lg-8" :placeholder "Search"}]]
   		[:ul.nav.navbar-nav.navbar-right [:li [:a "Link"] ]]
   		]
    [:div.container
     content]
    (include-js "//ajax.googleapis.com/ajax/libs/jquery/2.1.3/jquery.min.js")
    (include-js "//maxcdn.bootstrapcdn.com/bootstrap/3.3.1/js/bootstrap.min.js")   
    ]])