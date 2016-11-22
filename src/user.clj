(ns user
  (:require
    [clojure.tools.namespace.repl :refer [refresh]]
    [neovim-client.nvim :as nvim] ;; For repl convenience
    [socket-repl.socket-repl-plugin :as plugin]
    [socket-repl.system :as system]))

(defn go
  "Start the plugin."
  []
  (system/new-system true))
