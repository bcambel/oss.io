(def VERSION (clojure.string/replace (slurp "VERSION") #"\n" ""))
(println VERSION "-")
(defproject org.clojars.bcambel/hackersome VERSION
            :description "OpenSourceSofware Community"
            :url "http://www.oss.io"
            :license {:name "MIT"
                      :url "http://raw.github.com/bcambel/hackersome/blob/master/LICENCE"}

            :source-paths ["src/clj" "src/cljs"]
            :lein-release {:deploy-via :clojars :scm :git}
            :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                           [org.clojure/core.memoize "0.5.9"]
                           [org.clojure/core.async "0.3.443"]
                           [ring "1.6.1"]
                           [ring/ring-defaults "0.3.0"]

                           [http-kit "2.2.0"]
                           [compojure "1.6.0"]
                           [enlive "1.1.6"]
                           [clj-http "3.6.0"]
                           [clj-time "0.13.0"]

                           [environ "1.1.0"]
                           [com.cognitect/transit-clj "0.8.300"]
                           [cheshire "5.7.1"]
                           [com.google.guava/guava "22.0"]

                           [com.taoensso/carmine "2.16.0"]

                           [net.jpountz.lz4/lz4  "1.3.0"]
                           [prismatic/schema "1.1.6"]
                           [prismatic/plumbing "0.5.4"]
                ; [tentacles "0.2.5"]

                           [com.stuartsierra/component "0.3.2"]

                           [com.brainbot/iniconfig "0.2.0"]
                           [com.draines/postal "2.0.2"]

                           [com.climate/claypoole "1.1.4"] ; handle threads
                           [me.raynes/fs "1.4.6"]
                           [hiccup "1.0.5"]

                           [com.taoensso/timbre "4.7.4"]
                           [bcambel/raven-clj "1.5.0"]
                ; [spootnik/raven "0.1.2"]
                           [com.climate/squeedo "0.1.4"]
                           [metrics-clojure "2.9.0"]
                           [slingshot "0.12.2"]
                           [clj-datadog "3.0.1"]
                           [midje "1.8.3"]
                           [digest "1.4.5"]

                           [markdown-clj "0.9.62"]
                           [org.clojure/tools.nrepl "0.2.12"]

                           [org.clojure/java.jdbc "0.6.1"]
                           [honeysql "0.8.2"]
                           [org.postgresql/postgresql "9.4-1205-jdbc41"]
                           [hikari-cp "1.7.5"]
                           [org.clojars.runa/clj-kryo "1.5.0"]
                           [org.clojure/tools.namespace "0.3.0-alpha4"]
                           [com.stuartsierra/frequencies "0.1.0"]
                           ]
                :plugins [[lein-environ "1.0.0"]
                           [s3-wagon-private "1.1.2"]]

  ; :repositories [["private" {:url "s3p://hackersome/releases/" :creds :gpg}]]

            :codox {:defaults {:doc/format :markdown}
                    :src-dir-uri "http://github.com/bcambel/oss.io/blob/development/"
                    :src-linenum-anchor-prefix "L"}

            :main hsm.server
            :jvm-opts ["-XX:+CMSClassUnloadingEnabled"]
            :profiles {:1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
                       :master {:dependencies [[org.clojure/clojure "1.9.0-alpha14"]]
                                :java-agents [[com.newrelic.agent.java/newrelic-agent "3.39.1"]]}
              ; :twitter { :main hsm.integration.twttr :uberjar-name "hsm-twitter-pipe.jar"}
                       :gsync {:main hsm.gsync :uberjar-name "hsm.github.sync.jar"}
                       :main {:main hsm.server :uberjar-name ~(str "hsm-" VERSION ".jar")}
                       :dbsync {:main hsm.tasks.dbexport :uberjar-name "hsm.db.export.jar"}
                       :dev {:jvm-opts ["-XX:-OmitStackTraceInFastThrow"]
                             :source-paths ["dev"]
                             :repl-options {:init-ns user}

                             :plugins [[lein-midje "3.1.3"]
                                ; [lein-figwheel "0.1.4-SNAPSHOT"]
]
                             :dependencies [[midje "1.8.3"]
                                            [org.xerial.snappy/snappy-java "1.0.5"]
                                            [org.clojure/tools.namespace "0.2.11"]]

                             :env {:is-dev true}}

                       :uberjar {:env {:production true}
                                 :omit-source true
                                 :aot :all}}

            :aliases {"dev-git-sync" ["trampoline" "with-profile" "dev,gsync" "run"]
                      "all" ["with-profile" "dev:dev,master"]})
