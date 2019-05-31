package com.example.msi.websocketdemo.websocket;

import com.example.msi.websocketdemo.websocket.response.WebSocketMsg;

/**
 * Description:
 *
 * @author: guoyongping
 * @date: 2019-05-31 15:44
 */
public interface WebSocketListener {

    /**
     * 当接收到系统消息
     */
    void onMessageResponseClient(WebSocketMsg msg, int index);
}
