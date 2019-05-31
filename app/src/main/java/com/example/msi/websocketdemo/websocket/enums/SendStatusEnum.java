package com.example.msi.websocketdemo.websocket.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * 消息发送状态枚举
 */
public enum SendStatusEnum {
    ACCEPT_SUCCESS(1, "接受成功"),
    SEND_SUCCESS(5, "发送成功"),
    SEND_FAIL(10, "发送失败");

    private static final Map<Integer, SendStatusEnum> map = new HashMap<>();
    static {
        for (SendStatusEnum state : values()) {
            map.put(state.getCode(), state);
        }
    }

    private int code;
    private String name;

    SendStatusEnum(int code, String msg) {
        this.code = code;
        this.name = msg;
    }

    public int getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public static SendStatusEnum getEnum(int code) {
        return map.get(code);
    }
}
