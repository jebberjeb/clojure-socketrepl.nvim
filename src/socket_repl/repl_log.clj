(ns socket-repl.repl-log
  (:import
    (java.io PrintStream File)))

(defn write
  [{:keys [print-stream]} string]
  (.print print-stream string)
  (.flush print-stream))

(defn file
  [repl-log]
  (:file repl-log))

(defn start
  [{:keys [file] :as repl-log}]
  (assoc repl-log
         :print-stream (PrintStream. file)))

(defn stop
  [{:keys [print-stream] :as repl-log}]
  (.close print-stream)
  (dissoc repl-log :print-stream))

(defn new
  []
  {:file (File/createTempFile "socket-repl" ".txt")})
