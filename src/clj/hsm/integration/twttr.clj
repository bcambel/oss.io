; (ns hsm.integration.twttr
;   (:use
;    [twitter.oauth]
;    [twitter.callbacks]
;    [twitter.callbacks.handlers])
;   (:require
;    [cheshire.core :refer :all]
;    [taoensso.timbre :as log]
;    [twitter-streaming-client.core :as twt-strm-cli]
;    [clojure.core.async :as async :refer [go >! chan]]
;    [environ.core :refer [env]]
;    [hsm.conf :as conf]
;    [hsm.system :as system]
;    ; [http.async.client :as ac]
;    [com.stuartsierra.component :as component]
;    [twitter.api.streaming :as twt-stream])
;   (:gen-class))
;
; (def my-creds (make-oauth-creds (env :app-consumer-key)
;                                 (env :app-consumer-secret)
;                                 (env :user-access-token)
;                                 (env :user-access-token-secret)))
;
; (defn vacuum-twttr
;   [tracking pipe-fn]
;   (let [stream (twt-strm-cli/create-twitter-stream
;                   twt-stream/statuses-filter
;                   :oauth-creds my-creds
;                   :params {:track tracking})]
;     (twt-strm-cli/start-twitter-stream stream)
;     (loop []
;       (let [q (twt-strm-cli/retrieve-queues stream)
;         tweets (:tweet q)
;         texts (mapv #(:text %) tweets)]
;         (pipe-fn texts)
;         (Thread/sleep 1000))
;       (recur))))
;
; (defn start-listen
;   [keywords tweet-chan]
;   (let []
;         (log/warn "Start vacuum")
;     (vacuum-twttr keywords (fn[tweets]
;                                 (mapv #(go (>! tweet-chan ["test" %])) tweets)))))
;
; (defn -main
;   [& args]
;   (let [{:keys [conf] :or {conf "app.ini"}} {:conf "app.ini"}
;         c (conf/parse-conf conf true)
;         sys (component/start (system/worker-system { :zookeeper (:zookeeper-host c)}))
;         keywords (first args)]
;     (start-listen keywords (-> sys :kafka-producer))
;     )
;   )
