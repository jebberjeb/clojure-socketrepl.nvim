lein uberjar
cp target/uberjar/socket-repl-plugin-0.1.0-SNAPSHOT-standalone.jar .
rm -rf ~/.vim/bundle/clojure-socketrepl.nvim
cd ..
cp -rf clojure-socketrepl.nvim  ~/.vim/bundle
