(defproject org.clojars.bcambel/hsm.batch "0.1.0-SNAPSHOT"
  :description "Hackersome Batch processing layer"
  :url "http://hackersome.com/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
    [org.clojure/clojure "1.6.0"]

      [com.taoensso/timbre "3.3.1-1cd4b70"]
      [clj-aws-s3 "0.3.10" :exclusions [joda-time]]
      [midje "1.7.0-SNAPSHOT"]
      [clj-time "0.6.0"]
      [cheshire "5.3.1"]
      [cascading/cascading-hadoop2-mr1 "2.6.0" ]
       [cascalog/cascalog "2.1.1" 
       :exclusions [cascading/cascading-hadoop org.codehaus.plexus/plexus-utils commons-codec cascading/cascading-local]]
      [eu.bitwalker/UserAgentUtils "1.15"]
      [org.clojure/core.memoize "0.5.6"]
    ]

  :repositories {
    "conjars" "http://conjars.org/repo/"
    "sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :main hsm.batch
  :aot [hsm.batch]
  :target-path "target/%s"
  :uberjar-name "hsm.batch.jar"
  :profiles {
        :provided
             {:dependencies
              [
               [org.apache.hadoop/hadoop-mapreduce-client-jobclient "2.4.1"]
               [org.apache.hadoop/hadoop-common "2.4.1" 
                 :exclusions [commons-codec org.apache.httpcomponents/httpclient org.apache.httpcomponents/httpcore]]
               ]}
    :uberjar {:aot :all}})
