; (ns hsm.system.else
;   (:require
;     [taoensso.timbre          :as log]
;     [clojurewerkz.elastisch.rest    :as esr]
;     [com.stuartsierra.component     :as component]))
;
;
; (defrecord ElasticSearch [host port index-name conn]
;   component/Lifecycle
;   (start [component] (log/info "Starting ElasticSearch Component")
;     (let [conn (esr/connect (str host ":" port))]
;       (-> component
;         (assoc :conn conn)
;         (assoc :index index-name))))
;
;    (stop  [component] (log/info "Stopping ElasticSearch Component")
;       (assoc component :conn nil)))
;
; (defn elastisch
;   [host port index]
;   (map->ElasticSearch {:host host :port port :index-name index}))
