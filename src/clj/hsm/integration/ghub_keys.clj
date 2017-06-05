(ns hsm.integration.ghub_keys
    "Fetch repository information from github"
    (:require
      [clojure.string                 :as s]
      [cheshire.core                  :refer :all]
      [taoensso.timbre                :as log]
      [taoensso.carmine :as car :refer (wcar)]
      [clj-http.client                :as client]
      [hsm.cache :as cache]

      ))
(def ghub-root "https://api.github.com")
(def credit-store (atom {}))

;; load available keys
(def redis-opt {:pool {} :spec {:host "localhost" :port 6379}})

(defn get-keys
  []
  (partition 2
    (wcar redis-opt
      (car/hgetall "oss.system.github_keys")
    )))

(defn key-credits
  [client_id secret]
  (let [url (format "%s/%s?client_id=%s&client_secret=%s" ghub-root "rate_limit" client_id secret)
        _ (log/info url)
        {:keys [body headers ]} (client/get url)
        {:keys [limit remaining reset]} (-> body (parse-string true) :resources :core )]
        {:credit remaining :reset reset}))

(defn pick-key
  []
  (let [available-keys (get-keys)]
    (when-not (empty? available-keys)
      (rand-nth available-keys))))

(defn update-key-credits
  [client_id secret]
  (log/infof "Updating Credits for %s" client_id)
  (let [fact (key-credits client_id secret)]
    (log/infof "Credits for %s: %s" client_id fact)
    (wcar redis-opt
      (car/zadd "oss.system.github_keys_list" (:credit fact) client_id )
      (car/hset "oss.system.github_keys_state" client_id (generate-string fact)))))

(defn refresh-keys
  []
  (let [keys (get-keys)]
    (log/infof "Found keys %s" keys)
    (doall (pmap (fn [[x y]](println x y) (update-key-credits x y)) keys))
  ))

(defn set-interval [callback ms]
  (future (while true (do (Thread/sleep ms) (callback)))))

; (defonce job (set-interval refresh-keys 90000 ))
