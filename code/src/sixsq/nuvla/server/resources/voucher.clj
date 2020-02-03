(ns sixsq.nuvla.server.resources.voucher
  "
This resource contains the structure for a voucher, which is to be issued by a
third party and consumed by a Nuvla user.

New vouchers will by default be inserted into the system with state set to
NEW. Then based on the ACLs of that voucher, whoever can view it, can request
it through the activation operation, which will edit the voucher's state to
ACTIVATED, and assign it to the requesting user.

Afterwards, this voucher can also be redeemed through the operation 'redeem',
which adds a new timestamp to the voucher resource for accounting purposes.

Finally, at any time, the owner or user of the voucher can terminate the
voucher via the 'expire' operation.
"
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.voucher :as voucher]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.server.util.time :as time]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query       ["group/nuvla-user"]
                     :add         ["group/nuvla-user"]
                     :bulk-delete ["group/nuvla-user"]})


;;
;; initialization: common schema for all user creation methods
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::voucher/schema))


(defn initialize
  []
  (std-crud/initialize resource-type ::voucher/schema)
  (md/register resource-metadata))


;;
;; validation
;;

(def validate-fn (u/create-spec-validation-fn ::voucher/schema))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


;;
;; use default ACL method
;;

(defmethod crud/add-acl resource-type
  [resource request]
  (a/add-acl resource request))


;;
;; set the resource identifier to "voucher/uuid"
;;

(defn voucher->uuid
  [code supplier]
  (let [id (str/join ":" [code supplier])]
    (u/from-data-uuid id)))

(defmethod crud/new-identifier resource-type
  [{:keys [code supplier] :as voucher} resource-name]
  (->> (voucher->uuid code supplier)
    (str resource-type "/")
    (assoc voucher :id)))



;;
;; define country name based on 2-letter code
;;


(defn resolve-country-name
  [country-code]
  (let [country-map {:BD  "Bangladesh" :BE  "Belgium" :BF  "Burkina Faso" :BG  "Bulgaria" :BA  "Bosnia and Herzegovina"
                     :BB  "Barbados" :WF  "Wallis and Futuna" :BL  "Saint Barthelemy" :BM  "Bermuda"
                     :BN  "Brunei Darussalam" :BO  "Bolivia (Plurinational State of)" :BH  "Bahrain" :BI  "Burundi"
                     :BJ  "Benin" :BT  "Bhutan" :JM  "Jamaica" :BV  "Bouvet Island" :BW  "Botswana" :WS  "Samoa"
                     :BQ  "Bonaire, Sint Eustatius and Saba" :BR  "Brazil" :BS  "Bahamas" :JE  "Jersey" :BY  "Belarus"
                     :BZ  "Belize" :RU  "Russian Federation" :RW  "Rwanda" :RS  "Serbia" :TL  "Timor-Leste"
                     :RE  "Reunion" :TM  "Turkmenistan" :TJ  "Tajikistan" :RO  "Romania" :TK  "Tokela"
                     :GW  "Guinea-Bissa" :GU  "Guam" :GT  "Guatemala" :GS  "South Georgia and the South Sandwich Islands"
                     :GR  "Greece" :GQ  "Equatorial Guinea" :GP  "Guadeloupe" :JP  "Japan" :GY  "Guyana"
                     :GG  "Guernsey" :GF  "French Guiana" :GE  "Georgia" :GD  "Grenada"
                     :GB  "United Kingdom of Great Britain and Northern Ireland" :GA  "Gabon" :GN  "Guinea"
                     :GM  "Gambia" :GL  "Greenland" :GI  "Gibraltar" :GH  "Ghana" :OM  "Oman" :TN  "Tunisia"
                     :JO  "Jordan" :HR  "Croatia" :HT  "Haiti" :HU  "Hungary" :HK  "Hong Kong" :HN  "Honduras"
                     :HM  "Heard Island and McDonald Islands" :VE  "Venezuela (Bolivarian Republic of)"
                     :PR  "Puerto Rico" :PS  "Palestine, State of" :PW  "Pala" :PT  "Portugal"
                     :KN  "Saint Kitts and Nevis" :PY  "Paraguay" :IQ  "Iraq" :PA  "Panama" :PF  "French Polynesia"
                     :PG  "Papua New Guinea" :PE  "Per" :PK  "Pakistan" :PH  "Philippines" :PN  "Pitcairn"
                     :PL  "Poland" :PM  "Saint Pierre and Miquelon" :ZM  "Zambia" :EH  "Western Sahara" :EE  "Estonia"
                     :EG  "Egypt" :ZA  "South Africa" :EC  "Ecuador" :IT  "Italy" :VN  "Viet Nam" :SB  "Solomon Islands"
                     :ET  "Ethiopia" :SO  "Somalia" :ZW  "Zimbabwe" :SA  "Saudi Arabia" :ES  "Spain" :ER  "Eritrea"
                     :ME  "Montenegro" :MD  "Moldova, Republic of" :MG  "Madagascar" :MF  "Saint Martin (French part)"
                     :MA  "Morocco" :MC  "Monaco" :UZ  "Uzbekistan" :MM  "Myanmar" :ML  "Mali" :MO  "Macao"
                     :MN  "Mongolia" :MH  "Marshall Islands" :MK  "North Macedonia" :MU  "Mauritius" :MT  "Malta"
                     :MW  "Malawi" :MV  "Maldives" :MQ  "Martinique" :MP  "Northern Mariana Islands" :MS  "Montserrat"
                     :MR  "Mauritania" :IM  "Isle of Man" :UG  "Uganda" :TZ  "Tanzania, United Republic of"
                     :MY  "Malaysia" :MX  "Mexico" :IL  "Israel" :FR  "France" :AW  "Aruba"
                     :SH  "Saint Helena, Ascension and Tristan da Cunha" :SJ  "Svalbard and Jan Mayen" :FI  "Finland"
                     :FJ  "Fiji" :FK  "Falkland Islands (Malvinas)" :FM  "Micronesia (Federated States of)"
                     :FO  "Faroe Islands" :NI  "Nicaragua" :NL  "Netherlands" :NO  "Norway" :NA  "Namibia" :VU  "Vanuat"
                     :NC  "New Caledonia" :NE  "Niger" :NF  "Norfolk Island" :NG  "Nigeria" :NZ  "New Zealand"
                     :NP  "Nepal" :NR  "Naur" :NU  "Niue" :CK  "Cook Islands" :CI  "Cote d'Ivoire" :CH  "Switzerland"
                     :CO  "Colombia" :CN  "China" :CM  "Cameroon" :CL  "Chile" :CC  "Cocos (Keeling) Islands"
                     :CA  "Canada" :CG  "Congo" :CF  "Central African Republic" :CD  "Congo, Democratic Republic of the"
                     :CZ  "Czechia" :CY  "Cyprus" :CX  "Christmas Island" :CR  "Costa Rica" :CW  "Curacao"
                     :CV  "Cabo Verde" :CU  "Cuba" :SZ  "Eswatini" :SY  "Syrian Arab Republic"
                     :SX  "Sint Maarten (Dutch part)" :KG  "Kyrgyzstan" :KE  "Kenya" :SS  "South Sudan" :SR  "Suriname"
                     :KI  "Kiribati" :KH  "Cambodia" :SV  "El Salvador" :KM  "Comoros" :ST  "Sao Tome and Principe"
                     :SK  "Slovakia" :KR  "Korea, Republic of" :SI  "Slovenia"
                     :KP  "Korea (Democratic People's Republic of)" :KW  "Kuwait" :SN  "Senegal" :SM  "San Marino"
                     :SL  "Sierra Leone" :SC  "Seychelles" :KZ  "Kazakhstan" :KY  "Cayman Islands" :SG  "Singapore"
                     :SE  "Sweden" :SD  "Sudan" :DO  "Dominican Republic" :DM  "Dominica" :DJ  "Djibouti" :DK  "Denmark"
                     :VG  "Virgin Islands (British)" :DE  "Germany" :YE  "Yemen" :DZ  "Algeria"
                     :US  "United States of America" :UY  "Uruguay" :YT  "Mayotte"
                     :UM  "United States Minor Outlying Islands" :LB  "Lebanon" :LC  "Saint Lucia"
                     :LA  "Lao People's Democratic Republic" :TV  "Tuval" :TW  "Taiwan, Province of China"
                     :TT  "Trinidad and Tobago" :TR  "Turkey" :LK  "Sri Lanka" :LI  "Liechtenstein" :LV  "Latvia"
                     :TO  "Tonga" :LT  "Lithuania" :LU  "Luxembourg" :LR  "Liberia" :LS  "Lesotho" :TH  "Thailand"
                     :TF  "French Southern Territories" :TG  "Togo" :TD  "Chad" :TC  "Turks and Caicos Islands"
                     :LY  "Libya" :VA  "Holy See" :VC  "Saint Vincent and the Grenadines" :AE  "United Arab Emirates"
                     :AD  "Andorra" :AG  "Antigua and Barbuda" :AF  "Afghanistan" :AI  "Anguilla"
                     :VI  "Virgin Islands (U.S.)" :IS  "Iceland" :IR  "Iran (Islamic Republic of)" :AM  "Armenia"
                     :AL  "Albania" :AO  "Angola" :AQ  "Antarctica" :AS  "American Samoa" :AR  "Argentina"
                     :AU  "Australia" :AT  "Austria" :IO  "British Indian Ocean Territory" :IN  "India"
                     :AX  "Aland Islands" :AZ  "Azerbaijan" :IE  "Ireland" :ID  "Indonesia" :UA  "Ukraine" :QA  "Qatar"
                     :MZ  "Mozambique"}]
    (get country-map (keyword country-code))))


;;
;; CRUD operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defmethod crud/add resource-type
  [request]
  (let [country-name (resolve-country-name (:country (:body request)))
        body         (assoc (:body request) :country-name country-name)]
    (add-impl (assoc request :body body))))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [request]
  (let [country-name (resolve-country-name (:country (:body request)))
        body         (assoc (:body request) :country-name country-name)]
    (edit-impl (assoc request :body body))))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


(def bulk-delete-impl (std-crud/bulk-delete-fn resource-type collection-acl collection-type))


(defmethod crud/bulk-delete resource-type
  [request]
  (bulk-delete-impl request))



;;
;; DISTRIBUTE operation
;;

(defn distribute
  [voucher]
  (if (= (:state voucher) "NEW")
    (assoc voucher :state "DISTRIBUTED")
    (throw (r/ex-response "distribution is not allowed for this voucher" 400 (:id voucher)))))


(defmethod crud/do-action [resource-type "distribute"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id      (str resource-type "/" uuid)]
      (try
        (-> id
          (db/retrieve request)
          (a/throw-cannot-manage request)
          distribute
          (db/edit request))
        (catch Exception ei
          (ex-data ei))))
    (catch Exception ei
      (ex-data ei))))


;;
;; Activate operation
;;

(defn activate
  [voucher]
  (if (= (:state voucher) "DISTRIBUTED")
    (assoc voucher :state "ACTIVATED"
                   :activated (time/now-str))
    (throw (r/ex-response "activation is not allowed for this voucher" 400 (:id voucher)))))


(defmethod crud/do-action [resource-type "activate"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id      (str resource-type "/" uuid)
          user-id (auth/current-user-id request)
          voucher (db/retrieve id request)
          new-acl (update (:acl voucher) :manage conj user-id)]
      (try
        (-> id
          (db/retrieve request)
          (a/throw-cannot-view-data request)
          activate
          (assoc :user user-id :acl new-acl)
          (db/edit request))
        (catch Exception ei
          (ex-data ei))))
    (catch Exception ei
      (ex-data ei))))


;;
;; Redeem operation
;;

(defn redeem
  [voucher]
  (if (= (:state voucher) "ACTIVATED")
    (assoc voucher :state "REDEEMED"
                   :redeemed (time/now-str))
    (throw (r/ex-response "redeem is not allowed for this voucher" 400 (:id voucher)))))


(defmethod crud/do-action [resource-type "redeem"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (try
        (-> id
          (db/retrieve request)
          (a/throw-cannot-manage request)
          redeem
          (db/edit request))
        (catch Exception ei
          (ex-data ei))))
    (catch Exception ei
      (ex-data ei))))


;;;
;;; Expire operation
;;;


(defn expire
  [voucher]
  (if (not= (:state voucher) "EXPIRED")
    (assoc voucher :state "EXPIRED")
    (throw (r/ex-response "voucher is already expired" 400 (:id voucher)))))


(defmethod crud/do-action [resource-type "expire"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (try
        (-> id
          (db/retrieve request)
          (a/throw-cannot-manage request)
          expire
          (db/edit request))
        (catch Exception ei
          (ex-data ei))))
    (catch Exception ei
      (ex-data ei))))


;;
;; Set operation
;;

(defmethod crud/set-operations resource-type
  [{:keys [id state] :as resource} request]
  (let [distribute-op (u/action-map id :distribute)
        activate-op (u/action-map id :activate)
        expire-op   (u/action-map id :expire)
        redeem-op   (u/action-map id :redeem)
        can-manage? (a/can-manage? resource request)
        can-view?   (a/can-view? resource request)]
    (cond-> (crud/set-standard-operations resource request)
      (and can-manage? (#{"ACTIVATED"} state)) (update :operations conj redeem-op)
      (and can-manage? (#{"NEW" "ACTIVATED" "REDEEMED"} state)) (update :operations conj expire-op)
      (and can-manage? (#{"NEW"} state)) (update :operations conj distribute-op)
      (and can-view? (#{"DISTRIBUTED"} state)) (update :operations conj activate-op))))
