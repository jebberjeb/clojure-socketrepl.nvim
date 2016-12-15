let s:p_dir = expand('<sfile>:p:h')
let g:is_running = 0
let g:nvim_tcp_plugin_channel = 0

function! StartIfNotRunning()
    if g:is_running == 0
        echo 'Starting SocketREPL client...'
        let jar_file_path = s:p_dir . '/../' . 'socket-repl-plugin-0.1.0-SNAPSHOT-standalone.jar'
        call jobstart(['java', '-jar', jar_file_path], {'rpc': v:true})
        let g:is_running = 1
    endif
endfunction

function! Connect(host_colon_port)
    call StartIfNotRunning()
    if a:host_colon_port == ""
        let conn = "localhost:5555"
    else
        let conn = a:host_colon_port
    endif
    let res = rpcnotify(g:nvim_tcp_plugin_channel, 'connect', conn)
    return res
endfunction
command! -nargs=? Connect call Connect("<args>")

function! EvalBuffer()
    call StartIfNotRunning()
    ReplLog
    let res = rpcnotify(g:nvim_tcp_plugin_channel, 'eval-buffer', [])
    return res
endfunction
command! EvalBuffer call EvalBuffer()

function! EvalCode()
    call StartIfNotRunning()
    ReplLog
    let res = rpcnotify(g:nvim_tcp_plugin_channel, 'eval-code', [])
    return res
endfunction
command! EvalCode call EvalCode()

function! ReplLog(buffer_cmd)
    call StartIfNotRunning()
    let res = rpcnotify(g:nvim_tcp_plugin_channel, 'show-log', a:buffer_cmd)
    return res
endfunction
command! ReplLog call ReplLog(':botright new')

function! DismissReplLog()
    call StartIfNotRunning()
    let res = rpcnotify(g:nvim_tcp_plugin_channel, 'dismiss-log', [])
    return res
endfunction
command! DismissReplLog call DismissReplLog()

function! Doc()
    call StartIfNotRunning()
    ReplLog
    let res = rpcnotify(g:nvim_tcp_plugin_channel, 'doc', [])
    return res
endfunction
command! Doc call Doc()

if !exists('g:disable_socket_repl_mappings')
    nnoremap <leader>eb :EvalBuffer<cr>
    nnoremap <leader>ef :EvalCode<cr>
    nnoremap <leader>doc :Doc<cr>
    nnoremap <leader>rlog :ReplLog<cr>
    nnoremap <leader>drlog :DismissReplLog<cr>
endif
