(ns hsm.tasks.queue
  (:require [cemerick.bandalore :as sqs]
            [cheshire.core :refer :all]
            [hsm.integration.ghub :as gh]
            [hsm.cache :as cache]
            [taoensso.timbre :as log]
            [cheshire.core                  :refer :all]
            [clj-http.client                :as client]
            [taoensso.carmine :as car :refer (wcar)]
            ))

(defn pick-bee []
  (let [hive (wcar  {:pool {} :spec {:host "localhost" :port 6379}}
              (car/smembers "oss.worker.bees"))]
        (rand-nth hive)))

(defn assign-worker-bee
  [param]
  (let [bee (pick-bee )
        url (format "http://%s/%s/%s" bee "update-project" param )]
      (log/info url)
     (when-let [result (try
                        (-> (client/get url)
                            (get :body)
                            (parse-string true))
                        (catch Exception ex
                          (log/error ex)))]
      (gh/update-project-stats result)
      (gh/update-project-db param result)
  )))



(defn process-msg
  [msg]
  (let [{:keys [type params]} (parse-string (:body msg) true)]
    (log/infof "Processing %s %s" type params)
    (try
    (condp = type
      "update-project" (assign-worker-bee params)
      "import-org-events" (gh/import-org-events params)
      (str "unexpected value ->" type)
      )
    (catch Exception ex
      (do
        (log/warnf "Error during processing %s %s: %s" type params ex)
      )))))


(defn consume-messages
  []
  (let [ client (try
                    (sqs/create-client)
                  (catch Exception ex
                    (log/warn "Failed to create client")
                    ))]
      (let [available-queues  (sqs/list-queues client)
            _ (log/info available-queues)
            op-queue (.replace (or (first available-queues) "") "us-east-1" "us-west-1" )]
          (future (dorun (map (sqs/deleting-consumer client process-msg)
             (sqs/polling-receive client op-queue :max-wait Long/MAX_VALUE :limit 10)))))))

(defn start-listen []
    (try
    (consume-messages )
    (catch Exception ex
      (log/warn "Failed to start" ex))
  ))
