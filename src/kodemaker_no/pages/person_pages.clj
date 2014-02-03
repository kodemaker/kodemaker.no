(ns kodemaker-no.pages.person-pages
  (:require [kodemaker-no.formatting :refer [to-html comma-separated]]
            [kodemaker-no.markup :refer [render-link]]
            [hiccup.core :as hiccup]
            [clojure.string :as str]))

(defn- link-to-tech [tech]
  (if (:url tech)
    [:a {:href (:url tech)} (:name tech)]
    (:name tech)))

(defn prepend-to-paragraph [html node]
  (str/replace html #"^<p>" (str "<p>" (hiccup/html node))))

(defn append-to-paragraph [html node]
  (str/replace html #"</p>$" (str (hiccup/html node) "</p>")))

(defn- render-recommendation [rec]
  (list [:h3 (:title rec)]
        (when-not (empty? (:tech rec))
          [:p.near.cookie-w [:span.cookie (interpose " " (map link-to-tech (:tech rec)))]])
        (append-to-paragraph (to-html :md (:blurb rec))
                             (list " " (render-link (:link rec))))))

(defn- render-recommendations [recs person]
  (list [:h2 (str (:genitive person) " Anbefalinger")]
        (map render-recommendation recs)))

(defn- render-hobby [hobby]
  [:div.bd
   [:h3.mtn (:title hobby)]
   (prepend-to-paragraph (to-html :md (:description hobby))
                         (if (:url hobby)
                           [:a.illu {:href (:url hobby)} [:img {:src (:illustration hobby)}]]
                           [:img.illu {:src (:illustration hobby)}]))])

(defn- render-hobbies [hobbies _]
  (list [:h2 "Snakker gjerne om"]
        (map render-hobby hobbies)))

(defn- inline-list [label nodes]
  (list [:strong label]
        (comma-separated nodes)
        "<br>"))

(defn- render-tech [tech _]
  [:p
   (when-let [favs (:favorites-at-the-moment tech)]
     (inline-list "Favoritter for tiden: " (map link-to-tech favs)))
   (when-let [more (:want-to-learn-more tech)]
     (inline-list "Vil lære mer: " (map link-to-tech more)))])

(defn- render-presentation [pres]
  [:div.media
   [:a.img.thumb.mts {:href (or (-> pres :urls :video)
                                (-> pres :urls :slides)
                                (throw (Exception. (str "Missing url to video or slides in presentation " (:title pres)))))}
    [:img {:src (:thumb pres)}]]
   [:div.bd
    [:h4.mtn (:title pres)]
    [:p (:blurb pres)
     (when-let [url (-> pres :urls :video)] (list " " [:a.nowrap {:href url} "Se video"]))
     (when-let [url (-> pres :urls :slides)] (list " " [:a.nowrap {:href url} "Se slides"]))
     (when-let [url (-> pres :urls :source)] (list " " [:a.nowrap {:href url} "Se koden"]))]]])

(defn- render-presentations [presentations person]
  (list [:h2 (str (:genitive person) " Foredrag")]
        (map render-presentation presentations)))

(defn- project-link [project]
  (if-let [url (:url project)]
    [:a {:href url} (:name project)]
    (:name project)))

(defn- render-endorsement [endo]
  [:div.media
   (when (:photo endo)
     [:img.img.thumb.mts {:src (:photo endo)}])
   [:div.bd
    [:h4.mtn (:author endo)]
    (if (:title endo)
      [:p.near (:title endo) ", " (-> endo :project project-link)]
      [:p.near (-> endo :project project-link)])
    [:p [:q (:quote endo)]]]])

(defn- render-endorsements [endorsements person]
  (list [:h2 (str (:genitive person) " Referanser")]
        (map render-endorsement endorsements)))


(defn- render-presence-item [item]
  [:li
   [:a {:href (str (:baseUrl item) "/" (:nick item))}
    [:img {:src (str "/logos/" (:logo item)) :title (:title item)}]]])

(defn- render-presence [presence]
  [:ul.logoList
    (when-let [cv (-> presence :cv)]
      (render-presence-item {:baseUrl "http://www.kodemaker.no/cv" :nick cv :logo "cv-24.png" :title "Cv"}))
    (when-let [li (-> presence :linkedin)]
      (render-presence-item {:baseUrl "http://www.linkedin.com" :nick li :logo "linkedin-24.png" :title "LinkedIn"}))
    (when-let [tw (-> presence :twitter)]
      (render-presence-item {:baseUrl "http://www.twitter.com" :nick tw :logo "twitter-24.png" :title "Twitter"}))
    (when-let [so (-> presence :stackoverflow)]
      (render-presence-item {:baseUrl "http://www.stackoverflow.com" :nick so :logo "stackoverflow-24.png" :title "StackOverflow"}))
    (when-let [gh (-> presence :github)]
      (render-presence-item {:baseUrl "http://github.com" :nick gh :logo "github-24.png" :title "GitHub"}))
    (when-let [cw (-> presence :coderwall)]
      (render-presence-item {:baseUrl "http://www.coderwall.com" :nick cw :logo "coderwall-24.png" :title "Coderwall"}))])

(defn- render-aside [person]
  [:div.tight
   [:h4 (:full-name person)]
   [:p
    (:title person) "<br>"
    [:span.nowrap (:phone-number person)] "<br>"
    [:a {:href (str "mailto:" (:email-address person))}
       (:email-address person)]]
    (if (seq (:presence person))
      (render-presence (:presence person)))])

(defn- maybe-include [person kw f]
  (when (kw person)
    (f (kw person) person)))

(defn- person-page [person]
  {:title (:full-name person)
   :illustration (-> person :photos :half-figure)
   :lead [:p (:description person)]
   :aside (render-aside person)
   :body (list
          (maybe-include person :tech render-tech)
          (maybe-include person :recommendations render-recommendations)
          (maybe-include person :hobbies render-hobbies)
          (maybe-include person :presentations render-presentations)
          (maybe-include person :endorsements render-endorsements))})

(defn person-pages [people]
  (into {} (map (juxt :url #(partial person-page %)) people)))
