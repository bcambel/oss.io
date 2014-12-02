(ns hsm.conf
  (:require
    [clojure.tools.logging :as log]
    [com.brainbot.iniconfig :as iniconfig]
    [me.raynes.fs :refer [exists?]]))

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

(defn parse-conf
  [config apply-transform]
  (if-not (exists? config)
    (log/warn "Config file does not exist" config)
    (let [settings (iniconfig/read-ini config)]
      (log/warn settings)
      (if apply-transform
        (transform settings)
        settings))))