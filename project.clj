(defproject event-data-wikipedia-agent "0.1.1"
  :description "Event Data Wikipedia Agent"
  :url "http://eventdata.crossref.org"
  :license {:name "The MIT License (MIT)"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.391"]
                 [org.crossref.event-data-agent-framework "0.1.5"]
                 [robert/bruce "0.8.0"]
                 [throttler "1.0.0"]]
  :main ^:skip-aot event-data-wikipedia-agent.core
  :java-source-paths ["src-java"]
  :target-path "target/%s"
 :profiles {:uberjar {:aot :all}
             :prod {:resource-paths ["config/prod" "lib/socketio.jar"]}
             :dev  {:resource-paths ["config/dev" "lib/socketio.jar"]}})
