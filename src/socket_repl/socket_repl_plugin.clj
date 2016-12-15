(ns socket-repl.socket-repl-plugin
  "A plugin which connects to a running socket repl and sends output back to
  Neovim."
  (:require
    [clojure.core.async :as async]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.tools.logging :as log]
    [neovim-client.message :as message]
    [neovim-client.nvim :as nvim]
    [socket-repl.repl-log :as repl-log]
    [socket-repl.socket-repl :as socket-repl]
    [socket-repl.util :refer [log-start log-stop]]))

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

(defn write-error
  "Write a throwable's stack trace to the repl log."
  [repl-log throwable]
  (async/>!! (repl-log/input-channel repl-log)
             (str "\n##### PLUGIN ERR #####\n"
                  (.getMessage throwable) "\n"
                  (string/join "\n" (map str (.getStackTrace throwable)))
                  \n"######################\n")))

(defn run-command
  [{:keys [nvim socket-repl]} f]
  (fn [msg]
    (if-not (socket-repl/connected? socket-repl)
      (async/thread
        (nvim/vim-command
          nvim ":echo 'Use :Connect host:port to connect to a socket repl'"))
      (async/thread (f msg)))
    ;; Don't return an async channel, return something msg-pack can serialize.
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

(defn code-channel
  [plugin]
  (:code-channel plugin))

(defn start
  [{:keys [nvim repl-log socket-repl code-channel] :as plugin}]

  ;; Wire sub-component io.
  (log-start
    "plugin"
    (let [mult (async/mult code-channel)]
      (async/tap mult (socket-repl/input-channel socket-repl))
      (async/tap mult (repl-log/input-channel repl-log)))

    ;; Setup plugin functions.
    (nvim/register-method!
      nvim
      "connect"
      (fn [msg]
        (let [[host port] (-> msg
                              message/params
                              first
                              (string/split #":"))]
          (try
            (socket-repl/connect socket-repl host port)
            (catch Throwable t
              (log/error t "Error connecting to socket repl")
              (async/thread (nvim/vim-command
                              nvim
                              ":echo 'Unable to connect to socket repl.'"))))
          :done)))

    (nvim/register-method!
      nvim
      "eval-code"
      (run-command
        plugin
        (fn [msg]
          (let [coords (nvim/get-cursor-location nvim)
                buffer-text (nvim/get-current-buffer-text nvim)]
            (try
              (async/>!! code-channel (get-form-at buffer-text coords))
              (catch Throwable t
                (log/error t "Error evaluating a form")
                (write-error repl-log t)))))))

    (nvim/register-method!
      nvim
      "eval-buffer"
      (run-command
        plugin
        (fn [msg]
          (let [buffer (nvim/vim-get-current-buffer nvim)
                filename (nvim/buffer-get-name nvim buffer)]
            (if (.exists (io/as-file filename))
              (do
                ;; Not sure if saving the file is really always what we want,
                ;; but if we don't, stale data will be loaded.
                (nvim/vim-command nvim ":w")
                (async/>!! code-channel (format "(load-file \"%s\")" filename)))
              (let [code (string/join "\n" (nvim/buffer-get-line-slice
                                             nvim buffer 0 -1))]
                (async/>!! code-channel (format "(eval '(do %s))" code))))))))

    (nvim/register-method!
      nvim
      "doc"
      (run-command
        plugin
        (fn [msg]
          (nvim/get-current-word-async
            nvim
            (fn [word]
              (let [code (format "(clojure.repl/doc  %s)" word)]
                (async/>!! code-channel code)))))))

    (nvim/register-method!
      nvim
      "show-log"
      (run-command
        plugin
        (fn [msg]
          (let [file (-> repl-log repl-log/file .getAbsolutePath)]
            (let [original-window (nvim/vim-get-current-window nvim)
                  buffer-cmd (first (message/params msg))
                  rlog-buffer (get-rlog-buffer-name nvim)
                  rlog-buffer-visible? (when rlog-buffer
                                         (async/<!! (nvim/buffer-visible?-async
                                                      nvim rlog-buffer)))]
              (when-not rlog-buffer-visible?
                (nvim/vim-command
                  nvim
                  (format "%s | nnoremap <buffer> q :q<cr> | :let b:rlog=1 | :call termopen('tail -f %s') | :set ft=clojurerepl"
                          buffer-cmd file))
                (nvim/vim-set-current-window nvim original-window)))))))

    (nvim/register-method!
      nvim
      "dismiss-log"
      (run-command
        plugin
        (fn [msg]
          (nvim/vim-command
            nvim (format "bd! %s" (get-rlog-buffer-number nvim))))))
    plugin))

(defn stop
  [{:keys [nvim] :as plugin}]
  (log-stop
    "plugin"

    ;; Close the repl log buffer
    (nvim/vim-command
      nvim (format "bd! %s" (get-rlog-buffer-number nvim)))

    (async/close! (:code-channel plugin))
    plugin))

(defn new
  [nvim repl-log socket-repl]
  {:nvim nvim
   :repl-log repl-log
   :socket-repl socket-repl
   :code-channel (async/chan 1024)})
