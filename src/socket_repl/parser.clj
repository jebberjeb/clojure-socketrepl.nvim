(ns socket-repl.parser
  (:require
    [clojure.string :as string]
    [clojure.tools.reader.reader-types :as reader-types]
    [clojure.tools.reader :as reader]))

(defn position
  "Returns the zero-indexed position in a code string given line and column."
  [code-str row col]
  (->> code-str
       string/split-lines
       (take (dec row))
       (string/join)
       count
       (+ col (dec row)) ;; `(dec row)` to include for newlines
       dec
       (max 0)))

(defn search-start
  "Find the place to start reading from. Search backwards from the starting
  point, looking for a '[', '{', or '('. If none can be found, search from
  the beginning of `code-str`."
  [code-str start-row start-col]
  (let [openers #{\[ \( \{}
        start-position (position code-str start-row start-col)]
    (if (contains? openers (nth code-str start-position))
      start-position
      (let [code-str-prefix (subs code-str 0 start-position)]
        (->> openers
             (map #(string/last-index-of code-str-prefix %))
             (remove nil?)
             (apply max 0))))))

(defn read-next
  "Reads the next expression from some code. Uses an `indexing-pushback-reader`
  to determine how much was read, and return that substring of the original
  `code-str`, rather than what was actually read by the reader."
  [code-str start-row start-col]
  (let [code-str (subs code-str (search-start code-str start-row start-col))
        rdr (reader-types/indexing-push-back-reader code-str)]
    ;; Read a form, but discard it, as we want the original string.
    (reader/read rdr)
    (subs code-str
          0
          ;; Even though this returns the position *after* the read, this works
          ;; because subs is end point exclusive.
          (position code-str
                    (reader-types/get-line-number rdr)
                    (reader-types/get-column-number rdr)))))
