(ns hsm.tasks.queue
  (:require [cemerick.bandalore :as sqs]
            [cheshire.core :refer :all]
            [hsm.integration.ghub :as gh]
            [hsm.cache :as cache]
            )

  )

(defonce client (sqs/create-client))

(def available-queues  (sqs/list-queues client))

(defonce op-queue (.replace (or (first available-queues) "") "us-east-1" "us-west-1"))

(defn update-project-info
  [params]
  (let [updated-project (gh/update-project nil params)]
    (println (:watchers updated-project) (:description updated-project))
    (cache/hset {:pool {} :spec {:host "localhost" :port 6379}}
      (format "oss.stats_timeline_%s" (:full_name updated-project))
                            (str (System/currentTimeMillis))
                            (:watchers updated-project))))

(defn process-msg
  [msg]
  (let [{:keys [type params]} (parse-string (:body msg) true)]
    (println "Processing %s %s" type params)
    (condp = type
      "update-project" (update-project-info params)
      "import-org-events" (gh/import-org-events params)
      (str "unexpected value ->" type)
      )
  )
  )


(defn consume-messages
  [client q]
  (future (dorun (map (sqs/deleting-consumer client process-msg)
                   (sqs/polling-receive client q :max-wait Long/MAX_VALUE :limit 10)))))

(defn start-listen []
  (consume-messages client op-queue))
