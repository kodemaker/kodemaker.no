(ns kodemaker-no.ingestion.person
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [optimus.assets.creation]
            [kodemaker-no.homeless :as h]
            [kodemaker-no.ingestion.video :as video]))

(def cv-keys
  {:person/preferred-techs :preferred-techs
   :person/exclude-techs :exclude-techs})

(def person-keys
  {:person/administration? :administration?
   :person/business-presentations :business-presentations
   :person/certifications :certifications
   :person/cv-picture :cv-picture
   :person/description :description
   :person/education :education
   :person/email-address :email-address
   :person/employments :employments
   :person/endorsement-highlight :endorsement-highlight
   :person/endorsements :endorsements
   :person/experience-since :experience-since
   :person/hobbies :hobbies
   :person/innate-skills :innate-skills
   :person/languages :languages
   :person/open-source-contributions :open-source-contributions
   :person/open-source-projects :open-source-projects
   :person/phone-number :phone-number
   :person/presence :presence
   :person/profile-overview-picture :profile-overview-picture
   :person/profile-page-picture :profile-page-picture
   :person/project-highlights :project-highlights
   :person/projects :projects
   :person/qualifications :qualifications
   :person/recommendations :recommendations
   :person/screencasts :screencasts
   :person/side-projects :side-projects
   :person/start-date :start-date
   :person/title :title
   :person/workshops :workshops
   :cv/description :cv/description})

(defn url [file-name]
  (str (second (re-find #"people(.*).edn" file-name)) "/"))

(defn maybe-pagify [person file-name]
  (if (get person :person/profile-active?)
    (-> person
        (assoc :page/uri (url file-name))
        (assoc :page/kind :page.kind/profile))
    person))

(def presentation-keys
  {:db/ident :id
   :presentation/title :title
   :presentation/description :description
   :presentation/techs :tech
   :presentation/tech-list :tech
   :presentation/event-name :event
   :presentation/date :date
   :presentation/direct-link? :direct-link?
   :presentation/source-url :url
   :presentation/thumb :thumb})

(def presentation-url-keys
  {:presentation/video-url :video
   :presentation/slides-url :slides
   :presentation/source-url :source})

(def location-keys
  {:presentation/event-name :title
   :presentation/event-url :url})

(def call-to-action-keys
  {:presentation/call-to-action-text :text
   :presentation/call-to-action-url :url})

(defn presentation-data [presentation]
  (-> presentation
      (set/rename-keys {:blurb :description})
      (h/keep-vals presentation-keys)
      (merge
       (h/keep-vals (:urls presentation {}) presentation-url-keys)
       (h/keep-vals (:location presentation {}) location-keys)
       (h/keep-vals (:call-to-action presentation {}) call-to-action-keys))
      (h/update-in-existing [:presentation/techs] h/prep-techs)
      (h/update-in-existing [:presentation/tech-list] h/prep-tech-list)
      (h/update-in-existing [:presentation/date] h/parse-local-date)))

(def screencast-keys
  {:screencast/title :title
   :screencast/blurb :blurb
   :screencast/description :description
   :screencast/illustration :illustration
   :screencast/techs :tech
   :screencast/tech-list :tech
   :screencast/published :published
   :screencast/url :url
   :cv/blurb :cv/blurb})

(defn screencast-data [screencast]
  (-> screencast
      (h/keep-vals screencast-keys)
      (h/update-in-existing [:screencast/published] h/parse-local-date)
      (h/update-in-existing [:screencast/techs] h/prep-techs)
      (h/update-in-existing [:screencast/tech-list] h/prep-tech-list)))

(def tech-keys
  {:person/using-at-work :using-at-work
   :person/favorites-at-the-moment :favorites-at-the-moment
   :person/want-to-learn-more :want-to-learn-more})

(def project-keys
  {:project/customer :customer
   :project/summary :summary
   :project/employer :employer
   :project/description :description
   :project/exclude-from-profile? :exclude-from-profile?
   :project/years :years
   :project/start :start
   :project/end :end
   :project/techs :tech
   :project/tech-list :tech
   :list/idx :idx
   :cv/description :cv/description
   :cv/customer :cv/customer})

(defn year-range [start end]
  (let [end (if (= :ongoing end)
              (java.time.LocalDate/now)
              end)]
    (cond
      (and (nil? start) (nil? end)) nil
      (or (nil? start) (nil? end)) [(.getYear (or start end))]
      :default (range (.getYear start) (inc (.getYear end))))))

(defn possibly-infer-years [project]
  (if (nil? (:project/years project))
    (assoc project :project/years (year-range (:project/start project) (:project/end project)))
    project))

(defn project-data [project]
  (-> project
      (h/keep-vals project-keys)
      (h/update-in-existing [:project/employer] (fn [employer] {:db/ident (h/qualify "employer" employer)}))
      (h/update-in-existing [:project/techs] h/prep-techs)
      (h/update-in-existing [:project/tech-list] h/prep-tech-list)
      (h/update-in-existing [:project/start] #(h/parse-local-date (str % "-01")))
      (h/update-in-existing [:project/end] #(h/parse-local-date (str % "-01")))
      possibly-infer-years))

(def open-source-keys
  {:oss-project/url :url
   :oss-project/name :name
   :oss-project/description :description
   :oss-project/techs :tech
   :oss-project/tech-list :tech})

(defn open-source-project [project]
  (-> project
      (h/keep-vals open-source-keys)
      (h/update-in-existing [:oss-project/techs] h/prep-techs)
      (h/update-in-existing [:oss-project/tech-list] h/prep-tech-list)))

(def presentation-product-keys
  {:presentation-product/title :title
   :presentation-product/description :description
   :presentation-product/techs :tech
   :presentation-product/tech-list :tech
   :presentation-product/duration :duration
   :presentation-product/min-participants :min-participants
   :presentation-product/max-participants :max-participants})

(defn presentation-product-data [kind presentation]
  (-> presentation
      (h/keep-vals presentation-product-keys)
      (h/update-in-existing [:presentation-product/techs] h/prep-techs)
      (h/update-in-existing [:presentation-product/tech-list] h/prep-tech-list)
      (assoc :presentation-product/kind kind)))

(def side-project-keys
  {:side-project/title :title
   :side-project/description :description
   :side-project/illustration :illustration
   :side-project/techs :tech
   :side-project/tech-list :tech})

(defn side-project-data [side-project]
  (-> side-project
      (h/keep-vals side-project-keys)
      (h/update-in-existing [:side-project/techs] h/prep-techs)
      (h/update-in-existing [:side-project/tech-list] h/prep-tech-list)
      (cond-> (:link side-project)
        (assoc :side-project/url (-> side-project :link :url)
               :side-project/link-text (-> side-project :link :text)))))

(def recommendation-keys
  {:recommendation/title :title
   :recommendation/description :blurb
   :recommendation/url :url
   :recommendation/link-text :link-text
   :recommendation/techs :tech
   :recommendation/tech-list :tech})

(defn recommendation-data [idx recommendation]
  (-> recommendation
      (h/keep-vals recommendation-keys)
      (h/update-in-existing [:recommendation/techs] h/prep-techs)
      (h/update-in-existing [:recommendation/tech-list] h/prep-tech-list)
      (assoc :list/idx idx)
      (cond-> (:link recommendation)
        (assoc :recommendation/url (-> recommendation :link :url)
               :recommendation/link-text (-> recommendation :link :text)))))

(defn prep-projects [projects]
  (->> projects
       (map-indexed (fn [idx project]
                      (assoc project :idx idx)))
       (mapv project-data)))

(defn as-ordered-list [xs]
  (mapv #(assoc %2 :list/idx %1) (range) xs))

(defn prep-tech-preferences [techs]
  (->> techs
       h/prep-techs
       (map-indexed (fn [idx tech]
                      {:list/idx idx
                       :list/ref tech}))))

(defn add-inline-video-information [person-ident presentation]
  (let [video (video/video-data person-ident presentation)]
    (cond-> presentation
      video (assoc :presentation/video video)
      (:page/uri video) (assoc :presentation/video-url (:page/uri video)))))

(defn profile-data [file-name person]
  (let [ident (h/qualify "person" (:id person))
        presentations (concat (:presentations person)
                              (:upcoming person)
                              (:appearances person))]
    (-> person
        (h/keep-vals person-keys)
        (merge (h/keep-vals (get-in person [:cv :default] {}) cv-keys))
        (assoc :db/ident ident)
        (assoc :person/presentations (->> (mapv presentation-data presentations)
                                          (mapv #(add-inline-video-information ident %))))
        (h/update-in-existing [:person/screencasts] #(as-ordered-list (map screencast-data %)))
        (h/update-in-existing [:person/projects] prep-projects)
        (h/update-in-existing [:person/open-source-projects] #(as-ordered-list (map open-source-project %)))
        (h/update-in-existing [:person/open-source-contributions] #(as-ordered-list (map open-source-project %)))
        (h/update-in-existing [:person/side-projects] #(as-ordered-list (map side-project-data %)))
        (h/update-in-existing [:person/recommendations] (partial map-indexed #(recommendation-data %1 %2)))
        (h/update-in-existing [:person/business-presentations] (partial mapv #(presentation-product-data :presentation %)))
        (h/update-in-existing [:person/endorsements] as-ordered-list)
        (h/update-in-existing [:person/workshops] (partial mapv #(presentation-product-data :workshop %)))
        (h/update-in-existing [:person/start-date] h/parse-local-date-time)
        (h/update-in-existing [:person/innate-skills] h/prep-techs)
        (h/update-in-existing [:person/experience-since] str)
        (h/update-in-existing [:person/experience-since] #(Integer/parseInt %))
        (h/update-in-existing [:person/education] as-ordered-list)
        (h/update-in-existing [:person/preferred-techs] prep-tech-preferences)
        (h/update-in-existing [:person/exclude-techs] h/prep-techs)
        (assoc :person/given-name (first (:name person)))
        (assoc :person/family-name (last (:name person)))
        (assoc :person/full-name (str/join " " (:name person)))
        (assoc :person/profile-active? (get person :profile-active? true))
        (assoc :person/quit? (get person :quit? false))
        (merge (h/map-vals h/prep-techs (h/keep-vals (:tech person {}) tech-keys)))
        (maybe-pagify file-name))))

(defn cv-data [file-name person profile]
  (when (and (not (:administration? person))
             (not (:quit? person)))
    [(merge {:page/uri (str "/cv" (url file-name))
             :page/kind :page.kind/cv
             :cv/person (select-keys profile [:db/ident])}
            (select-keys person [:cv/description]))]))

(def blog-post-keys
  {:blog-post/external-url :url
   :blog-post/title :title
   :blog-post/blurb :blurb
   :blog-post/published :published
   :blog-post/techs :tech
   :blog-post/tech-list :tech
   :cv/blurb :cv/blurb})

(defn blog-post-data [author-id blog-post]
  (-> blog-post
      (h/keep-vals blog-post-keys)
      (assoc :blog-post/author {:db/ident author-id})
      (h/update-in-existing [:blog-post/published] h/parse-local-date)
      (h/update-in-existing [:blog-post/techs] h/prep-techs)
      (h/update-in-existing [:blog-post/tech-list] h/prep-tech-list)))

(defn profile-pics [{:keys [id]}]
  (some->> (str "public/foto/profiles/" (name id))
           io/resource
           io/as-file
           file-seq
           (map #(.getPath %))
           (map optimus.assets.creation/file-system-path-to-url-path)
           (filter #(re-find #"\.jpg$" %))
           (map #(second (str/split % #"public")))))

(def overridable-pictures
  [:person/profile-overview-picture
   :person/profile-page-picture
   :person/cv-picture])

(def exclusion-tags
  {:person/profile-overview-picture "no-overview"
   :person/profile-page-picture "no-profile"
   :person/cv-picture "no-cv"})

(defn select-picture [profile k pictures]
  (if-let [picture (get profile k)]
    picture
    (let [pictures (some-> pictures shuffle)
          in-use (vec (vals (select-keys profile overridable-pictures)))
          usable (remove #(re-find (re-pattern (format "\\b%s\\b" (exclusion-tags k))) %) pictures)]
      (or (first (remove (set in-use) usable))
          (first usable)
          (first pictures)
          "/foto/mask.jpg"))))

(defn add-overridable-pictures [profile pictures]
  (loop [profile profile
         [k & ks] overridable-pictures]
    (if (nil? k)
      profile
      (recur (assoc profile k (select-picture profile k pictures)) ks))))

(defn add-pictures [profile pictures]
  [(let [portraits (remove #(re-find #"\bno-circle\b" %) pictures)]
     (cond-> (add-overridable-pictures profile pictures)
       (seq pictures) (assoc :person/profile-pictures (vec pictures))
       (seq portraits) (assoc :person/portraits (vec portraits))))])

(defn create-tx [file-name person]
  (let [person-ident (h/qualify "person" (:id person))
        profile (profile-data file-name person)]
    (concat
     (add-pictures profile (profile-pics person))
     (cv-data file-name person profile)
     (map (partial blog-post-data person-ident) (:blog-posts person)))))

(comment
  (create-tx "people/magnar.edn" (read-string (slurp (clojure.java.io/resource "people/magnar.edn"))))
  (create-tx "people/stig.edn" (read-string (slurp (clojure.java.io/resource "people/stig.edn"))))

  (require '[datomic-type-extensions.api :as d])
  (def conn (d/connect "datomic:mem://kodemaker"))
  (def db (d/db conn))

  (d/touch (first (:person/recommendations (d/entity db :person/odin))))

  (add-pictures
   {:person/cv-picture "/images/cv.jpg"}
   []
   [])

  (add-pictures {} [] [])

  (add-pictures
   {}
   ["/images/a.jpg"
    "/images/b.jpg"]
   ["/images/portraits/1.jpg"
    "/images/portraits/2.jpg"])

  (add-overridable-pictures
   {:person/cv-picture "/images/cv.jpg"}
   ["/images/a.jpg"
    "/images/b.jpg"])
  )
