; (ns hsm.tasks.db
; 	(:require
; 		[hsm.conf :as conf]
;     [com.stuartsierra.component :as component]
;     [hsm.system :as system]
;     [hsm.schema :as schema])
; 	(:gen-class))
;
;
; (defn -main
;   [& args]
;   (let [{:keys [conf] :or {conf "app.ini"}} {:conf "app.ini"}
;         c (conf/parse-conf conf true)
;         sys (component/start (system/db-system {
;                                     :host (:db-host c)
;                                     :port (:db-port c)
;                                     :keyspace (:db-keyspace c)}))]
;         (schema/create-db-space (:db sys) (:db-keyspace c))
;   ))
