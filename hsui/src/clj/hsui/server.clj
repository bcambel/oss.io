(ns hsui.server
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [hsui.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel]]
            [liberator.core :refer [resource defresource]]
            [compojure.core :refer [GET defroutes]]
            [compojure.route :as route :refer [resources]]
            [compojure.handler :as handler :refer [api]]
            [ring.util.response :as resp]
            [ring.util.codec :as codec]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [ring.middleware.reload :as reload]
            [cognitect.transit :as t]
            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])
            [friend-oauth2.workflow :as oauth2]
            [friend-oauth2.util :as oauth2-util]
            [hsui.users :as users :refer (users)]
            [clj-http.client :as client]
            [hiccup.page :as h]
            [hiccup.element :as e]
            [cheshire.core :refer :all]
            [environ.core :refer [env]]
            [ring.adapter.jetty :refer [run-jetty]])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(declare render-repos-page)
(declare get-github-repos)

(defn body-as-string 
  [ctx]
  (if-let [body (get-in ctx [:request :body])]
    (condp instance? body
      java.lang.String body
      (slurp (io/reader body)))))

(defn generate-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defn respond [data & [status]]
  (let [out (ByteArrayOutputStream. 4096)
        writer (t/writer out :json)]
    (t/write writer data)
    { :status (or status 200)
     :headers { "Content-Type" "application/json" }
     :body
     (generate-string data)
     ;(.toString out)
     }
     ;(pr-str data)
))

(deftemplate defaultpage
  (io/resource "index.html") [] [:body] (if is-dev? inject-devmode-html identity))

(defn links
  []
  (respond [ {:url "http://google.com" :shares 213} 
                       {:url "http://github.com" :shares 2342} 
                       {:url "http://travelbird.nl" :shares 100 }]))


(defn extract 
  [data]
  (select-keys (:data data) [:url :score :title :domain :created_utc :author]))

(defn extract-links
  [data]
  (map extract data))

(defn get-reddit
  [url]
  (try 
    (-> url
      (client/get {:accept :json :as :json})
      (:body))
    (catch Throwable t 
      (do (log/error t) 
          []))))

(def ^:dynamic def-url "http://www.reddit.com/r/Python/top.json")

(defn alinks
  ([] 
     (links def-url))
  ([url]
     (let [jdata (get-reddit url)
        link-items (extract-links (get-in jdata [:data :children]))]
        (clojure.pprint/pprint link-items)
        (respond link-items))))

(defn- call-github
  [endpoint access-token]
  (log/warn "Calling GITHUB with" endpoint access-token)
  (-> (format "https://api.github.com%s%s&access_token=%s"
              endpoint
              (when-not (.contains endpoint "?") "?")
              access-token)
      client/get
      :body
      (json/parse-string (fn [^String s] (keyword (.replace s \_ \-))))))

(def client-config
  {:client-id ""
   :client-secret "" 
   ;; TODO get friend-oauth2 to support :context, :path-info
   :callback {:domain "http://dev.hackersome.com" :path "/oauth-github/github.callback"}})

(def uri-config
  {:authentication-uri {:url "https://github.com/login/oauth/authorize"
                        :query {:client_id (:client-id client-config)
                                :response_type "code"
                                :redirect_uri (oauth2-util/format-config-uri client-config)
                                :scope ""}}

   :access-token-uri {:url "https://github.com/login/oauth/access_token"
                      :query {:client_id (:client-id client-config)
                              :client_secret (:client-secret client-config)
                              :grant_type "authorization_code"
                              :redirect_uri (oauth2-util/format-config-uri client-config)
                              :code ""}}})



(defn render-something
  [request]

)

(defroutes routes
  (resources "/")
  (resources "/react" {:root "react"})
  (GET "/" req (defaultpage))
  ;(GET "/user/:id" req (user-details))
  (GET "/test" request (friend/authorize #{::users/user} (render-repos-page request))
  (GET "/links" req (links)))

(def page (handler/site
           (friend/authenticate
            routes
            {:allow-anon? true
             :default-landing-uri "/"
             ; :login-uri "/login"
             :unauthorized-handler #(-> 
                                     (h/html5 [:h2 "You do not have sufficient privileges to access " (:uri %)])
                                        resp/response
                                        (resp/status 401))
             :workflows [(oauth2/workflow
                          {:client-config client-config
                           :uri-config uri-config
                           :config-auth {:roles #{::users/user}}
                           :access-token-parsefn #(-> % 
                                                      :body codec/form-decode (get "access_token"))})]})))

(defn render-repos-page 
  "Shows a list of the current users github repositories by calling the github api
   with the OAuth2 access token that the friend authentication has retrieved."
  [request]
  (let [authentications (get-in request [:session :cemerick.friend/identity :authentications])
        access-token (:access_token (second (first authentications)))
        repos-response (get-github-repos access-token)]
    (str (vec (map :name repos-response)))))

(defn get-github-repos 
  "Github API call for the current authenticated users repository list."
  [access-token]
  (let [url (str "https://api.github.com/user/repos?access_token=" access-token)
        response (client/get url {:accept :json})
        repos (j/parse-string (:body response) true)]
    repos))

(defn wrap-exception-handler
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch IllegalArgumentException e
        (->
         (resp/response e)
         (resp/status 400)))
      (catch Exception e
        (do (log/error e)
        (->
         (resp/response e)
         (resp/status 500)))))))

(def http-handler
  (if is-dev?
    (reload/wrap-reload (api #'page))
    (api page)))

(def app
  (-> (api page)
      (wrap-exception-handler)))

(defn run [& [port]]
  (defonce ^:private server
    (do
      (if is-dev? (start-figwheel))
      (let [port (Integer. (or port (env :port) 10555))]
        (print "Starting web server on port" port ".\n")
        (run-jetty http-handler {:port port
                          :join? false}))))
  server)

(defn -main [& [port]]
  (run port))
