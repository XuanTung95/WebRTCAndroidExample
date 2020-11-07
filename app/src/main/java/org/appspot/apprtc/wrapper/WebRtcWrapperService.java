package org.appspot.apprtc.wrapper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;

import org.appspot.apprtc.AppRTCClient;
import org.appspot.apprtc.CallActivity;
import org.appspot.apprtc.R;
import org.json.JSONArray;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WebRtcWrapperService extends Service implements StompWebSocketRTCClient.OnServerMessage {
    private static final String TAG = "WebRtcService";
    //////////////////////////////////////////////////
    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_REQUEST_RANDOM_CHAT = "ACTION_REQUEST_RANDOM_CHAT";
    public static final String ACTION_LEAVE_ROOM = "ACTION_LEAVE_ROOM";
    public static final String ACTION_STOP = "ACTION_STOP";

    private final static String FOREGROUND_CHANNEL_ID = "foreground_channel_id";
    private NotificationManager mNotificationManager;
    private WebRtcWrapper webRtc;
    private StompWebSocketRTCClient appRtcClient;
    private RegisterUser registerUser;
    Handler handler; // my looper
    Intent startIntent;

    public WebRtcWrapperService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        handler = new Handler(Looper.myLooper());
    }

    private void createRegisterUser() {
        registerUser = new RegisterUser();
        registerUser.signature = UUID.randomUUID().toString();
        User user = new User();
        user.uuid = "1a1fe6a6ff4246e2a8346e5f5cb5d85d";
        user.pass = "1234";
        registerUser.user = user;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        // if user starts the service
        switch (intent.getAction()) {
            case ACTION_START:
                Log.d(TAG, "Received user starts foreground intent");
                boolean createNewRtcClient = false;
                if (appRtcClient==null) {
                    createNewRtcClient = true;
                } else {
                    if (appRtcClient.roomState==StompWebSocketRTCClient.ConnectionState.CLOSED){
                        createNewRtcClient = true;
                    } else if (appRtcClient.roomState==StompWebSocketRTCClient.ConnectionState.ERROR) {
                        appRtcClient.disconnectFromRoom();
                        createNewRtcClient = true;
                    }
                }
                if (createNewRtcClient){
                    createRegisterUser();
                    appRtcClient = new StompWebSocketRTCClient(this, registerUser);
                    AppRTCClient.RoomConnectionParameters roomConnectionParameters = new AppRTCClient.RoomConnectionParameters("ws://192.168.6.105:8080/ws/websocket", "roomId", false, "http://localhost");
                    appRtcClient.connectToRoom(roomConnectionParameters);
                }
                startForeground(123, prepareNotification());
                createNewConnection(intent);
                startIntent = intent;
                break;
            case ACTION_STOP:
                closeConnection();
                stopForeground(true);
                stopSelf();
                break;
            case ACTION_REQUEST_RANDOM_CHAT:
                sendRandomChatRequest();
                break;
            case ACTION_LEAVE_ROOM:
                sendLeaveRoomMessage();
                break;
            default:
                startForeground(123, prepareNotification());
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        closeConnection();
        super.onDestroy();
    }

    void createNewConnection(Intent intent){
        if (webRtc != null) {
            webRtc.onDestroy();
        }
        webRtc = new WebRtcWrapper(intent, getApplicationContext(), null);
        appRtcClient.setSignalingEvents(webRtc);
        webRtc.setOnServerMessage(this);
        webRtc.setupWithAppRTCClient(appRtcClient);
        webRtc.onCreate();
        webRtc.createPeerConnectionClient(getDefaultSignalingParam());

    }

    void closeConnection(){
        if (webRtc!=null) {
            webRtc.onDestroy();
            webRtc = null;
        }
        if (appRtcClient!=null){
            appRtcClient.disconnectFromRoom();
            appRtcClient = null;
        }
    }

    void sendRandomChatRequest(){
        if (appRtcClient != null) {
            RandomChatReq req = new RandomChatReq();
            req.signature = UUID.randomUUID().toString();
            req.from = "123123";
            req.room = "12";
            req.talkAbout = "nothing";
            req.birthFrom = 1995;
            req.birthTo = 2000;
            req.reqTimeMillis = 0;
            appRtcClient.sendRandomChatRequest(req);
        } else {
            Log.e(TAG, "Send RandomChat Request on null appRtcClient");
        }
    }

    void sendLeaveRoomMessage(){
        if (appRtcClient != null) {
            createNewConnection(startIntent);
            appRtcClient.sendLeaveRoomMessage();
        } else {
            Log.e(TAG, "Send LeaveRoom Request on null appRtcClient");
        }
    }

    private Notification prepareNotification() {
        // handle build version above android oreo
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                mNotificationManager.getNotificationChannel(FOREGROUND_CHANNEL_ID) == null) {
            CharSequence name = "name"; //getString(R.string.text_name_notification);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(FOREGROUND_CHANNEL_ID, name, importance);
            channel.enableVibration(false);
            mNotificationManager.createNotificationChannel(channel);
        }

        Intent notificationIntent = new Intent(this, CallActivity.class);
        notificationIntent.setAction("ACTION.MAIN_ACTION");
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // if min sdk goes below honeycomb
        /*if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        } else {
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }*/

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // make a stop intent
        Intent stopIntent = new Intent(this, WebRtcWrapperService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent pendingStopIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.notification);
        remoteViews.setOnClickPendingIntent(R.id.title, pendingStopIntent);
        remoteViews.setImageViewResource(R.id.image, R.drawable.ic_launcher);
        remoteViews.setTextViewText(R.id.title, "Custom notification");
        remoteViews.setTextViewText(R.id.text, "This is a custom layout");

        RemoteViews remoteViewsExpanded = new RemoteViews(getPackageName(), R.layout.notification);
        remoteViewsExpanded.setOnClickPendingIntent(R.id.title, pendingStopIntent);
        remoteViewsExpanded.setImageViewResource(R.id.image, R.drawable.ic_launcher);
        remoteViewsExpanded.setTextViewText(R.id.title, "Custom notification");
        remoteViewsExpanded.setTextViewText(R.id.text, "This is a custom layout");

        // if it is connected
        /*switch(stateService) {
            case Constants.STATE_SERVICE.NOT_CONNECTED:
                remoteViews.setTextViewText(R.id.tv_state, "DISCONNECTED");
                break;
            case Constants.STATE_SERVICE.CONNECTED:
                remoteViews.setTextViewText(R.id.tv_state, "CONNECTED");
                break;
        }*/

        // notification builder
        NotificationCompat.Builder notificationBuilder;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationBuilder = new NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID);
        } else {
            notificationBuilder = new NotificationCompat.Builder(this);
        }
        notificationBuilder
                .setContent(remoteViews)
                .setSmallIcon(R.drawable.ic_launcher)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(remoteViews)
                .setCustomBigContentView(remoteViewsExpanded)
                //.setCategory(NotificationCompat.CATEGORY_SERVICE)
                //.setOnlyAlertOnce(true)
                //.setOngoing(true)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        }

        return notificationBuilder.build();
    }

    @Override
    public void onServerMessage(String msg, int type) {
        handler.post(() -> {
            Log.d("WebRtcService", "got msg: "+msg);
        });
    }

    @Override
    public void onSocketClosed() {
        handler.post(() -> {
            if (appRtcClient!=null && appRtcClient.roomState != StompWebSocketRTCClient.ConnectionState.CLOSED) {
                appRtcClient.disconnectFromRoom();
            }
            appRtcClient = null;
            stopForeground(true);
            stopSelf();
        });
    }

    @Override
    public void onLeaveRoom() {
        handler.post(() -> {
            if (appRtcClient != null) {
                createNewConnection(startIntent);
            } else {
                Log.e(TAG, "Send LeaveRoom Request on null appRtcClient");
            }
        });
    }

    @Override
    public void notifyMessage(String msg) {
        handler.post(() -> {
            logMessage(msg);
        });
    }

    AppRTCClient.SignalingParameters getDefaultSignalingParam(){
        List<PeerConnection.IceServer> servers = new ArrayList<>();
            String url = "stun:turn2.l.google.com";
            String credential = "";
            PeerConnection.IceServer turnServer =
                    PeerConnection.IceServer.builder(url)
                            .setPassword(credential)
                            .createIceServer();
        servers.add(turnServer);
        AppRTCClient.SignalingParameters param = new AppRTCClient.SignalingParameters();
        param.iceServers=servers;
        return param;
    }
    //////////////////////////////////////////////////
    int msg_id = 0;
    public void logMessage(String messageBody) {
        Intent intent = new Intent(getApplicationContext(), WebRtcWrapperService.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0 /* Request code */, intent,
                PendingIntent.FLAG_ONE_SHOT);

        String channelId = "debug_channel_id";
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(getApplicationContext(), channelId)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle("Log")
                        .setContentText(messageBody)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    "Channel human readable title",
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify(msg_id++ /* ID of notification */, notificationBuilder.build());
    }
}