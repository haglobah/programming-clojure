(ns prog.crypto-vault
  (:require [prog.core :as core]
            [clojure.java.io :as io])
  (:import (java.security KeyStore KeyStore$SecretKeyEntry KeyStore$PasswordProtection)
           (javax.crypto Cipher KeyGenerator CipherOutputStream CipherInputStream)
           (java.io FileInputStream FileOutputStream)))

(defprotocol Vault
  (init-vault [vault])
  (vault-output-stream [vault])
  (vault-input-stream [vault]))
(defn vault-key [vault]
  (let [password (.toCharArray (.password vault))]
    (with-open [fis (FileInputStream. (.keystore vault))]
      (-> (doto (KeyStore/getInstance "JCEKS")
            (.load fis password))))))
(deftype CryptoVault [filename keystore password]
  Vault
  (init-vault [vault]
    (let [password (.toCharArray (.password vault))
          key (.generateKey (KeyGenerator/getInstance "AES"))
          keystore (doto (KeyStore/getInstance "JCEKS")
                     (.load nil password)
                     (.setEntry "vault-key"
                                (KeyStore$SecretKeyEntry. key)
                                (KeyStore$PasswordProtection. password)))]
      (with-open [fos (FileOutputStream. (.keystore vault))]
        (.store keystore fos password))))
  (vault-output-stream [vault]
    (let [cipher (doto (Cipher/getInstance "AES")
                   (.init Cipher/ENCRYPT_MODE (vault-key vault)))]
      (CipherOutputStream. (io/output-stream (.filename vault)) cipher)))
  (vault-input-stream [vault]
    (let [cipher (doto (Cipher/getInstance "AES")
                   (.init Cipher/DECRYPT_MODE (vault-key vault)))]
      (CipherInputStream. (io/input-stream (.filename vault)) cipher)))
  
  core/IOFactory
  (make-reader [vault]
               (core/make-reader (vault-input-stream vault)))
  (make-writer [vault]
               (core/make-writer (vault-output-stream vault))))

(extend CryptoVault
  clojure.java.io/IOFactory
  (assoc io/default-streams-impl
         :make-input-stream (fn [x _opts] (vault-input-stream x))
         :make-output-stream (fn [x _opts] (vault-output-stream x))))