(defproject hackersome-web "0.1.0-SNAPSHOT"
  :description "Hackersome Web Backend"
  :url "http://hackersome.com"
  :license {:name "MIT"
            :url "https://raw.githubusercontent.com/bcambel/hackersome/master/LICENSE"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                    [com.taoensso/timbre "3.1.6"]
                    [org.clojure/tools.cli "0.3.1"]
                    [cheshire "5.3.1"]
                    [raven-clj "0.6.0"]
                    [com.brainbot/iniconfig "0.2.0"]
                    [com.draines/postal "1.11.2"]
                    [me.raynes/fs "1.4.4"]
                    [clojurewerkz/cassaforte "2.0.0-beta8"]
                    [clj-oauth "1.5.1"]
                    [hiccup "1.0.5"]
                    [liberator "0.12.2"]
                    [clj-time "0.7.0"]
                    [http-kit "2.1.16"]
                    [compojure "1.1.8"]
                    [ring "1.3.1"]]
  :main ^:skip-aot hackersome.web.core
  :target-path "target/%s"
  :plugins [[lein-ring "0.8.13"]]
  :ring {:handler hackersome.web.core/handler}
  :profiles {:uberjar {:aot :all}
                  :dev {:dependencies [[midje "1.6.3"]
                                [ring-basic-authentication "1.0.5"]
                                   [ring-mock "0.1.5"]
                                   [spyscope "0.1.4" :exclusions [clj-time]]
                  ]
                                    :plugins [[lein-midje "3.1.3"]]}})
