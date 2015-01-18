(defproject org.clojars.bcambel/hackersome (slurp "VERSION")
  :description "Hackersome"
  :url "http://hackersome.com"
  :license {:name "MIT"
            :url "http://raw.github.com/bcambel/hackersome/blob/master/LICENCE"}

  :source-paths ["src/clj" "src/cljs"]
  :lein-release {:deploy-via :clojars :scm :git}
  :dependencies [[org.clojure/clojure "1.6.0"]
                [org.clojure/core.memoize "0.5.6"]
                [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                [ring "1.3.2"]
                [compojure "1.3.1"]
                [bidi "1.12.0"]
                [enlive "1.1.5"]
                [clj-http "1.0.1"]              
                [clj-time "0.8.0"]
                [figwheel "0.1.4-SNAPSHOT"]
                [environ "1.0.0"]
                [com.cognitect/transit-clj "0.8.259"]
                [cheshire "5.3.1"]
                [com.cemerick/friend "0.2.0" :exclusions [org.clojure/core.cache]]
                [friend-oauth2 "0.1.1"]
                [clojurewerkz/cassaforte "2.0.0"]
                [com.taoensso/carmine "2.9.0"]
                [net.jpountz.lz4/lz4  "1.2.0"]
                [prismatic/schema "0.3.3"]
                [prismatic/plumbing "0.3.5"]
                [tentacles "0.2.5"]
                [com.stuartsierra/component "0.2.2"]
                [com.brainbot/iniconfig "0.2.0"]
                [com.draines/postal "1.11.1"]
                [com.cemerick/piggieback "0.1.3"]
                [com.climate/claypoole "0.2.1"] ; handle threads
                [me.raynes/fs "1.4.6"]
                [clj-kafka/clj-kafka "0.2.8-0.8.1.1"]
                [hiccup "1.0.5"]
                [clojurewerkz/elastisch "2.1.0"]
                [liberator "0.12.2"]
                [commons-logging "1.1.3"]
                [raven-clj "1.2.0"]
                [twitter-api "0.7.7"]
                [metrics-clojure "2.4.0"]
                [slingshot "0.12.1"]
                [twitter-streaming-client/twitter-streaming-client "0.3.2"]
                [ch.qos.logback/logback-classic "1.1.2"]
                [org.clojure/tools.logging "0.3.1"]
                [weasel "0.4.0-SNAPSHOT"]
                [midje "1.7.0-SNAPSHOT"]
                [leiningen "2.5.0"]
                [org.clojure/tools.nrepl "0.2.5"]
                [raven-clj "1.2.0"]
                ]

  :java-agents [[com.newrelic.agent.java/newrelic-agent "2.19.0"]]
  :plugins [[lein-environ "1.0.0"]
            [lein-release "1.0.5"]
            [s3-wagon-private "1.1.2"]]
  :repositories [["private" {:url "s3p://hackersome/releases/" :creds :gpg}]]
  
  :codox {:defaults {:doc/format :markdown}
          :src-dir-uri "http://github.com/bcambel/hackersome/blob/development/"
          :src-linenum-anchor-prefix "L"}
  :min-lein-version "2.5.0"
  ; :uberjar-name "hsm.jar"
  :main hsm.server
  :jvm-opts ["-XX:+CMSClassUnloadingEnabled"]
  :profiles {
              :1.7 {:dependencies [[org.clojure/clojure "1.7.0-alpha4"]]}
              :master {:dependencies [[org.clojure/clojure "1.7.0-master-SNAPSHOT"]]}
              :twitter { :main hsm.integration.twttr :uberjar-name "hsm-twitter-pipe.jar"}
              :gsync { :main hsm.gsync :uberjar-name "hsm.github.sync.jar"}
              :tasksdb { :main hsm.tasks.db :uberjar-name "hsm.tasks.db.jar"} 
              :main {:main hsm.server :uberjar-name "hsm.jar"}
              
              :dev {
                  :jvm-opts ["-XX:-OmitStackTraceInFastThrow"]
                    :repl-options {:init-ns hsm.server
                                  ; :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]
                                }
                    :plugins [[lein-midje "3.1.3"]
                                [lein-figwheel "0.1.4-SNAPSHOT"]
                              ]
                    :dependencies [[midje "1.7.0-SNAPSHOT"] 
                                   [org.xerial.snappy/snappy-java "1.0.5"]]
                    :figwheel {:http-server-root "public"
                              :port 3449
                              :css-dirs ["resources/public/css"]}
                    :env {:is-dev true}
                    :cljsbuild {:builds {:app {:source-paths ["env/dev/cljs"]}}}}

             :uberjar {
                       :env {:production true}
                       :omit-source true
                       :aot :all}}

  :aliases { "dev-git-sync" ["trampoline" "with-profile" "dev,gsync" "run"]}
                       )
