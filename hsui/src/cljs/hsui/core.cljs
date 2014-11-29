(ns hsui.core
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-http.client :as http]
            [cognitect.transit :as t]
            [goog.net.XhrIo :as xhr]
            [cljs.core.async :as async :refer [put! chan <! timeout]])
  (:import [goog.ui IdGenerator]))

(enable-console-print!)

(defonce app-state (atom {:text "Hello Chestnut!" :links []}))


(defn parse-link [link-text] link-text)

(defn link-view 
  "Render a single link view"
  [link owner]
  ;(.log js/console link)
  (let [{:keys [url shares title score author]} link]
  (reify
    om/IRenderState
    (render-state [this {:keys [delete]}]
      (dom/li nil
              (dom/span shares)
              (dom/a #js {:href url } title)
              (dom/button #js {:onClick (fn [e] (put! delete @link))} "Delete"))))))


(defn add-link 
  "Adda new link to the container"
  [app owner]
  (let [input (om/get-node owner "new-link")
        new-link (-> input
                     .-value
                     parse-link)]
    (when new-link
      (om/transact! app :links #(conj % new-link))
      (set! (.-value input) ""))))

(defn handle-change
  [evt owner state]
  (om/set-state! owner :text (.. evt -target -value)))

(defn guid []
  (-> IdGenerator
      .getInstance
      .getNextUniqueId))

(defn- with-id
  [m]
  (assoc m :id (guid)))

(def r (t/reader :json))

(defn GetAjax [url]
  (let [ch (chan 1)]
    (xhr/send url
              (fn [event]
                (let [res (-> event .-target .getResponseText)]
                  (put! ch res)
                      (close! ch))))
    ch))

(defn fetch-links 
  [cursor {:keys [url] :as opts}]
  (go (let [response (<! (http/get url))
            ;status (:status response)
            ;r (t/reader :json)
            ;links (t/read r (:body response))
            links (:body response)
            ;link-data  links;(mapv with-id (map #(hash-map :link %) links))
            ]
        (.log js/console response)
        (.log js/console links)
        ;(.log js/console (t/read r links))
        ;(.log js/console (jsObj links))
        (om/update! cursor [:links] links))))

(defn link-list
  [cursor owner opts]
  (.log js/console (:links cursor))
  (om/component
   (dom/div #js {:className "Link List"}
            (into-array
             (om/build-all link-view (:links cursor) { :opts opts}))))) 

(defn link-list-view
  [app owner {:keys [poll-interval] :as opts}]
  (reify
    om/IInitState
    (init-state [_]
      {:delete (chan) :text ""})
    om/IWillMount
    (will-mount [_]
      (go (while true 
            (fetch-links app opts)
            (<! (timeout poll-interval)))))
    om/IRenderState
    (render-state [this state]
      (dom/div nil
               (dom/h2 nil "Link list")
               (om/build link-list app)
               (dom/div nil
                        (dom/input #js {:type "text" :ref "new-link" :value (:text state)
                                        :onChange #(handle-change % owner state)})
                        (dom/button #js {:type "button" :onClick #(add-link app owner) :className "btn btn-default"} "Add links"))))))

(defn link-app
  [cursor owner]
  (reify 
    om/IRender
    (render [this]
      (dom/div #js { :className "container" }
               (om/build link-list-view cursor {:opts {:poll-interval 30000
                                                       :url "/links" }})))))

(defn main []
  (om/root
   link-app
    app-state
    {:target (. js/document (getElementById "app"))}))

(defn swap-title
  []
  (swap! app-state assoc :text "testing"))

;(.setTimeout js/window swap-title 1000)

