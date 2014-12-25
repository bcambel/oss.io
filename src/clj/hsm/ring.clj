(ns hsm.ring
	(:require 
		[ring.util.response :as resp]
		[cheshire.core :refer :all]))


(defn wrap-exception-handler
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch IllegalArgumentException e
        (->
         (resp/response e)
         (resp/status 400)))
      (catch Throwable e
        (do
          (log/error (.getMessage e))
          (clojure.stacktrace/print-stack-trace e)
        (->
         (resp/response (.getMessage e))
         (resp/status 500)))))))

(defn json-resp
  "Generates JSON resp of given object, 
  constructs a RING 200 Response.
  TODO: Optionable status code.."
  [data & [status]]
  (-> (generate-string data)
        (resp/response)
        (resp/header "Content-Type" "application/json")
        (resp/status (or status 200))))