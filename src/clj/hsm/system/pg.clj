(ns hsm.system.pg
  (:require
    [taoensso.timbre          :as log   ]
    [com.stuartsierra.component     :as component]
    [clojure.java.jdbc              :as jdbc]
    ))

(def DEFAULT-DB-SPEC
  {:classname   "org.postgresql.Driver"
   :subprotocol "postgresql"
   :subname "postgres"
   :user     "test"
   :password "test"
   :host "127.0.0.1"
   :db "postgres"})

(def pg-db DEFAULT-DB-SPEC)

(defrecord PGDatabase [db-spec connection]
  component/Lifecycle

  (start [component]
    (let [conn (jdbc/get-connection (:db-spec component))]
      (assoc component :connection conn)))

  (stop [component]
    (.close connection)
    (assoc component :connection nil)))

(defn new-database
  ([db-spec]
    (PGDatabase. (merge DEFAULT-DB-SPEC db-spec) nil)))
