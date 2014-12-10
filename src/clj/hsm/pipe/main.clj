(ns hsm.pipe.main
  (:use [clj-kafka.core :only (with-resource)])
  (:require 
    [clojure.tools.logging :as log]
    [clj-kafka.zk :as zk]
    [clj-kafka.consumer.zk :as consumer.zk]
    [clj-kafka.producer :as kfk.prod]
    [environ.core :refer [env]]
    ))


(def config {"zookeeper.connect" (env :zookeeper)
     "group.id" "clj-kafka.consumer"
     "auto.offset.reset" "smallest"
     "auto.commit.enable" "false"})

(defn get-broker-list 
  []
  (zk/broker-list (zk/brokers {"zookeeper.connect" (env :zookeeper)})))

(defn send! 
  [topic msg]
  (let [producer-config {"metadata.broker.list" (get-broker-list)
     "serializer.class" "kafka.serializer.DefaultEncoder"
     "partitioner.class" "kafka.producer.DefaultPartitioner"}
     producer (kfk.prod/producer producer-config)]
  (kfk.prod/send-message producer
              (kfk.prod/message topic (.getBytes msg)))))

(def outputter (comp :value println))

(defn receive!
  [topic action]
  (with-resource [c (consumer.zk/consumer config)]
     consumer.zk/shutdown
     (mapv :value (take 2 (consumer.zk/messages c topic)))))