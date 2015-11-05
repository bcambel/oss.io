(ns hsm.system
     (:require
        [clojure.java.io              :as io]
        [cheshire.core                :refer :all]
        [clojurewerkz.cassaforte.cql  :as cql]
        [clojure.tools.logging        :as log]
        [hsm.dev                      :refer :all]
        [hsm.controllers.user         :as c.u]       
        [hsm.controllers.project      :as c.pr]
        [hsm.controllers.main         :as c.m]
        [hsm.integration.ghub         :as ghub]
        [hsm.ring :as ringing         :refer [json-resp wrap-exception-handler wrap-nocache wrap-log redirect]]
        [hsm.system.kafka             :as sys.kafka]
        [hsm.system.cassandra         :as sys.cassandra]
        [hsm.system.redis             :as sys.redis]
        [hsm.system.else              :as sys.else]
        [compojure.handler            :as handler :refer [api]]
        [compojure.route              :as route :refer [resources]]
        [ring.middleware.reload       :as reload]
        [ring.util.response           :as resp]
        [ring.adapter.jetty           :refer [run-jetty]]
        [raven-clj.ring               :refer [wrap-sentry]]
        [raven-clj.core               :refer  [capture]]
        [net.cgrand.enlive-html       :refer [deftemplate]]
        [compojure.core               :refer [GET POST PUT defroutes]]
        [com.stuartsierra.component   :as component]))

(deftemplate defaultpage
  (io/resource "index.html") [] [:body] (if is-dev? inject-devmode-html identity))

(defn sample-conn
  [db request]
  (let [conn (:connection db)]
    (json-resp (cql/select conn :user))))


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
                 :else else}]
      (defroutes routes
        (resources "/")
        (resources "/react" {:root "react"})
        ; (GET  "/" req (defaultpage))
        (GET  "/"                                 request (c.m/homepage specs request))
        (GET  "/about"                            request (c.m/about specs request))
        (GET  "/test"                             request (sample-conn db request))

        (GET  "/users"                            request (c.u/some-user specs request))
        (GET  "/user2/:id"                        request (c.u/get-user2 specs request))
        (GET  "/user2/:id/sync"                   request (c.u/sync-user2 [db event-chan] request))
        (GET  "/user2/:id/followers"              request (c.u/user2-follower specs request))
        (GET  "/user2/:id/following"              request (c.u/user2-following specs request))
        (GET  "/user2/:id/starred"                request (c.u/user2-starred specs request))
        (GET  "/user2/:id/contrib"                request (c.u/user2-contrib specs request))
        (GET  "/user2/:id/activity"               request (c.u/user2-activity specs request))
       
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
        
        (GET  "/tutorial/:user/:slug"             request (c.m/tutorial specs request))
        (GET  "/tutorial/"                        request (c.m/all-tutorial specs request))
        (GET  "/tutorial/:user/"                  request (c.m/all-tutorial specs request))

        (GET  "/import/:language"                 [language] (json-resp (ghub/import-repos [db event-chan] language)))
        (GET  "/search"                           request (c.pr/search specs request))
        ; (GET  "/search/update"                    request (c.pr/update-search specs request))
        ; (GET  "/search/update-user"               request (c.pr/update-user-search-index specs request))

        (route/not-found "Page not found")
        ))

    (def http-handler
      (if is-dev?
        (reload/wrap-reload (api #'routes))
        (api routes)))

    (def dsn (:sentry-dsn (:conf conf)))
    
    (def app
      (-> http-handler
          (wrap-exception-handler dsn)
          (wrap-sentry dsn)
          (wrap-nocache)
          (wrap-log)
          ; (if is-dev? wrap-nocache identity )
          ))

    ; (if is-dev? (start-figwheel))
    (let [server (run-jetty app {:port (Integer. port)
                            :join? (not is-dev?)})]
      
      ; (capture (:sentry-dsn conf) "Starting up..")

      (assoc this :server server)))

  (stop [this]
    (log/warn "Stopping HTTP Server")
    (.stop server)))

(defn http-server
  [port]
  (map->HTTP {:port port}))

(defn front-end-system
  [config-options]
  (let [{:keys [host port keyspace server-port zookeeper redis-host
                redis-port else-host else-port else-index]} config-options]
    (-> (component/system-map
          :db (sys.cassandra/cassandra-db host port keyspace)
          :redis (sys.redis/redis-db redis-host redis-port)
          :kafka-producer "a" ;(sys.kafka/kafka-producer zookeeper)
          :else (sys.else/elastisch else-host else-port else-index)
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
      :kafka-producer (sys.kafka/kafka-producer zookeeper)
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
          :db (sys.cassandra/cassandra-db host port keyspace)
          :app (component/using
            (map->Integration {})
            [:db]
            )))))