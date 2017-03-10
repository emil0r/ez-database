(defproject ez-database "0.6.0"
  :description "Handling database queries with ease"
  :url "https://github.com/emil0r/ez-database"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [honeysql "0.8.2"]]
  :profiles {:dev {:dependencies [[org.postgresql/postgresql "42.0.0"]
                                  [joplin.core "0.3.10"]
                                  [joplin.jdbc "0.3.10"]
                                  [midje "1.9.0-alpha6"]
                                  [yesql "0.5.3"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]
                                  [org.postgresql/postgresql "42.0.0"]
                                  [joplin.core "0.3.10"]
                                  [joplin.jdbc "0.3.10"]
                                  [midje "1.9.0-alpha6"]
                                  [yesql "0.5.3"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]
                                  [org.postgresql/postgresql "42.0.0"]
                                  [joplin.core "0.3.10"]
                                  [joplin.jdbc "0.3.10"]
                                  [midje "1.9.0-alpha6"]
                                  [yesql "0.5.3"]]}})
