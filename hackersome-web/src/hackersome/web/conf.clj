(ns hackersome.web.conf
  (:require
    [com.brainbot.iniconfig :as iniconfig]
    [taoensso.timbre :as timbre :refer [debug warn info] ]
    [me.raynes.fs :refer [exists?]]
    [hackersome.web.utils :as utils]
    )
  (:gen-class))

(defn transform
  "Converts a nested dict(2nd level max) into a flat format with keywords as keys. Default key's values are applied as they are
  Given { \"default\" { \"a\" 1 \"b\" 2} \"email\" { \"host\" \"gmail.com\" \"port\" 587} }
  transform into
  {:email-port 587, :a 1, :b 2, :email-host \"gmail.com\"}
  See that {default { a 1}}  transformed => { :a 1 }
  "
  [configs]
  (let [intermediate (map
                       (fn[key]
                         (map
                           #(hash-map
                             (keyword (str (when-not (= "default" key) key) (when-not (= "default" key) "-") %))
                             (get (get configs key) %))
                           (keys (get configs key))))
                       (keys configs))]
    (apply merge (apply concat intermediate))))

(defn read-config
  [config]
  (if-not (exists? config)
    (warn "Config file does not exist" config)
    (let [settings (iniconfig/read-ini config)]
      ; better to have keywords than strings
      (transform settings)
      ;(apply merge (map #(hash-map (keyword %) (get (get settings "dataexport") %)) (keys (get settings "dataexport"))))
      )))

