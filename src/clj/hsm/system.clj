(ns hsm.system
     (:require 
        [clojure.java.io :as io]
        [cheshire.core :refer :all]
        [clojurewerkz.cassaforte.client :as cc]
        [clojurewerkz.cassaforte.cql    :as cql]
        [clojure.tools.logging :as log]
        [hsm.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel]]
        [compojure.handler :as handler :refer [api]]
        [compojure.route :as route :refer [resources]]
        [ring.middleware.reload :as reload]
        [ring.util.response :as resp]
        [ring.adapter.jetty :refer [run-jetty]]
        [net.cgrand.enlive-html :refer [deftemplate]]
        [compojure.core :refer [GET defroutes]]
        [com.stuartsierra.component :as component]))

(deftemplate defaultpage
  (io/resource "index.html") [] [:body] (if is-dev? inject-devmode-html identity))

(defn wrap-exception-handler
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch IllegalArgumentException e
        (->
         (resp/response e)
         (resp/status 400)))
      (catch Throwable e
        (do 
          (log/error e)
        (->
         (resp/response (.getMessage e))
         (resp/status 500)))))))

(defn generate-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defn generate-json-resp [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"}
   :body (generate-string data)})

(defn sample-conn [db request]
  (let [conn (:connection db)]
    (generate-json-resp (cql/select conn :user))))

(defrecord HTTP [port db server]
  component/Lifecycle

  (start [this]
    (log/info "Starting HTTP Server on " port)

    (defroutes routes
          (resources "/")
          (resources "/react" {:root "react"})
          (GET "/" req (defaultpage))
          (GET "/test" request (sample-conn db request)))

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
    (.stop server)
    ) 
  )

(defn http-server [port]
  (map->HTTP {:port port}))

(defrecord CassandraDB [host port keyspace connection]
  component/Lifecycle

  (start [component]
    (log/info "Starting Cassandra database")
    ;; In the 'start' method, initialize this component
    ;; and start it running. For example, connect to a
    ;; database, create thread pools, or initialize shared
    ;; state.
    (let [conn (cc/connect [host])]
        (cql/use-keyspace conn keyspace)
      ;; Return an updated version of the component with
      ;; the run-time state assoc'd in.
      (assoc component :connection conn)))

  (stop [component]
    (log/info "Stopping database")
    ;; In the 'stop' method, shut down the running
    ;; component and release any external resources it has
    ;; acquired.
    (.close connection)
    ;; Return the component, optionally modified. Remember that if you
    ;; dissoc one of a record's base fields, you get a plain map.
    (assoc component :connection nil)))


(defn cassandra-db 
  [host port keyspace]
  (map->CassandraDB {:host host :port port :keyspace keyspace}))


(defn front-end-system [config-options]
  (let [{:keys [host port keyspace server-port]} config-options]
    (-> (component/system-map
          :db (cassandra-db host port keyspace)
          :app (component/using 
            (http-server server-port)
            [:db]
            )
          ))))