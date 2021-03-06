(ns copom.events.entity
  (:require
    [ajax.core :as ajax]
    [copom.db :refer [app-db]]
    [copom.events.utils :refer [base-interceptors superscription-coercions]]
    [copom.views.components :as comps]
    [re-frame.core :as rf]))

(rf/reg-event-fx
  :entity/query
  base-interceptors
  (fn [_ [query]]
    (let [{:entity/keys [name phone]} @query
          col (if name "names" "phones")]
      (ajax/GET (str "/api/entities/" col)
                {:params {:query (or name phone)}
                 :handler #(rf/dispatch [:assoc-in! query [:items] %])
                 :error-handler #(prn %)
                 :response-format :json
                 :keywords? true}))
    nil))

(rf/reg-event-fx
  :entity/create
  base-interceptors
  (fn [_ [{:keys [params handler]}]]
    (ajax/POST "/api/entities"
               {:params params
                :handler handler})
    nil))

(rf/reg-event-fx
  :entity.create/handler
  base-interceptors
  (fn [_ [doc]]
    (rf/dispatch [:entity/create
                  {:params doc
                   :handler #(rf/dispatch [:remove-modal])}])
    nil))


; When there's an eid and sid, will create an entity-superscription.
; When there's only the eid, will create a sup with the given params, 
; and then create an entity-superscription.
(rf/reg-event-fx
  :entity.superscription/create
  base-interceptors
  (fn [_ [{eid :entity/id sid :superscription/id :keys [params handler]}]]
    (let [base (str "/api/entities/" eid "/superscriptions")
          uri (if sid (str base "/" sid) base)]
      (ajax/POST uri
                 {:handler handler
                  :params (superscription-coercions params)
                  :response-format :json
                  :keywords? true})
      nil)))

(rf/reg-event-fx
  :entity.superscription/delete
  base-interceptors
  (fn [_ [{:keys [handler] eid :entity/id sid :superscription/id}]]
    (ajax/DELETE (str "/api/entities/" eid 
                      "/superscriptions/" sid)
                 {:handler handler})
    nil))


; Takes a doc and the path to the entity to be dissoc.
(rf/reg-event-fx
  :entity/dissoc!
  base-interceptors
  (fn [_ [{:keys [doc path]}]]
    (let [parent-path (pop path)
          v (get-in @doc parent-path)
          i (last path)
          before (take i v)
          after (drop (inc i) v)]
      (rf/dispatch [:assoc-in! doc parent-path (into (vec before) after)]))
    nil))

;; assoc the selected superscription (by its :superscription/id)
;; to :entity/superscription of the main doc.
(rf/reg-event-fx
  :entity.entity-pick-modal/select
  base-interceptors
  (fn [_ [{rid :request/id eid :entity/id :keys [doc temp-doc path entity]}]]
    ;; Create a request-entity of the selected entity:
    (when rid
      (rf/dispatch
        [:request.entity/create
         {:request/id rid
          :entity/id eid
          :params entity
          :handler
          (fn [_]
            ;; Assoc the selected entity's fields to the doc.
            ;; We either conj it to the path, or assoc it to the path 
            ;; inside a vector.
            (let [v (assoc @temp-doc :entity/superscription
                      (->> (:entity/superscriptions entity)
                           (some #(and (= (:superscription/id %)
                                          (get-in @temp-doc [:entity/superscription])) 
                                       %))))
                  parent-path (if (int? (last path)) 
                                (pop path) path)
                  entities (get-in @doc parent-path)]
              (if (seq entities)
                (rf/dispatch [:update-in! doc parent-path conj v])
                (rf/dispatch [:assoc-in! doc parent-path [v]]))))
          :error-handler #(js/alert %)}]))
    (rf/dispatch [:remove-modal]) 
    nil))