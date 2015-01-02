(ns hsm.system
     (:require
        [clojure.java.io              :as io]
        [cheshire.core                :refer :all]
        [clojurewerkz.cassaforte.cql  :as cql   ]
        [clojure.tools.logging        :as log]
        [hsm.dev                      :refer :all]
        [hsm.controllers.user         :as cont-user]
        [hsm.controllers.post         :as cont-post]
        [hsm.controllers.project      :as cont-project]
        [hsm.controllers.coll         :as c.coll]
        [hsm.controllers.discussion   :as cont-disc]
        [hsm.controllers.main         :as c.main]
        [hsm.integration.ghub         :as ghub]
        [hsm.ring :as ringing         :refer [json-resp wrap-exception-handler]]
        [hsm.system.kafka             :as sys.kafka]
        [hsm.system.cassandra         :as sys.cassandra]
        [hsm.system.redis             :as sys.redis]
        [compojure.handler            :as handler :refer [api]]
        [compojure.route              :as route :refer [resources]]
        [ring.middleware.reload       :as reload]
        [ring.util.response           :as resp]
        [ring.adapter.jetty           :refer [run-jetty]]
        [net.cgrand.enlive-html       :refer [deftemplate]]
        [compojure.core               :refer [GET POST PUT defroutes]]
        [com.stuartsierra.component   :as component]))

(deftemplate defaultpage
  (io/resource "index.html") [] [:body] (if is-dev? inject-devmode-html identity))

(defn sample-conn
  [db request]
  (let [conn (:connection db)]
    (json-resp (cql/select conn :user))))

(defrecord HTTP [port db kafka-producer redis server]
  component/Lifecycle

  (start [this]
    "In the near future find a nice macro/solution
    to refactor route definitions"

    (log/info "Starting HTTP Server on " port)
    (let [event-chan (:channel kafka-producer)
          specs {:db db :event-chan event-chan :redis redis}]
      (defroutes routes
        (resources "/")
        (resources "/react" {:root "react"})
        ; (GET  "/" req (defaultpage))
        (GET  "/"                         request (c.main/homepage [db event-chan] request))
        (GET  "/test"                     request (sample-conn db request))
        (POST "/user/create"              request (cont-user/create-user [db event-chan] request))
        (POST "/post/create"              request (cont-post/create-post [db event-chan] request))
        (POST "/discussion/create"        request (cont-disc/create-discussion [db event-chan] request))
        (GET  "/discussion/:id"           [id request] (cont-disc/get-discussion [db event-chan] id request))
        (GET  "/discussion/:id/posts"     [id request] (cont-disc/get-discussion-posts [db event-chan] id request))
        (POST "/discussion/:id/post/create" request (cont-disc/post-discussion [db event-chan] request))
        (POST "/discussion/:id/follow"    [id request] (cont-disc/follow-discussion [db event-chan] id request))
        (POST "/discussion/:id/unfollow"  [id request] (cont-disc/unfollow-discussion [db event-chan] id request))
        (GET  "/users"                    request (cont-user/some-user specs request))
        (GET  "/user2/:id"                request (cont-user/get-user2 [db event-chan] request))
        (GET  "/user2/:id/sync"           request (cont-user/sync-user2 [db event-chan] request))
        (GET  "/user/:id"                 request (cont-user/get-user [db event-chan] request))
        (GET  "/user/:id/activity"        request (cont-user/get-user-activity [db event-chan] request))
        (POST "/user/:id/follow"          request (cont-user/follow-user [db event-chan] request))
        (POST "/user/:id/unfollow"        request (cont-user/unfollow-user [db event-chan] request))
        (GET  "/user/:id/followers"       request (cont-user/get-user-followers [db event-chan] request))
        (GET  "/user/:id/following"       request (cont-user/get-user-following [db event-chan] request))
        (POST "/link/create"              request (cont-post/create-link [db event-chan] request))
        (POST "/link/:id/upvote"          request (cont-post/upvote-link [db event-chan] request))
        (GET  "/link/:id"                 request (cont-post/show-link [db event-chan] request))
        (GET  "/links/:date"              request (cont-post/list-links [db event-chan] request))
        (GET  "/p/:user/:project"         request (cont-project/get-proj [db event-chan] request))
        (GET  "/top-projects"             request (cont-project/list-top-proj [db event-chan] request))
        (GET  "/:platform/index"          request (c.main/platform [db event-chan] request))
        (GET  "/:platform/top-projects"   request (cont-project/list-top-proj [db event-chan] request))
        (GET  "/:platform/discussions"    request (cont-disc/discussions [db event-chan] request))
        (GET  "/collections"              request (c.coll/load-coll [db event-chan] request))
        (GET  "/import/:language"         [language] (json-resp (ghub/import-repos [db event-chan] language)))
        (route/not-found "Page not found")
        ))

    (def http-handler
      (if is-dev?
        (reload/wrap-reload (api #'routes))
        (api routes)))

    (def app
      (-> http-handler
          (wrap-exception-handler)))

    (if is-dev? (start-figwheel))
    (let [server (run-jetty app {:port (Integer. port)
                            :join? false})]
      (assoc this :server server)))

  (stop [this]
    (log/warn "Stopping HTTP Server")
    (.stop server)))

(defn http-server
  [port]
  (map->HTTP {:port port}))

(defn front-end-system
  [config-options]
  (let [{:keys [host port keyspace server-port zookeeper redis-host redis-port]} config-options]
    (-> (component/system-map
          :db (sys.cassandra/cassandra-db host port keyspace)
          :redis (sys.redis/redis-db redis-host redis-port)
          :kafka-producer (sys.kafka/kafka-producer zookeeper)
          :app (component/using
            (http-server server-port)
            [:db :kafka-producer :redis]
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