package com.example.msi.websocketdemo.websocket.enums

/**
 * 消息类型枚举
 */
enum class MessageTypeEnum private constructor(val code: Int) {

    /**
     * 心跳
     */
    HEART_BEAT(1),
    /**
     * 其他人登录
     */
    OTHER_LOGIN(2),
    /**
     * 心跳ACK
     */
    HEART_BEAT_ACK(101),
    /**
     * 通用消息ACK
     */
    COMMON_MSG_ACK(102)
}
