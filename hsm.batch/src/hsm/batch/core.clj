(ns hsm.batch.core (:gen-class))

(declare select-values)
(declare convert)

(defn third[coll] (nth coll 3))

(defn parsevaluate [coll] 
  (reverse 
    (sort-by third 
        (map 
          #(select-values (convert %) ["name" "id" "watchers" "language" :full_name]) 
            coll))))

(defn convert[o] 
  (let [res (dissoc 
              (assoc 
                (->> o 
                  (:columns) 
                  (map #(apply hash-map (subvec % 0 2))) 
                  (apply merge)) 
                :full_name (:key o)) "")]
  ; (print res)
  (assoc res "watchers" 
    (Integer/parseInt 
      (get res "watchers")))))

(defn select-values
  "clojure.core contains select-keys 
  but not select-values."
  [m ks]
  (reduce 
    #(if-let [v (m %2)] 
        (conj %1 v) %1) 
    [] ks))




