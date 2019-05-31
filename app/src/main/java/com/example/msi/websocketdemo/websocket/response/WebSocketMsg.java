package com.example.msi.websocketdemo.websocket.response;

/**
 * Description:websocket的消息信息体
 *
 * @author: guoyongping
 * @date: 2019-05-31 11:03
 */
public class WebSocketMsg {
    private int cmd;
    private String msg;
    private String msgId;

    public WebSocketMsg() {
    }

    public WebSocketMsg(int cmd, String msg) {
        this.cmd = cmd;
        this.msg = msg;
    }

    public int getCmd() {
        return cmd;
    }

    public void setCmd(int cmd) {
        this.cmd = cmd;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    @Override
    public String toString() {
        return "WebSocketMsg{" + "cmd=" + cmd + ", msg='" + msg + '\'' + ", msgId='" + msgId + '\'' + '}';
    }
}
