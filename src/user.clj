(ns user
  (:require
    [clojure.tools.namespace.repl :refer [refresh]]
    [neovim-client.nvim :as nvim] ;; For repl convenience
    [socket-repl.socket-repl-plugin :as plugin]
    [socket-repl.system :as system]))

(def system-atom (atom nil))

(defn go
  "Start the plugin."
  []
  (reset! system-atom (system/new-system true)))

(defn stop
  []
  (system/stop @system-atom))

(defn reset
  []
  (system/stop @system-atom)
  (refresh :after `go))
