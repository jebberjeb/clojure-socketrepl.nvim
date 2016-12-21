(ns socket-repl.system
  "The system created by wiring various components together." 
  (:require
    [clojure.core.async :as async]
    [clojure.tools.logging :as log]
    [neovim-client.nvim :as nvim]
    [socket-repl.socket-repl-plugin :as plugin]
    [socket-repl.repl-log :as repl-log]
    [socket-repl.socket-repl :as socket-repl]
    [socket-repl.nrepl :as nrepl])
  (:gen-class))

(defn new-system*
  [nvim]
  (let [socket-repl (socket-repl/start (socket-repl/new))
        nrepl (nrepl/start (nrepl/new))
        repl-log (repl-log/start (repl-log/new socket-repl nrepl))
        plugin (plugin/start (plugin/new nvim nrepl repl-log socket-repl))]
    {:nvim nvim
     :repl-log repl-log
     :socket-repl socket-repl
     :nrepl nrepl
     :plugin plugin}))

(defn new-system
  ([]
   (log/info "starting plugin using STDIO")
   (new-system* (nvim/new)))
  ([uds-filepath]
   (log/info "starting plugin using UDS" uds-filepath)
   (new-system* (nvim/new uds-filepath)))
  ([host port]
   (log/info (format "starting plugin using TCP socket %s:%s" host port))
   (new-system* (nvim/new host port))))

(defn stop
  [{:keys [nvim plugin repl-log socket-repl] :as system}]
  (plugin/stop plugin)
  (repl-log/stop repl-log)
  (socket-repl/stop socket-repl)
  (nvim/stop nvim))

(defn -main
  [& args]
  (if (= 1 (count args))
    (new-system (first args))
    (new-system)))
