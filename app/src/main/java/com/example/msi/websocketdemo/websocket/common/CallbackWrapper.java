package com.example.msi.websocketdemo.websocket.common;

import java.util.concurrent.ScheduledFuture;

/**
 * Created by zly on 2017/7/23.
 */

public class CallbackWrapper {

    private final IWsCallback tempCallback;
    private final ScheduledFuture timeoutTask;


    public CallbackWrapper(IWsCallback tempCallback, ScheduledFuture timeoutTask) {
        this.tempCallback = tempCallback;
        this.timeoutTask = timeoutTask;
    }


    public IWsCallback getTempCallback() {
        return tempCallback;
    }


    public ScheduledFuture getTimeoutTask() {
        return timeoutTask;
    }

}
