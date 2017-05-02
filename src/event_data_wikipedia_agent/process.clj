(ns event-data-wikipedia-agent.process
  "Process edit events in Wikipedia."
  (:require [org.crossref.event-data-agent-framework.core :as c]
            [crossref.util.doi :as cr-doi]
            [event-data-common.status :as status]
            [clojure.data.json :as json]
            [clojure.tools.logging :as l]
            [org.httpkit.client :as http]
            [clj-time.coerce :as clj-time-coerce]
            [clj-time.format :as clj-time-format]
            [robert.bruce :refer [try-try-again]])
  (:import [java.net URLEncoder]
           [java.util UUID]
           [org.apache.commons.codec.digest DigestUtils]))

(def date-format
  (:date-time-no-ms clj-time-format/formatters))

(defn parse
  "Parse an input into a Percolator Action. Note that an Action ID is not supplied."
  [data]
  (when (= "edit" (:type data))
    (let [canonical-url (-> data :meta :uri)
          encoded-title (->> data :meta :uri (re-find #"^.*?/wiki/(.*)$") second)
          title (:title data)

          action-type (:type data)
          old-id (-> data :revision :old)
          new-id (-> data :revision :new)

          old-pid (str (:server_url data) "/w/index.php?title=" encoded-title "&oldid=" old-id)
          new-pid (str (:server_url data) "/w/index.php?title=" encoded-title "&oldid=" new-id)

          new-restbase-url (str (:server_url data) "/api/rest_v1/page/html/" encoded-title "/" new-id)

          ; normalize format
          timestamp-str (clj-time-format/unparse date-format (clj-time-coerce/from-string (-> data :meta :dt)))]

      {
       :url new-pid
       :occurred-at timestamp-str
       :relation-type-id "references"
       :subj {
         :pid new-pid
         :url canonical-url
         :title title
         :api-url new-restbase-url}
       :observations [{:type :content-url
                       :input-url new-restbase-url
                       ; The Wikipedia robots.txt files prohibit access to the API.
                       ; There are separate terms for the RESTBase API, plus we have specific permission.
                       :ignore-robots true
                       :sensitive true}]
     :extra-events [
       {:subj_id old-pid
        :relation_type_id "is_version_of"
        :obj_id canonical-url
        :occurred_at timestamp-str}
       {:subj_id new-pid
        :relation_type_id "is_version_of"
        :obj_id canonical-url
        :occurred_at timestamp-str}
       {:subj_id new-pid
        :relation_type_id "replaces"
        :obj_id old-pid
        :occurred_at timestamp-str}]})))
