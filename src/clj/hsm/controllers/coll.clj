(ns hsm.controllers.coll
  (:require 
    [cheshire.core :refer :all]
    [clojure.tools.logging :as log]
    [hiccup.def                   :refer [defhtml]]
    [hsm.actions :as actions]
    [hsm.views :refer [layout panel render-user left-menu]]
    [hsm.ring :refer [json-resp html-resp redirect]]
    [hsm.utils :refer :all]))

(defn add-p-coll
  [{:keys [db event-chan redis conf]} request]
  (let [host (host-of request)
        is-json (type-of request :json)
        id (BigInteger. (id-of request))
        body (body-of request)
        project-form-param (get (:form-params request) "project")
        project-to-add (if-not (nil? project-form-param) project-form-param (get body "project"))
        coll (first (actions/get-collection db id))]
    (log/warn (:form-params request))
    (let [items (or (:items coll) {})
          new-items (assoc items project-to-add "1")]
      (log/debug "Before" items)
      (log/debug "After" new-items)
      (actions/update-collection db id new-items)
      (redirect (format "/collections/%s%s" id (if is-json "?json=1" ""))))))

(defn rm-coll
  [{:keys [db event-chan redis conf]} request]
  (let [host (host-of request)
        is-json (type-of request :json)
        id (BigInteger. (id-of request))]
    (actions/delete-collection db id)
    (if is-json 
      (json-resp {:ok 1})
      (redirect "/collections" ))
  ))

(defn del-p-coll
  [{:keys [db event-chan redis conf]} request]
  (let [host (host-of request)
        is-json (type-of request :json)
        id (BigInteger. (id-of request))
        body (body-of request)
        project-form-param (get (:form-params request) "project")
        project-to-rm (if-not (nil? project-form-param) project-form-param (get body "project"))
        coll (first (actions/get-collection db id))]
    (log/warn (:form-params request))
    (let [items (:items coll)
          new-items (dissoc items project-to-rm )]
      (log/debug "Before" items)
      (log/debug "After" new-items)
      (actions/update-collection db id new-items)
      (redirect (format "/collections/%s%s" id (if is-json "?json=1" ""))))))

(def submit-form "$(this).parent('form').submit();return false;")

(defhtml render-collection
  [c]
    [:div.panel.panel-default
      [:div.panel-heading
        [:h3 {:style "display:inline;"}
          [:a {:href (str "/collections/" (:id c))}(:name c)]
        [:div.button-group.pull-right.actions
          [:form { :action (str "/collections/" (:id c) "/star") :data-remote "true"  :method "POST" }
            [:a.gh-btn {:href "#" :onclick submit-form}
              [:i.fa.fa-star] " Star "]]

          [:a.gh-count {:style "display:block" :href (str "/collections/" (:id c) "/stargazers")} (:stargazers c)]
          [:form { :action (str "/collections/" (:id c) "/fork") :data-remote "true" :data-redirect :true :method "POST" }
            [:a.gh-btn {:href "#" :onclick submit-form}
              [:i.fa.fa-code-fork]
              [:span.gh-text "Fork"]]]
          [:a.gh-count {:style "display:block" :href (str "/collections/" (:id c) "/forks")} (:forks c)]]]]

      [:div.panel-body
        [:p (:description c)]
        (for [item (keys (:items c))]
          (let [el (get item (:items c))]
            [:div.row.coll-row
              [:h4 
                [:a.pull-left {:href (str "/p/" (str item el))} (str item el)]]
              [:form {:method "POST" :action (format "/collections/%s/delete" (:id c))}
                [:input {:type "hidden" :name :project :value item}]
                [:a {:href "#" :onclick submit-form }
                  [:i.fa.fa-minus-circle.red]]]]))]
        [:div.panel-footer
          [:a.green {:href "#" :onclick "$(this).parent().find('form').toggle()"} "Add New"]
          [:form {:method "POST" :action (format "/collections/%s/add" (:id c)) :style "display:none;"}
            [:div#remote 
              [:input.typeahead {:type "text" :name :project :placeholder "Type to find project"}]]
              [:a.btn.btn-default {:href "#" :rel "nofollow" :onclick submit-form} "Add"]]

          [:a.red.pull-right {:href (format "/collections/%s/rm" (:id c)) :rel "nofollow" } "Delete"]
              ]])

(defn get-coll
  [{:keys [db event-chan redis conf]} request]
  (let [host (host-of request)
        is-json? (type-of request :json)
        id (BigInteger. (id-of request))
        coll (first (actions/get-collection db id))
        coll-extra (actions/get-collection-extra db id)
        coll (merge coll {:stargazers (count (:stargazers coll-extra)) :forks  (count (:forks coll-extra))})
        coll-followers (partial actions/load-users-by-id db) ]
    (log/warn coll-extra)
    (if is-json?
      (json-resp coll)
      (layout host
        (render-collection coll)
        [:hr]
        (let [stargazers (coll-followers (vec (:stargazers coll-extra)))]
          (panel "Stargazers"
            [:div.row.user-list 
              (for [x (reverse (sort-by :followers stargazers))]
                [:div.col-lg-3.user-thumb
                  (render-user x)])]))
        ))))
  

(defn create-coll
  [{:keys [db event-chan redis conf]} request]
  (let [body (body-of request)
        data (select-keys (mapkeyw body) [:name])
        new-id (id-generate)]
    (actions/create-collection db (merge {:id new-id} data))
    (json-resp {:id (str new-id) 
                :url (format "/collections/%s" new-id) } )))

(defn find-extra-of
  [coll-extras x]
  (let [candidate (first (filter #(= (:id %) (:id x)) coll-extras))
        data (or candidate { :stargazers #{} :forks #{}})]

    (merge x {:stargazers (count (:stargazers data)) 
              :forks (count (:forks data))})))

(defn load-coll
  [{:keys [db event-chan redis conf]} request]
  (let [host (host-of request)
        is-json (type-of request :json)
        hosted-pl     (host->pl->lang host)
        platform      (or (or hosted-pl (pl->lang (id-of request :platform)) ) "Python")
        colls (actions/load-collections db 10)
        coll-extras (actions/get-collection-extras-by-id db (map :id colls))]
    (let [colls (map (partial find-extra-of coll-extras) colls)]
      (log/warn colls)
      (if is-json
        (json-resp colls)
        (layout host
          [:div.row
            [:div.col-lg-3 
              (left-menu host platform "collections")
            ]
            [:div.col-lg-9
              [:div.jumbotron
                [:h3 "Create a List/Collection to group your projects together "]
                [:form {:action "/collections/create" :method "POST" :data-remote "true" :data-redirect "true" :id :create-coll}
                  [:div.form-group
                  [:input.form-control {:type :text :name :name }]
                  [:input {:type :hidden :name :test :value 1}]]
                  [:button.btn.btn-primary {:type :submit} "Create"]]]
              [:div.row
                (for [c colls]
                  [:div.col-lg-6
                    (render-collection c)])]]])))))

(defn star-coll
  [{:keys [db event-chan redis conf]} request]
  (let [host (host-of request)
        is-json? (type-of request :json)
        id (BigInteger. (id-of request))
        user-id (str (whois request))
        coll (first (actions/get-collection db id))
        user-set #{user-id}]
    (log/warn "[STAR]" id user-id user-set)
    (when (!nil? coll)
      (actions/star-collection db id user-set))
    (json-resp {:ok 1})
  ))

(defn fork-coll
  [{:keys [db event-chan redis conf]} request]
  (let [host (host-of request)
        is-json? (type-of request :json)
        id (BigInteger. (id-of request))
        user-id (whois request)]
    (if-let [coll (first (actions/get-collection db id))]
      (let [new-id (id-generate)
            new-coll (merge coll {:id new-id :user_id user-id})]
        (actions/create-collection db new-coll)
        (actions/add-collection-fork db id new-id)
        (json-resp {:id new-id :url (str "/collections/" new-id) }))
      (json-resp {:ok 1}))))


(defn coll-stargazers
  [{:keys [db event-chan redis conf]} request])

(defn coll-forks
  [{:keys [db event-chan redis conf]} request])

