(ns hsm.server
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [hsm.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel]]
            [liberator.core :refer [resource defresource]]
            [compojure.core :refer [GET defroutes]]
            [compojure.route :as route :refer [resources]]
            
            [ring.util.response :as resp]
            [ring.util.codec :as codec]
            [net.cgrand.enlive-html :refer [deftemplate]]
            
            [cognitect.transit :as t]
            [cemerick.friend :as friend]
            [hsm.users :as users :refer (users)]
            [hsm.github :as ghub :refer [render-repos-page]]
            [hsm.conf :as conf]
            [hsm.system :as system]
            [clj-http.client :as client]
            [com.stuartsierra.component :as component]
            [hiccup.page :as h]
            [hiccup.element :as e]
            [cheshire.core :refer :all]
            [environ.core :refer [env]]
            [ring.adapter.jetty :refer [run-jetty]])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn body-as-string 
  [ctx]
  (if-let [body (get-in ctx [:request :body])]
    (condp instance? body
      java.lang.String body
      (slurp (io/reader body)))))

(defn generate-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defn respond [data & [status]]
  (let [out (ByteArrayOutputStream. 4096)
        writer (t/writer out :json)]
    (t/write writer data)
    { :status (or status 200)
     :headers { "Content-Type" "application/json" }
     :body
     (generate-string data)
     ;(.toString out)
     }
     ;(pr-str data)
))



(defn links
  []
  ; (throw (Throwable. "Testing"))
  (respond [ {:url "http://google.com" :title "Google" :shares 213} 
                       {:url "http://github.com" :title "Github" :shares 2342} 
                       {:url "http://travelbird.nl" :title :travelbird :shares 100 }]))

; (defroutes routes
;   (resources "/")
;   (resources "/react" {:root "react"})
;   (GET "/" req (defaultpage))
;   ;(GET "/user/:id" req (user-details))
;   (GET "/test" request (friend/authorize #{::user} (render-repos-page request)))
;   (GET "/links" req (links)))


(defn startup 
  [{:keys [conf] :or {conf "app.ini"}} ]
  (let [c (conf/parse-conf conf true)]
        (log/warn "Parsed config")
        (let [sys (system/front-end-system {
                                    :server-port (:port c)
                                    :host (:db-host c) 
                                    :port (:db-port c) 
                                    :keyspace (:db-keyspace c)})
        app-sys (component/start sys)]
    ; (run (:port c))
    ))
  )

(defn -main [& args]
  (startup {})
  )
