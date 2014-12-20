(ns hsm.integration.twttr
  (:use
   [twitter.oauth]
   [twitter.callbacks]
   [twitter.callbacks.handlers])
  (:require
   [cheshire.core :refer :all]
   [clojure.tools.logging :as log]
   [twitter-streaming-client.core :as twt-strm-cli]
   [clojure.core.async :as async :refer [go >! chan]]
   [environ.core :refer [env]]
   [hsm.pipe.main :as pipe]
   [http.async.client :as ac]
   [twitter.api.streaming :as twt-stream])
  (:gen-class))

(def my-creds (make-oauth-creds (env :app-consumer-key)
                                (env :app-consumer-secret)
                                (env :user-access-token)
                                (env :user-access-token-secret)))

(defn vacuum-twttr
  [tracking pipe-fn]
  (let [stream (twt-strm-cli/create-twitter-stream
                  twt-stream/statuses-filter
                  :oauth-creds my-creds
                  :params {:track tracking})]
    (twt-strm-cli/start-twitter-stream stream)
    (loop []
      (let [q (twt-strm-cli/retrieve-queues stream)
        tweets (:tweet q)
        texts (mapv #(:text %) tweets)]
        (pipe-fn texts)
        (Thread/sleep 1000))
      (recur))))

(defn start-listen
  [keywords]
  (let [tweet-chan (chan) 
        kafka-pipe (pipe/init tweet-chan)]
        (log/warn "Start vacuum")
    (vacuum-twttr keywords (fn[tweets]
                                (mapv #(go (>! tweet-chan ["test" %])) tweets)))))

(defn -main
  [& args]
  (start-listen (first args)))
