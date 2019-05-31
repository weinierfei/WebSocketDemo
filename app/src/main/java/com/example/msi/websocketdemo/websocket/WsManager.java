package com.example.msi.websocketdemo.websocket;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import com.example.msi.websocketdemo.BuildConfig;
import com.example.msi.websocketdemo.WsApplication;
import com.example.msi.websocketdemo.websocket.common.CallbackDataWrapper;
import com.example.msi.websocketdemo.websocket.common.ICallback;
import com.example.msi.websocketdemo.websocket.enums.WsStatus;
import com.example.msi.websocketdemo.websocket.enums.MessageTypeEnum;
import com.example.msi.websocketdemo.websocket.response.WebSocketMsg;
import com.google.gson.Gson;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;
import com.orhanobut.logger.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by zly on 2017/6/8.
 */

public class WsManager {
    private static final String TAG = "WsManager";

    private static final long HEARTBEAT_INTERVAL = 10000;//心跳间隔
    private static final int FRAME_QUEUE_SIZE = 5;
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int REQUEST_TIMEOUT = 10000;//请求超时时间
    private static final String DEF_TEST_URL = "ws://10.0.8.55:18888/websocket?uuid=" + 1000002 + "&sendSiteId=" + 6;//测试服默认地址
    private static final String DEF_RELEASE_URL = "正式服地址";//正式服默认地址
    private static final String DEF_URL = BuildConfig.DEBUG ? DEF_TEST_URL : DEF_RELEASE_URL;

    private int reconnectCount = 0;//重连次数
    private int maxReConnectCount = 5;// 最大重连次数
    private long minInterval = 3000;//重连最小时间间隔
    private long maxInterval = 60000;//重连最大时间间隔
    private int heartbeatFailCount = 0;// 心跳失败重试次数
    private String url;
    private WsStatus mStatus;
    private WebSocket ws;
    private WebSocketListener webSocketListener;
    private final int SUCCESS_HANDLE = 0x01;
    private final int ERROR_HANDLE = 0x02;
    private int mIndex;

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SUCCESS_HANDLE:
                    CallbackDataWrapper successWrapper = (CallbackDataWrapper) msg.obj;
                    successWrapper.getCallback().onSuccess(successWrapper.getData());
                    break;
                case ERROR_HANDLE:
                    CallbackDataWrapper errorWrapper = (CallbackDataWrapper) msg.obj;
                    errorWrapper.getCallback().onFail((String) errorWrapper.getData());
                    break;
            }
        }
    };


    private WsManager() {
    }

    private static class WsManagerHolder {
        private static WsManager mInstance = new WsManager();
    }

    public static WsManager getInstance() {
        return WsManagerHolder.mInstance;
    }


    public void init() {
        try {
            /**
             * configUrl其实是缓存在本地的连接地址
             * 这个缓存本地连接地址是app启动的时候通过http请求去服务端获取的,
             * 每次app启动的时候会拿当前时间与缓存时间比较,超过6小时就再次去服务端获取新的连接地址更新本地缓存
             */
            String configUrl = "ws://10.0.8.55:18888/websocket?uuid=" + 1000002 + "&sendSiteId=" + 6;
            url = TextUtils.isEmpty(configUrl) ? DEF_URL : configUrl;
            ws = new WebSocketFactory().createSocket(url, CONNECT_TIMEOUT)
                    .setFrameQueueSize(FRAME_QUEUE_SIZE)//设置帧队列最大值为5
                    .setMissingCloseFrameAllowed(false)//设置不允许服务端关闭连接却未发送关闭帧
                    .addListener(new WsListener())//添加回调监听
                    .connectAsynchronously();//异步连接
            setStatus(WsStatus.CONNECTING);
            Logger.t(TAG).d("第一次连接");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 开启心跳
     */
    private void startHeartbeat() {
        mHandler.postDelayed(heartbeatTask, HEARTBEAT_INTERVAL);
    }

    /**
     * 取消心跳
     */
    private void cancelHeartbeat() {
        heartbeatFailCount = 0;
        mHandler.removeCallbacks(heartbeatTask);
    }

    /**
     * 心跳任务
     */
    private Runnable heartbeatTask = new Runnable() {
        @Override
        public void run() {
            sendHeartBeatPing(new ICallback() {
                @Override
                public void onSuccess(Object o) {
                    heartbeatFailCount = 0;
                }


                @Override
                public void onFail(String msg) {
                    heartbeatFailCount++;
                    if (heartbeatFailCount >= 3) {
                        reconnect();
                    }
                }
            });

            mHandler.postDelayed(this, HEARTBEAT_INTERVAL);
        }
    };

    /**
     * 发送文本消息给服务端
     *
     * @param msg      文本消息
     * @param callback 回调
     */
    private <T> void sendMsg(String msg, final ICallback callback) {
        if (!isNetConnect()) {
            callback.onFail("网络不可用");
            return;
        }
        Logger.t(TAG).d("发送消息给服务端 : %s", new Gson().toJson(msg));
        ws.sendText(new Gson().toJson(msg));
    }

    /**
     * 发送心跳 (ping方式)
     *
     * @param callback 回调
     */
    private void sendHeartBeatPing(final ICallback callback) {
        if (!isNetConnect()) {
            callback.onFail("网络不可用");
            return;
        }
        Logger.t(TAG).d("发送心跳");
        ws.sendPing();
    }

    /**
     * 发送心跳 (文本)
     *
     * @param callback 回调
     */
    private void sendHeartBeatText(final ICallback callback) {
        if (!isNetConnect()) {
            callback.onFail("网络不可用");
            return;
        }
        WebSocketMsg webSocketMsg = new WebSocketMsg();
        webSocketMsg.setCmd(MessageTypeEnum.HEART_BEAT.getCode());

        Logger.t(TAG).d("发送心跳 : %s", new Gson().toJson(webSocketMsg));
        ws.sendText(new Gson().toJson(webSocketMsg));
    }


    /**
     * 继承默认的监听空实现WebSocketAdapter,重写我们需要的方法
     * onTextMessage 收到文字信息
     * onConnected 连接成功
     * onConnectError 连接失败
     * onDisconnected 连接关闭
     */
    class WsListener extends WebSocketAdapter {
        @Override
        public void onTextMessage(WebSocket websocket, String text) throws Exception {
            super.onTextMessage(websocket, text);
            Logger.t(TAG).d("接收到消息 :%s", text);
            WebSocketMsg webSocketMsg = new Gson().fromJson(text, WebSocketMsg.class);
            webSocketListener.onMessageResponseClient(webSocketMsg, mIndex);
            // 给服务端回包
            if (webSocketMsg.getCmd() == MessageTypeEnum.OTHER_LOGIN.getCode()) {
                WebSocketMsg webSocketMsg1 = new WebSocketMsg();
                webSocketMsg1.setCmd(MessageTypeEnum.COMMON_MSG_ACK.getCode());
                webSocketMsg1.setMsgId(webSocketMsg.getMsgId());
                String res = new Gson().toJson(webSocketMsg1);
                ws.sendText(res);
                Logger.t(TAG).d("回包给服务端 :%s", res);
            } else if (webSocketMsg.getCmd() == MessageTypeEnum.HEART_BEAT_ACK.getCode()) {
                Logger.t(TAG).d("接受到心跳响应 :%s", text);
            } else {
                Logger.t(TAG).d("不支持的消息类型 :%s", text);
            }
        }


        @Override
        public void onPongFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
            super.onPongFrame(websocket, frame);
            Logger.t(TAG).d("接受到心跳响应 :%s", frame.toString());
        }

        @Override
        public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
            super.onConnected(websocket, headers);
            Logger.t(TAG).d("连接成功");
            setStatus(WsStatus.CONNECT_SUCCESS);
            cancelReconnect();//连接成功的时候取消重连,初始化连接次数
            // 开启心跳
            startHeartbeat();
        }


        @Override
        public void onConnectError(WebSocket websocket, WebSocketException exception) throws Exception {
            super.onConnectError(websocket, exception);
            Logger.t(TAG).d("连接错误");
            setStatus(WsStatus.CONNECT_FAIL);
            reconnect();//连接错误的时候调用重连方法
        }


        @Override
        public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame,
                                   WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
            super.onDisconnected(websocket, serverCloseFrame, clientCloseFrame, closedByServer);
            Logger.t(TAG).d("断开连接");
            setStatus(WsStatus.CONNECT_FAIL);
            reconnect();//连接断开的时候调用重连方法
        }
    }


    private void setStatus(WsStatus status) {
        this.mStatus = status;
    }


    private WsStatus getConnectStatus() {
        return mStatus;
    }


    public void disconnect() {
        if (ws != null) {
            ws.disconnect();
        }
    }

    public void reconnect() {
        if (!isNetConnect()) {
            reconnectCount = 0;
            Logger.t(TAG).d("重连失败网络不可用");
            return;
        }

        // 如果重连次数大于最大重连数 直接断开长连接 开始轮询
        if (reconnectCount > maxReConnectCount){
            disconnect();
            // 开启轮询
            Logger.t(TAG).d("重连次数大于最大重连数,断开长连接并开启轮询");

            return;
        }

        //当前连接断开了 同时不是正在重连状态
        if (ws != null && !ws.isOpen() && getConnectStatus() != WsStatus.CONNECTING) {
            reconnectCount++;
            setStatus(WsStatus.CONNECTING);
            cancelHeartbeat();

            long reconnectTime = minInterval;
            if (reconnectCount > 3) {
                url = DEF_URL;
                long temp = minInterval * (reconnectCount - 2);
                reconnectTime = temp > maxInterval ? maxInterval : temp;
            }

            Logger.t(TAG).d("准备开始第%d次重连,重连间隔%d -- url:%s", reconnectCount, reconnectTime, url);
            mHandler.postDelayed(mReconnectTask, reconnectTime);
        }
    }


    private Runnable mReconnectTask = new Runnable() {

        @Override
        public void run() {
            try {
                ws = new WebSocketFactory().createSocket(url, CONNECT_TIMEOUT)
                        .setFrameQueueSize(FRAME_QUEUE_SIZE)//设置帧队列最大值为5
                        .setMissingCloseFrameAllowed(false)//设置不允许服务端关闭连接却未发送关闭帧
                        .addListener(new WsListener())//添加回调监听
                        .connectAsynchronously();//异步连接
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };


    private void cancelReconnect() {
        reconnectCount = 0;
        mHandler.removeCallbacks(mReconnectTask);
    }


    private boolean isNetConnect() {
        ConnectivityManager connectivity = (ConnectivityManager) WsApplication.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            NetworkInfo info = connectivity.getActiveNetworkInfo();
            if (info != null && info.isConnected()) {
                // 当前网络是连接的
                if (info.getState() == NetworkInfo.State.CONNECTED) {
                    // 当前所连接的网络可用
                    return true;
                }
            }
        }
        return false;
    }

    public void setWebSocketListener(WebSocketListener webSocketListener, int index) {
        this.webSocketListener = webSocketListener;
        this.mIndex = index;
    }
}
