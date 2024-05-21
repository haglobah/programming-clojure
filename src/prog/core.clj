(ns prog.core
  (:require
   [clojure.string :as string]) 
  (:use
   [clojure.java.io :only (reader)])
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

(defn minutes-to-millis [mins] (* mins 1000 60))

(defn recently-modified? [file]
  (> (.lastModified file)
     (- (System/currentTimeMillis) (minutes-to-millis 30))))

(defn non-blank? [line] (not (string/blank? line)))
(defn non-git? [file] (not (.contains (.toString (.getName file)) ".git")))
(defn clj-source? [file] (.endsWith (.toString (.getName file)) ".clj"))

(defn clojure-loc [base-file]
  (reduce
   +
   (for [file (file-seq base-file) :when (and (non-git? file) (clj-source? file))]
     (with-open [rdr (reader file)]
       (count (filter non-blank? (line-seq rdr)))))))

(defn -main [& _args]
  (println (countdown [] 10)))
