(ns socket-repl.parser-test
  (:require
    [clojure.test :as test :refer [deftest is]]
    [socket-repl.parser :as parser]))

(deftest parse
  (is (= "(println ::Foo)"
         (parser/read-next "(println ::Foo)\n(println 'bar)\n::baz"
                           1 2)))
  (is (= "println"
         (parser/read-next "println ::Foo)\n(println 'bar)\n::baz"
                           1 2)))
  (is (= "(println 'bar)"
         (parser/read-next "println ::Foo)\n(println 'bar)\n::baz"
                           2 3)))
  (is (= "{:foo :bar}"
         (parser/read-next (str "println ::Foo)\n(println 'bar)\n::baz blah"
                                " blah blah\n(doit {:foo :bar} x)")
                           4 10)))
  (is (= "(foo bar)"
         (parser/read-next "(foo bar)"
                                1 1)))
  (is (= "(foo bar)"
         (parser/read-next "\n\n\n\n(foo bar)"
                           5 1))))

;(clojure.test/run-tests 'socket-repl.parser-test)
