(ns konserve-couchdb.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.core.async :refer [<!!] :as async]
            [konserve.core :as k]
            [konserve.storage-layout :as kl]
            [com.ashafa.clutch :as cl]
            [konserve-couchdb.core :refer [new-couchdb-store delete-store]]
            [malli.generator :as mg]))

(deftype UnknownType [])

(defn exception? [thing]
  (instance? Throwable thing))

(def username "admin")
(def password "password")

(deftest get-nil-test
  (testing "Test getting on empty store"
    (let [_ (println "Getting from an empty store")
          db "nil-test"
          conn (str "http://" username ":" password "@localhost:5984/" db)
          store (<!! (new-couchdb-store conn))]
      (is (= nil (<!! (k/get store :foo))))
      (is (= nil (<!! (k/get-meta store :foo))))
      (is (not (<!! (k/exists? store :foo))))
      (is (= :default (<!! (k/get-in store [:fuu] :default))))
      (<!! (k/bget store :foo (fn [res] 
                                (is (nil? res))))))))

(deftest write-value-test
  (testing "Test writing to store"
    (let [_ (println "Writing to store")
          db "write-test"
          conn (str "http://" username ":" password "@localhost:5984/" db)
          store (<!! (new-couchdb-store (cl/get-database conn)))]
      (is (not (<!! (k/exists? store :foo))))
      (<!! (k/assoc store :foo :bar))
      (is (<!! (k/exists? store :foo)))
      (is (= :bar (<!! (k/get store :foo))))
      (is (= :foo (:key (<!! (k/get-meta store :foo)))))
      (<!! (k/assoc-in store [:baz] {:bar 42}))
      (is (= 42 (<!! (k/get-in store [:baz :bar]))))
      (delete-store store))))

(deftest update-value-test
  (testing "Test updating values in the store"
    (let [_ (println "Updating values in the store")
          db "update-test"
          conn (str "http://" username ":" password "@localhost:5984/" db)
          store (<!! (new-couchdb-store conn))]
      (<!! (k/assoc store :foo :baritone))
      (is (= :baritone (<!! (k/get-in store [:foo]))))
      (<!! (k/update-in store [:foo] name))
      (is (= "baritone" (<!! (k/get-in store [:foo]))))
      (delete-store store))))

(deftest exists-test
  (testing "Test check for existing key in the store"
    (let [_ (println "Checking if keys exist")
          db "exists-test"
          conn (str "http://" username ":" password "@localhost:5984/" db)
          store (<!! (new-couchdb-store conn))]
      (is (not (<!! (k/exists? store :foo))))
      (<!! (k/assoc store :foo :baritone))
      (is  (<!! (k/exists? store :foo)))
      (<!! (k/dissoc store :foo))
      (is (not (<!! (k/exists? store :foo))))
      (delete-store store))))

(deftest binary-test
  (testing "Test writing binary date"
    (let [_ (println "Reading and writing binary data")
          db "binary-test"
          conn (str "http://" username ":" password "@localhost:5984/" db)
          store (<!! (new-couchdb-store conn))
          cb (atom false)
          cb2 (atom false)]
      (is (not (<!! (k/exists? store :binbar))))
      (<!! (k/bget store :binbar (fn [ans] (is (nil? (:input-stream ans))))))
      (<!! (k/bassoc store :binbar (byte-array (range 30))))
      (<!! (k/bget store :binbar (fn [res]
                                    (reset! cb true)
                                    (is (= (map byte (slurp (:input-stream res)))
                                           (range 30))))))
      (<!! (k/bassoc store :binbar (byte-array (map inc (range 30))))) 
      (<!! (k/bget store :binbar (fn [res]
                                    (reset! cb2 true)
                                    (is (= (map byte (slurp (:input-stream res)))
                                           (map inc (range 30)))))))                                          
      (is (<!! (k/exists? store :binbar)))
      (is @cb)
      (is @cb2)
      (delete-store store))))
  
(deftest key-test
  (testing "Test getting keys from the store"
    (let [_ (println "Getting keys from store")
          db "key-test"
          conn (str "http://" username ":" password "@localhost:5984/" db)
          store (<!! (new-couchdb-store conn))]
      (is (= #{} (<!! (async/into #{} (k/keys store)))))
      (<!! (k/assoc store :baz 20))
      (<!! (k/assoc store :binbar 20))
      (is (= #{:baz :binbar} (<!! (async/into #{} (k/keys store)))))
      (delete-store store))))  

(deftest append-test
  (testing "Test the append store functionality."
    (let [_ (println "Appending to store")
          db "append-test"
          conn (str "http://" username ":" password "@localhost:5984/" db)
          store (<!! (new-couchdb-store conn))]
      (<!! (k/append store :foo {:bar 42}))
      (<!! (k/append store :foo {:bar 43}))
      (is (= (<!! (k/log store :foo))
             '({:bar 42}{:bar 43})))
      (is (= (<!! (k/reduce-log store
                              :foo
                              (fn [acc elem]
                                (conj acc elem))
                              []))
             [{:bar 42} {:bar 43}]))
      (delete-store store))))

(deftest invalid-store-test
  (testing "Invalid store functionality."
    (let [_ (println "Connecting to invalid store")
          db "invalid-test"
          conn1 (str "http://error:error@localhost:5984/" db)
          conn2 (str "http://" username ":" password "@localhost:5984/" db)
          store (<!! (new-couchdb-store conn1))
          store2 (<!! (new-couchdb-store conn2))]
      (is (exception? store))
      (is (not (exception? store2))))))


(def home
  [:map
    [:name string?]
    [:description string?]
    [:rooms pos-int?]
    [:capacity float?]
    [:address
      [:map
        [:street string?]
        [:number int?]
        [:country [:enum "kenya" "lesotho" "south-africa" "italy" "mozambique" "spain" "india" "brazil" "usa" "germany"]]]]])

(deftest realistic-test
  (testing "Realistic data test."
    (let [_ (println "Entering realistic data")
          db "realistic-test"
          conn (str "http://" username ":" password "@localhost:5984/" db)
          store (<!! (new-couchdb-store conn))
          home (mg/generate home {:size 20 :seed 2})
          address (:address home)
          addressless (dissoc home :address)
          name (mg/generate keyword? {:size 15 :seed 3})
          num1 (mg/generate pos-int? {:size 5 :seed 4})
          num2 (mg/generate pos-int? {:size 5 :seed 5})
          floater (mg/generate float? {:size 5 :seed 6})]
      
      (<!! (k/assoc store name addressless))
      (is (= addressless 
             (<!! (k/get store name))))

      (<!! (k/assoc-in store [name :address] address))
      (is (= home 
             (<!! (k/get store name))))

      (<!! (k/update-in store [name :capacity] * floater))
      (is (= (* floater (:capacity home)) 
             (<!! (k/get-in store [name :capacity]))))  

      (<!! (k/update-in store [name :address :number] + num1 num2))
      (is (= (+ num1 num2 (:number address)) 
             (<!! (k/get-in store [name :address :number]))))             
      
      (delete-store store))))   

(deftest bulk-test
  (testing "Bulk data test."
    (let [_ (println "Writing bulk data")
          db "bulk-test"
          conn (str "http://" username ":" password "@localhost:5984/" db)
          store (<!! (new-couchdb-store conn))
          string8MB (apply str (repeat 8000000 "7"))
          range2MB 2097152
          sevens (repeat range2MB 7)]
      (print "\nWriting 8MB string: ")
      (time (<!! (k/assoc store :record string8MB)))
      (is (= (count string8MB) (count (<!! (k/get store :record)))))
      (print "Writing 2MB binary: ")
      (time (<!! (k/bassoc store :binary (byte-array sevens))))
      (<!! (k/bget store :binary (fn [{:keys [input-stream]}]
                                    (is (= (pmap byte (slurp input-stream))
                                           sevens)))))
      (delete-store store))))  

(deftest raw-meta-test
  (testing "Test header storage"
    (let [_ (println "Checking if headers are stored correctly")
          db "raw-meta-test"
          conn (str "http://" username ":" password "@localhost:5984/" db)
          store (<!! (new-couchdb-store conn))]
      (<!! (k/assoc store :foo :bar))
      (<!! (k/assoc store :eye :ear))
      (let [mraw (<!! (kl/-get-raw-meta store :foo))
            mraw2 (<!! (kl/-get-raw-meta store :eye))
            mraw3 (<!! (kl/-get-raw-meta store :not-there))
            header (take 4 (map byte mraw))]
        (<!! (kl/-put-raw-meta store :foo mraw2))
        (<!! (kl/-put-raw-meta store :baritone mraw2))
        (is (= header [1 1 1 0]))
        (is (nil? mraw3))
        (is (= :eye (:key (<!! (k/get-meta store :foo)))))
        (is (= :eye (:key (<!! (k/get-meta store :baritone))))))        
      (delete-store store))))          

(deftest raw-value-test
  (testing "Test value storage"
    (let [_ (println "Checking if values are stored correctly")
          db "raw-value-test"
          conn (str "http://" username ":" password "@localhost:5984/" db)
          store (<!! (new-couchdb-store conn))]
      (<!! (k/assoc store :foo :bar))
      (<!! (k/assoc store :eye :ear))
      (let [vraw (<!! (kl/-get-raw-value store :foo))
            vraw2 (<!! (kl/-get-raw-value store :eye))
            vraw3 (<!! (kl/-get-raw-value store :not-there))
            header (take 4 (map byte vraw))]
        (<!! (kl/-put-raw-value store :foo vraw2))
        (<!! (kl/-put-raw-value store :baritone vraw2))
        (is (= header [1 1 1 0]))
        (is (nil? vraw3))
        (is (= :ear (<!! (k/get store :foo))))
        (is (= :ear (<!! (k/get store :baritone)))))      
      (delete-store store))))   

(deftest exceptions-test
  (testing "Test exception handling"
    (let [_ (println "Generating exceptions")
          db "exceptions-test"
          conn (str "http://" username ":" password "@localhost:5984/" db)
          store (<!! (new-couchdb-store conn))
          params (clojure.core/keys store)
          corruptor (fn [s k] 
                        (if (= (type (k s)) clojure.lang.Atom)
                          (clojure.core/assoc-in s [k] (atom {})) 
                          (clojure.core/assoc-in s [k] (UnknownType.))))
          corrupt (reduce corruptor store params)] ; let's corrupt our store
      (is (exception? (<!! (new-couchdb-store (UnknownType.)))))
      (is (exception? (<!! (k/get corrupt :bad))))
      (is (exception? (<!! (k/get-meta corrupt :bad))))
      (is (exception? (<!! (k/assoc corrupt :bad 10))))
      (is (exception? (<!! (k/dissoc corrupt :bad))))
      (is (exception? (<!! (k/assoc-in corrupt [:bad :robot] 10))))
      (is (exception? (<!! (k/update-in corrupt [:bad :robot] inc))))
      (is (exception? (<!! (k/exists? corrupt :bad))))
      (is (exception? (<!! (k/keys corrupt))))
      (is (exception? (<!! (k/bget corrupt :bad (fn [_] nil)))))   
      (is (exception? (<!! (k/bassoc corrupt :binbar (byte-array (range 10))))))
      (is (exception? (<!! (kl/-get-raw-value corrupt :bad))))
      (is (exception? (<!! (kl/-put-raw-value corrupt :bad (byte-array (range 10))))))
      (is (exception? (<!! (kl/-get-raw-meta corrupt :bad))))
      (is (exception? (<!! (kl/-put-raw-meta corrupt :bad (byte-array (range 10))))))
      (is (exception? (<!! (delete-store corrupt))))
      (delete-store store))))