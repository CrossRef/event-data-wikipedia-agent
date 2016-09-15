(ns event-data-wikipedia-agent.core
  (:require [org.crossref.event-data-agent-framework.core :as c]
            [org.crossref.event-data-agent-framework.util :as agent-util]
            [org.crossref.event-data-agent-framework.web :as agent-web]
            [crossref.util.doi :as cr-doi])
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :refer [reader]])
  (:require [config.core :refer [env]]
            [clj-time.coerce :as coerce]
            [clj-time.core :as clj-time])
   (:gen-class))

(def source-token "36c35e23-8757-4a9d-aacf-345e9b7eb50d")
(def version "0.1.0")

(defn ingest-stream [callback]
  (log/info "Start ingest stream!"))

(def agent-definition
  {:agent-name "wikipedia-agent"
   :version version
   :schedule []
   :runners [{:name "ingest-stream"
              :fun ingest-stream
              :required-artifacts []}]
   :build-evidence (fn [input] nil)
   :process-evidence (fn [evidence] nil)})

(defn -main [& args]
  (c/run args agent-definition))