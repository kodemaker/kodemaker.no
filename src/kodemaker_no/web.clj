(ns kodemaker-no.web
  (:require clojure.core.memoize
            [clojure.data.json :as json]
            [config :refer [export-directory]]
            [html5-walker.core :as html5-walker]
            [imagine.core :as imagine]
            [kodemaker-no.atomic :as atomic]
            [kodemaker-no.content :refer [load-content]]
            [kodemaker-no.cultivate :refer [cultivate-content]]
            [kodemaker-no.homeless :refer [wrap-content-type-utf-8]]
            [kodemaker-no.images :as images]
            [kodemaker-no.pages :as pages]
            [kodemaker-no.prepare-pages :refer [prepare-pages]]
            [kodemaker-no.validate :refer [validate-content]]
            [optimus.assets :as assets]
            optimus.export
            [optimus-img-transform.core :refer [transform-images]]
            [optimus.optimizations :as optimizations]
            [optimus.prime :as optimus]
            optimus.strategies
            [prone.middleware :as prone]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.resource :refer [wrap-resource]]
            [stasis.core :as stasis]))

(defn get-assets []
  (assets/load-assets
   "public"
   [
    ;: ny
    "/css/kodemaker.css"
    "/css/pygments.css"
    #"/img/.*\..+"
    #"/foto/.*\..+"
    #"/fonts/.*\..+"
    #"/icons/.*\..+"

    ;; gammal
    #"/styles/.*\.css"
    "/favicon.ico"
    #"/certificates/.*\.pdf"
    #"/illustrations/.*\.jpg"
    #"/illustrations/.*\.png"
    #"/thumbs/.*\.jpg"
    #"/forside/.*\.jpg"
    #"/references/.*\.jpg"
    #"/fullsize-photos/.*\.jpg"
    #"/photos/.*\.jpg"
    #"/photos/.*\.svg"
    #"/photos/.*\.png"
    #"/logos/.*\.png"
    #"/logos/.*\.svg"
    #"/images/.*\.png"
    #"/images/.*\.jpg"
    #"/images/blogg/.*\.png"
    #"/videos/.*\.mp4"]))

(defn get-pages []
  (let [content (load-content)
        pages (-> content
                  validate-content
                  cultivate-content
                  pages/create-pages
                  (prepare-pages images/image-asset-config))]
    (stasis/merge-page-sources
     {:site-pages pages
      :raw-pages (:raw-pages content)
      :raw-css (:raw-css content)})))

(def optimize
  (-> (fn [assets options]
        (-> (map #(assoc % :context-path "/assets") assets)
            (transform-images {:regexp #"/fullsize-photos/.*\.jpg"
                               :quality 0.3
                               :width (* 920 2)
                               :progressive true})
            (transform-images {:regexp #"/photos/.*\.jpg"
                               :quality 0.3
                               :width (* 290 2)
                               :progressive true})
            (transform-images {:regexp #"/references/.*\.jpg"
                               :quality 0.3
                               :width (* 680 2)
                               :progressive true})
            (transform-images {:regexp #"/illustrations/.*\.jpg"
                               :quality 0.3
                               :width (* 210 2)
                               :progressive true})
            (transform-images {:regexp #"/thumbs/.*\.jpg"
                               :quality 0.3
                               :width (* 100 2)
                               :progressive false}) ; too small, will be > kb
            (optimizations/all options)
            (->> (remove :bundled)
                 (remove :outdated))))
      (clojure.core.memoize/lru {} :lru/threshold 3)))

(defn- dummy-mail-sender [handler]
  (fn [req]
    (if (= "/send-mail" (:uri req))
      (do (prn "Sending mail!" (slurp (:body req)))
          {:status 302
           :headers {"Location" "/takk/"}})
      (handler req))))

(defn create-app [& [opts]]
  (if (:new-site? opts)
    (-> (atomic/serve-pages (:conn opts))
        (wrap-resource "videos")
        (imagine/wrap-images images/image-asset-config)
        (optimus/wrap get-assets optimizations/none optimus.strategies/serve-live-assets-autorefresh)
        wrap-content-type
        wrap-content-type-utf-8
        prone/wrap-exceptions)
    (-> (stasis/serve-pages get-pages)
        dummy-mail-sender
        (wrap-resource "videos")
        (imagine/wrap-images images/image-asset-config)
        (optimus/wrap get-assets optimize optimus.strategies/serve-live-assets-autorefresh)
        wrap-content-type
        wrap-content-type-utf-8
        prone/wrap-exceptions)))

(declare app)
(defn init-app! [] ;; this horrible thing is here for "lein ring server"
  (def app (create-app)))

(defn extract-images [html]
  (for [node (html5-walker/find-nodes html [:img])]
    (.getAttribute node "src")))

(defn get-images [pages-dir]
  (->> (stasis/slurp-directory pages-dir #"\.html+$")
       vals
       (mapcat extract-images)
       (into #{})))

(defn get-image-assets [pages-dir asset-config]
  (->> (get-images pages-dir)
       (filter #(imagine/image-url? % asset-config))))

(defn export-images [pages-dir dir asset-config]
  (doseq [image (get-image-assets pages-dir asset-config)]
    (-> image
        imagine/image-spec
        (imagine/inflate-spec asset-config)
        (imagine/transform-image-to-file (str dir image)))))

(defn- load-export-dir []
  (stasis/slurp-directory export-directory #"\.[^.]+$"))

(defn export [& args]
  (let [[format] (map read-string args)
        assets (optimize (get-assets) {})
        old-files (load-export-dir)]
    (stasis/empty-directory! export-directory)
    (optimus.export/save-assets assets export-directory)
    (stasis/export-pages (get-pages) export-directory {:optimus-assets assets
                                                       :base-url "https://www.kodemaker.no"})
    (export-images export-directory export-directory (assoc images/image-asset-config :cacheable-urls? true))
    (if (= format :json)
      (println (json/write-str (dissoc (stasis/diff-maps old-files (load-export-dir)) :unchanged)))
      (do
        (println)
        (println "Export complete:")
        (stasis/report-differences old-files (load-export-dir))
        (println)))))

(comment
  (export-images "./build/" "./build/" images/image-asset-config)

  (get-image-assets "./build/" images/image-asset-config)


  (-> "/image-assets/mega-banner/_/references/geir-oterhals.jpg"
      imagine/image-spec
      (imagine/inflate-spec images/image-asset-config)
      (imagine/transform-image-to-file "ui/resources/public/devcard_images/geir.jpg"))

  )
