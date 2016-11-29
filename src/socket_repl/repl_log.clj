(ns socket-repl.repl-log
  "Writes (presumably socket output) to the repl log."
  (:require
    [clojure.core.async :as async]
    [socket-repl.socket-repl :as socket-repl])
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

(defn start
  [{:keys [file input-channel socket-repl] :as repl-log}]

  ;; Subscribe to socket-repl output.
  (socket-repl/subscribe-output socket-repl input-channel)

  ;; Write input to file.
  (let [print-stream (PrintStream. file)]
    (async/thread
      (loop []
        (when-let [input (async/<!! input-channel)]
          (.println print-stream input)
          (.flush print-stream)
          (recur))))
    (assoc repl-log :print-stream print-stream)))

(defn stop
  [{:keys [print-stream input-channel] :as repl-log}]
  (.close print-stream)
  (async/close! input-channel)
  (dissoc repl-log :print-stream :input-channel))

(defn new
  [socket-repl]
  {:socket-repl socket-repl
   :file (File/createTempFile "socket-repl" ".txt")
   :print-stream nil
   :input-channel (async/chan 1024)})
