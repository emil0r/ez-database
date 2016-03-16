(defproject ez-database "0.4.0-SNAPSHOT"
  :description "Handling database queries with ease"
  :url "https://github.com/emil0r/ez-database"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/java.jdbc "0.4.2"]
                 [slingshot "0.12.2"]
                 [honeysql "0.6.1"]]
  :profiles {:dev {:dependencies [[org.postgresql/postgresql "9.3-1102-jdbc41"]
                                  [joplin.core "0.3.2"]
                                  [joplin.jdbc "0.3.2"]
                                  [midje "1.7.0"]
                                  [yesql "0.5.1"]]}})
