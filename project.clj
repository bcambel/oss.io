(def VERSION (clojure.string/replace (slurp "VERSION") #"\n" ""))
(println VERSION "-")
(defproject org.clojars.bcambel/hackersome VERSION
  :description "Hackersome"
  :url "http://hackersome.com"
  :license {:name "MIT"
            :url "http://raw.github.com/bcambel/hackersome/blob/master/LICENCE"}

  :source-paths ["src/clj" "src/cljs"]
  :lein-release {:deploy-via :clojars :scm :git}
  :dependencies [[org.clojure/clojure "1.8.0"]
                [org.clojure/core.memoize "0.5.9"]
                [org.clojure/core.async "0.2.385"]
                [ring "1.5.0"]
                [ring/ring-defaults "0.2.1"]

                [http-kit "2.2.0"]
                [compojure "1.5.1"]
                [enlive "1.1.5"]
                [clj-http "3.2.0"]
                [clj-time "0.12.0"]

                [environ "1.1.0"]
                [com.cognitect/transit-clj "0.8.288"]
                [cheshire "5.3.1"]
                [com.google.guava/guava "19.0"]

                [com.taoensso/carmine "2.14.0"]

                [net.jpountz.lz4/lz4  "1.3.0"]
                [prismatic/schema "0.3.3"]
                [prismatic/plumbing "0.3.5"]
                ; [tentacles "0.2.5"]

                [com.stuartsierra/component "0.3.1"]

                [com.brainbot/iniconfig "0.2.0"]
                [com.draines/postal "1.11.1"]

                [com.climate/claypoole "0.2.1"] ; handle threads
                [me.raynes/fs "1.4.6"]
                [hiccup "1.0.5"]

                [com.taoensso/timbre "4.7.4"]
                [bcambel/raven-clj "1.4.3"]

                [metrics-clojure "2.4.0"]
                [slingshot "0.12.2"]

                [midje "1.8.3"]
                [digest "1.4.4"]

                [markdown-clj "0.9.62"]


                [org.clojure/java.jdbc "0.4.2"]
                [honeysql "0.6.2"]
                [org.postgresql/postgresql "9.4-1205-jdbc41"]
                [org.clojars.runa/clj-kryo "1.5.0"]

                ]


  :plugins [[lein-environ "1.0.0"]
            ; [lein-release "1.0.5"]
            [s3-wagon-private "1.1.2"]]

  ; :repositories [["private" {:url "s3p://hackersome/releases/" :creds :gpg}]]

  :codox {:defaults {:doc/format :markdown}
          :src-dir-uri "http://github.com/bcambel/hackersome/blob/development/"
          :src-linenum-anchor-prefix "L"}
  ; :min-lein-version "2.5.0"

  :main hsm.server
  :jvm-opts ["-XX:+CMSClassUnloadingEnabled"]
  :profiles {
              :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
              :master {:dependencies [[org.clojure/clojure "1.8.0"]]
                        :java-agents [[com.newrelic.agent.java/newrelic-agent "3.31.1"]]
                      }
              ; :twitter { :main hsm.integration.twttr :uberjar-name "hsm-twitter-pipe.jar"}
              :gsync { :main hsm.gsync :uberjar-name "hsm.github.sync.jar"}
              :main {:main hsm.server :uberjar-name ~(str "hsm-"VERSION".jar")}
              :dbsync {:main hsm.tasks.dbexport :uberjar-name "hsm.db.export.jar"}
              :dev {
                  :jvm-opts ["-XX:-OmitStackTraceInFastThrow" "-javaagent:newrelic/newrelic.jar"]
                  :source-paths ["dev"]
                    :repl-options {:init-ns user}
                    ;               ; :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]
                    ;             }
                    :plugins [[lein-midje "3.1.3"]
                                ; [lein-figwheel "0.1.4-SNAPSHOT"]
                              ]
                    :dependencies [[midje "1.7.0-SNAPSHOT"]
                                   [org.xerial.snappy/snappy-java "1.0.5"]
                                   [org.clojure/tools.namespace "0.2.11"]]
                    ; :figwheel {:http-server-root "public"
                    ;           :port 3449
                    ;           :css-dirs ["resources/public/css"]}
                    :env {:is-dev true}
                    ; :cljsbuild {:builds {:app {:source-paths ["env/dev/cljs"]}}                    }
                  }

             :uberjar {
                       :env {:production true}
                       :omit-source true
                       :aot :all}}

  :aliases { "dev-git-sync" ["trampoline" "with-profile" "dev,gsync" "run"]
              "all" ["with-profile" "dev:dev,1.7:dev,master"]
    }
                       )
