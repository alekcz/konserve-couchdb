(ns konserve-couchdb.io
  "IO function for interacting with database"
  (:require [com.ashafa.clutch :as cl])
  (:import  [java.util Base64 Base64$Decoder Base64$Encoder]
            [java.io ByteArrayInputStream]))

(set! *warn-on-reflection* 1)

(def ^Base64$Encoder b64encoder (. Base64 getEncoder))
(def ^Base64$Decoder b64decoder (. Base64 getDecoder))

(defn split-header [^"[B" bytes]
  (when bytes
    (let [data  (->> bytes vec (split-at 4))
          streamer (fn [header data] (list (byte-array ^"[B" header) (-> data byte-array (ByteArrayInputStream.))))]
      (apply streamer data))))

(defn prep-write
  [id data]
  (when data
    (let [[meta val] data]
      {:_id id
       :meta (.encodeToString b64encoder ^"[B" meta)
       :data (.encodeToString b64encoder ^"[B" val)})))

(defn decode [^String data']
  (when data' (.decode b64decoder ^String data')))

(defn it-exists? 
  [db id]
  (cl/document-exists? db id))
  
(defn get-it 
  [db id]
  (let [doc (cl/get-document db id)
        meta (:meta doc)
        data (:data doc)]
    [(-> meta decode split-header) (-> data decode split-header)]))

(defn get-it-only
  [db id]
  (-> (cl/get-document db id) :data decode split-header))

(defn get-meta
  [db id]
  (-> (cl/get-document db id) :meta decode split-header))

(defn delete-it 
  [db id]
  (let [doc (cl/get-document db id)]
    (when doc (cl/delete-document db doc))))

(defn update-it 
  [db id data]
  (let [doc (prep-write id data)]
    (delete-it db id)
    (cl/put-document db doc)))

(defn get-keys 
  [db]
  (let [res (cl/all-documents db)]
    (map #(get-meta db (:id %)) res)))

(defn raw-get-it-only 
  [db id]
  (-> (cl/get-document db id) :data decode))

(defn raw-get-meta 
  [db id]
  (-> (cl/get-document db id) :meta decode))
  
(defn raw-update-it-only 
  [db id data]
  (let [existing (into {} (cl/get-document db id))
        doc (assoc existing 
              :_id id
              :meta (:meta existing)
              :data (.encodeToString b64encoder ^"[B" data))]
    (cl/put-document db doc)))

(defn raw-update-meta
  [db id meta]
  (let [existing (into {} (cl/get-document db id))
        doc (assoc existing 
              :_id id
              :meta (.encodeToString b64encoder ^"[B" meta)
              :data (:data existing))]
    (cl/put-document db doc)))
