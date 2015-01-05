(ns hsm.system.kafka
  (:use [clj-kafka.core :only (with-resource)])
  (:require
    [clojure.tools.logging                   :as log]
    [clj-kafka.zk                           :as zk]
    [clj-kafka.consumer.zk                   :as consumer.zk]
    [clj-kafka.producer                     :as kfk.prod]
    [clojure.core.async                     :as async :refer [alts! go chan]]
    [com.stuartsierra.component             :as component]))

(defn get-broker-list
  "Returns a comma separated list of brokers"
  [zookeeper]
  (zk/broker-list 
    (zk/brokers {"zookeeper.connect" zookeeper})))

(defn send!
  [producer topic msg]
  (kfk.prod/send-message producer
              (kfk.prod/message topic (.getBytes msg))))

(defrecord KafkaProducer [zookeeper prod-chan channel]
  component/Lifecycle

  (start [component]
    (try
      (log/warn "Starting KAFKA PROD Component")
      (let [producing-channel (or prod-chan (chan))
            brokers (get-broker-list zookeeper)
            producer-config {"metadata.broker.list" brokers
                           "serializer.class" "kafka.serializer.DefaultEncoder"
                           "partitioner.class" "kafka.producer.DefaultPartitioner"}
            producer (kfk.prod/producer producer-config)]
        (go (while true
          (let [[[topic msg] ch] (alts! [producing-channel])]
            (send! producer topic msg))))
         (assoc component :channel producing-channel))
      (catch Throwable t 
        (do 
          (log/warn "[KAFKA-PROD] FAILED")
          (log/error t)
          (assoc component :channel nil)))))
  (stop [component]

    )
  )

(defn kafka-producer
  [zookeeper]
  (map->KafkaProducer {:zookeeper zookeeper}))


(defrecord KafkaConsumer [zookeeper topics]
  component/Lifecycle

  (start [component]
    (let [consumer-config {"zookeeper.connect" zookeeper
             "group.id" "clj-kafka.consumer"
             "auto.offset.reset" "smallest"
             "auto.commit.enable" "true"}]

             )
    )
  (stop [component])

  )