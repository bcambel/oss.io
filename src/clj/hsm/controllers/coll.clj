(ns hsm.controllers.coll
  (:require 
    [hsm.actions :as actions]
    [hsm.views :refer [layout panel]]
    [hsm.ring :refer [json-resp html-resp]]
    [hsm.utils :refer [type-of id-of host-of]]))

(defn add-p-coll
  [{:keys [db event-chan redis conf]} request])

(defn del-p-coll
  [{:keys [db event-chan redis conf]} request])

(defn get-coll
  [{:keys [db event-chan redis conf]} request])

(defn create-coll
  [{:keys [db event-chan redis conf]} request] 
  )
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
            [:div.col-lg-3
            (panel 
              [:h3 (:name c)]
              [:div 
                [:p (:description c)]
                (for [item (keys (:items c))]
                  (let [el (get item (:items c))]
                    [:li [:a {:href (str "/p/" (str item el))} (str item el)]]))])])]))))


