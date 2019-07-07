(ns copom.events.requests
  (:require
    [ajax.core :as ajax]
    [copom.events.utils :refer [base-interceptors]]
    [re-frame.core :as rf]))

(def requests-interceptos 
  (conj base-interceptors (rf/path :requests)))

(defn event-timestamp [date time]
  (-> date (.split "T") first
      (str "T" time ".000>")))


(defn request-coercions [m]
  (-> m
      (update :request/delicts #(->> % (filter (fn [[k v]] v)) (map (fn [[k v]] k))))
      (assoc :request/event-timestamp (event-timestamp (:request/date m) (:request/time m)))
      (dissoc :request/date :request/time)))
  

(rf/reg-event-db
  :requests/clear-form
  requests-interceptos
  (fn [reqs _]
    (assoc reqs :new nil)))

(def required-request-fields
  #{:request/complaint :request/summary :request/status})

(def field-translation
  {:request/complaint "natureza"
   :request/summary "resumo da requisição"
   :request/status "status"})

(defn min-request-params? [params]
  (let [[complaint summary status] ((apply juxt required-request-fields) params)]
    (and complaint summary status)))

(defn missing-fields [fields required-fields]
  (clojure.set/difference required-fields (set (keys fields))))

(defn request-error-msg [fields]
  (str
    (->> (missing-fields fields required-request-fields)
         (select-keys field-translation)
         vals
         (clojure.string/join ", ")
         (str "Campos obrigatórios: "))
    "."))

(defn create-request! [fields]
  (ajax/POST "/api/requests"
             {:params (request-coercions fields)
              :handler #(do 
                            (rf/dispatch [:navigate/by-path "/#/"])
                            (rf/dispatch [:requests/clear-form]))
              :error-handler #(prn %)})
  nil)

(rf/reg-event-fx
  :requests/create
  base-interceptors
  (fn [{:keys [db]} [fields]]
    (if (min-request-params? fields)
      (create-request! fields)
      {:db (assoc-in db [:requests :new :request/errors]
                     (request-error-msg fields))})))
        

(rf/reg-event-fx
  :requests/load-delicts
  base-interceptors
  (fn [_ _]
    (ajax/GET "/api/delicts"
              {:handler #(rf/dispatch [:rff/set [:delicts/all] %])
               :error-handler #(prn %)
               :response-format :json
               :keywords? true})
    nil))

(rf/reg-event-fx
  :requests/load-requests
  base-interceptors
  (fn [_ _]
    (ajax/GET "/api/requests"
              {:handler #(rf/dispatch [:rff/set [:requests/all] %])
               :error-handler #(prn %)
               :response-format :json
               :keywords? true})
    nil))

(rf/reg-sub
  :delicts/all
  (fn [db] (:delicts/all db)))

(rf/reg-sub
  :requests/all
  (fn [db _] (:requests/all db)))

(rf/reg-sub
  :requests/pending
  :<- [:requests/all]
  (fn [reqs _]
    (filter #(not= (:status %) "done") reqs)))

(rf/reg-sub
  :requests/latest
  :<- [:requests/all]
  (fn [reqs _]
    (take 10 reqs)))
        
(rf/reg-sub
  :requests.new/delicts
  (fn [db _]
    (get-in db [:requests :new :request/delicts])))

(rf/reg-sub 
  :requests/priority-score
  :<- [:delicts/all]
  :<- [:requests.new/delicts]
  (fn [[delicts checked] _]
    (->> checked
         (filter (fn [[id bool]] bool))
         (reduce (fn [acc [id bool]] 
                   (+ acc (some (fn [d] 
                                  (and (= id (:delict/id d)) 
                                       (:delict/weight d)))
                                delicts)))
                 0))))
                 