(ns socket-repl.system
  "The system created by wiring various components together." 
  (:require
    [neovim-client.nvim :as nvim]
    [socket-repl.socket-repl-plugin :as plugin]
    [socket-repl.repl-log :as repl-log])
  (:gen-class))

(defn new-system
  [debug]
  ;; TODO - separate new & start in nvim
  (let [nvim (if debug
               (nvim/new "localhost" 7777)
               (nvim/new))
        repl-log (repl-log/start (repl-log/new))]
    {:nvim nvim
     :repl-log repl-log
     :plugin (plugin/start (plugin/new debug nvim repl-log))}))

(defn stop
  [{:keys [nvim plugin repl-log] :as system}]
  (plugin/stop plugin)
  (repl-log/stop repl-log)
  (nvim/stop nvim))

(defn -main
  [& args]
  (new-system false))
