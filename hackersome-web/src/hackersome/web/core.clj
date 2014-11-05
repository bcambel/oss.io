(ns hackersome.web.core
    (:require
    [clojure.tools.cli :refer [parse-opts]]
    [clojure.string :as s]
    [clojure.java.io :as io]
    [taoensso.timbre :as timbre :refer [debug info warn stacktrace error]]
    [cheshire.core :as json]
     [compojure.handler :refer [site]]
    [compojure.core :as cmpj]
    [compojure.route :as route]
    [org.httpkit.server :as httpkit]
    [hackersome.web.conf :refer [read-config]]
    [hackersome.web.utils :refer [->int]]
    )
  (:gen-class))

(def access-control-headers
  { "Access-Control-Allow-Origin" "*"
    "Access-Control-Allow-Headers" "Origin, X-Requested-With, Content-Type, Accept, X-Api-Token, X-Account-Id"})

(defn show-landing-page
  "A placeholder empty page"
  [req]
  { :status 200 :body "Landing page!." })

(defn welcome-page
  "A placeholder empty page"
  [options req]
  { :status 200 :body "Welcome page!." })

(defn options-query
  "Send back CORS headers to the client Browser that we enable access from ?"
  [req]
  { :status 200
    :headers access-control-headers })

(defn startup
  [options]
  (cmpj/defroutes all-routes
                  (cmpj/GET "/" [] show-landing-page)
                  (cmpj/OPTIONS "/export" [] options-query )
                  (cmpj/GET "/welcome" [] (partial welcome-page options))
                  (route/not-found "<p>Page not found.</p>"))
  (warn options)
  (warn (:port options))
  (let [stop-server (httpkit/run-server (site #'all-routes)
                                        {:thread (or (->int (:thread options)) 4)
                                         :port (->int (:port options)) })]
    (debug "Defining shutdown hook")
    (.addShutdownHook (Runtime/getRuntime) (Thread. (fn []
                                                      (debug "Server shutting down")
                                                      (stop-server :timeout 1000))))))

(defn usage [options-summary]
  (->> ["Hackersome Web server."
        ""
        "Usage: program-name -c <configuration_file>"
        ""
        "Options:"
        options-summary
        "Please refer to the manual page for more information."]
       (s/join \newline)))


(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (s/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(def cli-options
  "Define Command line options"
  [ ["-c" "--config CONFIG" "Config file to rule" :default "/etc/hackersome.conf"]
    ["-h" "--help"]])

(defn -main
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) (exit 0 (usage summary))
      errors (exit 1 (error-msg errors)))
    (let [external-settings (read-config (:config options))]
      (when-not (nil? external-settings)
        (let [app-settings (merge options external-settings)]
              (debug app-settings)
              (startup app-settings))))))
