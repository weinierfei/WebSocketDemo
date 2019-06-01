package com.example.msi.websocketdemo.websocket.common

/**
 * Created by zly on 2017/6/12.
 */

interface ICallback<T> {

    fun onSuccess(t: T)

    fun onFail(msg: String)

}
