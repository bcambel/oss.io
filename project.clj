(defproject hsm "0.1.0-SNAPSHOT"
  :description "Hackersome"
  :url "http://hackersome.com"
  :license {:name "MIT"
            :url "http://raw.github.com/bcambel/hackersome/blob/master/LICENCE"}

  :source-paths ["src/clj" "src/cljs"]

  :dependencies [[org.clojure/clojure "1.6.0"]
                [org.clojure/clojurescript "0.0-2371" :scope "provided"]
                [ring "1.3.1"]
                [compojure "1.2.0"]
                [enlive "1.1.5"]
                [om "0.7.3"]
                [clj-http "1.0.1"]
                [cljs-http "0.1.20"]
                [clj-time "0.7.0"]
                [figwheel "0.1.4-SNAPSHOT"]
                [environ "1.0.0"]
                [com.cognitect/transit-clj "0.8.259"]
                [com.cognitect/transit-cljs "0.8.192"]
                [cheshire "5.3.1"]
                [com.cemerick/friend "0.2.0" :exclusions [org.clojure/core.cache]]
                [friend-oauth2 "0.1.1"]
                [clojurewerkz/cassaforte "2.0.0-rc2"]
                [net.jpountz.lz4/lz4  "1.2.0"]
                [prismatic/schema "0.3.3"]
                [prismatic/om-tools "0.3.6"]
                [prismatic/dommy "1.0.0"]
                [prismatic/plumbing "0.3.5"]
                [tentacles "0.2.5"]
                [com.stuartsierra/component "0.2.2"]
                [com.brainbot/iniconfig "0.2.0"]
                [com.draines/postal "1.11.1"]
                [com.cemerick/piggieback "0.1.3"]
                [com.climate/claypoole "0.2.1"] ; handle threads
                [me.raynes/fs "1.4.6"]
                [hiccup "1.0.5"]
                [liberator "0.12.2"]
                [commons-logging "1.1.3"]
                [raven-clj "1.2.0"]
                [ch.qos.logback/logback-classic "1.1.2"]
                [org.clojure/tools.logging "0.3.1"]
                [weasel "0.4.0-SNAPSHOT"]
                [leiningen "2.5.0"]]

  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-environ "1.0.0"]]

  :min-lein-version "2.5.0"

  :uberjar-name "hsm.jar"
  :main hsm.server
  :cljsbuild {:builds {:app {:source-paths ["src/cljs"]
                             :compiler {:output-to     "resources/public/js/app.js"
                                        :output-dir    "resources/public/js/out"
                                        :source-map    "resources/public/js/out.js.map"
                                        :preamble      ["react/react.min.js"]
                                        :externs       ["react/externs/react.js"]
                                        :optimizations :none
                                        :pretty-print  true}}}}
  :jvm-opts ["-XX:+CMSClassUnloadingEnabled"]
  :profiles {
              :1.7 {:dependencies [[org.clojure/clojure "1.7.0-alpha4"]]}
              :master {:dependencies [[org.clojure/clojure "1.7.0-master-SNAPSHOT"]]}
              :dev {
                    :repl-options {:init-ns hsm.server
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                    :plugins [[lein-figwheel "0.1.4-SNAPSHOT"]
                              [lein-midje "3.1.3"]]
                    :dependencies [[midje "1.6.3"] [org.xerial.snappy/snappy-java "1.0.5"]]
                    :figwheel {:http-server-root "public"
                              :port 3449
                              :css-dirs ["resources/public/css"]}
                    :env {:is-dev true}
                    :cljsbuild {:builds {:app {:source-paths ["env/dev/cljs"]}}}}

             :uberjar {:hooks [leiningen.cljsbuild]
                       :env {:production true}
                       :omit-source true
                       :aot :all
                       :cljsbuild {:builds {:app
                                            {:source-paths ["env/prod/cljs"]
                                             :compiler
                                             {:optimizations :advanced
                                              :pretty-print false}}}}}})
