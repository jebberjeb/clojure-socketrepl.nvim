(ns socket-repl.socket-repl
  "Provides a channel interface to socket repl input and output."
  (:require
    [clojure.java.io :as io]
    [clojure.core.async :as async]
    [socket-repl.repl-log :as repl-log])
  (:import
    (java.net Socket)
    (java.io PrintStream)))

(defn write-code
  "Writes a string of code to the socket repl connection."
  [{:keys [repl-log connection]} code-string]
  (let [{:keys [print-stream]} @connection]
    (async/>!! (repl-log/input-channel repl-log) code-string)
    (.println print-stream code-string)
    (.flush print-stream)))

(defn connect
  "Create a connection to a socket repl."
  [{:keys [repl-log connection]} host port]
  (let [socket (java.net.Socket. host (Integer/parseInt port))
        reader (io/reader socket)]
    (reset! connection {:host host
                        :port port
                        :print-stream (-> socket io/output-stream PrintStream.)
                        :reader reader})
    (future
      (loop []
        (when-let [line (.readLine reader)]
          (async/>!! (repl-log/input-channel repl-log) line)
          (recur))))))

(defn connected?
  [{:keys [connection]}]
  (:host @connection))

(defn start
  [socket-repl]
  socket-repl)

(defn stop
  [{:keys [connection] :as socket-repl}]
  (let [{:keys [reader print-stream]} @connection]
    (.close reader)
    (.close print-stream))
  socket-repl)

(defn new
  [repl-log]
  {:repl-log repl-log
   :connection (atom {:host nil
                      :port nil
                      :reader nil
                      :print-stream nil})})
