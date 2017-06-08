(ns hsm.controllers.task
    "Intermediary between tasks and ops"
    (:require
      [clojure.string                 :as s]
      [taoensso.timbre                :as log]
      [hsm.integration.ghub         :as gh]
      [hsm.tasks.queue :as q]
      ))


(defn get-url [request]
  (log/info request)
  (let [url (-> request :params :url)]
    (log/info url)
    (gh/get-url* url)
  )
  )
