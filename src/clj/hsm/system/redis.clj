(ns hsm.system.redis
  (:require
    [taoensso.timbre          :as log]
    [taoensso.carmine               :as car :refer (wcar)]
    [com.stuartsierra.component     :as component]))




(defrecord Redis [host port conn]
  component/Lifecycle
  (start [component] (log/info "Starting Redis Component")
    (let [conn {:pool {} :spec {:host host :port (Integer/parseInt port)}}]
      (assoc component :conn conn)))

   (stop  [component] (log/info "Stopping Redis Component")
      (assoc component :conn nil)))

(defn redis-db
  [host port]
  (map->Redis {:host host :port port})
  )
