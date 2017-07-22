(ns hsm.tasks.queue
  (:require [cemerick.bandalore :as sqs]
            [cheshire.core :refer :all]
            [ring.util.codec :as codec]
            [hsm.integration.ghub :as gh]
            [hsm.cache :as cache]
            [taoensso.timbre :as log]
            [cheshire.core                  :refer :all]
            [clj-http.client                :as client]
            [truckerpath.clj-datadog.core :as dd]
            [taoensso.carmine :as car :refer (wcar)]
            [raven-clj.core :refer [capture]]
            [hsm.conf                       :as conf]
            [raven-clj.interfaces :refer [stacktrace]]
            ))

(defn exec-with-care
  [exec-fn args]
  (future
    (try
      (apply exec-fn args)
      (catch Exception ex
        (log/error ex)
        (log/warnf "Error processing %s %s" exec-fn ex)
        (capture (-> @conf/app-conf :data :sentry-dsn )
                  (-> {:message (format "Processing Exception on %s" exec-fn)}
                      (stacktrace ex)))
        (log/warn "Captured trace")))))

(defn process-msg
  [msg]
  (let [{:keys [type params]} (parse-string (:body msg) true)]
    (log/infof "Processing %s %s" type params)
    (try
      (condp = type
        "update-project" (future (gh/update-project-remotely params))
        "enhance-user" (future (gh/find-n-update-user nil params true))
        "sync-user" (future
                      (gh/sync-some-users {:connection nil} (Integer/parseInt params)))
        "sync-single-user" (future (gh/find-n-update-user {} params))
        "import-org-events" (future (gh/import-org-events params))
        "iterate-users" (future (gh/iterate-users-since (Long/parseLong params)))
        "iterate-projects" (exec-with-care gh/iterate-projects-since [(Long/parseLong params)])
        (log/warn (str "unexpected value ->" type))
        )
    (catch Exception ex
      (do
        (log/error ex)
        (log/warnf "Error during processing %s %s: %s" type params ex)
      )))
      (log/infof "Returning task %s" (:id msg))
      ))


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
          (future
            (dorun
              (map
                (sqs/deleting-consumer client process-msg)
                (sqs/polling-receive client op-queue :max-wait Long/MAX_VALUE :limit 10)))))))

(defn start-listen []
    (try
    (consume-messages )
    (catch Exception ex
      (log/warn "Failed to start" ex))
  ))
