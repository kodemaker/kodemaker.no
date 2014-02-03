(ns kodemaker-no.web
  (:require [kodemaker-no.pages :as pages]
            [kodemaker-no.content :refer [load-content]]
            [kodemaker-no.cultivate :refer [cultivate-content]]
            [kodemaker-no.prepare-pages :refer [prepare-pages]]
            [kodemaker-no.validate :refer [validate-content]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [stasis.core :as stasis]
            [optimus.assets :as assets]
            [optimus.prime :as optimus]
            [optimus.optimizations :as optimizations]
            [optimus.strategies :refer [serve-live-assets]]
            [optimus-img-transform.core :refer [transform-images]]
            [optimus.export]
            [kodemaker-no.homeless :refer [wrap-content-type-utf-8]]
            [config :refer [export-directory]]))

(defn get-assets []
  (assets/load-assets "public" ["/styles/responsive.css"
                                "/styles/unresponsive.css"
                                "/404.html"
                                #"/illustrations/.*\.jpg"
                                #"/thumbs/.*\.jpg"
                                #"/photos/.*\.jpg"
                                #"/photos/.*\.svg"
                                #"/photos/.*\.png"
                                #"/logos/.*\.png"
                                #"/logos/.*\.gif"
                                #"/images/.*\.png"]))

(defn get-pages []
  (-> (load-content)
      validate-content
      cultivate-content
      pages/create-pages
      prepare-pages))

(defn optimize [assets options]
  (-> assets
      (transform-images {:regexp #"/photos/.*\.jpg"
                         :quality 0.3
                         :width (* 290 2)
                         :progressive true})
      (transform-images {:regexp #"/illustrations/.*\.jpg"
                         :quality 0.3
                         :width (* 210 2)
                         :progressive true})
      (transform-images {:regexp #"/thumbs/.*\.jpg"
                         :quality 0.3
                         :width (* 100 2)
                         :progressive false}) ; too small, will be > kb
      (optimizations/all options)))

(def app (-> (stasis/serve-pages get-pages)
             (optimus/wrap get-assets optimize serve-live-assets)
             wrap-content-type
             wrap-content-type-utf-8))

(defn export []
  (let [assets (optimize (get-assets) {})]
    (stasis/empty-directory! export-directory)
    (optimus.export/save-assets assets export-directory)
    (stasis/export-pages (get-pages) export-directory {:optimus-assets assets})))
