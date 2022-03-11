(ns sixsq.nuvla.server.resources.email.texts
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.server.resources.email.utils :refer [render-email]]))

(defn trial-ending [{:keys [trial-days-left resources]}]
  (let [url        "https://nuvla.io/pricing"
        pre-texts  (conj [(format "Your Nuvla.io trial is will end in %d days. Now is a good time to upgrade and get all the awesome benefits." 
                                  trial-days-left)
                          "Your account will be deactivated when the trial ends."]
                         (when (seq resources) "The following resources will be frozen: "))
        post-texts ["You’re also going to lose access to any data you have in Nuvla.io. You no longer will be able to receive data from your Nuvla Edge devices on Nuvla.io."
                    "Do not worry! We keep it safe with us for when you decide to upgrade."
                    "By upgrading now, you will ensure your data flows stays active. Just add a payment method in your Nulva.io account."
                    (format "Luckily you still have %d days to upgrade." trial-days-left)]
        url-cta    "Choose a Novla.io plan today: "]
    {:subject "Nuvla trial ending"
     :body    [:alternative
               {:type    "text/plain"
                :content (str/join "\n" 
                                   (concat pre-texts 
                                           (map (partial str "- ")
                                                resources) 
                                           post-texts
                                           [(format (str/join "\n" 
                                                              [url-cta 
                                                               "\n    %s\n"]) 
                                                    url)]))}
               {:type    "text/html; charset=utf-8"
                :content (render-email
                           {:title          "Your Nuvla.io trial is ending"
                            :button-text    url-cta
                            :button-url     url
                            :text-1        (str "<p>" 
                                                (str/join "</p><p>" 
                                                          (concat pre-texts 
                                                                  (when (seq resources) 
                                                                    (str "<ul><li>" 
                                                                         (str/join "</li><li>" resources) 
                                                                         "</li></ul>")) 
                                                                  post-texts))
                                                "</p>")})}]}))

(defn trial-ended [{:keys [resources]}]
  (let [url         "https://nuvla.io/pricing"
        pre-texts   (conj ["Your Nuvla.io trail expired today. "]
                          (when (seq resources) "We froze the following resources: ")
                          ["If you want to unfreeze them just add a payment method in your Nuvla.io account and choose a subscription:"])
        post-texts  ["Would you mind sharing briefly with me what kept your from upgrading?"
                     "Feel free to hit reply here or we can just schedule a quick 5-minute Skype/Zoom/Hangouts call."
                     "Would really love to hear your feedback."
                     ""
                     "Thank you."]
        url-cta     "Choose a Novla.io plan today: "]
    {:subject "Nuvla trial ending"
     :body    [:alternative
               {:type    "text/plain"
                :content (str/join "\n" 
                                   (concat pre-texts 
                                           (map (partial str "- ")
                                                resources) 
                                           [(format (str/join "\n" 
                                                              [url-cta 
                                                               "\n    %s\n"]) 
                                                    url)]
                                           post-texts))}
               {:type    "text/html; charset=utf-8"
                :content (render-email
                           {:title          "Your Nuvla.io trial ended"
                            :button-text    url-cta
                            :button-url     url
                            :text-1         (str "<p>" 
                                                 (str/join "</p><p>" 
                                                           (concat pre-texts 
                                                                   (when (seq resources) 
                                                                     (str "<ul><li>" 
                                                                          (str/join "</li><li>" resources) 
                                                                          "</li></ul>"))))
                                                 "</p>")
                           :text-2         post-texts})}]}))
