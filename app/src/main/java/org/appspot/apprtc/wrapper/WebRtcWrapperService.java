package org.appspot.apprtc.wrapper;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.appspot.apprtc.AppRTCAudioManager;
import org.appspot.apprtc.AppRTCClient;
import org.appspot.apprtc.CallActivity;
import org.appspot.apprtc.CallFragment;
import org.appspot.apprtc.CpuMonitor;
import org.appspot.apprtc.DirectRTCClient;
import org.appspot.apprtc.HudFragment;
import org.appspot.apprtc.PeerConnectionClient;
import org.appspot.apprtc.R;
import org.appspot.apprtc.UnhandledExceptionHandler;
import org.appspot.apprtc.WebSocketRTCClient;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.FileVideoCapturer;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFileRenderer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class WebRtcWrapperService extends Service  {
    private static final String TAG = "WebRtcService";
    //////////////////////////////////////////////////
    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_STOP = "ACTION_STOP";

    private final static String FOREGROUND_CHANNEL_ID = "foreground_channel_id";
    private NotificationManager mNotificationManager;
    private WebRtcWrapper webRtc;

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
                startForeground(123, prepareNotification());
                createNewConnection(intent);
                break;
            case ACTION_STOP:
                closeConnection();
                stopForeground(true);
                stopSelf();
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
        webRtc.onCreate();
    }

    void closeConnection(){
        if (webRtc!=null) {
            webRtc.onDestroy();
            webRtc = null;
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

    //////////////////////////////////////////////////

}