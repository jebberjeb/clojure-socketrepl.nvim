(ns user
  (:require
    [clojure.tools.namespace.repl :refer [refresh]]
    [neovim-client.nvim :as nvim] ;; For repl convenience
    [socket-repl.socket-repl-plugin :as plugin]
    [socket-repl.system :as system]))

(def system-atom (atom nil))

(defn nvim
  "Get the nvim component, for convenience"
  []
  (:nvim @system-atom))

(defn go
  "Start the plugin."
  []
  (reset! system-atom (system/new-system "localhost" 7777)))

(defn stop
  []
  (system/stop @system-atom)
  (reset! system-atom nil))

(defn reset
  []
  (system/stop @system-atom)
  (refresh :after `go))
