(ns socket-repl.nrepl
  "Provides a channel interface to nrepl input and output."
  (:require
    [clojure.java.io :as io]
    [clojure.core.async :as async]
    [clojure.tools.nrepl :as nrepl]
    [socket-repl.util :refer [log-start log-stop]]))

(defn subscribe-output
  "Pipes the nrepl output to `chan`"
  [{:keys [output-channel]} chan]
  (async/pipe output-channel chan))

(defn connect
  "Create a connection to a nrepl."
  [{:keys [connection]} host port]
  (let [port (Integer/parseInt port)]
    (reset! connection {:host host
                        :port port
                        :connection (nrepl/connect :port port)})))

(defn output-channel
  [nrepl]
  (:output-channel nrepl))

(defn input-channel
  [nrepl]
  (:input-channel nrepl))

(defn client
  [nrepl]
  (-> nrepl
      :connection
      deref
      :connection
      (nrepl/client 1000)))

(defn connected?
  [{:keys [connection]}]
  (:host @connection))

(defn start
  [{:keys [input-channel output-channel] :as nrepl}]
  (log-start
    "nrepl"
    (async/thread
      (loop []
        (when-let [input (async/<!! input-channel)]
          (when (connected? nrepl)
            (async/onto-chan
              output-channel
              (nrepl/response-values
                (nrepl/message (client nrepl)
                               {:op "eval" :code (pr-str input)}))
              false))
          (recur))))
    nrepl))

(defn stop
  [{:keys [connection output-channel input-channel] :as nrepl}]
  (log-stop
    "nrepl"
    (.close (:connection @connection))
    (async/close! output-channel)
    (async/close! input-channel)
    nrepl))

(defn new
  []
  {:input-channel (async/chan 1024)
   :output-channel (async/chan 1024)
   :connection (atom {:host nil
                      :port nil
                      :connection nil})})
