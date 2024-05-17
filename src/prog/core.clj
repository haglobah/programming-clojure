(ns prog.core
  (:require
    [clojure.string :as string])
  (:gen-class))

(defn ellipsize [words]
  (let [[w u v :as the-words] (string/split words #"\s+")]
    (string/join " " [w u v "... (" (str (count the-words)) "words)"])))

(defn countdown [result x]
  (if (zero? x)
    result
    (countdown (conj result x) (dec x))))

(defn indexed [coll] (map-indexed vector coll))

(defn index-filter [pred coll]
  (when pred
    (for [[idx el] (indexed coll) :when (pred el)] idx)))

(defn index-of-any [pred coll]
  (first (index-filter pred coll)))

(defn -main [& _args]
  (println (countdown [] 10)))
