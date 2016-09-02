; (ns hsm.tasks.dbexport
;   (:require
;     [hsm.conf :as conf]
;     [com.stuartsierra.component :as component]
;     [clojurewerkz.cassaforte.cql  :as cql]
;     [clojure.java.jdbc :as jdbc]
;     [hsm.system :as system]
;     [hsm.system.pg :refer [pg-db]]
;     [hsm.schema :as schema]
;     [clj-kryo.core :as kryo]
;      [taoensso.timbre        :as log]
;     [cheshire.core :refer :all]
;     [hsm.server])
;   (:gen-class))
;
; (def prior-queue (java.util.ArrayList.))
;
; (defn refactor-user-data
;   [user]
;   (-> user (assoc :login (:user user))
;     (assoc :stargazers (kryo/serialize (:stargazers user)))
;     (assoc :followers (kryo/serialize (:followers user)))
;     (assoc :following (kryo/serialize (:following user)))
;     (assoc :starred (kryo/serialize (:starred user)))
;     (assoc :repos (kryo/serialize (:repos user)))
;    (dissoc :user)))
;
; (defn check-size-write-json
;   [idx n output]
;   (when (> (.size prior-queue) n)
;     (spit (format output (int (/ idx 10000))) (generate-string (vec prior-queue)))
;     (log/info "Written " n)
;     (.clear prior-queue)))
;
; (defn check-size-insert-db
;   [data-fn idx table data]
;   ; (log/info (.size prior-queue))
;     (data-fn table (map refactor-user-data data))
;     )
;
; (defn db-insert
;   ([table x]
;     (try
;       (apply (partial jdbc/insert! pg-db table) x)
;         (catch Throwable t
;           (log/error "Failed to insert " t)
;           nil
;           ) ) nil))
;
; (defn sync-data
;   [sys n-read n-write table field fnexec]
;   (let [a nil]
;     (doall
;       (map-indexed
;         (fn[idx x]
;           (.add prior-queue x)
;           (when (> (.size prior-queue) n-write)
;             (fnexec idx table (vec prior-queue))
;             (.clear prior-queue)
;             )
;           )
;         (cql/iterate-table (-> sys :db :connection) table field n-read)))
;   ))
;
; (comment
;   (sync-data sys 1000 10000 :github_project :full_name "hackersome-data/project_part%s.json")
;   (sync-data sys 100 10000 :github_project_list :proj "hackersome-data/project_list_part%s.json")
;   (sync-data sys 1000 10000 :github_user :login "hackersome-data/user_part%s.json")
;   (sync-data sys 1000 1000 :github_user_list :user "hackersome-data/user_list_part%s.json")
; )
;
;
;   ; (when (> (.size prior-queue) 0)
;   ;   (insert-recs (map #(into {} % ) (vec (.toArray prior-queue)))))
;
;
;  ; (apply (partial jdbc/insert! pg-db :github_project_list)
;  ;    (map #(merge % {"contributors" (kryo/serialize (get % "contributors"))
;  ;                    "watchers" (kryo/serialize (get % "watchers")) "stargazers" (kryo/serialize nil) }) dataset))
;
;
; (defn import-data []
;   (for [idx (range 0 150 10)]
;     (let [data (parse-string (slurp (format "hackersome-user-data/user_part%s.json" idx)))]
;       (apply (partial jdbc/insert! pg-db :github_user) data)
;       nil))
;   (for [idx (range 1 896)]
;     (let [data (parse-string (slurp (format "hackersome_data/export_part%s.json" idx)))
;           data (map #(assoc % "description" (.replace (or (:description %) "") "\u0000" "")) data)]
;       (apply (partial jdbc/insert! pg-db :github_project) data)
;       nil
;       )))
;
;
;
; (defn merge-users []
;   (doseq [idx (range 1 226)]
;     (let [data (parse-string (slurp (format "hackersome-data/user_list_part%s.json" idx)) true)
;           data (map refactor-user-data data)
;          data (vec (set (vals (apply merge (map #(hash-map (:login %) %) data)))))]
;       (doall (map db-insert data))
;       nil
;       )))
;
; (defn -main
;   [& args]
;   ; (let [{:keys [conf] :or {conf "app.ini"}} {:conf "app.ini"}
;   ;       c (conf/parse-conf conf true)
;   ;       sys (component/start (system/db-system {
;   ;                                   :host (:db-host c)
;   ;                                   :port (:db-port c)
;   ;                                   :keyspace (:db-keyspace c)}))]
;
;   ; )
;
;   ; (let [sys (hsm.server/startup {})]
;     (merge-users)
;   ; )
;
;
;
;
;
;
;
; )
