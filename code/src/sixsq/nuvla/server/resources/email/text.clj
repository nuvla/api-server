(ns sixsq.nuvla.server.resources.email.text
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.server.resources.email.utils :refer [render-email]]))

(defn trial-ending [{:keys [trial-days-left]}]
  {:template      :trial
   :subject       "Nuvla trial ending"
   :title         "Your Nuvla trial is ending"
   :button-text   "Add your payment methods"
   :button-url    "https://nuvla.io"
   :text-1        (format "Your Nuvla.io trial will end in %d days. Make sure you have a payement method set, to avoid that your resources are frozen when the trial ends." 
                          trial-days-left)
   ; :resources-text-1 "The following resources will be frozen: "
   :text-2        "You’re also going to lose access to any data you have in Nuvla. You no longer will be able to receive data from your Nuvla Edge devices on Nuvla.io. Do not worry! We keep it safe with us for when you set your payment method."
   :text-3        (format "By entering an payment method, you will ensure your data flows stays active. Just add a payment method in your Nulva.io account. Luckily you still have %d days." 
                          trial-days-left)})

(defn trial-ended [{:keys [resources-frozen?]}]
  {:template      :trial
   :subject        "Nuvla trial ended"
   :title          "Your Nuvla trial ended"
   :button-text    "Add your payment methods"
   :button-url     "https://nuvla.io/profile"
   :text-1         (str "Your Nuvla trail expired today. " (when resources-frozen? "If you want to unfreeze your resources login to Nuvla.io and unfreeze them from your profile page.")) 
   :text-3         "Would you mind sharing briefly with us what kept your from adopting Nuvla.io? Feel free to reach us in the live chat in Nuvla.io. Would really love to hear your feedback. Thank you."})
