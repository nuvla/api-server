(ns sixsq.nuvla.server.resources.email.text
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.server.resources.email.utils :refer [render-email]]))

(defn trial-ending [{:keys [trial-days-left]}]
  {:template      :trial
   :subject       "Nuvla trial ending"
   :title         "Your Nuvla trial is ending"
   :button-text   "Choose a Novla plan today"
   :button-url    "https://nuvla.io/pricing"
   :text-1        (format "Your Nuvla.io trial is will end in %d days. Now is a good time to upgrade and get all the awesome benefits. Your account will be deactivated when the trial ends." 
                          trial-days-left)
   ; :resources-text-1 "The following resources will be frozen: "
   :text-2        "You’re also going to lose access to any data you have in Nuvla. You no longer will be able to receive data from your Nuvla Edge devices on Nuvla.io. Do not worry! We keep it safe with us for when you decide to upgrade."
   :text-3        (format "By upgrading now, you will ensure your data flows stays active. Just add a payment method in your Nulva.io account. Luckily you still have %d days to upgrade." 
                          trial-days-left)})

(def trial-ended
  {:template      :trial
   :subject        "Nuvla trial ended"
   :title          "Your Nuvla trial ended"
   :button-text    "Choose a Novla plan today"
   :button-url     "https://nuvla.io/pricing"
   :text-1         "Your Nuvla trail expired today. If you want to unfreeze your resources just add a payment method in your Nuvla.io account and choose a subscription." 
   :text-2         "Would you mind sharing briefly with me what kept your from upgrading? Feel free to hit reply here or we can just schedule a quick 5-minute Skype/Zoom/Hangouts call. Would really love to hear your feedback. Thank you."})
