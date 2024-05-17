(ns prog.core
  (:require
    [clojure.string :as string])
  (:gen-class))

(defn ellipsize [words]
  (let [[w u v :as the-words] (string/split words #"\s+")]
    (string/join " " [w u v "... (" (str (count the-words)) "words)"])))

(defn -main
  [& args]
  (println (str "Hello from " (string/upper-case "clojure!"))))
