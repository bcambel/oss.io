(ns fe.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

(defonce app-state (atom {:text "Hello Chestnut!" :list ["Andrii" "Erik" "Bahadir"]
:contacts
     [{:first "Ben" :last "Bitdiddle" :email "benb@mit.edu"}
      {:first "Alyssa" :middle-initial "P" :last "Hacker" :email "aphacker@mit.edu"}
      {:first "Eva" :middle "Lu" :last "Ator" :email "eval@mit.edu"}
      {:first "Louis" :last "Reasoner" :email "prolog@mit.edu"}
      {:first "Cy" :middle-initial "D" :last "Effect" :email "bugs@mit.edu"}
      {:first "Lem" :middle-initial "E" :last "Tweakit" :email "morebugs@mit.edu"}]
  }))


; (defn contacts-view [app owner]
;   (reify
;     om/IRender
;     (render [this]
;       (dom/div nil
;         (dom/h2 nil "Contact list")
;         (apply dom/ul nil
;           (om/build-all contact-view (:contacts app)))))))

(defn main []
(om/root
  (fn [app owner]
    (om/component 
      ; (dom/h3 nil (:text app))
      (apply dom/ul #js {:className "animals"}
        (map (fn[text] (dom/li nil text)) (:list app))
        )
      ))
  app-state
  {:target (. js/document (getElementById "app"))}))

; (swap! app-state assoc :text "Multiple roots!")
; (defn main []
;   (om/root
;     (fn [app owner]
;       (om/component (dom/h2 nil (:text app))))
;       ; (reify
;         ; om/IRender
;         ; (render [_]
;           ; (dom/h1 nil (:text app))
;           ; (dom/h2 "Setting" (:text app))
;           ))
;     app-state
;     {:target (. js/document (getElementById "app"))}))
