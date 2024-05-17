(ns programming-clojure.core
  (:require
    [clojure.string :as string])
  (:gen-class))

(defn ellipsize [words]
  (let [[w1 w2 w3 :as the-words] (string/split words #"\s+")]
    (string/join " " [w1 w2 w3 "... (" (str (count the-words))])))

(defn -main
  [& args]
  (println (str "Hello from " (string/upper-case "clojure!"))))
gs
