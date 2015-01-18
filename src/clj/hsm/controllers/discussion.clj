(ns hsm.controllers.discussion
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [markdown.core :refer [md-to-html-string]]
            [cheshire.core :refer :all]
            [ring.util.response :as resp]
            [slingshot.slingshot :refer [throw+ try+]]
            [hsm.actions :as actions]
            [hsm.pipe.event :as event-pipe]
            [hsm.views :refer [layout panel panelx]]
            [hsm.ring :refer [json-resp html-resp]]
            [hsm.utils :as utils :refer [body-of host-of whois type-of id-of common-of]]))

(defn get-discussion 
  [{:keys [db event-chan redis conf]} request]
  (let [{:keys [host id body json? user]} (common-of request)
        disc-id (BigInteger. id)
        discussion (actions/load-discussion db disc-id)
        posts (actions/load-discussion-posts db disc-id)]
    (log/info "[DISC]Loading " id discussion)
    (if json?
      (json-resp discussion)
      (html-resp
          (layout host 
            (panelx
              [:h3 (:title discussion)] "" ""
              [:div.bs-callout.bs-callout-danger
                [:p (get-in discussion [:post :text])]
                [:hr]
                [:a.btn.btn-primary.btn-xs {:href "#reply-section"} [:i.fa.fa-reply] "Reply"]]
              [:hr]
               (for [p posts]
                [:div.bs-callout {:id (str "post-" (:id p))}
                    (md-to-html-string (:text p))])
              [:hr]
              [:div#reply-section.bs-callout.bs-callout-info
                [:h4 "Reply to the post"]
                [:form {:data-remote :true :data-redirect :true :action (str "/discussion/" id  "/post/create") :method :POST}
                  [:div.form-group
                    [:textarea.form-control {:name :text :rows 10 :data-provide :markdown}]]
                  [:button.btn.btn-success {:type :submit} [:i.fa.fa-reply] "Post"]]]))))))

(defn ^:private following-discussion
  [f act-name {:keys [db event-chan redis conf]} request]
  (let [{:keys [host id body json? user pl]} (common-of request)
        
        discussion-id (BigInteger. id)]
    (let [result (f db discussion-id user)]
      (event-pipe/follow-discussion act-name event-chan {:user user :id discussion-id})
      (json-resp result))))

(def follow-discussion (partial following-discussion actions/follow-discussion :follow-discussion))
(def unfollow-discussion (partial following-discussion actions/unfollow-discussion :unfollow-discussion))

(defn get-discussion-posts
  [{:keys [db event-chan redis conf]} request]
  (let [host  (host-of request)
        body (body-of request)
        id (id-of request)
        platform 1
        user (whois request)
        discussion-id (BigInteger. id)
        data (utils/mapkeyw body)]
    (let [result (actions/load-discussion-posts db discussion-id )]
      (json-resp result))))

(defn post-discussion
  [{:keys [db event-chan redis conf]} request]
  (log/warn request)
  (let [host  (host-of request)
        body (body-of request)
        platform 1
        id (id-of request)
        user (whois request)
        discussion-id (BigInteger. id)
        data (utils/mapkeyw body)]
        (log/warn host body)
    (let [result (actions/new-discussion-post db user discussion-id data)]
      (try+ 
        (event-pipe/post-discussion event-chan {:post result :discussion-id discussion-id :current-user user}))
      (json-resp {:id (str result) 
        :url (format "/discussion/%s" id )})))) ;(str result)

(defn create-discussion
  [{:keys [db event-chan redis conf]} request]
  (log/warn request)
  (let [host  (host-of request)
        body (body-of request)
        platform 1
        user (whois request)
        data (utils/mapkeyw body)]
    (let [res (actions/create-discussion db platform user data)]
      (try+ (event-pipe/create-discussion event-chan {:discussion res  :current-user user}))
      (json-resp res))))

(defn new-discussion
  [{:keys [db event-chan redis conf]} request]
  (let [host (host-of request)]
  (html-resp 
    (layout host 
      [:div.bs-callout.bs-callout-danger
        [:h4 "Start a new discussion"]
        [:p "Please search first before creating new discussion"]
        ]
        [:form {:data-remote :true :action "/discussion/create" :method :POST}
          [:div.form-group
            [:label "Question"]
            [:input.form-control {:type :text :name :title}]]
          [:div.form-group
            [:label "Explain.."]
            [:textarea.form-control {:type :text :name :post :rows 10}]]

          [:button.btn.btn-success {:type :submit} "Start Discussion"]
            ]
  ))))

(defn discussions
  [{:keys [db event-chan redis conf]} request]
  (log/warn request)
  (let [host  (host-of request)
        body (body-of request)
        id (id-of request)
        user (whois request)
        pl (id-of request :platform)
        is-json (type-of request :json)]
    (when-let [discussion-list (actions/list-top-disc db pl 50)]
      (if is-json
        (json-resp discussion-list)
        (html-resp 
          (layout host 
            [:div
              [:div.row
                (panel [:a {:href (format "/%s/discussions" pl)} "Discussions"]
                [:ul {:style "list-style-type:none;padding-left:1px;" }
                  (for [x discussion-list]
                    [:li 
                      [:a {:href (str "/discussion/" (:id x))} (:title x) 
                        [:p {:style "color:gray" } (get-in x [:post :text])]]])])]]))))))
