(ns hsm.system
     (:require 
        [clojurewerkz.cassaforte.client :as cc]
        [clojurewerkz.cassaforte.cql    :as cql]
        [clojure.tools.logging :as log]
        [ring.adapter.jetty :refer [run-jetty]]
        [compojure.core :refer [GET defroutes]]
        [com.stuartsierra.component :as component]))

(defn sample-conn [db request]
  (let [conn (:connection db)]
    (cql/select conn :users)
    )
  )

(defrecord HTTP [port db server]
  component/Lifecycle

  (start [this]
    (log/info "Starting HTTP Server on " port)

    (defroutes routes
          (GET "/test" request (sample-conn db request)))
    
    (let [server (run-jetty routes {:port (Integer. port)
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



; (defrecord Routing [db]
;   component/Lifecycle
;   (start [component]
;     (defroutes routes
;       (GET "/test" request (sample-conn db request)))
;     (assoc this :routes routes)
;     )
;   (stop [component])
;   )

; (defn routes [])



(defn front-end-system [config-options]
  (let [{:keys [host port keyspace server-port]} config-options]
    (-> (component/system-map
          :db (cassandra-db host port keyspace)
          :app (component/using 
            (http-server server-port)
            [:db]
            )
          ))))