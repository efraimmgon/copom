(ns copom.views.requests
  (:require
    [clojure.string :as string]
    [copom.views.components :as comps :refer 
     [card card-nav checkbox-input form-group radio-input]]
    [copom.router :as router]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [copom.forms :as rff :refer [input select textarea]]))

(defn to-iso-date [d]
  (-> d
      .toISOString
      (.split "T")
      first))

(defn to-time-string [d]
  (-> d
      .toTimeString
      (.split " ")
      first))

(defn calculate-priority [r]
  (if-let [delicts (seq (:request/delicts r))]
    (->> delicts (map :delict/weight) (reduce +))
    0))

(defn list-requests [requests]
  [:ul.list-group.list-group-flush
    (for [r @requests]
      ^{:key (:request/id r)}
      [:a {:href (str "#/requisicoes/" (:request/id r) "/editar")}
        [:li.list-group-item
          (calculate-priority r) " | "
          (:request/complaint r) " | "
          (:request/created-at r)]])])

;; -------------------------
;; Create Request Page

;; `m` is {:(table-name)/id oid :superscription/id sid}
(defn clear-address-form-button [m path]
  [:button.btn.btn-warning.float-right 
   {:on-click #(rf/dispatch [:superscriptions/remove m path])}
   "Limpar endereço"])
  

;; TODO: route-types sub
;; TODO: typeheads for neighborhood, route-name, city, state
(defn address-form [doc path]
  (r/with-let [route-types (atom #{"Rua", "Avenida", "Rodovia", "Linha", "Travessa", "Praça"})]
    [:div
     [form-group
      [:span "Logradouro"]
      [:div.form-row
       [:div.col
        ;; TODO: typehead
        (let [path (conj path :superscription/neighborhood)]
          [comps/input
           {:type :typehead
            :name (conj path :neighborhood/name)
            :class "form-control"
            :doc doc
            :getter :neighborhood/name
            :handler #(swap! doc assoc-in (conj path :neighborhood/id)
                             (:neighborhood/id %))
            :data-source {:uri "/api/neighborhoods"}
            :placeholder "Bairro"}])]
       [:div.col-2
        [select {:name (conj path :superscription/route :route/type)
                 :class "form-control"
                 :doc doc
                 :default-value "Rua"}
         (for [r @route-types]
           ^{:key r}
           [:option {:value r} r])]]
       [:div.col
        ;; TODO: typehead
        (let [path (conj path :superscription/route)]
          [comps/input
           {:type :typehead
            :class "form-control"
            :doc doc
            :name (conj path :route/name)
            :getter :route/name
            :handler #(swap! doc assoc-in (conj path :route/id) (:route/id %))
            :data-source {:uri "/api/routes"}
            :placeholder "Logradouro"}])]
        
       [:div.col
        [input {:type :text
                :class "form-control"
                :doc doc
                :name (conj path :superscription/num)
                :placeholder "Número"}]]]]
     [:div.form-row
      [:div.col
       [form-group
        "Complemento"
        [input {:type :text
                :class "form-control"
                :doc doc
                :name (conj path :superscription/complement)
                :placeholder "Apartamento, Quadra, Lote, etc."}]]]
      [:div.col
       [form-group
        "Ponto de referência"
        [input {:type :text
                :class "form-control"
                :doc doc
                :name (conj path :superscription/reference)}]]]]
     ;; TODO: typehead
     [:div.form-row
      [:div.col
       [form-group
        "Município"
        [input {:type :text
                :class "form-control"
                :doc doc
                :name (conj path :superscription/city)
                :default-value "Guarantã do Norte"}]]]
      [:div.col
       ;; TODO: typehead
       [form-group
        "Estado"
        [input {:type :text
                :class "form-control"
                :doc doc
                :name (conj path :superscription/state)
                :default-value "Mato Grosso"}]]]]]))

(defn entity-form [doc path role]
  (r/with-let [docs-type ["CNH", "CNPJ" "CPF" "RG" "Passaporte"]]
;               fields (rf/subscribe [:rff/query path])]
    [:div
     [input {:type :hidden
             :class "form-control"
             :doc doc
             :name (conj path :entity/role)
             :default-value (name role)}]
     [:div.form-row
      [:div.col
       [form-group
        [:span "Nome"
         [:span.text-danger " *obrigatório"]]
        [input {:type :text
                :class "form-control"
                :doc doc
                :name (conj path :entity/name)}]]]
      [:div.col
       [form-group
        [:span "Telefone"
         [:span.text-danger " *obrigatório"]]
        [input {:type :number
                :class "form-control"
                :doc doc
                :name (conj path :entity/phone)}]]]]
     [:fieldset
      [:legend "Endereço"
       #_ ; TODO
       [clear-address-form-button 
        {:request/id (:request/id @(rf/subscribe [:rff/query (vec (butlast path))]))
         :entity/id (:entity/id @fields)
         :superscription/id (get-in @fields [:entity/superscription 
                                             :superscription/id])}
        (conj path :entity/superscription)]]
      [address-form doc (conj path :entity/superscription)]]
     [:fieldset
       [:legend "Documento de identidade"]
       [:div.form-row
        [:div.col
         [form-group
          "Tipo de documento"
          [select {:name (conj path :entity/doc-type)
                   :class "form-control"
                   :doc doc
                   :default-value "CPF"}
           (for [d docs-type]
             ^{:key d}
             [:option {:value d} d])]]]
        [:div.col
         [form-group
          "Órgão emissor"
          [input {:type :text
                  :class "form-control"
                  :doc doc
                  :name (conj path :entity/doc-issuer)}]]]
        [:div.col
         [form-group
          "Número do documento"
          [input {:type :text
                  :class "form-control"
                  :doc doc
                  :name (conj path :entity/doc-number)}]]]]]
     ;; TODO: on-click expand
     [:fieldset
      [:legend "Filiação"]
      [:div.form-row
       [:div.col
        [form-group
         "Pai"
         [input {:type :text
                 :class "form-control"
                 :doc doc
                 :name (conj path :entity/father)}]]]
       [:div.col
        [form-group
         "Mãe"
         [input {:type :text
                 :class "form-control"
                 :doc doc
                 :name (conj path :entity/mother)}]]]]]]))

(defn request-form [doc path]
  (r/with-let [fields (rf/subscribe [:rff/query path])
               delicts (rf/subscribe [:delicts/all])
               priority-score (rf/subscribe [:requests/priority-score path])]
    [card-nav
     [{:nav-title "Fato"
       :body ;; NOTE: use a select typehead? But where would I find all
             ;; the items?
             [:div
               ;comps/pretty-display @doc]
               [form-group
                [:span "Natureza"
                 [:span.text-danger " *obrigatório"]]
                [comps/input
                 {:name :request/complaint
                  :type :typehead
                  :class "form-control"
                  :doc doc
                  :data-source {:uri "/api/requests/complaints/all"}}]]
               [form-group
                [:span "Resumo da requisição"
                 [:span.text-danger " *obrigatório"]]
                [textarea {:name :request/summary
                           :doc doc
                           :class "form-control"}]]
               [:div.form-group
                 [:label "Prioridade"
                  ": " (@priority-score (:request/delicts @doc))]
                 (for [{:delict/keys [id name weight]} @delicts]
                   ^{:key id}
                   [checkbox-input {:name [:request/delicts id]
                                    :doc doc
                                    :label (string/capitalize name)}])]
               [form-group
                "Data"
                [input {:type :date
                        :class "form-control"
                        :doc doc
                        :name :request/date
                        :default-value (to-iso-date (js/Date.))}]]
               [form-group
                "Hora"
                [input {:type :time
                        :class "form-control"
                        :doc doc
                        :name :request/time
                        :default-value (to-time-string (js/Date.))}]]]}
      {:nav-title "Endereço do fato"
       :body [:fieldset
              [:legend "Endereço"
               [clear-address-form-button 
                {:request/id (:request/id @fields)
                 :superscription/id (get-in @fields [:request/superscription
                                                     :superscription/id])}
                (conj path :request/superscription)]]
              [address-form doc [:request/superscription]]]}
      ; typehead
      {:nav-title "Solicitante(s)"
       ; TODO: (button to add another)]}]]
       :body [entity-form doc [:request/requester] :requester]}
      ;; typehead
      {:nav-title "Suspeito(s)"
       ; TODO: (button to add another)]
       :body [entity-form doc [:request/suspect] :suspect]}
      ;; typehead
      {:nav-title "Testemunha(s)"
       ; TODO: (button to add another)]
       :body [entity-form doc [:request/witness] :witness]}
      ;; typehead
      ;; mirror solicitante
      {:nav-title "Vítima(s)"
       ; TODO: (button to add another)]
       :body [entity-form doc [:request/victim] :victim]}
      {:nav-title "Providências"
       :body [:div
              [form-group
                "Providências"
                [textarea {:name :request/measures
                           :class "form-control"
                           :doc doc}]
               [form-group
                 [:span "Status"
                  [:span.text-danger " *obrigatório"]]
                 [radio-input {:name :request/status
                               :class "form-check-input"
                               :doc doc
                               :value "pending"
                               :label "Em aberto"
                               :checked? (when (-> @doc :request/status nil?) true)}]
                 [radio-input {:name :request/status
                               :class "form-check-input"
                               :doc doc
                               :value "dispatched"
                               :label "Despachado"}]
                 [radio-input {:name :request/status
                               :class "form-check-input"
                               :doc doc
                               :value "done"
                               :label "Finalizado"}]]]]}]]))      

(defn create-request-page []
  (r/with-let [fields (rf/subscribe [:rff/query [:requests :new]])
               doc (r/atom {})
               errors (rf/subscribe [:rff/query [:requests :new :request/errors]])]
    (rff/set-doc! ::request-page doc @fields)
    [:section.section>div.container>div.content
     [card
      {:title [:h4 "Nova requisição"
               (when @errors [:span.alert.alert-danger @errors])
               [:div.btn-group.float-right
                 [:button.btn.btn-success
                  {:on-click #(rf/dispatch [:requests/create @doc])}
                  "Criar"]
                 [:a.btn.btn-danger
                  {:href (router/href :requests)
                   :on-click #(rf/dispatch [:requests/clear-form])} 
                                  
                  "Cancelar"]]]
       :body [request-form doc [:requests :new]]}]]))

(defn create-request-button []
  [:a.btn.btn-success
   {:href "/#/requisicoes/criar"}
   "Nova requisição"])

(defn request-page []
  (r/with-let [fields (rf/subscribe [:rff/query [:requests/edit]])
               doc (r/atom {})
               errors (rf/subscribe [:rff/query [:requests :new :request/errors]])]
    (rff/set-doc! ::request-page doc @fields)
    [:section.section>div.container>div.content
     [card
      {:title [:h4 "Requisição #" (:request/id @doc)
               (when @errors [:span.alert.alert-danger @errors])
               " "
               [:div.btn-group
                 [:button.btn.btn-success
                  {:on-click #(rf/dispatch [:requests/update @doc])}
                  "Salvar"]
                 [:a.btn.btn-danger
                  {:href (router/href :requests)
                   :on-click #(rf/dispatch [:requests/clear-form])} 
                                  
                  "Cancelar"]]]
       :body [request-form doc [:requests/edit]]}]]))

; TODO: pagination (show only n requests per page)
(defn requests-page []
  (r/with-let [requests (rf/subscribe [:requests/all])]
    [:section.section>div.container>div.content
     [card
      {:title [:h4 "Requisições"
               [:span.float-right 
                [create-request-button]]]
       :body (if (seq @requests)
               [list-requests requests]
               "Não há requisições.")}]]))


;; -------------------------
;; Dashboard Page


(defn latest-requests []
  (r/with-let [requests (rf/subscribe [:requests/latest])]
    [card 
     {:title [:h4 "Últimas Requisições"]
      :body (if (seq @requests)
              [list-requests requests]
              [:h6 "Não há requisições"])}]))

(defn pending-requests []
  (r/with-let [requests (rf/subscribe [:requests/pending])]
    [card
     {:title [:h4 "Requisições em aberto"]
      :body (if (seq @requests)
              [list-requests requests]
              [:h6 "Não há requisições em aberto."])}]))

(defn manage-requests-button []
  [:a.btn.btn-primary
   {:href "#/requisicoes"}
   "Gerenciar requisições"])

(defn dashboard []
  [:section.section>div.container>div.content
   [:div.row>div.col-md-12
     [latest-requests]]
   [:div.row>div.col-md-12
     [pending-requests]]
   [:div.row>div.col-md-12
     ; TODO: search requests
     [manage-requests-button] " "
     [create-request-button]]])