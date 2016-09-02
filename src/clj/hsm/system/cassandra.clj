; (ns hsm.system.cassandra
; 	(:require
; 		[taoensso.timbre					:as log 	]
; 		[hsm.schema 										:as db 		]
; 		[clojurewerkz.cassaforte.client :as cc 		]
;     [clojurewerkz.cassaforte.policies   :as cp]
;     [clojurewerkz.cassaforte.cql    :as cql 	]
;     [clojurewerkz.cassaforte.query 	:as dbq		]
;     [com.stuartsierra.component 		:as component]))
;
; (defrecord CassandraDB [host port keyspace connection]
;   component/Lifecycle
;
;   (start [component]
;     (log/info "Starting Cassandra database")
;     (let [hosts (vec (.split host ","))]
;       ; (try
;         ; (let [conn (cc/connect hosts {
;         ;       :load-balancing-policy (cp/round-robin-policy)
;         ;       :reconnection-policy (cp/exponential-reconnection-policy 100 1000)
;         ;       :retry-policy (cp/retry-policy :downgrading-consistency)
;         ;       } )]
;         ;   (db/create-or-use-keyspace conn keyspace)
;         ;   (assoc component :connection conn))
;         ; (catch Throwable t
;         ;   (log/error t)
;         ;   )
;         ; )
;       (assoc component :connection {})
;       ))
;
;   (stop [component]
;     (log/info "Stopping database")
;     (.close connection)
;     (assoc component :connection nil)))
;
; (defn cassandra-db
;   [host port keyspace]
;   (map->CassandraDB {:host host
; 										 :port port
; 										 :keyspace keyspace}))
