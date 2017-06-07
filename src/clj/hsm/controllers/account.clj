(ns hsm.controllers.account
  (:require
  [cheshire.core                :refer :all]
  [taoensso.timbre              :as log]
  [clj-http.client                :as client]
  [clojure.java.jdbc              :as jdbc]
  [honeysql.core                  :as sql]
  [honeysql.helpers               :as sqlh]
  [hsm.conf                       :as conf]
  [hsm.system.pg                  :refer [pg-db]]
  [hsm.utils                      :refer [pg-json common-of !nil?]]
  [hsm.views :as views :refer [layout panel panelx render-user left-menu]]
  [hsm.ring :refer [json-resp html-resp redirect]]
  ))

(defn get-config
  []
  (:data @conf/app-conf))

(defn get-token-data
  [token]
  (let [response (client/get (format "https://ossio.api.oneall.com/connections/%s.json" token)
                    {:basic-auth [ (:oneall-public (get-config))
                                   (:oneall-secret (get-config))
                                  ]})]
      (-> response
          :body
          (parse-string true)
          )))

(defn find-user-id
  [user-token]
  (log/infof "Check user %s" user-token)
  (get (first
    (try (jdbc/query pg-db
      (-> (sqlh/select :user_id)
          (sqlh/from :user_token_link)
          (sqlh/where [:= :user_token user-token])
          (sqlh/limit 1)
          (sql/build)
          (sql/format :quoting :ansi)))
      (catch Exception ex
        (do
          (log/error (.getNextException ex))
          [])))) :user_id ))

(defn find-user-account
  [email]
  (log/infof "Check user %s" email)
  (first
    (try (jdbc/query pg-db
      (-> (sqlh/select :*)
          (sqlh/from :oss_user)
          (sqlh/where [:= :email email])
          (sqlh/limit 1)
          (sql/build)
          (sql/format :quoting :ansi)))
      (catch Exception ex
        (do
          (log/error (.getNextException ex))
          []
          )))))

(defn create-link
  [user-id user-token]
  (jdbc/insert! pg-db :user_token_link {:user_id user-id :user_token user-token}
  ))

(defn create-account
  [user-data user-token]
  (log/infof "Creating account for %s" user-token)
  (let [email (-> user-data :emails first (get :value))
        existing-account (find-user-account email)
        account-info {:name (get-in user-data [:name :formatted])
                      :email email
                      :img (-> user-data :thumbnailUrl)}]
    (jdbc/insert! pg-db :user_identity
      {:id user-token
        :identity (-> user-data
                      generate-string
                      pg-json )})
    (if (!nil? existing-account)
      (do
        (create-link (:id existing-account) user-token)
        existing-account)
      (when-let [oss-user (first (mapv identity (jdbc/insert! pg-db :oss_user account-info )))]
        (log/infof "New user info %s" oss-user)
        (create-link (:id oss-user) user-token)
        (:id oss-user)
        ))))


(defn authorize
  [specs request]
  (log/info request)
  (let [session-data (:session request)]
    (log/warn session-data))

  (let [{:keys [oa_action oa_social_login_token connection_token]} (:params request) ]
    (log/infof "Using %s token" connection_token)
    (when-let [data (get-token-data connection_token)]
      (let [data (get-in data [:response :result :data])]
        (log/infof "Found Data %s" data)

        (log/infof "Is a success? %s" (get-in data [:plugin :data :status]))
        (if (= "success" (get-in data [:plugin :data :status]))
          (let [user-token (get-in data [:user :user_token])
                identity (get-in data [:user :identity])
                identity-token (:identity_token identity)
                provider-id (:provider_identity_uid identity)
                user-id (find-user-id user-token)
                _ (log/infof "Existing id %s" user-id)
                user {:new? (nil? user-id)
                      :id (or user-id -1) }]
              (let [oss-user-id (if (:new? user)
                                  (create-account identity user-token)
                                  user-id)]
                (-> (redirect "/" )
                  (assoc :session (assoc (:session request) :user oss-user-id))))))))))


(defn register
  [specs request]
  (let [{:keys [host id body json? user platform
                req-id limit-by url hosted-pl]} (common-of request)]
    (html-resp
      (views/layout {:website host :platform platform
                     :title "Join Open Source Software community now"
                     :description "Be part of the Open Source Software community."
                     ; replace this with a multiplication or combination
                      :keywords "open source, software community, python community, clojure community"
                   }

        [:div.row
          [:div.col-lg-10.col-lg-offset-2.jumbotron
          [:h3 "Join the Community for free now!"]
          [:hr ]
          [:p "Pick one below"]
          [:div#oa_social_login_container]]
        ]))
  ))
