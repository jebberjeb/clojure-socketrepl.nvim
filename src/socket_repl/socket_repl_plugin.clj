(ns socket-repl.socket-repl-plugin
  "A plugin which connects to a running socket repl and sends output back to
  Neovim."
  (:require
    [clojure.core.async :as async :refer [go go-loop >! <! >!! <!!]]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.tools.logging :as log]
    [neovim-client.message :as message]
    [neovim-client.nvim :as nvim])
  (:import
    (java.net Socket)
    (java.io PrintStream File)))

(defn position
  "Find the position in a code string given line and column."
  [code-str [y x]]
  (->> code-str
       string/split-lines
       (take (dec y))
       (string/join "\n")
       count
       (+ (inc x))))

(defn get-form-at
  "Returns the enclosing form from a string a code using [row col]
  coordinates."
  [code-str coords]
  (let [pos (position code-str coords)]
    (read-string
      ;; Start at the last index of paren on or before `pos`, read a form.
      (subs code-str (if (= \( (.charAt code-str pos))
                       pos
                       (.lastIndexOf (subs code-str 0 pos) "("))))))

(defn output-file
  []
  (File/createTempFile "socket-repl" ".txt"))

(defn connection
  "Create a connection to a socket repl."
  [host port]
  (let [socket (java.net.Socket. host (Integer/parseInt port))]
    {:host host
     :port port
     :out (-> socket
              io/output-stream
              PrintStream.)
     :in (io/reader socket)}))

(defn write-output
  "Write a string to the output file."
  [{:keys [:file-stream]} string]
  (.print file-stream string)
  (.flush file-stream))

(defn write-error
  "Write a throwable's stack trace to the output file."
  [connection throwable]
  (write-output
    connection
    (str "\n##### PLUGIN ERR #####\n"
         (.getMessage throwable) "\n"
         (string/join "\n" (map str (.getStackTrace throwable)))
         \n"######################\n")))

(defn write-code
  "Writes a string of code to the socket repl connection."
  [{:keys [:out] :as connection} code-string]
  (write-output connection (str code-string "\n"))
  (.println out code-string)
  (.flush out))

(defn connected-to-socket-repl?
  "Returns true if currently connected to a socket repl server."
  [connection]
  ;; Choose one of the properties set by establishing the connection.
  (:host connection))

(defn run-command
  [nvim connection-atom f]
  (fn [msg]
    (if-not (connected-to-socket-repl? @connection-atom)
      (nvim/vim-command-async
        nvim
        ":echo 'Use :Connect host:port to connect to a socket repl'"
        (fn [_]))
      (do
        (swap! connection-atom assoc :last (System/currentTimeMillis))
        (f msg)))
    :done))

(defn get-rlog-buffer
  "Returns the buffer w/ b:rlog set, if one exists."
  [nvim]
  (some->> (nvim/vim-get-buffers nvim)
           (filter #(nvim/buffer-get-var nvim % "rlog"))
           first))

(defn get-rlog-buffer-name
  "Returns the name of the buffer w/ b:rlog set, if one exists."
  [nvim]
  (let [buffer (get-rlog-buffer nvim)]
    (when buffer (nvim/buffer-get-name nvim buffer))))

(defn get-rlog-buffer-number
  "Returns the number of the buffer w/ b:rlog set, if one exists."
  [nvim]
  (let [buffer (get-rlog-buffer nvim)]
    (when buffer (nvim/buffer-get-number nvim buffer))))

(defn connect!
  "Connect to a socket repl. Adds the connection to the `socket-repl-conn`
  atom. Creates `go-loop`s to delegate input from the socket to `handler` one
  line at a time.

  `handler` is a function which accepts one string argument."
  [socket-repl-conn host port handler]
  (let [conn (connection host port)
        chan (async/chan 1024)
        file (output-file)]
    (reset! socket-repl-conn
            (assoc conn
                   :handler handler
                   :chan chan
                   :file file
                   :file-stream (PrintStream. file)
                   :last (System/currentTimeMillis)))

    ;; input producer
    (future
      (loop []
        (log/info "reading from repl socket")
        (when-let [line (str (.readLine (:in conn)) "\n")]
          (>!! chan line)
          (recur))))

    ;; input consumer
    (future
      (loop []
        (when-let [x (<!! chan)]
          (log/info "writing to repl socket")
          (handler x)
          (recur)))))

  "success")

(defn start
  [{:keys [debug nvim socket-repl-conn] :as plugin}]
  (nvim/register-method!
    nvim
    "connect"
    (fn [msg]
      (let [[host port] (-> msg
                            message/params
                            first
                            (string/split #":"))]
        (try
          (connect! socket-repl-conn host port
                    (fn [x]
                      (write-output @socket-repl-conn x)))
          (catch Throwable t
            (log/error t "Error connecting to socket repl")
            (nvim/vim-command-async
              nvim
              ":echo 'Unable to connect to socket repl.'"
              (fn [_])))))))

  (nvim/register-method!
    nvim
    "eval-code"
    (run-command
      nvim
      socket-repl-conn
      (fn [msg]
        (go
          (let [coords (nvim/get-cursor-location nvim)
                buffer-text (nvim/get-current-buffer-text nvim)]
            (try
              (write-code @socket-repl-conn (get-form-at buffer-text coords))
              (catch Throwable t
                (log/error t "Error evaluating a form")
                (write-error @socket-repl-conn t))))))))

  (nvim/register-method!
    nvim
    "eval-buffer"
    (run-command
      nvim
      socket-repl-conn
      (fn [msg]
        (go
          (let [buffer (nvim/vim-get-current-buffer nvim)
                filename (nvim/buffer-get-name nvim buffer)]
            (if (.exists (io/as-file filename))
              (do
                ;; Not sure if saving the file is really always what we want,
                ;; but if we don't, stale data will be loaded.
                (nvim/vim-command nvim ":w")
                (write-code @socket-repl-conn
                            (format "(load-file \"%s\")" filename)))
              (let [code (string/join "\n" (nvim/buffer-get-line-slice
                                             nvim buffer 0 -1))]
                (write-code @socket-repl-conn
                            (format "(eval '(do %s))" code)))))))))

  (nvim/register-method!
    nvim
    "doc"
    (run-command
      nvim
      socket-repl-conn
      (fn [msg]
        (nvim/get-current-word-async
          nvim
          (fn [word]
            (let [code (format "(clojure.repl/doc  %s)" word)]
              (write-code @socket-repl-conn code)))))))

  (nvim/register-method!
    nvim
    "show-log"
    (run-command
      nvim
      socket-repl-conn
      (fn [msg]
        (let [file (-> @socket-repl-conn :file .getAbsolutePath)]
          (go
            (let [original-window (nvim/vim-get-current-window nvim)
                  buffer-cmd (first (message/params msg))
                  rlog-buffer (get-rlog-buffer-name nvim)
                  rlog-buffer-visible? (when rlog-buffer
                                         (<! (nvim/buffer-visible?-async
                                               nvim rlog-buffer)))]
              (when-not rlog-buffer-visible?
                (nvim/vim-command
                  nvim
                  (format "%s | nnoremap <buffer> q :q<cr> | :let b:rlog=1 | :call termopen('tail -f %s')"
                          buffer-cmd file))
                (nvim/vim-set-current-window nvim original-window))))
          ;; Don't return a core.async channel, else msgpack will fail to
          ;; serialize it.
          "success"))))

  (nvim/register-method!
    nvim
    "dismiss-log"
    (run-command
      nvim
      socket-repl-conn
      (fn [msg]
        (go
          (nvim/vim-command
            nvim (format "bd! %s" (get-rlog-buffer-number nvim))))
        ;; Don't return a core.async channel, else msgpack will fail to
        ;; serialize it.
        "success")))
plugin)

(defn stop
  [plugin]
  plugin)

(defn new
  [debug nvim]
  {:nvim nvim
   :debug debug
   :socket-repl-conn (atom nil)})
