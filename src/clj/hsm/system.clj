(ns hsm.system
     (:require
        [clojure.java.io              :as io]
        [cheshire.core                :refer :all]
        [taoensso.timbre              :as log]
        [hsm.dev                      :refer :all]
        [hsm.controllers.user         :as c.u]
        [hsm.controllers.project      :as c.pr]
        [hsm.controllers.main         :as c.m]
        [hsm.controllers.task         :as c.t]
        [hsm.controllers.account         :as account]
        [hsm.integration.ghub         :as ghub]
        [hsm.ring :as ringing         :refer [json-resp wrap-exception-handler
                                              wrap-nocache wrap-log redirect]]
        [raven-clj.ring               :refer [capture-error wrap-sentry]]
        [hsm.system.redis             :as sys.redis]
        [hsm.system.pg              :as sys.pg]
        [compojure.handler            :as handler :refer [api]]
        [compojure.route              :as route :refer [resources]]
        [ring.middleware.reload       :as reload]
        [ring.middleware.defaults     :refer :all]
        [ring.middleware.keyword-params :refer :all]
        [ring.util.response           :as resp]
        [net.cgrand.enlive-html       :refer [deftemplate]]
        [compojure.core               :refer [GET POST PUT defroutes]]
        [taoensso.carmine.ring        :refer [carmine-store]]
        [com.stuartsierra.component   :as component])
    (:use org.httpkit.server))



(defrecord HTTP [port db kafka-producer redis else conf server]
  component/Lifecycle

  (start [this]
    "In the near future find a nice macro/solution
    to refactor route definitions"

    (log/info "Starting HTTP Server on " port)
    (let [event-chan (:channel kafka-producer)
          specs {:db db
                 :event-chan event-chan
                 :redis redis
                 :conf conf
                ;  :else else
                 }]
      (defroutes routes
        (resources "/")

        (GET  "/"                                 request (c.m/homepage specs request))
        (GET  "/about"                            request (c.m/about specs request))


        (GET  "/users"                            request (c.u/some-user specs request))
        (GET  "/user2/:id"                        request (c.u/get-user2 specs request))
        (GET  "/user2/:id/sync"                   request (c.u/sync-user2 [db event-chan] request))
        (GET  "/user2/:id/followers"              request (c.u/user2-follower specs request))
        (GET  "/user2/:id/following"              request (c.u/user2-following specs request))
        (GET  "/user2/:id/starred"                request (c.u/user2-starred specs request))
        (GET  "/user2/:id/contrib"                request (c.u/user2-contrib specs request))
        (GET  "/user2/:id/activity"               request (c.u/user2-activity specs request))
        (GET  "/user2/:id/events"                 request (c.u/organization-events specs request))

        (GET  "/os/:user/:project"                request (c.pr/get-proj specs request))
        (GET  "/open-source/:user/:project"       request (c.pr/get-proj specs request))
        (GET  "/open-source"                      request (redirect "/open-source/"))
        (GET  "/open-source/"                     request (c.pr/list-top-proj specs request))

        (GET  "/p/:user/:project"                 request (c.pr/get-proj specs request))
        (GET  "/p/:user/:project/:mod"            request (c.pr/get-proj-module specs request))

        (GET  "/python-packages/"                 request (c.pr/get-py-proj specs request))
        (GET  "/python-packages/:project"         request (c.pr/get-py-proj specs request))
        (GET  "/python-packages/:project/"        request (c.pr/get-py-proj specs request))
        (GET  "/top-python-contributors-developers"
                                                  request (c.pr/get-py-contribs specs request))

        (GET  "/top-projects/"                    request (c.pr/list-top-proj specs request))
        (GET  "/top-projects"                     request (c.pr/list-top-proj specs request))
        (GET  "/top-:platform-projects/"          request (c.pr/list-top-proj specs request))

        (GET  "/os/"                              request (c.pr/list-top-proj specs request))
        (GET  "/os"                               request (c.pr/list-top-proj specs request))

        (GET  "/:platform/index"                  request (c.m/platform specs request))
        (GET  "/:platform/top-projects"           request (c.pr/list-top-proj specs request))

        ; (GET  "/tutorial/:user/:slug"             request (c.m/tutorial specs request))
        ; (GET  "/tutorial/"                        request (c.m/all-tutorial specs request))
        ; (GET  "/tutorial/:user/"                  request (c.m/all-tutorial specs request))

        (GET  "/import/:language"                 [language] (json-resp (ghub/import-repos [db event-chan] language)))

        (GET "/update-project/:user/:project"     [user project] (json-resp (ghub/update-project-info (format "%s/%s" user project))))
        (GET "/get-url/:url"                      request (json-resp (c.t/get-url request)))
        (POST "/auth"                             request (account/authorize specs request))
        (GET "/register"                          request (account/register specs request))

        (route/not-found "Page not found")
        ))

    (def http-handler
      (if is-dev?
        (reload/wrap-reload (api #'routes))
        (api routes)))

    (def dsn (:sentry-dsn (:conf conf)))

    (def app
      (-> http-handler
          (wrap-defaults api-defaults)
          (wrap-defaults (-> site-defaults
                          (assoc-in [:security :anti-forgery] false)
                          (assoc-in [:session :store] (carmine-store (:conn redis)))
                          ))
          (wrap-sentry dsn {:namespaces ["hsm"]})
          ; (wrap-exception-handler dsn)
          (wrap-nocache)
          (wrap-log)
          ))

    (let [server (run-server app {:port (Integer. port)
                            :join? (not is-dev?)})]

      (assoc this :server server)))

  (stop [this]
    (log/warn "Stopping HTTP Server")
    (server)))

(defn http-server
  [port]
  (map->HTTP {:port port}))

(defn front-end-system
  [config-options]
  (let [{:keys [host port keyspace server-port zookeeper redis-host
                redis-port else-host else-port else-index]} config-options]
    (-> (component/system-map
          :db 1 ;(sys.cassandra/cassandra-db host port keyspace)
          :redis (sys.redis/redis-db redis-host redis-port)
          :kafka-producer "a" ;(sys.kafka/kafka-producer zookeeper)
           :else 1 ;(sys.else/elastisch else-host else-port else-index)
          ; :pg-db (sys.pg/new-database {})
          :conf config-options
          :app (component/using
            (http-server server-port)
            [:db :kafka-producer :redis :conf :else] ;
            )))))

(defrecord Worker [kafka-producer]
   component/Lifecycle

  (start [this]
    (assoc this :kafka-producer kafka-producer))
  (stop [this]))

(defn worker
  []
  (map->Worker {}))

(defn worker-system [config-options]
  (let [{:keys [zookeeper]} config-options]
    (-> (component/system-map
      :kafka-producer {}
      :app (component/using
        (worker)
        [:kafka-producer])))))

(defrecord Integration [db]
  component/Lifecycle

  (start [this]
    (assoc this :db db))
  (stop [this]
    (dissoc this :db)))

(defn db-system
  [config-options]
  (let [{:keys [host port keyspace]} config-options]
    (-> (component/system-map
          :db 1 ;(sys.cassandra/cassandra-db host port keyspace)
          :app (component/using
            (map->Integration {})
            [:db]
            )))))
