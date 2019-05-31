package com.example.msi.websocketdemo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.example.msi.websocketdemo.websocket.WebSocketListener;
import com.example.msi.websocketdemo.websocket.WsManager;
import com.example.msi.websocketdemo.websocket.enums.MessageTypeEnum;
import com.example.msi.websocketdemo.websocket.response.WebSocketMsg;

public class MainActivity extends AppCompatActivity implements WebSocketListener {
    private static final String TAG = "MainActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        WsManager manager = WsManager.getInstance();
        manager.setWebSocketListener(this, MessageTypeEnum.OTHER_LOGIN.getCode());
        manager.init();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        WsManager.getInstance().disconnect();
    }

    @Override
    public void onMessageResponseClient(WebSocketMsg msg, int index) {

        Log.i(TAG, "------>" + msg.toString());



    }
}
