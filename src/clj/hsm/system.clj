(ns hsm.system
     (:require
        [clojure.java.io :as io]
        [cheshire.core :refer :all]
        [clojurewerkz.cassaforte.cql    :as cql   ]
        [clojure.tools.logging :as log]
        [hsm.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel]]
        [hsm.controllers.user :as cont-user]
        [hsm.controllers.post :as cont-post]
        [hsm.controllers.project :as cont-project]
        [hsm.controllers.discussion :as cont-disc]
        [hsm.controllers.main :as c.main]
        [hsm.integration.ghub :as ghub]
        [hsm.ring :as ringing :refer [json-resp wrap-exception-handler]]
        [hsm.system.kafka :as sys.kafka]
        [hsm.system.cassandra :as sys.cassandra]
        [compojure.handler :as handler :refer [api]]
        [compojure.route :as route :refer [resources]]
        [ring.middleware.reload :as reload]
        [ring.util.response :as resp]
        [ring.adapter.jetty :refer [run-jetty]]
        [net.cgrand.enlive-html :refer [deftemplate]]
        [compojure.core :refer [GET POST PUT defroutes]]
        [com.stuartsierra.component :as component]))

(deftemplate defaultpage
  (io/resource "index.html") [] [:body] (if is-dev? inject-devmode-html identity))

(defn sample-conn
  [db request]
  (let [conn (:connection db)]
    (json-resp (cql/select conn :user))))

(defrecord HTTP [port db kafka-producer server]
  component/Lifecycle

  (start [this]
    "In the near future find a nice macro/solution 
    to refactor route definitions"

    (log/info "Starting HTTP Server on " port)
    (let [event-chan (:channel kafka-producer)]
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
        (GET  "/user/:id"                 [id request] (cont-user/get-user [db event-chan] id request))
        (GET  "/user/:id/activity"        [id request] (cont-user/get-user-activity [db event-chan] id request))
        (POST "/user/:id/follow"          request (cont-user/follow-user [db event-chan] request))
        (POST "/user/:id/unfollow"        request (cont-user/unfollow-user [db event-chan] request))
        (GET  "/user/:id/followers"       request (cont-user/get-user-followers [db event-chan] request))
        (GET  "/user/:id/following"       request (cont-user/get-user-following [db event-chan] request))
        (POST "/link/create"              request (cont-post/create-link [db event-chan] request))
        (POST "/link/:id/upvote"          request (cont-post/upvote-link [db event-chan] request))
        (GET  "/link/:id"                 request (cont-post/show-link [db event-chan] request))
        (GET  "/links/:date"              request (cont-post/list-links [db event-chan] request))
        ; (GET "/project/:id"               request (cont-project/get-proj [db event-chan] request))
        (GET "/p/:user/:project"    request (cont-project/get-proj [db event-chan] request))
        (GET "/top-projects"              request (cont-project/list-top-proj [db event-chan] request))
        (GET "/:platform/index"                 request (c.main/platform [db event-chan] request))
        (GET "/:platform/top-projects"    request (cont-project/list-top-proj [db event-chan] request))
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

(defn front-end-system [config-options]
  (let [{:keys [host port keyspace server-port zookeeper]} config-options]
    (-> (component/system-map
          :db (sys.cassandra/cassandra-db host port keyspace)
          :kafka-producer (sys.kafka/kafka-producer zookeeper)
          :app (component/using
            (http-server server-port)
            [:db :kafka-producer]
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