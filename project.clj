(defproject socket-repl-plugin "0.1.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha12"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [org.clojure/tools.reader "1.0.0-beta4"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jmdk/jmxtools
                                                    com.sun.jmx/jmxri]]
                 [neovim-client "0.1.0"]]
  :main ^:skip-aot socket-repl.system
  :target-path "target/%s"
  :profiles {:uberjar {:aot [clojure.tools.logging.impl
                             socket-repl.socket-repl-plugin
                             socket-repl.system]}})
