(ns hsm.system.cassandra
	(:require 
		[clojure.tools.logging			:as log 	]
		[hsm.schema 					:as db 		]
		[clojurewerkz.cassaforte.client :as cc 		]
        [clojurewerkz.cassaforte.cql    :as cql 	]
        [clojurewerkz.cassaforte.query 	:as dbq		]
        [com.stuartsierra.component 	:as component]))

(defrecord CassandraDB [host port keyspace connection]
  component/Lifecycle

  (start [component]
    (log/info "Starting Cassandra database")
    (let [conn (cc/connect [host])]
      (db/create-or-use-keyspace conn keyspace)
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
  (map->CassandraDB {:host host 
  					 :port port 
  					 :keyspace keyspace}))