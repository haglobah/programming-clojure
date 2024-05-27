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

; 3. Sequences

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

; 4. Functional Programming

(defn lazy-seq-fibo
  ([] (concat [0 1] (lazy-seq-fibo 0N 1N)))
  ([a b]
   (let [n (+ a b)]
     (lazy-seq
      (cons n (lazy-seq-fibo b n))))))

(defn fibo []
  (map first (iterate (fn [[a b ]] [b (+ a b)]) [0N 1N])))

(def lots-of-fibs (take 1000000000 (fibo)))

(comment
  (rem (nth (lazy-seq-fibo) 1000000) 1000)

  (take 5 (iterate (fn [[a b]] [b (+ a b)]) [0 1]))

  (nth lots-of-fibs 100) 
  :rcf)

(comment
  (partition 2 1 [:h :t :t :h :t :t]) 

  :rcf)

(declare my-even? my-odd?)

(defn my-even? [n]
  (if (= n 0)
    true
    #(my-odd? (dec n))))

(defn my-odd? [n]
  (if (= n 0)
    false
    #(my-even? (dec n))))

(comment 
  (trampoline my-even? 10000000000)
  :rcf)

; 6. Protocols and Datatypes

(defprotocol IOFactory
  "A protocol for that can be read from and written to."
  (make-reader [this] "Creates a BufferedReader")
  (make-writer [this] "Creates a BufferedWriter"))

(extend java.io.InputStream
  IOFactory
  {:make-reades (fn [_src]
                  (throw
                   (IllegalArgumentException.
                    "Can't open as an OutputStream.")))
   :make-writer (fn [dst]
                  (-> dst java.io.OutputStreamWriter. java.io.BufferedWriter.))})

(extend-type java.io.File
  IOFactory
  (make-reader [src]
    (make-reader (java.io.FileInputStream. src)))
  (make-writer [dst]
    (make-writer (java.io.FileOutputStream. dst))))

(extend-protocol IOFactory
  java.net.Socket
  (make-reader [src]
    (make-reader (.getInputStream src)))
  (make-writer [dst]
    (make-writer (.getOutputStream dst)))
  
  java.net.URL
  (make-reader [src]
    (make-reader (if (= "file" .getProtocol src)
                   (-> src (.getPath java.io.FileInputStream.))
                   (.openStream src))))
  (make-writer [dst]
    (make-writer (if (= "file" .getProtocol dst)
                   (-> dst (.getPath java.io.FileInputStream.))
                   (throw (IllegalArgumentException. "Can't write to non-file URL")))))
  )

(defn -main [& _args]
  (println (countdown [] 10)))
