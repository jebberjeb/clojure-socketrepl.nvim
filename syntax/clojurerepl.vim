if exists("b:current_syntax")
  finish
endif

runtime! syntax/clojure.vim

syntax match clojurePrompt "user=> "

highlight default link clojurePrompt Identifier
