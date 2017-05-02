(ns event-data-wikipedia-agent.core
  (:require [event-data-wikipedia-agent.process :as process]
            [org.crossref.event-data-agent-framework.core :as c]
            [event-data-common.status :as status]
            [crossref.util.doi :as cr-doi]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.core.async :refer [alts!! timeout thread buffer chan <!! >!!]]
            [clj-http.client :as client]
            [config.core :refer [env]]
            [clj-time.coerce :as coerce]
            [clj-time.core :as clj-time]
            [throttler.core :refer [throttle-fn]])
   (:gen-class))

(def version (System/getProperty "event-data-wikipedia-agent.version"))
(def source-token "36c35e23-8757-4a9d-aacf-345e9b7eb50d")
(def license "https://creativecommons.org/publicdomain/zero/1.0/")
(def source-id "wikipedia")

(def stream-url "https://stream.wikimedia.org/v2/stream/recentchange")
(def action-chunk-size 100)

(def action-input-buffer 1000000)
(def action-chan (delay (chan action-input-buffer (partition-all action-chunk-size))))

(defn run
  [c]
  "Send parsed events to the chan and block.
   On exception, log and exit (allowing it to be restarted)"
  (try
    (let [response (client/get stream-url {:as :stream})]
      (with-open [reader (io/reader (:body response))]
        (doseq [line (line-seq reader)]
          (let [timeout-ch (timeout 1000)
                result-ch (thread (or line :timeout))
                [x chosen-ch] (alts!! [timeout-ch result-ch])]
              ; timeout: x is nil, xs is nil
              ; null from server: x is :nil, xs is rest
              ; data from serer: x is data, xs is rest
              (cond
                (nil? x) nil
                (= :timeout x) nil
                :default
                    (when (.startsWith x "data:")
                      (when-let [parsed (process/parse (json/read-str (.substring x 5) :key-fn keyword))]
                        (>!! c parsed))))))))
                    
    (catch Exception ex
      ; Could be SocketException, UnknownHostException or something worse!
      ; log and exit.
      (log/error "Error subscribing to stream" (.getMessage ex))
      (.printStackTrace ex))))

(defn run-loop
  [c]
  (loop []
    (log/info "Starting...")
    (run c)
    (log/info "Stopped!")
    (Thread/sleep 1000)
    (recur)))

(defn ingest-stream
  "Managed in a thread by the Agent Framework. Subscribe to the WC Stream, put data in the input-stream-chan."
  [_ _]
  (log/info "Start ingest stream!")
  (run-loop @action-chan)
  (log/info "Stopped ingest stream."))


(defn run-send
  "Take chunks of Actions from the action-chan, assemble into Percolator Input Packages, put them on the input-package-channel.
   Blocks forever."
  [artifacts input-package-channel]
  ; Take chunks of inputs, a few tweets per input bundle.
  ; Gather then into a Page of actions.
  (log/info "Waiting for chunks of actions...")
  (let [c @action-chan]
    (loop [actions (<!! c)]
      (log/info "Got a chunk of" (count actions) "actions")
      (let [payload {:pages [{:actions actions}]
                     :agent {:version version}
                     :license license
                     :source-token source-token
                     :source-id source-id}]
        (status/send! "wikipedia-agent" "send" "input-package" (count actions))
        (>!! input-package-channel payload)
        (log/info "Sent a chunk of" (count actions) "actions"))
      (recur (<!! c)))))


(def agent-definition
  {:agent-name "wikipedia-agent"
   :version version
   :schedule []
   :runners [{:name "ingest-stream"
              :fun ingest-stream
              :required-artifacts []}
              {:name "run-send"
               :fun run-send
               :required-artifacts []}]
   :build-evidence (fn [input] nil)
   :process-evidence (fn [evidence] nil)})

(defn -main [& args]
  (c/run agent-definition))
