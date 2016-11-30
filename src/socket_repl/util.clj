(ns socket-repl.util)

(defmacro log-start
  [component-name & body]
  `(do
     (clojure.tools.logging/info ~component-name "starting")
     (let [result# (do ~@body)]
       (clojure.tools.logging/info ~component-name "started")
       result#)))

(defmacro log-stop
  [component-name & body]
  `(do
     (clojure.tools.logging/info ~component-name "stopping")
     (let [result# (do ~@body)]
       (clojure.tools.logging/info ~component-name "stopped")
       result#)))
