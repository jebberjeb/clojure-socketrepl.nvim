(ns socket-repl.system
  "The system created by wiring various components together." 
  (:require
    [neovim-client.nvim :as nvim]
    [socket-repl.socket-repl-plugin :as plugin])
  (:gen-class))

(defn new-system
  [debug]
  ;; TODO - separate new & start in nvim
  (let [nvim (if debug
               (nvim/new "localhost" 7777)
               (nvim/new))]
    {:nvim nvim
     :plugin (plugin/new debug nvim)}))

(defn start
  [{:keys [nvim plugin] :as system}]
  ;; TODO
  ;;(nvim/start nvim)
  (plugin/start plugin))

(defn stop
  [{:keys [nvim plugin] :as system}]
  (plugin/stop plugin)
  (nvim/stop nvim))

(defn -main
  [& args]
  (start (new-system false)))
