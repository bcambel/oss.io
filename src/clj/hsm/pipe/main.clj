(ns hsm.pipe.main
  (:use [clj-kafka.core :only (with-resource)])
  (:require
    [clojure.tools.logging :as log]
    [clj-kafka.zk :as zk]
    [clj-kafka.consumer.zk :as consumer.zk]
    [clj-kafka.producer :as kfk.prod]
    [clojure.core.async :as async :refer [alts! go]]
    [environ.core :refer [env]]))

; (defn init
;   "Initialize producer connection to Kafka and start listening given channel
;   to place all the messages into the kafka"
;   [channel]
;   (let [producer-config {"metadata.broker.list" (get-broker-list)
;                          "serializer.class" "kafka.serializer.DefaultEncoder"
;                          "partitioner.class" "kafka.producer.DefaultPartitioner"}
;      producer (kfk.prod/producer producer-config)]
;      ))

; (def outputter (comp :value println))



; (defn receive!
;   [topic action config]
;   (map byte-array->str
;       (with-resource [c (consumer.zk/consumer config)]
;         consumer.zk/shutdown
;         (mapv :value (take 2 (consumer.zk/messages c topic))))))
