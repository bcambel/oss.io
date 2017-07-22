(ns hsm.views
  (:require
    [clojure.string     :as s]
    [taoensso.timbre :as log]
    [hiccup.core        :refer [html ]]
    [hiccup.page        :refer [doctype include-css include-js html5]]
    [hiccup.def         :refer [defhtml ]]
    [hsm.dev            :refer [is-dev?]]
    [hsm.conf           :refer [languages]]))

(def VERSION (try (slurp "VERSION") (catch Throwable t)))

(def SHORTVERSION (try (subs VERSION 0 8) (catch Throwable t VERSION) ))

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

(defn get-placement
  [host]
  (let [placement (s/replace host #"\." "")]
    (if (= placement "pythonhackerscom")
      placement
      "hackersomecom")))

(defhtml left-menu
  [host platform page]
  [:div.bs-callout.bs-callout-success
    ; [:a.btn.btn-success {:href "#mc_embed_signup" :data-toggle :modal} "Subscribe Free"]
    [:p {:style "margin-top:10px"} "Join " [:b 10350] "+ others. No spamming." [:br][:b "I promise!"]]
    [:p
      [:a {:href "https://github.com/bcambel/hackersome?ref=left_menu_link"} "Follow us at github."]]
      [:iframe {:src "http://ghbtns.com/github-btn.html?user=bcambel&repo=oss.io&type=watch&count=true&size=normal"
                :allowtransparency true :frameborder 0 :scroling 0 :width "260px" :height "30px"}]
    [:hr]
    [:a.twitter-follow-button {:href "https://twitter.com/pythonhackers" :data-show-count true :data-size :small }]

    [:hr]
    [:script#_carbonads_js {:type "text/javascript" :src (format "//cdn.carbonads.com/carbon.js?zoneid=1673&serve=C6AILKT&placement=ossio") }]
    ])

(defhtml languages-pane
  []
  [:table.table
  (for [lang languages]
    [:tr [:td
    [:a {:href (format "/%s/top-projects" lang)} lang]]])])

(def property-ids
  { "hackersome.com" "UA-57973731-1"
    "pythonhackers.com" "UA-57973731-4"
    "clojurehackers.com" "UA-57973731-3"
    "dev.hackersome.com" "UA-57973731-1"
    "oss.io" "UA-57973731-5"
    "www.oss.io" "UA-57973731-10"
    "java-hackers.com" "UA-57973731-6"
     })

(def disqus-ids
{
  "pythonhackers.com" "pythonhackers"
  "clojurehackers.com" "pythonhackers"
  "hackersome.com" "pythonhackers"
  "dev.hackersome.com" "hackersome"
  "sweet.io" "sweetio"
  "oss.io" "ossio"
  "answer.io" "pythonhackers"
  })

(defhtml render-user
  [x & {:keys [show-followers] :or {show-followers false}}]
  [:div.user-card
    [:a {:href (format "/user2/%s" (:login x)) :title (:name x)}
      ; [:img.img-rounded {:src (:image x) :style "width:36px;height:36px;"}]
      [:span.name (:login x)]]
    (when show-followers
      [:span.followers.pull-right (:followers x)])
    ])

(defhtml embedded
  [& content]
  (html5
    {:lang "en-US"}
    [:head
      [:meta {:charset "UTF-8"}]
      (include-css "//maxcdn.bootstrapcdn.com/bootswatch/3.3.7/solar/bootstrap.min.css")
      (include-css "//maxcdn.bootstrapcdn.com/font-awesome/4.2.0/css/font-awesome.min.css")]
    [:body
      content
      [:img {:src "http://strck.hackersome.com/pixel.gif?embedded=1" :alt ""}]]))


(defhtml layout
  [{:keys [website platform title description keywords]} & content]
  (let [property-id (get property-ids website)]
    (html5
      {:lang "en-US"}
      [:head
        [:meta {:charset "UTF-8"}]
        ; [:meta {:name "robots" :content "noindex"}]
        [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0"}]
        [:meta {:content "IE=edge,chrome=1"
                :http-equiv "X-UA-Compatible"}]
        [:title (or title (format "Top %s Projects - Open Source Software" platform))]
        [:meta {:name "description" :content description}]
        [:meta {:name "keywords" :content (or keywords description)}]
        (include-css "//maxcdn.bootstrapcdn.com/bootswatch/3.3.7/paper/bootstrap.min.css")
        (include-css "//maxcdn.bootstrapcdn.com/font-awesome/4.2.0/css/font-awesome.min.css")
        (include-css "/css/style.css")
        ]
       [:body
         [:div.nav.navbar-default
          [:div.container
             [:div.navbar-header
                [:span.rotated.orange (format "%s" (str "(" platform ")"))]
                [:a.navbar-brand {:href (format "http://%s" website)}  "OpenSourceSofware" ]]
                [:div.navbar-collapse.collapse
                 [:ul.nav.navbar-nav
                  [:li [:a {:href "/users?ref=top_menu_link"} "Users"]]
                  [:li [:a {:href "/open-source/?ref=top_menu_link"} "Top Projects"]]
                   [:li.dropdown
                     [:a.dropdown-toggle {:data-toggle "dropdown" :href "#"} "Languages" [:span.caret]]
                     [:ul.dropdown-menu
                       (for [lang languages]
                         [:li [:a {:href (format "/%s/top-projects" lang) } lang]])]]
                  [:li [:a {:href "/about"} "About"]]
                  [:li [:a {:href "/register"} "Join"]]
                  ]

                  [:ul.nav.navbar-nav.navbar-right [:li
                  ; [:div#oa_social_login_container ]
                  ]]]]]
        [:div.container-fluid
          ; [:div.col-lg-1.left-panel ""]
          [:div.col-lg-11
            [:div.row {:style "padding-top:20px;"}
              content

              [:div#disqus_thread.col-lg-10.col-lg-offset-2]]]
          [:div.col-lg-1]
         ]
         [:footer.container-fluid.footer
          [:div.col-lg-10.col-lg-offset-1
            [:p "Designed, built and made in Amsterdam with all the love by " [:a {:href "http://twitter.com/bahadircambel"} "@bcambel"]]
            [:p
              website " running version  " [:a {:href (str "https://github.com/bcambel/oss.io/commit/" VERSION)} (str "@" SHORTVERSION)]]
            [:p "Code licensed under " [:a {:href "https://github.com/bcambel/oss.io/blob/development/LICENSE"} :MIT]]
            [:hr]
            (when-not is-dev?
              [:div
                [:p
                  [:a.twitter-share-button {:href "https://twitter.com/share"
                    :data-text "Top Projects on"
                    :data-via "pythonhackers" :data-size :normal
                    } "Tell your friends"]
                  [:a.twitter-follow-button {:href "https://twitter.com/pythonhackers" :data-show-count true :data-size :normal }]]
                [:hr]
                [:iframe {:src "http://ghbtns.com/github-btn.html?user=bcambel&repo=pythonhackers&type=watch&count=true&size=normal"
                          :allowtransparency true :frameborder 0 :scroling 0 :width "120px" :height "30px"}]
                [:iframe {:src "http://ghbtns.com/github-btn.html?user=bcambel&repo=oss.io&type=watch&count=true&size=normal"
                          :allowtransparency true :frameborder 0 :scroling 0 :width "260px" :height "30px"}]
            ])]
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

        (when (if (nil? is-dev?) true is-dev?)
          [:script {:type "text/javascript"}
          (str
          (format "(function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
      (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
      m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
      })(window,document,'script','//www.google-analytics.com/analytics.js','ga');
      ga('create', '%s', 'auto');
      ga('send', 'pageview');" property-id)
      (format
      "var oneall_subdomain = 'ossio';var oa = document.createElement('script');oa.type = 'text/javascript'; oa.async = true;oa.src = '//' + oneall_subdomain + '.api.oneall.com/socialize/library.js';
      var s = document.getElementsByTagName('script')[0];s.parentNode.insertBefore(oa, s);
      var _oneall = _oneall || [];
      _oneall.push(['social_login', 'set_providers', ['github','google','twitter']]);
      _oneall.push(['social_login', 'set_callback_uri', 'http://%s/auth']);
      _oneall.push(['social_login', 'do_render_ui', 'oa_social_login_container']);" website)
          "!function(d,s,id){var js,fjs=d.getElementsByTagName(s)[0],p=/^http:/.test(d.location)?'http':'https';if(!d.getElementById(id)){js=d.createElement(s);js.id=id;js.src=p+'://platform.twitter.com/widgets.js';fjs.parentNode.insertBefore(js,fjs);}}(document, 'script', 'twitter-wjs');"

          (when-not is-dev?
          (format
          "(function() {
            var dsq = document.createElement('script'); dsq.type = 'text/javascript'; dsq.async = true;
            dsq.src = '//%s.disqus.com/embed.js';
            (document.getElementsByTagName('head')[0] || document.getElementsByTagName('body')[0]).appendChild(dsq);
        })();" (or (get disqus-ids website) "hackersome")))
(when-not is-dev?
"!function(){var analytics=window.analytics=window.analytics||[];if(!analytics.initialize)if(analytics.invoked)window.console&&console.error&&console.error('Segment snippet included twice.');else{analytics.invoked=!0;analytics.methods=['trackSubmit','trackClick','trackLink','trackForm','pageview','identify','group','track','ready','alias','page','once','off','on'];analytics.factory=function(t){return function(){var e=Array.prototype.slice.call(arguments);e.unshift(t);analytics.push(e);return analytics}};for(var t=0;t<analytics.methods.length;t++){var e=analytics.methods[t];analytics[e]=analytics.factory(e)}analytics.load=function(t){var e=document.createElement('script');e.type='text/javascript';e.async=!0;e.src=('https:'===document.location.protocol?'https://':'http://')+'cdn.segment.com/analytics.js/v1/'+t+'/analytics.min.js';var n=document.getElementsByTagName('script')[0];n.parentNode.insertBefore(e,n)};analytics.SNIPPET_VERSION='3.0.1';
  analytics.load('eqMNHeqB0Ukx8AWah4nKiwFwaxbeJlGg');
  analytics.page();}}();
")
(format
"!function(){window.jstag=function(e){var t=!1,n=window,r=document,i='/js/io',s=Array.prototype.slice,o=e.url||'';return n.jstag||{load:function(){var e,s=r.getElementsByTagName('script')[0];return t=!0,'JSON'in n&&Array.prototype.forEach||(i+='w'),r.getElementById(i)?this:(e=r.createElement('script'),e.id=i,e.src=o+i+'.min.js',s.parentNode.insertBefore(e,s),this)},_q:[],_c:e,bind:function(e){this._q.push([e,s.call(arguments,1)])},ready:function(){this._q.push(['ready',s.call(arguments)])},send:function(){return t||this.load(),this._q.push(['ready','send',s.call(arguments)]),this},ts:(new Date).getTime()}
}({cid:'report',url:'//%s/',path:'',idpath:''});jstag.send({data:1});}();" (if is-dev? "dev.strck.hackersome.com" "strck.hackersome.com"))
          )
        ])
    [:img {:src "http://strck.hackersome.com/pixel.gif" :alt ""}]])))
