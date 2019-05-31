package com.example.msi.websocketdemo.websocket.common;

/**
 * Created by zly on 2017/7/23.
 */

public interface IWsCallback<T> {
    void onSuccess(T t);
    void onError(String msg);
    void onTimeout();
}
