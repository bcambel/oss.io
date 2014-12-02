(def version (clojure.string/trim (:out (clojure.java.shell/sh "git" "describe" "--always"))))

(defproject hackersome version
  :description "Hackersome"
  :url "https://hackersome.com/"
  :license {:name "MIT"
            :url  "https://github.com/bcambel/hackersome/blob/development/LICENSE"}
  :dependencies [[hsui ~version]]

  :plugins [[lein-sub "0.3.0"]]

  :sub ["hsui"])