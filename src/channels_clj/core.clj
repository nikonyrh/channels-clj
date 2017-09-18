(ns channels-clj.core
  (:require [clojure.core.async          :as async :refer [>! <! >!! <!! go go-loop chan buffer close! put! thread alts! alts!! timeout]]
            [java-time                   :as t]
            [nikonyrh-utilities-clj.core :as u]
            [taoensso.carmine            :as car])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def redis-source-id (str (java.util.UUID/randomUUID))) ; Used to identify Redis messages which were created by this process
(def redis-defaults {:host "127.0.0.1" :port 6379})     ; Makes local development a bit easier

(defn conj-window [self width i] ; vec has O(1) conj and subv opearations :)
  (let [self (conj self i)
        from (-> self count (- width) (max 0))]
    (if (pos? from) (subvec self from) self)))

(defmacro defchan [sym args body & [privates publics close]]
  (let [close (cond (some? close) close
                    (-> args last (= 'out))  '(->> out :chan close!)
                    (-> args last (= 'outs)) '(->> out :chan close! (doseq [out outs])))
        body (if (-> body first (= 'do))     body (list 'do body))
        body (if (-> args last  (not= 'out)) body (concat (butlast body) [`(when-let [i# ~(last body)] (>! (:chan ~'out) i#))]))]
    `(defn ~sym ~args (let [in# (chan 1) ~@privates]
                        (go-loop [] (if-let [~'i (<! in#)] ~(concat body ['(recur)]) ~close))
                        (hash-map :chan in# ~@publics)))))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defchan sink           []             ())
(defchan map-pipe       [f out]        (->> i f))
(defchan window-pipe    [width out]    (->> i (swap! self conj-window width)) [self (atom [])])
(defchan multicast-pipe [& outs]       (->> i (>! (:chan out)) (doseq [out outs])))
(defchan atom-sink      [value]        (->> i (reset! value)) [value (if (instance? clojure.lang.Atom value) value (atom value))] [:atom value])
(defchan redis-sink     [key & [spec]] (->> i (hash-map :redis-source-id redis-source-id :value) (car/publish key) (car/wcar conn))
  [conn {:pool {} :spec (merge redis-defaults spec)}])

(defn redis-source [key spec out]
  (let [listener (car/with-new-pubsub-listener (merge redis-defaults spec)
                   {key (fn [[msg-type _ data]] (when (= msg-type "message") (-> out :chan (>!! data))))} (car/subscribe key))]
    {:close (fn [] (car/close-listener listener) (-> out :chan close!))}))

(defmacro pipe-> [& forms] (->> forms reverse (reduce (fn [result form] (concat form (list result))))))

(defn println-sink   []         (pipe-> (map-pipe u/my-println) (sink)))
(defn println-pipe   [name out] (-> #(do (u/my-println name ": " %) %) (map-pipe out)))
(defn timestamp-pipe [out]      (-> #(let [ts (str (t/local-date) " " (t/local-time))]
                                      (if (map? %) (assoc % :timestamp ts)
                                                   {:timestamp ts :value %}))
                                    (map-pipe out)))

