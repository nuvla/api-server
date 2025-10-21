(ns com.sixsq.nuvla.server.resources.mec.query-filter
  "MEC 010-2 Query Filter Parser
   
   Implements FIQL-like query filter parsing for MEC API endpoints.
   Supports filtering collections by attribute values.
   
   Filter Syntax:
   - Equality: filter=(eq,field,value)
   - Not equal: filter=(neq,field,value)
   - Greater than: filter=(gt,field,value)
   - Less than: filter=(lt,field,value)
   - In set: filter=(in,field,value1,value2,value3)
   - And: filter=(and,(eq,field1,value1),(eq,field2,value2))
   - Or: filter=(or,(eq,field1,value1),(eq,field2,value2))
   
   Example:
   GET /app_instances?filter=(eq,appName,my-app)
   GET /app_instances?filter=(and,(eq,operationalState,STARTED),(neq,appName,test))"
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]))


;;
;; Filter Parser
;;

(defn- tokenize
  "Tokenize filter string into components.
   Split by commas but respect parentheses nesting."
  [s]
  (loop [chars (seq s)
         tokens []
         current []
         depth 0]
    (if-let [c (first chars)]
      (cond
        ;; Open paren increases depth
        (= c \()
        (recur (rest chars) tokens (conj current c) (inc depth))
        
        ;; Close paren decreases depth
        (= c \))
        (recur (rest chars) tokens (conj current c) (dec depth))
        
        ;; Comma at depth 0 is a separator
        (and (= c \,) (zero? depth))
        (recur (rest chars) (conj tokens (str/join current)) [] 0)
        
        ;; Any other character
        :else
        (recur (rest chars) tokens (conj current c) depth))
      
      ;; End of string
      (if (seq current)
        (conj tokens (str/join current))
        tokens))))


(defn- parse-filter-expr
  "Parse a single filter expression.
   Returns a map with :op, :field, :value(s)"
  [expr]
  (let [trimmed (str/trim expr)]
    (if (str/starts-with? trimmed "(")
      ;; Expression is wrapped in parens
      (let [inner (subs trimmed 1 (dec (count trimmed)))
            tokens (tokenize inner)
            op (first tokens)]
        (case op
          "eq"
          {:op :eq
           :field (keyword (second tokens))
           :value (nth tokens 2)}
          
          "neq"
          {:op :neq
           :field (keyword (second tokens))
           :value (nth tokens 2)}
          
          "gt"
          {:op :gt
           :field (keyword (second tokens))
           :value (nth tokens 2)}
          
          "lt"
          {:op :lt
           :field (keyword (second tokens))
           :value (nth tokens 2)}
          
          "gte"
          {:op :gte
           :field (keyword (second tokens))
           :value (nth tokens 2)}
          
          "lte"
          {:op :lte
           :field (keyword (second tokens))
           :value (nth tokens 2)}
          
          "in"
          {:op :in
           :field (keyword (second tokens))
           :values (vec (drop 2 tokens))}
          
          "and"
          {:op :and
           :exprs (mapv parse-filter-expr (rest tokens))}
          
          "or"
          {:op :or
           :exprs (mapv parse-filter-expr (rest tokens))}
          
          ;; Unknown operator
          (throw (ex-info "Unknown filter operator"
                          {:operator op
                           :expression expr}))))
      
      ;; Not wrapped in parens, treat as literal
      {:op :literal
       :value trimmed})))


(defn parse-filter
  "Parse a filter query string into a filter expression.
   
   Parameters:
   - filter-str: Filter query string
   
   Returns:
   Filter expression map or nil if empty/invalid"
  [filter-str]
  (when (and filter-str (not (str/blank? filter-str)))
    (try
      (parse-filter-expr filter-str)
      (catch Exception e
        (log/warn "Failed to parse filter:" filter-str "-" (.getMessage e))
        nil))))


;;
;; Filter Evaluation
;;

(defn- coerce-value
  "Coerce string value to appropriate type for comparison"
  [v]
  (cond
    ;; Try integer
    (re-matches #"-?\d+" v)
    (Long/parseLong v)
    
    ;; Try boolean
    (= "true" v)
    true
    
    (= "false" v)
    false
    
    ;; Keep as string
    :else
    v))


(defn- compare-values
  "Compare two values with type coercion"
  [op v1 v2]
  (let [cv1 (if (string? v1) (coerce-value v1) v1)
        cv2 (if (string? v2) (coerce-value v2) v2)]
    (try
      (case op
        :eq (= cv1 cv2)
        :neq (not= cv1 cv2)
        :gt (> (compare cv1 cv2) 0)
        :lt (< (compare cv1 cv2) 0)
        :gte (>= (compare cv1 cv2) 0)
        :lte (<= (compare cv1 cv2) 0)
        false)
      (catch Exception e
        (log/debug "Comparison failed:" cv1 op cv2 "-" (.getMessage e))
        false))))


(defn- evaluate-filter-expr
  "Evaluate a filter expression against a resource.
   
   Parameters:
   - expr: Parsed filter expression
   - resource: Resource map to evaluate against
   
   Returns:
   Boolean indicating if resource matches filter"
  [expr resource]
  (case (:op expr)
    :eq
    (compare-values :eq (get resource (:field expr)) (:value expr))
    
    :neq
    (compare-values :neq (get resource (:field expr)) (:value expr))
    
    :gt
    (compare-values :gt (get resource (:field expr)) (:value expr))
    
    :lt
    (compare-values :lt (get resource (:field expr)) (:value expr))
    
    :gte
    (compare-values :gte (get resource (:field expr)) (:value expr))
    
    :lte
    (compare-values :lte (get resource (:field expr)) (:value expr))
    
    :in
    (let [field-val (get resource (:field expr))
          coerced-vals (map coerce-value (:values expr))]
      (some #(= field-val %) coerced-vals))
    
    :and
    (every? #(evaluate-filter-expr % resource) (:exprs expr))
    
    :or
    (some #(evaluate-filter-expr % resource) (:exprs expr))
    
    :literal
    true ; Literal expressions always match
    
    ;; Unknown operator
    false))


(defn apply-filter
  "Apply filter expression to a collection of resources.
   
   Parameters:
   - filter-expr: Parsed filter expression (from parse-filter)
   - resources: Collection of resource maps
   
   Returns:
   Filtered collection"
  [filter-expr resources]
  (if filter-expr
    (filter #(evaluate-filter-expr filter-expr %) resources)
    resources))


;;
;; Pagination
;;

(defn- build-page-link
  "Build a pagination link"
  [base-uri page size]
  {:href (str base-uri "?page=" page "&size=" size)})


(defn paginate
  "Apply pagination to a collection with HAL-style links.
   
   Parameters:
   - resources: Collection of resources
   - opts: Pagination options
     * :page - Page number (1-based, default 1)
     * :size - Page size (default 20)
     * :base-uri - Base URI for links (default '')
   
   Returns:
   Map with :items, :total, :page, :size, :_links"
  [resources {:keys [page size base-uri]
              :or   {page 1 size 20 base-uri ""}}]
  (let [total (count resources)
        page (max 1 page)
        size (max 1 (min size 100)) ; Cap at 100
        offset (* (dec page) size)
        total-pages (int (Math/ceil (/ total (double size))))
        items (vec (take size (drop offset resources)))
        
        ;; HAL-style links
        links {:self (build-page-link base-uri page size)}
        links (if (> page 1)
                (assoc links
                       :first (build-page-link base-uri 1 size)
                       :prev (build-page-link base-uri (dec page) size))
                links)
        links (if (< page total-pages)
                (assoc links
                       :next (build-page-link base-uri (inc page) size)
                       :last (build-page-link base-uri total-pages size))
                links)]
    
    {:items items
     :total total
     :page page
     :size size
     :totalPages total-pages
     :_links links}))


;;
;; Field Selection
;;

(defn parse-fields
  "Parse fields parameter into a set of keywords.
   
   Parameters:
   - fields-str: Comma-separated field names (e.g., 'appName,operationalState')
   
   Returns:
   Set of field keywords or nil for all fields"
  [fields-str]
  (when (and fields-str (not (str/blank? fields-str)))
    (set (map keyword (str/split fields-str #",")))))


(defn select-fields
  "Select specified fields from resources.
   
   Parameters:
   - fields: Set of field keywords (from parse-fields) or nil for all
   - resources: Collection of resource maps
   
   Returns:
   Collection with only selected fields"
  [fields resources]
  (if fields
    (map #(select-keys % (conj fields :id)) resources) ; Always include :id
    resources))


;;
;; Combined Query Processing
;;

(defn process-query
  "Process query parameters: filter, paginate, and select fields.
   
   Parameters:
   - resources: Collection of resources to query
   - query-params: Map of query parameters
     * :filter - Filter expression string
     * :page - Page number
     * :size - Page size
     * :fields - Comma-separated field names
     * :base-uri - Base URI for pagination links
   
   Returns:
   Map with :items, :total, :page, :size, :_links"
  [resources query-params]
  (let [{:keys [filter page size fields base-uri]} query-params
        
        ;; Parse parameters
        filter-expr (parse-filter filter)
        field-set (parse-fields fields)
        page (or (some-> page Integer/parseInt) 1)
        size (or (some-> size Integer/parseInt) 20)
        
        ;; Apply operations in order: filter -> select fields -> paginate
        filtered (apply-filter filter-expr resources)
        selected (select-fields field-set filtered)
        result (paginate selected {:page page
                                    :size size
                                    :base-uri (or base-uri "")})]
    
    result))
