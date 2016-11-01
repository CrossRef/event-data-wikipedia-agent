(ns event-data-wikipedia-agent.process
  "Process edit events in Wikipedia."

  (:require [org.crossref.event-data-agent-framework.web :as framework-web]
            [org.crossref.event-data-agent-framework.core :as c]
            [crossref.util.doi :as cr-doi])
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as l]
            [clojure.set :refer [difference]])
  (:require [org.httpkit.client :as http]
            [clj-time.coerce :as clj-time-coerce])
  (:import [java.net URLEncoder]
           [java.util UUID])
  (:require [robert.bruce :refer [try-try-again]]))

(def source-token "36c35e23-8757-4a9d-aacf-345e9b7eb50d")
(def version "0.1.6")

; Counters. 
(def heartbeat-restbase (atom 0))
(def heartbeat-restbase-ok (atom 0))
(def heartbeat-restbase-error (atom 0))

(defn build-restbase-url
  "Build a URL that can be retrieved from RESTBase"
  [server-name title revision]
  (let [encoded-title (URLEncoder/encode (.replaceAll title " " "_"))]
    (str "https://" server-name "/api/rest_v1/page/html/" encoded-title "/" revision)))

(defn build-wikipedia-url
  "Build a URL that can be used to fetch the page via the normal HTML website."
  [server-name title]
  (str "https://" server-name "/w/index.php?" (#'http/query-string {:title title})))


(defn process-bodies
  "Process a diff between two HTML documents.
   Return [added-dois removed-dois]."
  [{old-revision :old-revision old-body :old-body
    new-revision :new-revision new-body :new-body
    title :title
    server-name :server-name
    input-event-id :input-event-id}]

  (let [old-dois (framework-web/extract-dois-from-body old-body)
        new-dois (framework-web/extract-dois-from-body new-body)
        added-dois (difference new-dois old-dois)
        removed-dois (difference old-dois new-dois)]

    
    [added-dois removed-dois]))

(defn process
  "Process a new input event by looking up old and new revisions.
  Return an Evidence Record (which may or may not be empty of events)."
  [data]
  
  (let [server-name (get data "server_name")
        server-url (get data "server_url")
        title (get data "title")
        old-revision (get-in data ["revision" "old"])
        new-revision (get-in data ["revision" "new"])
        old-restbase-url (build-restbase-url server-name title old-revision)
        new-restbase-url (build-restbase-url server-name title new-revision)
        
        {old-status :status old-body :body} (try-try-again {:tries 10 :delay 1000 :return? #(= 200 (:status %))} (fn [] @(http/get old-restbase-url {:timeout 10000}))) 
        {new-status :status new-body :body} (try-try-again {:tries 10 :delay 1000 :return? #(= 200 (:status %))} (fn [] @(http/get new-restbase-url {:timeout 10000})))

        timestamp (clj-time-coerce/from-long (* 1000 (get data "timestamp")))

        [added-dois removed-dois]  (when (and (= 200 old-status) (= 200 new-status))
                                    (process-bodies {:old-revision old-revision :old-body old-body
                                                     :new-revision new-revision :new-body new-body
                                                     :title title
                                                     :server-name server-name
                                                     :timestamp timestamp}))

        canonical-url (framework-web/fetch-canonical-url (build-wikipedia-url server-name title))

        added-events (map (fn [doi] {:action "add" :doi doi :event-id (str (UUID/randomUUID))}) added-dois)
        removed-events (map (fn [doi] {:action "delete" :doi doi :event-id (str (UUID/randomUUID))}) removed-dois)
        
        all-events (concat added-events removed-events)
        
        author-url (when-let [author-name (get-in data ["input" "user"])]
                      (str "https://" server-name "/wiki/User:" author-name))
        
        deposits (map (fn [event]     
                           {:uuid (:event-id event)
                            :source_token source-token
                            :subj_id canonical-url
                            :obj_id (cr-doi/normalise-doi (:doi event))
                            :relation_type_id "references"
                            :source_id "wikipedia"
                            :action (:action event)
                            :occurred_at (str timestamp)
                            :subj (merge
                                     {:title title
                                      :issued (str timestamp)
                                      :pid canonical-url
                                      :URL canonical-url
                                      :type "entry-encyclopedia"}
                                     (when author-url {:author {:literal author-url}}))}) all-events)]
    
        (swap! heartbeat-restbase-ok (partial + 2))
        
        (if (= 200 old-status)
          (swap! heartbeat-restbase-ok inc)
          (do 
            (swap! heartbeat-restbase-error inc)
            (l/error "Failed to fetch" old-restbase-url)))

        (if (= 200 new-status)
          (swap! heartbeat-restbase-ok inc)
          (do 
            (swap! heartbeat-restbase-error inc)
            (l/error "Failed to fetch" new-restbase-url)))
                
        (when (> (count added-events) 0)
          (c/send-heartbeat "wikipedia-agent/input/found-doi-added" (count added-events)))
        
        (when (> (count removed-events) 0)
          (c/send-heartbeat "wikipedia-agent/input/found-doi-removed" (count removed-events)))

    {:artifacts []
     :agent {:name "wikipedia" :version version}
     :input {:stream-input data
             :old-revision-id old-revision
             :new-revision-id new-revision
             :old-body old-body
             :new-body new-body}
     :processing {:canonical canonical-url
                  :dois-added added-events
                  :dois-removed removed-events}
     :deposits deposits}))


(defn process-change-event
  "Return Evidence Records from the Event, but only if there are Events in it."
  [change-event]
  ; Only interested in 'edit' type events (not 'comment', 'categorize' etc).
  (when (= (get change-event "type") "edit")
    (let [result (process change-event)]
      (when (not-empty (:deposits result))
        result))))
