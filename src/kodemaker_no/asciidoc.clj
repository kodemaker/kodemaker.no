(ns kodemaker-no.asciidoc
  (:require [asciidoclj.core :as adoc]
            [clojure.string :as str]))

(def adoc-parse (memoize adoc/parse))

(defn- find-part [doc title]
  (first (filter #(= title (:title %)) (:parts doc))))

(defn- content [part]
  (when part
    (-> part :content
        (str/replace #"<div class=\"[^\"]+\">\n" "")
        (str/replace #"\n</div>\n?" ""))))

(defn- htmlize-part [part]
  (str "<h2>" (:title part) "</h2>" (content part)))

(defn- nil-if-blank [s]
  (if (empty? s) nil s))

(defn- patch-together-article [doc]
  (->> doc :parts
       (remove #(-> % :title #{":lead" ":aside" ":ignore"}))
       (map htmlize-part)
       (str/join)
       (nil-if-blank)))

(defn parse-page [s]
  (let [doc (adoc-parse (str s "\n\n== :ignore"))]
    {:title (-> doc :header :document-title)
     :url (-> doc :header :attributes :url)
     :illustration (-> doc :header :attributes :illustration)
     :lead (-> doc (find-part ":lead") content)
     :article (-> doc patch-together-article)
     :aside (-> doc (find-part ":aside") content)}))
