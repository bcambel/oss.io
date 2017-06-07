(ns hsm.server
  (:require
            [taoensso.timbre :as log]
            [hsm.conf :as conf]
            [hsm.system :as system]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [hsm.tasks.queue :as worker-queue]
            [clojure.tools.nrepl.server :as repl]
            )
  (:gen-class))


(defn startup
  [{:keys [conf] :or {conf "app.ini"}} ]
  (let [c (conf/parse-conf conf true)]
        (log/warn "Parsed config" conf)
        (let [sys (system/front-end-system {
                                    :server-port (:port c)
                                    :zookeeper (:zookeeper-host c)
                                    :host (:db-host c)
                                    :port (:db-port c)
                                    :keyspace (:db-keyspace c)
                                    :redis-host (:redis-host c)
                                    :redis-port (:redis-port c)
                                    :else-host (:else-host c)
                                    :else-port (:else-port c)
                                    :else-index (:else-index c)
                                    :conf c})
        app-sys (component/start sys)]
        (when (get c :worker false)
          (log/info "Worker Actived! ")
          (worker-queue/start-listen))
        (defonce server
          (repl/start-server :port (Integer/parseInt
                                    (get c :repl "7888"))))
        app-sys
    )))

(defn -main [& args]
    (try
      (log/info "Starting....")

      (startup {:conf (first args)})

      (catch Throwable t
        (do
          (log/warn "FAILED!")
          (log/error t)
          (log/warn (.getMessage t))
          (log/warn (.getCause t))))))
