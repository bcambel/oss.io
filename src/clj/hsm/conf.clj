(ns hsm.conf
  (:require
    [taoensso.timbre :as log]
    [com.brainbot.iniconfig :as iniconfig]
    [me.raynes.fs :refer [exists?]]))

(defn when-not-key
  "Weirdest function of all times."
  [key rez]
  (when-not (= "default" key) rez))
  
(defn transform
  "Converts a nested dict(2nd level max) into a flat format with keywords as keys.
  Default key's values are applied as they are
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
                             (keyword (str (when-not-key key key) (when-not-key key "-") %))
                             (get (get configs key) %))
                           (keys (get configs key))))
                       (keys configs))]
    (apply merge (apply concat intermediate))))

(def app-conf (atom {:data nil}))

(defn parse-conf
  [config apply-transform]
  (if-not (exists? config)
    (log/warn "Config file does not exist" config)
    (let [settings (iniconfig/read-ini config)]
      (log/warn settings)
      (let [configuration (if apply-transform
                            (transform settings)
                            settings)]
      (swap! app-conf assoc :data configuration)
      configuration
      ))))

(def languages
  [ "Java" "Clojure" "Python" "JavaScript" "Go" "C" "PHP" "HTML"
    "Erlang" "Rust" "Lisp" "Elixir" "Csharp" "CSS" "D" "Dart"
    "Scala" "Groovy" "Haskell" "R" "Julia" "Lua" "Racket"])
