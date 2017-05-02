(ns event-data-wikipedia-agent.core-test
  (:require [clojure.test :refer :all]
            [event-data-wikipedia-agent.process :as process]))

(def input-event
  {:bot false,
   :user "Ss112",
   :id 213768786,
   :timestamp 1491582266,
   :wiki "dewiki",
   :revision {:new 164337986, :old 164332799},
   :server_script_path "/w",
   :minor true,
   :server_url "https://de.wikipedia.org",
   :server_name "de.wikipedia.org",
   :length {"new" 17813, "old" 17813},
   :title "Clean Bandit",
   :type "edit",
   :meta {
     :request_id "35024bf9-4076-4dec-9702-05f3445c46c3",
     :offset 124177711,
     :dt "2017-04-07T18:24:26+02:00",
     :uri "https://de.wikipedia.org/wiki/Clean_Bandit",
     :partition 0,
     :id "ab2f6ca6-1bae-11e7-a1d3-90b11c28ca3a",
     :domain "de.wikipedia.org",
     :schema_uri "mediawiki/recentchange/1",
     :topic "eqiad.mediawiki.recentchange"},
   :namespace 0,
   :comment "/* Singles */"})

(def non-edit-input-event
  {:bot false,
   :user "Ss112",
   :id 213768786,
   :timestamp 1491582266,
   :wiki "dewiki",
   :revision {:new 164337986, :old 164332799},
   :server_script_path "/w",
   :minor true,
   :server_url "https://de.wikipedia.org",
   :server_name "de.wikipedia.org",
   :length {"new" 17813, "old" 17813},
   :title "Clean Bandit",
   ; Not an edit.
   :type "delete",
   :meta {
     :request_id "35024bf9-4076-4dec-9702-05f3445c46c3",
     :offset 124177711,
     :dt "2017-04-07T18:24:26+02:00",
     :uri "https://de.wikipedia.org/wiki/Clean_Bandit",
     :partition 0,
     :id "ab2f6ca6-1bae-11e7-a1d3-90b11c28ca3a",
     :domain "de.wikipedia.org",
     :schema_uri "mediawiki/recentchange/1",
     :topic "eqiad.mediawiki.recentchange"},
   :namespace 0,
   :comment "/* Singles */"})

(def expected-action
  {:occurred-at "2017-04-07T16:24:26Z"
   :url "https://de.wikipedia.org/w/index.php?title=Clean_Bandit&oldid=164337986"
   :relation-type-id "references"
   :subj {
    :pid "https://de.wikipedia.org/w/index.php?title=Clean_Bandit&oldid=164337986"
    :url "https://de.wikipedia.org/wiki/Clean_Bandit"
    :title "Clean Bandit"
    :api-url "https://de.wikipedia.org/api/rest_v1/page/html/Clean_Bandit/164337986"}
   :observations [{:type :content-url
                   :input-url "https://de.wikipedia.org/api/rest_v1/page/html/Clean_Bandit/164337986"
                   :sensitive true
                   :ignore-robots true}]
   :extra-events [
     {:subj_id "https://de.wikipedia.org/w/index.php?title=Clean_Bandit&oldid=164332799"
      :relation_type_id "is_version_of"
      :obj_id "https://de.wikipedia.org/wiki/Clean_Bandit"
      :occurred_at "2017-04-07T16:24:26Z"}
     {:subj_id "https://de.wikipedia.org/w/index.php?title=Clean_Bandit&oldid=164337986"
      :relation_type_id "is_version_of"
      :obj_id "https://de.wikipedia.org/wiki/Clean_Bandit"
      :occurred_at "2017-04-07T16:24:26Z"}
     {:subj_id "https://de.wikipedia.org/w/index.php?title=Clean_Bandit&oldid=164337986"
      :relation_type_id "replaces"
      :obj_id "https://de.wikipedia.org/w/index.php?title=Clean_Bandit&oldid=164332799"
      :occurred_at "2017-04-07T16:24:26Z"}]})

(deftest parse-test
  (testing "parse should convert into an Action"
    (is (= (process/parse input-event) expected-action))
    (is (nil? (process/parse non-edit-input-event)) "If it's no an edit, an input event should not be parsed.")))
