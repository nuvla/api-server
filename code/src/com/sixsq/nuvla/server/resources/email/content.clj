(ns com.sixsq.nuvla.server.resources.email.content)

(defn trial-ending [{:keys [days-left resources]}]
  {:template        :trial
   :subject         "Nuvla trial ending"
   :title           "Your Nuvla trial is ending"
   :button-text     "Add a Payment Method"
   :button-url      "https://nuvla.io/ui/sign-in?redirect=profile"
   :text-1          (format "We hope you are enjoying using Nuvla. Your trial will end in %d days. Make sure you enter a payment method before then, so that you can continue using the service when the trial ends."
                            days-left)
   :text-2          "If you are working within an organisation that already has a subscription, just get in touch with them to know which group you can use to benefit from their subscription. If you are not sure, contact us and we’ll help you."
   :resources-title (format "Without a payment method in %d days, the following resources will be frozen (but not deleted yet):" days-left)
   :resources       resources
   :text-3          "Don’t worry! While we wait for you to set up a payment method, we will keep your data and resources safe for 30 days. After this time, they will be automatically deleted."
   :text-4          (format "By entering a payment method, you will ensure you continue to have access to all the benefits of the platform and the marketplace. Just add a payment method via your profile in your Nuvla account. Luckily you still have %d days left." days-left)})

(defn trial-ended [{:keys [resources]}]
  {:template        :trial
   :subject         "Nuvla trial ended"
   :title           "Your Nuvla trial ended"
   :button-text     "Add a Payment Method"
   :button-url      "https://nuvla.io/ui/sign-in?redirect=profile"
   :resources-title "The following resources are now frozen: "
   :resources       resources
   :text-1          (str "Your Nuvla trial expired today." (when (seq resources) " However, you have not defined a payment method. This is why we have frozen the following resources:"))
   :text-4          "We will keep your resources frozen for 30 days. After this time, they will be removed permanently, unless you add a payment method within that time."
   :text-5          "If you have feedback on what you liked and disliked about Nuvla during the trial, we would love to hear it.  Please send us an email or drop the feedback in the chat bot."})

(defn trial-ended-with-payment [{:keys [resources]}]
  {:template        :trial
   :title           "Your Nuvla trial ended"
   :text-1          "Your Nuvla trial has ended and you’re all set up ."
   :resources-title "You are using the following resources: "
   :resources       resources
   :text-3          "From now on, you will be invoiced for all paying resources you consume on Nuvla. You can track your consumption, access your invoices or change your payment method via your profile in Nuvla."
   :text-4          "We thank you for continuing your journey to the edge with us. We would also love to hear about the amazing things you plan to accomplish with Nuvla."})

(defn coupon-ending [{:keys [days-left]}]
  {:template        :trial
   :id              :coupon-ending
   :subject         "Nuvla coupon ending"
   :title           "Your Nuvla coupon is ending"
   :text-1          (format "Your coupon will end in %d days." days-left)
   :text-4          "If you are working within an organisation, get in touch with them to see, if you are eligible for another coupon."})

(defn coupon-ended [_]
  {:template        :trial
   :id              :coupon-ended
   :subject         "Nuvla coupon ended"
   :title           "Your Nuvla coupon ended"
   :text-1          "Your Nuvla coupon expired today."
   :text-4          "If you are working within an organisation, get in touch with them to see, if you are eligible for another coupon."})
