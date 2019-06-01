package com.example.msi.websocketdemo.websocket

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import com.example.msi.websocketdemo.BuildConfig
import com.example.msi.websocketdemo.WsApplication
import com.example.msi.websocketdemo.websocket.common.ICallback
import com.example.msi.websocketdemo.websocket.enums.MessageTypeEnum
import com.example.msi.websocketdemo.websocket.enums.WsStatus
import com.example.msi.websocketdemo.websocket.response.WebSocketMsg
import com.google.gson.Gson
import com.neovisionaries.ws.client.*
import com.orhanobut.logger.Logger
import java.io.IOException

/**
 * Created by zly on 2017/6/8.
 */

class WsManager private constructor() {

    private var reconnectCount = 0//重连次数
    private val maxReConnectCount = 5// 最大重连次数
    private val minInterval: Long = 3000//重连最小时间间隔
    private val maxInterval: Long = 60000//重连最大时间间隔
    private var heartbeatFailCount = 0// 心跳失败重试次数
    private var url: String? = null
    private var connectStatus: WsStatus? = null
    private var ws: WebSocket? = null
    private var webSocketListener: WebSocketListener? = null
    private val SUCCESS_HANDLE = 0x01
    private val ERROR_HANDLE = 0x02
    private var mIndex: Int = 0

    private val mHandler = object : Handler(Looper.getMainLooper()){}

    /**
     * 心跳任务
     */
    private val heartbeatTask = object : Runnable {
        override fun run() {
            sendHeartBeatPing(object : ICallback<Any> {
                override fun onSuccess(o: Any) {
                    heartbeatFailCount = 0
                }


                override fun onFail(msg: String) {
                    heartbeatFailCount++
                    if (heartbeatFailCount >= 3) {
                        reconnect()
                    }
                }
            })

            mHandler.postDelayed(this, HEARTBEAT_INTERVAL)
        }
    }


    private val mReconnectTask = Runnable {
        try {
            ws = WebSocketFactory().createSocket(url!!, CONNECT_TIMEOUT)
                    .setFrameQueueSize(FRAME_QUEUE_SIZE)//设置帧队列最大值为5
                    .setMissingCloseFrameAllowed(false)//设置不允许服务端关闭连接却未发送关闭帧
                    .addListener(WsListener())//添加回调监听
                    .connectAsynchronously()//异步连接
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


    private// 当前网络是连接的
    // 当前所连接的网络可用
    val isNetConnect: Boolean
        get() {
            val connectivity = WsApplication.getContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (connectivity != null) {
                val info = connectivity.activeNetworkInfo
                if (info != null && info.isConnected) {
                    if (info.state == NetworkInfo.State.CONNECTED) {
                        return true
                    }
                }
            }
            return false
        }

    private object WsManagerHolder {
        val mInstance = WsManager()
    }


    fun init() {
        try {
            /**
             * configUrl其实是缓存在本地的连接地址
             * 这个缓存本地连接地址是app启动的时候通过http请求去服务端获取的,
             * 每次app启动的时候会拿当前时间与缓存时间比较,超过6小时就再次去服务端获取新的连接地址更新本地缓存
             */
            val configUrl = "ws://10.0.8.55:18888/websocket?uuid=" + 1000002 + "&sendSiteId=" + 6
            url = if (TextUtils.isEmpty(configUrl)) DEF_URL else configUrl
            ws = WebSocketFactory().createSocket(url!!, CONNECT_TIMEOUT)
                    .setFrameQueueSize(FRAME_QUEUE_SIZE)//设置帧队列最大值为5
                    .setMissingCloseFrameAllowed(false)//设置不允许服务端关闭连接却未发送关闭帧
                    .addListener(WsListener())//添加回调监听
                    .connectAsynchronously()//异步连接
            setStatus(WsStatus.CONNECTING)
            Logger.t(TAG).d("第一次连接")
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    /**
     * 开启心跳
     */
    private fun startHeartbeat() {
        mHandler.postDelayed(heartbeatTask, HEARTBEAT_INTERVAL)
    }

    /**
     * 取消心跳
     */
    private fun cancelHeartbeat() {
        heartbeatFailCount = 0
        mHandler.removeCallbacks(heartbeatTask)
    }

    /**
     * 发送文本消息给服务端
     *
     * @param msg      文本消息
     * @param callback 回调
     */
    private fun <T> sendMsg(msg: String, callback: ICallback<*>) {
        if (!isNetConnect) {
            callback.onFail("网络不可用")
            return
        }
        Logger.t(TAG).d("发送消息给服务端 : %s", Gson().toJson(msg))
        ws!!.sendText(Gson().toJson(msg))
    }

    /**
     * 发送心跳 (ping方式)
     *
     * @param callback 回调
     */
    private fun sendHeartBeatPing(callback: ICallback<*>) {
        if (!isNetConnect) {
            callback.onFail("网络不可用")
            return
        }
        Logger.t(TAG).d("发送心跳")
        ws!!.sendPing()
    }

    /**
     * 发送心跳 (文本)
     *
     * @param callback 回调
     */
    private fun sendHeartBeatText(callback: ICallback<*>) {
        if (!isNetConnect) {
            callback.onFail("网络不可用")
            return
        }
        val webSocketMsg = WebSocketMsg()
        webSocketMsg.cmd = MessageTypeEnum.HEART_BEAT.code

        Logger.t(TAG).d("发送心跳 : %s", Gson().toJson(webSocketMsg))
        ws!!.sendText(Gson().toJson(webSocketMsg))
    }


    /**
     * 继承默认的监听空实现WebSocketAdapter,重写我们需要的方法
     * onTextMessage 收到文字信息
     * onConnected 连接成功
     * onConnectError 连接失败
     * onDisconnected 连接关闭
     */
    internal inner class WsListener : WebSocketAdapter() {
        @Throws(Exception::class)
        override fun onTextMessage(websocket: WebSocket?, text: String?) {
            super.onTextMessage(websocket, text)
            Logger.t(TAG).d("接收到消息 :%s", text)
            val webSocketMsg = Gson().fromJson(text, WebSocketMsg::class.java)
            webSocketListener!!.onMessageResponseClient(webSocketMsg, mIndex)
            // 给服务端回包
            if (webSocketMsg.cmd == MessageTypeEnum.OTHER_LOGIN.code) {
                val webSocketMsg1 = WebSocketMsg()
                webSocketMsg1.cmd = MessageTypeEnum.COMMON_MSG_ACK.code
                webSocketMsg1.msgId = webSocketMsg.msgId
                val res = Gson().toJson(webSocketMsg1)
                ws!!.sendText(res)
                Logger.t(TAG).d("回包给服务端 :%s", res)
            } else if (webSocketMsg.cmd == MessageTypeEnum.HEART_BEAT_ACK.code) {
                Logger.t(TAG).d("接受到心跳响应 :%s", text)
            } else {
                Logger.t(TAG).d("不支持的消息类型 :%s", text)
            }
        }


        @Throws(Exception::class)
        override fun onPongFrame(websocket: WebSocket?, frame: WebSocketFrame?) {
            super.onPongFrame(websocket, frame)
            Logger.t(TAG).d("接受到心跳响应 :%s", frame!!.toString())
        }

        @Throws(Exception::class)
        override fun onConnected(websocket: WebSocket?, headers: Map<String, List<String>>?) {
            super.onConnected(websocket, headers)
            Logger.t(TAG).d("连接成功")
            setStatus(WsStatus.CONNECT_SUCCESS)
            cancelReconnect()//连接成功的时候取消重连,初始化连接次数
            // 开启心跳
            startHeartbeat()
        }


        @Throws(Exception::class)
        override fun onConnectError(websocket: WebSocket?, exception: WebSocketException?) {
            super.onConnectError(websocket, exception)
            Logger.t(TAG).d("连接错误")
            setStatus(WsStatus.CONNECT_FAIL)
            reconnect()//连接错误的时候调用重连方法
        }


        @Throws(Exception::class)
        override fun onDisconnected(websocket: WebSocket?, serverCloseFrame: WebSocketFrame?,
                                    clientCloseFrame: WebSocketFrame?, closedByServer: Boolean) {
            super.onDisconnected(websocket, serverCloseFrame, clientCloseFrame, closedByServer)
            Logger.t(TAG).d("断开连接")
            setStatus(WsStatus.CONNECT_FAIL)
            reconnect()//连接断开的时候调用重连方法
        }
    }


    private fun setStatus(status: WsStatus) {
        this.connectStatus = status
    }


    fun disconnect() {
        if (ws != null) {
            ws!!.disconnect()
        }
    }

    fun reconnect() {
        if (!isNetConnect) {
            reconnectCount = 0
            Logger.t(TAG).d("重连失败网络不可用")
            return
        }

        // 如果重连次数大于最大重连数 直接断开长连接 开始轮询
        if (reconnectCount > maxReConnectCount) {
            disconnect()
            // 开启轮询
            Logger.t(TAG).d("重连次数大于最大重连数,断开长连接并开启轮询")

            return
        }

        //当前连接断开了 同时不是正在重连状态
        if (ws != null && !ws!!.isOpen && connectStatus != WsStatus.CONNECTING) {
            reconnectCount++
            setStatus(WsStatus.CONNECTING)
            cancelHeartbeat()

            var reconnectTime = minInterval
            if (reconnectCount > 3) {
                url = DEF_URL
                val temp = minInterval * (reconnectCount - 2)
                reconnectTime = if (temp > maxInterval) maxInterval else temp
            }

            Logger.t(TAG).d("准备开始第%d次重连,重连间隔%d -- url:%s", reconnectCount, reconnectTime, url)
            mHandler.postDelayed(mReconnectTask, reconnectTime)
        }
    }


    private fun cancelReconnect() {
        reconnectCount = 0
        mHandler.removeCallbacks(mReconnectTask)
    }

    fun setWebSocketListener(webSocketListener: WebSocketListener, index: Int) {
        this.webSocketListener = webSocketListener
        this.mIndex = index
    }

    companion object {
        private val TAG = "WsManager"

        private val HEARTBEAT_INTERVAL: Long = 10000//心跳间隔
        private val FRAME_QUEUE_SIZE = 5
        private val CONNECT_TIMEOUT = 5000
        private val REQUEST_TIMEOUT = 10000//请求超时时间
        private val DEF_TEST_URL = "ws://10.0.8.55:18888/websocket?uuid=" + 1000002 + "&sendSiteId=" + 6//测试服默认地址
        private val DEF_RELEASE_URL = "正式服地址"//正式服默认地址
        private val DEF_URL = if (BuildConfig.DEBUG) DEF_TEST_URL else DEF_RELEASE_URL

        val instance: WsManager
            get() = WsManagerHolder.mInstance
    }
}
