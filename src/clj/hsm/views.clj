(ns hsm.views
  (:require
    [clojure.string     :as s]
    [clojure.tools.logging :as log]
    [hiccup.core        :refer [html ]]
    [hiccup.page        :refer [doctype include-css include-js html5]]
    [hiccup.def         :refer [defhtml ]]
    [hsm.dev            :refer [is-dev?]]
    [hsm.conf           :refer [languages]]))

(def VERSION (try (slurp "VERSION") (catch Throwable t)))

(defhtml row-fluid
  [& content]
  [:div.row-fluid
   content])

(defhtml container-fluid
  [& content]
  [:div.container-fluid
    content])

(defhtml panelx
  ([header footer body-css & content ]
    (let [panel-body-css (s/join " " 
                            (conj (set body-css) "panel-body"))]
      [:div.panel.panel-default 
        [:div.panel-heading header]
        [:div {:class panel-body-css} 
          content]
        [:div.panel-footer footer]])))

(defhtml panel
  [header & content]
    (panelx header "" ["panel-body"] content))

(defhtml left-menu
  [host platform page]
  [:div.bs-callout.bs-callout-success ;{:style "background-color:#f4f4f4;" }
  ; (panelx "Social" "" ""
    [:a.btn.btn-success {:href "#mc_embed_signup" :data-toggle :modal} "Subscribe"]
    [:hr]
    [:p "Join " [:b 926] " others"]
    [:p "No spamming. I promise!"]
    [:hr]
    [:a.twitter-share-button {:href "https://twitter.com/share" 
      :data-text (format "Top %s Projects" platform)
      :data-via "pythonhackers" :data-url (format "%s/%s" host page) :data-size :large
      :data-hashtags "python,hackers,github"
      } "Tell your friends"]
    [:a.twitter-follow-button {:href "https://twitter.com/pythonhackers" :data-show-count true :data-size :small }]
    [:div.fb-like {:data-href (format "http://%s/top-%s-projects" host platform)}]
    [:hr]
    [:script#_carbonads_js {:type "text/javascript" :src "//cdn.carbonads.com/carbon.js?zoneid=1673&serve=C6AILKT&placement=pythonhackerscom" }]
    ])

(defhtml languages-pane
  []
  [:table.table
  (for [lang languages]
    [:tr [:td
    [:a {:href (format "/%s/top-projects" lang)} lang]]])])

(def propert-ids 
  { "hackersome.com" "UA-57973731-1"
    "sweet.io" "UA-33058338-1"
    "pythonhackers.com" "UA-57973731-4"
    "clojurehackers.com" "UA-57973731-3"
    "dev.hackersome.com" "UA-57973731-1" })

(defhtml render-user
  [x & {:keys [show-followers] :or {show-followers false}}]
  [:div.user-card
    [:a {:href (format "/user2/%s" (:login x)) :title (:name x)}
      [:img.img-rounded {:src (:image x) :style "width:36px;height:36px;"}]
      [:span.name (:login x)]]
    (when show-followers
      [:span.followers.pull-right (:followers x)])
    ])

(defhtml layout
  [website & content]
  (let [property-id (get propert-ids website)]
    (html5
      {:lang "en-US"}
      [:head
        [:meta {:charset "UTF-8"}]
        ; [:meta {:name "robots" :content "noindex"}]
        [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0"}]
        [:meta {:content "IE=edge,chrome=1"
                :http-equiv "X-UA-Compatible"}]
        [:title (str website  " Top Projects - Hackersome" )]
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
                  [:li [:a {:href "/open-source/"} "Top Projects"]]
                  ; [:li [:a {:href "/collections"} "Collections"]]
                  [:li [:a {:href "/discussions"} "Discussions"]]
                   [:li.dropdown
                     [:a.dropdown-toggle {:data-toggle "dropdown" :href "#"} "Platforms" [:span.caret]]
                     [:ul.dropdown-menu
                       (for [lang languages]
                         [:li [:a {:href (format "/%s/index" lang) } lang]])]]]
                  [:form.navbar-form.navbar-left {:method "GET" :action "/p/"}
                    [:div.form-group 
                      [:input.form-control.typeahead.input-xs {:type "text" :name "project"}]]
                      [:button.btn.btn-default.btn-xs {:type "Submit" :onclick "window.location='/p/'+ $(this).parents('form').find('input').val();return false;"} "Go"]]
                  [:ul.nav.navbar-nav.navbar-right [:li [:a "Hello"] ]]]]]
        [:div.container-fluid
          ; [:div.col-lg-1.left-panel ""]
          [:div.col-lg-10.col-lg-offset-1
            [:div.row {:style "padding-top:20px;"}
              content]]
          [:div.col-lg-1]
         ]
         [:footer.container-fluid.footer
          [:div.col-lg-10.col-lg-offset-1
          [:p "Designed, built and made in Amsterdam with all the love by" [:a {:href "http://twitter.com/bahadircambel"} "@bcambel"]]
          [:p
            "Running version  " [:a {:href (str "https://github.com/bcambel/hackersome/commit/" VERSION)} (str "@" VERSION)]]
          [:p
          [:a.twitter-follow-button {:href "https://twitter.com/pythonhackers" :data-show-count true :data-size :large }]]
          [:iframe {:src "http://ghbtns.com/github-btn.html?user=bcambel&repo=hackersome&type=watch&count=true&size=large" 
                    :allowtransparency true :frameborder 0 :scroling 0 :width "260px" :height "30px"}]
            ]
         ]
        [:div#mc_embed_signup.modal.fade
          [:div.modal-dialog
            [:div.modal-content
              [:form#mc-embedded-subscribe-form.form-inline {:novalidate "novalidate", :target "_blank", :name "mc-embedded-subscribe-form", :method "post", :action "http://pythonarticles.us7.list-manage.com/subscribe/post?u=ec40ca305ad5132552f8666a7&id=d588dd7362"}
              [:div.modal-header [:button.close {:aria-hidden "true", :data-dismiss "modal", :type "button"} "×"] [:h4.modal-title "Newsletter Subscription"]]
              [:div.modal-body [:p "Drop us your email and we will keep you updated.."]
                  [:input#mce-EMAIL.form-control {:style "height:45px;font-size:1.5em;", :required "required", :placeholder "email address", :name "EMAIL", :value "", :type "text"}]
                  [:br] [:br]
                  [:p [:a.twitter-follow-button.pull-right {:data-lang :en :data-show-count :true
                      :href "https://twitter.com/pythonhackers"} "Follow @pythonhackers"]]

                      ]
              [:div.modal-footer [:button.btn.btn-default.pull-left {:data-dismiss "modal"} "Close"]
          [:input#mc-embedded-subscribe.btn.btn-primary {:name "subscribe", :value "Subscribe", :type "submit"}]]]]]]

        [:script {:type "text/javascript"} "var session = {};"]
        (include-js "//cdnjs.cloudflare.com/ajax/libs/underscore.js/1.7.0/underscore-min.js"
                    "//ajax.googleapis.com/ajax/libs/jquery/2.1.3/jquery.min.js"
                    "//maxcdn.bootstrapcdn.com/bootstrap/3.3.1/js/bootstrap.min.js"
                    "//cdnjs.cloudflare.com/ajax/libs/typeahead.js/0.10.4/typeahead.bundle.min.js"
                    "//cdnjs.cloudflare.com/ajax/libs/handlebars.js/2.0.0/handlebars.min.js"
                    "//cdnjs.cloudflare.com/ajax/libs/bootstrap-markdown/2.8.0/js/bootstrap-markdown.js")
        (include-js "/js/app.js")

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
            limit: 20,
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
!function(d,s,id){var js,fjs=d.getElementsByTagName(s)[0],p=/^http:/.test(d.location)?'http':'https';if(!d.getElementById(id)){js=d.createElement(s);js.id=id;js.src=p+'://platform.twitter.com/widgets.js';fjs.parentNode.insertBefore(js,fjs);}}(document, 'script', 'twitter-wjs');
          "
          )
        ])[:img {:src "http://strck.hackersome.com/pixel.gif" :alt ""}]])))