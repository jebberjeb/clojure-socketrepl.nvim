(ns socket-repl.repl-log
  "Writes (presumably socket output) to the repl log."
  (:require
    [clojure.core.async :as async]
    [socket-repl.nrepl :as nrepl]
    [socket-repl.socket-repl :as socket-repl]
    [socket-repl.util :refer [log-start log-stop]])
  (:import
    (java.io PrintStream File)))

(defn file
  [repl-log]
  (:file repl-log))

(defn input-channel
  "A channel containing strings to be written to the repl log. Each element
  is expected to represent a line, and are println'd to the repl log."
  [repl-log]
  (:input-channel repl-log))

(defn format-input
  [s]
  s)

(defn start
  [{:keys [file input-channel socket-repl nrepl] :as repl-log}]

  (log-start
    "repl-log"
    ;; Subscribe to socket-repl output.
    (socket-repl/subscribe-output socket-repl input-channel)

    ;; Subscribe to nrepl output.
    (nrepl/subscribe-output nrepl input-channel)

    ;; Write input to file.
    (let [print-stream (PrintStream. file)]
      (async/thread
        (loop []
          (when-let [input (async/<!! input-channel)]
            (.println print-stream (#'format-input input))
            (.flush print-stream)
            (recur))))
      (assoc repl-log :print-stream print-stream))))

(defn stop
  [{:keys [print-stream input-channel] :as repl-log}]
  (log-stop
    "repl-log"
    (.close print-stream)
    (async/close! input-channel)
    (dissoc repl-log :print-stream :input-channel)))

(defn new
  [socket-repl nrepl]
  {:socket-repl socket-repl
   :nrepl nrepl
   :file (File/createTempFile "socket-repl" ".txt")
   :print-stream nil
   :input-channel (async/chan 1024)})
