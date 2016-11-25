(ns socket-repl.system
  "The system created by wiring various components together." 
  (:require
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
        repl-log (repl-log/start (repl-log/new))
        socket-repl (socket-repl/start (socket-repl/new))]

    ;; TODO - where should this live, if anywhere?
    (clojure.core.async/pipe (socket-repl/output-channel socket-repl)
                             (repl-log/input-channel repl-log))

    ;; TODO - same for plugin? it generates output (code destined for repl)
    ;; It can be fanned out, then piped to repl-log & socket (input)

    {:nvim nvim
     :repl-log repl-log
     :socket-repl socket-repl
     :plugin (plugin/start (plugin/new debug nvim repl-log socket-repl))}))

(defn stop
  [{:keys [nvim plugin repl-log socket-repl] :as system}]
  (plugin/stop plugin)
  (repl-log/stop repl-log)
  (socket-repl/stop socket-repl)
  (nvim/stop nvim))

(defn -main
  [& args]
  (new-system false))
