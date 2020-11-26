(defproject ez-database "0.8.2"

  :description "Handling database queries with ease"

  :url "https://github.com/emil0r/ez-database"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/java.jdbc "0.7.11"]
                 [honeysql "0.9.10"]]

  :profiles {:dev {:dependencies [[org.postgresql/postgresql "42.0.0"]
                                  [joplin.core "0.3.10"]
                                  [joplin.jdbc "0.3.10"]
                                  [midje "1.9.9"]
                                  [yesql "0.5.3"]]}})
