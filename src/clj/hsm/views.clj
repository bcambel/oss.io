(ns hsm.views
  (:require
    [clojure.string     :as s]
    [hiccup.core        :refer [html ]]
    [hiccup.page        :refer [doctype include-css include-js html5]]
    [hiccup.def         :refer [defhtml ]]
    [hsm.dev            :refer [is-dev?]]
    [hsm.conf           :refer [languages]]))

(defhtml row-fluid
  [& content]
  [:div.row-fluid
   content])

(defhtml container-fluid
  [& content]
  [:div.container-fluid
    content])

(defhtml panelx
  ([header body-css & content ]
    (let [panel-body-css (s/join " " 
                            (conj (set body-css) "panel-body"))]
      [:div.panel.panel-default 
        [:div.panel-heading header]
        [:div {:class panel-body-css} 
          content]])))

(defhtml panel
  [header & content]
    (panelx header ["panel-body"] content))

(defhtml languages-pane
  []
  [:table.table
  (for [lang languages]
    [:tr [:td
    [:a {:href (format "/%s/top-projects" lang)} lang]]])])

(def propert-ids 
  { "hackersome.com" "UA-57973731-1"
    "sweet.io" "UA-33058338-1"
    "pythonhackers.com" "UA-42128994-1"
    "clojurehackers.com" "UA-57973731-3"
    "dev.hackersome.com" "UA-57973731-1" })

(defhtml render-user
  [x]
  [:a {:href (format "/user2/%s" (:login x)) :title (:name x)} 
    [:img.img-rounded {:src (:image x) :style "width:36px;height:36px;"}]
    [:span.name (:login x)]]
    [:span.followers.pull-right (:followers x)])

(defhtml layout
  [website & content]
  (let [property-id (get propert-ids website)]
    (html5
      {:lang "en-US"}
      [:head
        [:meta {:charset "UTF-8"}]
        [:meta {:content "IE=edge,chrome=1"
                :http-equiv "X-UA-Compatible"}]
        [:title "Hackersome"]
        (include-css "//maxcdn.bootstrapcdn.com/bootswatch/3.3.1/lumen/bootstrap.min.css")
        (include-css "//maxcdn.bootstrapcdn.com/font-awesome/4.2.0/css/font-awesome.min.css")  
        (include-css "/css/style.css")]
       [:body
         [:div.nav.navbar-default
          [:div.container
             [:div.navbar-header
                [:a.navbar-brand {:href (format "http://%s" website)} "Hackersome"]]
                [:div.navbar-collapse.collapse
                 [:ul.nav.navbar-nav
                  [:li [:a {:href "/users"} "Users"]]
                  [:li [:a {:href "/collections"} "Collections"]]
                   [:li.dropdown
                     [:a.dropdown-toggle {:data-toggle "dropdown" :href "#"} "Projects" [:span.caret]]
                     [:ul.dropdown-menu
                       (for [lang languages]
                         [:li [:a {:href (format "/%s/index" lang) } lang]])]]]
                  [:ul.nav.navbar-nav.navbar-right [:li [:a "Hello"] ]]]]]
        [:div.container-fluid
          [:div.col-lg-1.left-panel ""]
          [:div.col-lg-9.col-lg-offset-1
            [:div.row {:style "padding-top:20px;"}
              content]]
          [:div.col-lg-1]
         ]
        (include-js "//ajax.googleapis.com/ajax/libs/jquery/2.1.3/jquery.min.js")
        (include-js "//maxcdn.bootstrapcdn.com/bootstrap/3.3.1/js/bootstrap.min.js")
        (include-js "//cdnjs.cloudflare.com/ajax/libs/typeahead.js/0.10.4/typeahead.bundle.min.js")
        (include-js "//cdnjs.cloudflare.com/ajax/libs/handlebars.js/2.0.0/handlebars.min.js")
        (when (if (nil? is-dev?) true is-dev?)
          [:script {:type "text/javascript"}
          (str 
          (format "(function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
      (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
      m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
      })(window,document,'script','//www.google-analytics.com/analytics.js','ga');
      ga('create', '%s', 'auto');
      ga('send', 'pageview');" property-id)
          
          "psearch = new Bloodhound({
            queryTokenizer: Bloodhound.tokenizers.whitespace,
            datumTokenizer: Bloodhound.tokenizers.obj.whitespace('value'),
            limit: 10,
            remote: '/search?json=1&q=%QUERY'
            });
          psearch.initialize();
          $('.typeahead').typeahead(null,{
            name: 'project-search',
            hint: true,
            highlight: true,
            minLength: 2,
            displayKey: 'full_name',
            source: psearch.ttAdapter(),
            templates: {
              empty: '<div class=\"empty-message alert alert-danger\"><p>No results!</p></div>',
              suggestion: Handlebars.compile('<p><strong>{{watchers}}</strong>- {{full_name}}</p>')}
            });
          "
          )
        ])])))