(ns hsm.gsync
  (:require
    [taoensso.timbre :as log]
    [com.stuartsierra.component :as component]
    [hsm.conf :as conf]
    [hsm.system :as system]
    [hsm.integration.ghub :as gh]))

(defn -main
  [& args]
  (let [{:keys [conf] :or {conf "app.ini"}} {:conf "app.ini"}
        c (conf/parse-conf conf true)
        sys (component/start (system/db-system {
                                    :host (:db-host c)
                                    :port (:db-port c)
                                    :keyspace (:db-keyspace c)}))
        number-of-users (Integer/parseInt (first args))]
    (log/warn "Start operations!")
    (gh/sync-some-users (-> sys :db) number-of-users)
    (log/warn "Done!")
    (component/stop sys)
    (System/exit 0)
    )
  )
