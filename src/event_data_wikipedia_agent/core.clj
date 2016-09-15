(ns event-data-wikipedia-agent.core
  (:require [event-data-wikipedia-agent.process :as process])
  (:require [org.crossref.event-data-agent-framework.core :as c]
            [org.crossref.event-data-agent-framework.util :as agent-util]
            [org.crossref.event-data-agent-framework.web :as agent-web]
            [crossref.util.doi :as cr-doi])
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :refer [reader]]
            [clojure.data.json :as json]
            [clojure.core.async :refer [thread buffer chan <!! >!!]])
  (:require [config.core :refer [env]]
            [clj-time.coerce :as coerce]
            [clj-time.core :as clj-time]
            [throttler.core :refer [throttle-fn]])
  (:import [wikipedia RCStreamLegacyClient]
           [java.util.logging Logger Level])
   (:gen-class))



(def input-stream-buffer (buffer 4096))
(def input-stream-chan (chan input-stream-buffer))

; Throttled status updates otherwise it would be unbearable. Also work cross threads.

; Input from RCStream
(def heartbeat-recent-changes-stream (atom 0))

; Process input from RCStream
(def heartbeat-process-input (atom 0))



(defn stream-callback [type-name args]
  (let [arg (first args)
        arg-str (.toString arg)
        parsed (json/read-str arg-str)]
    (swap! heartbeat-recent-changes-stream inc)
    (>!! input-stream-chan parsed)))

(defn- error-callback []
  ; Just panic. Whole process will be restarted.
  ; EX_IOERR
  (System/exit 74))

(defn- new-client []
  (log/info "Subscribe" (env :recent-changes-subscribe-filter))
  (let [the-client (new RCStreamLegacyClient stream-callback (env :recent-changes-subscribe-filter) error-callback)]
    (.run the-client)))
 
(defn run-client
  "Run the ingestion, blocking. If the RCStreamLegacyClient dies, exit."
  []
    ; The logger is mega-chatty (~50 messages per second at INFO). We have alternative ways of seeing what's going on.
    (log/info "Start RC Stream...")
    (.setLevel (Logger/getLogger "io.socket") Level/OFF)
    
    ; Crash the process if there's a failure.  
    (new-client)
    (log/info "RC Stream running."))

(defn process-stream
  "Managed in a thread by the Agent Framework. Process data from the input-stream-chan."
  [artifacts callback]
  (log/info "Start processing stream")
  (loop [item (<!! input-stream-chan)]
    (swap! heartbeat-process-input inc)
    (let [result (process/process-change-event item)]
      (when result
        (prn result)
        (callback result)))
    
    (recur (<!! input-stream-chan))))

(defn ingest-stream
  "Managed in a thread by the Agent Framework. Subscribe to the WC Stream, put data in the input-stream-chan."
  [artifacts callback]
  
  (log/info "Start ingest stream!")
  (run-client))

(defn report-queue-sizes
  ""
  [artifacts callback]
  (c/send-heartbeat "wikipedia-agent/input/recent-changes-input" @heartbeat-recent-changes-stream)
  (reset! heartbeat-recent-changes-stream 0)
  (c/send-heartbeat "wikipedia-agent/process/process-input" @heartbeat-process-input)
  (reset! heartbeat-process-input 0)
  
  (c/send-heartbeat "wikipedia-agent/restbase-input/query" @process/heartbeat-restbase)
  (reset! process/heartbeat-restbase 0)
  
  (c/send-heartbeat "wikipedia-agent/restbase-input/ok" @process/heartbeat-restbase-ok)
  (reset! process/heartbeat-restbase-ok 0)
  
  (c/send-heartbeat "wikipedia-agent/restbase-input/error" @process/heartbeat-restbase-error)
  (reset! process/heartbeat-restbase-error 0)
  
  
  (c/send-heartbeat "wikipedia-agent/process/input-queue" (count input-stream-buffer)))

(def agent-definition
  {:agent-name "wikipedia-agent"
   :version process/version
   :schedule [{:name "queue-sizes"
              :fun report-queue-sizes
              :seconds 10
              :required-artifacts []}]
   :runners [{:name "ingest-stream"
              :fun ingest-stream
              :required-artifacts []}
             {:name "process-stream"
              :fun process-stream
              :threads 100
              :required-artifacts []}]
   :build-evidence (fn [input] nil)
   :process-evidence (fn [evidence] nil)})

(defn -main [& args]
  (c/run args agent-definition))