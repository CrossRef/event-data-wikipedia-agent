(defproject event-data-wikipedia-agent "0.1.9"
  :description "Event Data Wikipedia Agent"
  :url "http://eventdata.crossref.org"
  :license {:name "The MIT License (MIT)"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [org.clojure/core.async "0.2.391"]
                 [org.crossref.event-data-agent-framework "0.1.18"]
                 [event-data-common "0.1.29-SNAPSHOT"]
                 [robert/bruce "0.8.0"]
                 [throttler "1.0.0"]
                 [commons-codec/commons-codec "1.10"]
                 [clj-http "2.3.0"]]
  :main ^:skip-aot event-data-wikipedia-agent.core
  :java-source-paths ["src-java"]
  :target-path "target/%s"
  :jvm-opts ["-Duser.timezone=UTC"]
  :profiles {:uberjar {:aot :all}
             :prod {:resource-paths ["config/prod" "lib/socketio.jar"]}
             :dev  {:resource-paths ["config/dev" "lib/socketio.jar"]}})
