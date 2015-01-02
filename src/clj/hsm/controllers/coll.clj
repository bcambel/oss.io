(ns hsm.controllers.coll
  (:require 
    [hsm.actions :as actions]
    [hsm.views :refer [layout panel]]
    [hsm.ring :refer [json-resp html-resp]]
    [hsm.utils :refer [type-of id-of host-of]]))


(defn load-coll
  [[db event-chan] request]
  (let [host (host-of request)
        is-json (type-of request :json)
        colls (actions/load-collections db 10)]
    (if is-json
      (json-resp colls)
      (layout host
        [:div
          (for [c colls]
            (panel 
              [:h3 (:name c)]
              [:div 
                [:p (:description c)]
                (for [item (keys (:items c))]
                  (let [el (get item (:items c))]
                    [:li (str item el)]))]))]))))


