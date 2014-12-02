(ns hsm.github
    (:require 
      [hsm.users :as users :refer (users)]
      [clojure.tools.logging :as log]
      [cheshire.core :as json]
      [hiccup.page :as h]
      [hiccup.element :as e]
      [ring.util.response :as resp]
      [clj-http.client :as client]
      [compojure.handler :as handler :refer [api]]
      [cemerick.friend :as friend]
      (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])
      [friend-oauth2.workflow :as oauth2]
      [friend-oauth2.util :as oauth2-util :refer [format-config-uri get-access-token-from-params]]
      (cemerick.friend [workflows :as workflows]
                           [credentials :as creds])
    ))

(declare render-repos-page)
(declare get-github-repos)

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


(def page (handler/site
           (friend/authenticate
            nil
            {:allow-anon? true
             :default-landing-uri "/"
             :login-uri "/login"
             :unauthorized-handler #(-> 
                                     (h/html5 [:h2 "You do not have sufficient privileges to access " (:uri %)])
                                        resp/response
                                        (resp/status 401))
             :auth-error-fn #(log/warn "[AUTH] Failed" %)
             :workflows [(oauth2/workflow
                          {:client-config client-config
                           :uri-config uri-config
                           :config-auth {:roles #{::user}}
                           :access-token-parsefn get-access-token-from-params})]})))

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
        repos (json/parse-string (:body response) true)]
    repos))