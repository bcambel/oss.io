(ns hsm.integration.twttr
    (:use
   [twitter.oauth]
   [twitter.callbacks]
   [twitter.callbacks.handlers]
   [twitter.api.streaming])
    (:require
   [cheshire.core :as json]
   [http.async.client :as ac])
  (:import
   (twitter.callbacks.protocols AsyncStreamingCallback))

    )




(def my-creds (make-oauth-creds (env :app-consumer-key)
                                (env :app-consumer-secret)
                                (env :user-access-token)
                                (env :user-access-token-secret)))

(def ^:dynamic 
     *custom-streaming-callback* 
     (AsyncStreamingCallback. (comp println #(:text %) json/parse-string #(str %2)) 
                      (comp println response-return-everything)
                  exception-print))

(statuses-filter :params {:track "clojure"}
         :oauth-creds my-creds
         :callbacks *custom-streaming-callback*