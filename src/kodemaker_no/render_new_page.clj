(ns kodemaker-no.render-new-page
  (:require [clojure.java.io :as io]
            [dumdom.string :as dumdom]
            [optimus.link :as link]
            [ui.layout :as layout]
            [ui.sections :as sections]))

(defn render-section [section]
  (try
    ((case (:kind section)
       :article sections/article-section
       :banner sections/banner-section
       :bruce sections/bruce-section
       :container sections/container-section
       :cv-intro sections/cv-intro-section
       :definitions sections/definition-section
       :enumeration sections/enumeration-section
       :footer layout/footer
       :grid sections/grid-section
       :grid-header sections/grid-header-section
       :header layout/header-section
       :hip sections/hip-section
       :profile sections/profile-section
       :pønt sections/pønt-section
       :seymour sections/seymour-section
       :tech-intro sections/tech-intro-section
       :titled sections/titled-section
       :vertigo sections/vertigo-section
       :widescreen sections/widescreen-section) section)
    (catch Exception e
      (throw (ex-info (format "Unable to render section: %s" (.getMessage e)) {:section section})))))

(defn- head-title [title]
  (if-let [title-str (or (:head title) (and (string? title) title))]
    (str title-str " | Kodemaker")
    "Kodemaker"))

(defn render-page [page request]
  (str "<!DOCTYPE html>"
   (dumdom/render
    [:html
     [:head
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
      [:link {:rel "stylesheet" :href "https://fonts.googleapis.com/css?family=Lato:300,400&display=swap"}]
      [:link {:rel "stylesheet" :href (link/file-path request "/css/kodemaker.css")}]
      [:link {:rel "stylesheet" :href (link/file-path request "/css/pygments.css")}]
      [:link {:href (link/file-path request "/favicon.ico") :rel "icon" :type "image/x-icon"}]
      [:link {:href (link/file-path request "/favicon.ico") :rel "shortcut icon" :type "image/ico"}]
      [:link {:href (link/file-path request "/favicon.ico") :rel "shortcut icon" :type "image/x-icon"}]
      [:link {:href (link/file-path request "/favicon.ico") :rel "shortcut icon" :type "image/vnd.microsoft.icon"}]
      [:title (head-title (:title page))]]
     [:body
      [:script (slurp (io/resource "public/scripts/analytics.js"))]
      (map render-section (:sections page))]])))
