(ns sixsq.nuvla.server.resources.email.content)

(defn trial-ending [{:keys [days-left resources]}]
  {:template        :trial
   :subject         "Nuvla trial ending"
   :title           "Your Nuvla trial is ending"
   :button-intro    "Add your payment method to use your resources:"
   :button-text     "Add your payment methods"
   :button-url      "https://nuvla.io/ui/sign-in?redirect=profile"
   :text-1          (format "Your Nuvla.io trial will end in %d days. Make sure you have a payment method set, to avoid that your resources are frozen when the trial ends."
                            days-left)
   :resources-title "The following resources will be frozen: "
   :resources       resources
   :text-2          "You’re also going to lose access to any data you have in Nuvla. You no longer will be able to receive data from your Nuvla Edge devices on Nuvla.io. Do not worry! We keep it safe with us for when you set your payment method."
   :text-3          (format "By entering an payment method, you will ensure your data flows stays active. Just add a payment method in your Nulva.io account. Luckily you still have %d days."
                            days-left)})

(defn trial-ended [{:keys [resources]}]
  {:template        :trial
   :subject         "Nuvla trial ended"
   :title           "Your Nuvla trial ended"
   :button-intro    "Add your payment method to use your resources:"
   :button-text     "Add your payment methods"
   :button-url      "https://nuvla.io/ui/sign-in?redirect=profile"
   :resources-title "The following resources are now frozen: "
   :resources       resources
   :text-1          (str "Your Nuvla trial expired today. " (when (seq resources) "If you want to unfreeze your resources login to Nuvla.io and unfreeze them from your profile page. Your frozen resources will be deleted after 30 days."))
   :text-3          "Would you mind sharing briefly with us what kept your from adopting Nuvla.io? Feel free to reach us in the live chat in Nuvla.io. Would really love to hear your feedback. Thank you."})

(defn trial-ended-with-payment [{:keys [resources]}]
  {:template        :trial
   :title           "Your Nuvla trial ended"
   :resources-title "You are using the following resources: "
   :resources       resources
   :text-1          "Your Nuvla setup was successful. Your account is now a full paid account, since your trial ended today.  "
   :text-3          "We are glad that you are using Nuvla. We are sure you will continue to accomplish amazing things with Nuvla. Thank you. "})
