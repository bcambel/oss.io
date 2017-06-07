(ns hsm.ring
  (:require
    [taoensso.timbre  :as log]
    [ring.util.response     :as resp]
    [cognitect.transit       :as t]
    [clojure.stacktrace     :as clj-stk]
    [cheshire.core           :refer :all]
    [truckerpath.clj-datadog.core :as dd]
    [digest]
    [truckerpath.clj-datadog.core :as dd]
    [hsm.dev :refer [is-dev?]]
    )
  (:import
    [java.net InetAddress]
    [java.util UUID]
    [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn assign-id
  [request]
    (assoc request :req-id (subs (digest/sha-256 (str (UUID/randomUUID))) 0 20)))

(defn wrap-log
  [handler]
  (fn [request]
    (let [ req (assign-id request)]
      (log/sometimes 0.1 (log/info req))

      (let [response (handler req)]
        (log/info "HTTP" (:status response) (:req-id req) (:uri req))
        (try
          (dd/increment {} "page.views" 1 { :url (:uri req)  :status (:status response) })
          (catch Throwable e
            (log/error e)))

      response
    ))))

(defn wrap-exception-handler
  ""
  [handler dsn]
  (fn [req]
    (try
      (handler req)
      (catch IllegalArgumentException e
        (-> e
         (resp/response)
         (resp/status 400)))
      (catch Throwable e
        (do
          (when is-dev?
            (log/error e)
            (throw e))
        (->
         (resp/response "Sorry. An error occured.")
         (resp/status 500)))))))

(defn wrap-nocache
  [handler]
  (fn [request]
     (let [response (handler request)]
        ; (log/info "[NOCACHE]")
        (-> response
        (assoc-in [:headers  "Pragma"] "no-cache")
        (assoc-in [:headers  "X-Req-ID"] (:req-id request))
        ; (assoc-in [:headers "X-Server-Name"] (.getHostName (InetAddress/getLocalHost)))
        ))))

(defn json-resp
  "Generates JSON resp of given object,
  constructs a RING 200 Response.
  TODO: Optionable status code.."
  [data & [status]]
  (let [http-status-code (or status 200)]
    ; (log/info http-status-code)
    (-> (generate-string data)
          (resp/response)
          (resp/header "Content-Type" "application/json")
          (resp/status http-status-code))))

(defn html-resp
  "Generates Text/HTML resp of given object,
  constructs a RING 200 Response.
  TODO: Optionable status code.."
  [data & [status]]
    (let [http-status-code (or status 200)]
      (-> data
            (resp/response)
            (resp/content-type "text/html")
            (resp/charset "UTF-8")
            (resp/status http-status-code))))


(defn trans-resp
  "Generate Transit-JSON based response.
  Default Status 200"
  [data & [status]]
  (let [out (ByteArrayOutputStream. 4096)
        writer (t/writer out :json)]
    (t/write writer data)
    (-> (.toString out)
        (resp/response)
        (resp/header "Content-Type" "application/transit+json")
        (resp/status (or status 200)))))

(def redirect resp/redirect)
