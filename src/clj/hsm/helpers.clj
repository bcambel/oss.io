(ns hsm.helpers
  (:require 
    [clojure.tools.logging :as log]
    [hsm.utils              :refer :all]))

(defn containss?
  [s x]
  (log/warn s x)
  (.contains x s))

(defn host->pl->lang
  [host]
  (condp containss? (clojure.string/lower-case host)
    "pythonhackers.com" "Python"
    "clojurehackers.com" "Clojure"
    nil
  ))

(defn pl->lang
	[platform]
  (when (!nil? platform )
  	(condp = (clojure.string/lower-case platform)
  		"cpp" "C++"
  		"csharp" "C#"
      "python" "Python"
      "clojure" "Clojure"
      "clj" "Clojure"
  		platform)))