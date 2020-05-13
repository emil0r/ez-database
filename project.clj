(defproject ez-database "0.8.0-SNAPSHOT"

  :description "Handling database queries with ease"

  :url "https://github.com/emil0r/ez-database"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/java.jdbc "0.7.8"]
                 [honeysql "0.9.3"]]

  :profiles {:dev {:dependencies [[org.postgresql/postgresql "42.0.0"]
                                  [joplin.core "0.3.10"]
                                  [joplin.jdbc "0.3.10"]
                                  [midje "1.9.2"]
                                  [yesql "0.5.3"]
                                  [com.layerware/hugsql "0.4.9"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]
                                  [org.postgresql/postgresql "42.0.0"]
                                  [joplin.core "0.3.10"]
                                  [joplin.jdbc "0.3.10"]
                                  [midje "1.9.2"]
                                  [yesql "0.5.3"]
                                  [com.layerware/hugsql "0.4.9"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]
                                  [org.postgresql/postgresql "42.0.0"]
                                  [joplin.core "0.3.10"]
                                  [joplin.jdbc "0.3.10"]
                                  [midje "1.9.2"]
                                  [yesql "0.5.3"]
                                  [com.layerware/hugsql "0.4.9"]]}})
