(ns hsm.ring
  (:require
    [clojure.tools.logging  :as log]
    [ring.util.response     :as resp]
    [cognitect.transit       :as t]
    [clojure.stacktrace     :as clj-stk]
    [raven-clj.core               :refer  [capture]]
    [raven-clj.ring               :refer [capture-error]]
    [cheshire.core           :refer :all])
  (:import
    [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn wrap-log
  [handler]
  (fn [request]
    (log/info request)
    (let [response (handler request)]
      (log/info (str "[HTTP" (:status response)"]") (:uri request))
      response
    )))

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
          (log/error e)
          
          (when dsn
            
              (let [ft (capture-error dsn req {:message (str e "->" (.getMessage e))} e nil)]
                (log/info "SENTRY: " (deref ft 1000 :timed-out) e))
              )
              ; :message (.getMessage e) 
              ; :exception {
              ;   :stacktrace (clj-stk/print-stack-trace e) 
              ;   :type (str (class e))}}
              ;   ))
          (clj-stk/print-stack-trace e)
        (->
         (resp/response "Sorry. An error occured.")
         (resp/status 500)))))))

(defn wrap-nocache
  [handler]
  (fn [request]
     (let [response (handler request)]
        (assoc-in response [:headers  "Pragma"] "no-cache"))))

(defn json-resp
  "Generates JSON resp of given object, 
  constructs a RING 200 Response.
  TODO: Optionable status code.."
  [data & [status]]
  (let [http-status-code (or status 200)]
    (log/info http-status-code)
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