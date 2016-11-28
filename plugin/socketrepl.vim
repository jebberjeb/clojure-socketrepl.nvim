let s:p_dir = expand('<sfile>:p:h')
let g:is_running = 0
let g:channel = -1

function! StartIfNotRunning()
    if g:is_running == 0
        echo 'starting plugin...'
        let jar_file_path = s:p_dir . '/../' . 'socket-repl-plugin-0.1.0-SNAPSHOT-standalone.jar'
        let g:channel = rpcstart('java', ['-jar', jar_file_path])
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
    let res = rpcrequest(g:channel, 'connect', conn)
    return res
endfunction
command! -nargs=? Connect call Connect("<args>")

function! EvalBuffer()
    call StartIfNotRunning()
    ReplLog
    let res = rpcrequest(g:channel, 'eval-buffer', [])
    return res
endfunction
command! EvalBuffer call EvalBuffer()

function! EvalCode()
    call StartIfNotRunning()
    ReplLog
    let res = rpcrequest(g:channel, 'eval-code', [])
    return res
endfunction
command! EvalCode call EvalCode()

function! ReplLog(buffer_cmd)
    call StartIfNotRunning()
    let res = rpcrequest(g:channel, 'show-log', a:buffer_cmd)
    return res
endfunction
command! ReplLog call ReplLog(':botright new')

function! DismissReplLog()
    call StartIfNotRunning()
    let res = rpcrequest(g:channel, 'dismiss-log', [])
    return res
endfunction
command! DismissReplLog call DismissReplLog()

function! Doc()
    call StartIfNotRunning()
    ReplLog
    let res = rpcrequest(g:channel, 'doc', [])
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
