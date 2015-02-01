(ns hsm.system.cassandra
	(:require 
		[clojure.tools.logging					:as log 	]
		[hsm.schema 										:as db 		]
		[clojurewerkz.cassaforte.client :as cc 		]
    [clojurewerkz.cassaforte.cql    :as cql 	]
    [clojurewerkz.cassaforte.query 	:as dbq		]
    [com.stuartsierra.component 		:as component]))

(defrecord CassandraDB [host port keyspace connection]
  component/Lifecycle

  (start [component]
    (log/info "Starting Cassandra database")
    (let [hosts (vec (.split host ","))
          conn (cc/connect hosts)]
      (db/create-or-use-keyspace conn keyspace)
      (assoc component :connection conn)))

  (stop [component]
    (log/info "Stopping database")
    (.close connection)
    (assoc component :connection nil)))

(defn cassandra-db
  [host port keyspace]
  (map->CassandraDB {:host host 
										 :port port 	
										 :keyspace keyspace}))