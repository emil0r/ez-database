(defproject ez-database "2.0.0-SNAPSHOT"

  :description "Handling database queries with ease"

  :url "https://github.com/emil0r/ez-database"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/java.jdbc "0.7.11"]
                 [com.github.seancorfield/honeysql "2.4.962"]]

  :profiles {:dev {:dependencies [[org.postgresql/postgresql "42.5.1"]
                                  [joplin.core "0.3.10"]
                                  [joplin.jdbc "0.3.10"]
                                  [midje "1.9.9"]
                                  [yesql "0.5.3"]]}})
