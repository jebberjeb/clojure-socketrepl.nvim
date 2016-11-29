(ns socket-repl.system
  "The system created by wiring various components together." 
  (:require
    [clojure.core.async :as async]
    [neovim-client.nvim :as nvim]
    [socket-repl.socket-repl-plugin :as plugin]
    [socket-repl.repl-log :as repl-log]
    [socket-repl.socket-repl :as socket-repl])
  (:gen-class))

(defn new-system
  [debug]
  ;; TODO - separate new & start in nvim
  (let [nvim (if debug
               (nvim/new "localhost" 7777)
               (nvim/new))
        socket-repl (socket-repl/start (socket-repl/new))
        repl-log (repl-log/start (repl-log/new socket-repl))
        plugin (plugin/start (plugin/new debug nvim repl-log socket-repl))]

    ;; TODO - this feels like part of plugin startup, as it *has* to know
    ;; about repl-log and socket-repl directly.
    (let [mult (async/mult (plugin/code-channel plugin))]
      (async/tap mult (socket-repl/input-channel socket-repl))
      (async/tap mult (repl-log/input-channel repl-log)))

    {:nvim nvim
     :repl-log repl-log
     :socket-repl socket-repl
     :plugin plugin}))

(defn stop
  [{:keys [nvim plugin repl-log socket-repl] :as system}]
  (plugin/stop plugin)
  (repl-log/stop repl-log)
  (socket-repl/stop socket-repl)
  (nvim/stop nvim))

(defn -main
  [& args]
  (new-system false))
