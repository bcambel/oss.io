(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [hsm.server :refer [startup]]
            [com.stuartsierra.component :as component]
            ))




(defn go []
  (def sys (startup {:conf "settings.dev.ini"}))

  )

(defn reset []
  (component/stop-system sys)
  (refresh)
  )
