(ns socket-repl.socket-repl
  "Provides a channel interface to socket repl input and output."
  (:require
    [clojure.java.io :as io]
    [clojure.core.async :as async])
  (:import
    (java.net Socket)
    (java.io PrintStream)))

(defn write-code
  "Writes a string of code to the socket repl connection."
  [{:keys [connection output-channel]} code-string]
  (let [{:keys [print-stream]} @connection]
    (async/>!! output-channel code-string)
    (.println print-stream code-string)
    (.flush print-stream)))

(defn connect
  "Create a connection to a socket repl."
  [{:keys [connection output-channel]} host port]
  (let [socket (java.net.Socket. host (Integer/parseInt port))
        reader (io/reader socket)]
    (reset! connection {:host host
                        :port port
                        :print-stream (-> socket io/output-stream PrintStream.)
                        :reader reader})
    (future
      (loop []
        (when-let [line (.readLine reader)]
          (async/>!! output-channel line)
          (recur))))))

(defn output-channel
  [socket-repl]
  (:output-channel socket-repl))

(defn connected?
  [{:keys [connection]}]
  (:host @connection))

(defn start
  [socket-repl]
  socket-repl)

(defn stop
  [{:keys [connection output-channel] :as socket-repl}]
  (let [{:keys [reader print-stream]} @connection]
    (.close reader)
    (.close print-stream))
  (async/close! output-channel)
  socket-repl)

(defn new
  []
  {:output-channel (async/chan 1024)
   :connection (atom {:host nil
                      :port nil
                      :reader nil
                      :print-stream nil})})
