(ns hsm.tasks.dbexport
  (:require
    [hsm.conf :as conf]
    [com.stuartsierra.component :as component]
    [hsm.system :as system]
    [hsm.schema :as schema]
    [cheshire.core :refer :all])
  (:gen-class))

(def prior-queue (java.util.ArrayList.))

(defn check-size-write-json
  [idx n output]
  (when (> (.size prior-queue) n)
    (spit (format output (int (/ idx 10000))) (generate-string (vec prior-queue)))
    (log/info "Written " n)
    (.clear prior-queue)))

(defn check-size [n]
  (when (> (.size prior-queue) n)
        (insert-recs (vec prior-queue))
        (log/info "Written " n)
        (.clear prior-queue)))

(defn sync-data
  [sys n-read n-write table field output ]
  (doall (map-indexed
          (fn[idx x]
            (.add prior-queue x)
            (check-size-write-json idx n-write output))
       (cql/iterate-table (-> sys :db :connection) table field n-read)))
  (check-size-write-json -1 0 output))

(comment
  (sync-data sys 1000 10000 :github_project :full_name "hackersome-data/project_part%s.json")
  (sync-data sys 100 10000 :github_project_list :proj "hackersome-data/project_list_part%s.json")
  (sync-data sys 1000 10000 :github_user :login "hackersome-data/user_part%s.json")
  (sync-data sys 1000 10000 :github_user_list :login "hackersome-data/user_list_part%s.json")
)
  ; (when (> (.size prior-queue) 0)
  ;   (insert-recs (map #(into {} % ) (vec (.toArray prior-queue)))))



(defn import-data []
  (for [idx (range 0 150 10)]
    (let [data (parse-string (slurp (format "hackersome-user-data/user_part%s.json" idx)))]
      (apply (partial jdbc/insert! pg-db :github_user) data)
      nil))
  (for [idx (range 1 896)]
    (let [data (parse-string (slurp (format "hackersome_data/export_part%s.json" idx)))
          data (map #(assoc % "description" (.replace (or (:description %) "") "\u0000" "")) data)]
      (apply (partial j/insert! pg-db :github_project) data)
      nil
      )))

(defn -main
  [& args]
  (let [{:keys [conf] :or {conf "app.ini"}} {:conf "app.ini"}
        c (conf/parse-conf conf true)
        sys (component/start (system/db-system {
                                    :host (:db-host c)
                                    :port (:db-port c)
                                    :keyspace (:db-keyspace c)}))]

  ))