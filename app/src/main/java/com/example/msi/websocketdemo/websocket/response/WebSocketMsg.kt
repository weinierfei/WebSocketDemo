package com.example.msi.websocketdemo.websocket.response

/**
 * Description:websocket的消息信息体
 *
 * @author: guoyongping
 * @date: 2019-05-31 11:03
 */
class WebSocketMsg {
    var cmd: Int = 0
    var msg: String? = null
    var msgId: String? = null

    constructor() {}

    constructor(cmd: Int, msg: String) {
        this.cmd = cmd
        this.msg = msg
    }

    override fun toString(): String {
        return "WebSocketMsg{cmd=$cmd, msg='$msg', msgId='$msgId'}"
    }
}
