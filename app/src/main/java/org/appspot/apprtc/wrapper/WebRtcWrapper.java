package org.appspot.apprtc.wrapper;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.FragmentTransaction;
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
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

import org.appspot.apprtc.AppRTCAudioManager;
import org.appspot.apprtc.AppRTCClient;
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
import java.util.UUID;

public class WebRtcWrapper implements AppRTCClient.SignalingEvents,
        PeerConnectionClient.PeerConnectionEvents,
        CallFragment.OnCallEvents {
    private static final String TAG = "CallRTCClient";

    public static final String EXTRA_ROOMID = "org.appspot.apprtc.ROOMID";
    public static final String EXTRA_URLPARAMETERS = "org.appspot.apprtc.URLPARAMETERS";
    public static final String EXTRA_LOOPBACK = "org.appspot.apprtc.LOOPBACK";
    public static final String EXTRA_VIDEO_CALL = "org.appspot.apprtc.VIDEO_CALL";
    public static final String EXTRA_SCREENCAPTURE = "org.appspot.apprtc.SCREENCAPTURE";
    public static final String EXTRA_CAMERA2 = "org.appspot.apprtc.CAMERA2";
    public static final String EXTRA_VIDEO_WIDTH = "org.appspot.apprtc.VIDEO_WIDTH";
    public static final String EXTRA_VIDEO_HEIGHT = "org.appspot.apprtc.VIDEO_HEIGHT";
    public static final String EXTRA_VIDEO_FPS = "org.appspot.apprtc.VIDEO_FPS";
    public static final String EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED =
            "org.appsopt.apprtc.VIDEO_CAPTUREQUALITYSLIDER";
    public static final String EXTRA_VIDEO_BITRATE = "org.appspot.apprtc.VIDEO_BITRATE";
    public static final String EXTRA_VIDEOCODEC = "org.appspot.apprtc.VIDEOCODEC";
    public static final String EXTRA_HWCODEC_ENABLED = "org.appspot.apprtc.HWCODEC";
    public static final String EXTRA_CAPTURETOTEXTURE_ENABLED = "org.appspot.apprtc.CAPTURETOTEXTURE";
    public static final String EXTRA_FLEXFEC_ENABLED = "org.appspot.apprtc.FLEXFEC";
    public static final String EXTRA_AUDIO_BITRATE = "org.appspot.apprtc.AUDIO_BITRATE";
    public static final String EXTRA_AUDIOCODEC = "org.appspot.apprtc.AUDIOCODEC";
    public static final String EXTRA_NOAUDIOPROCESSING_ENABLED =
            "org.appspot.apprtc.NOAUDIOPROCESSING";
    public static final String EXTRA_AECDUMP_ENABLED = "org.appspot.apprtc.AECDUMP";
    public static final String EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED =
            "org.appspot.apprtc.SAVE_INPUT_AUDIO_TO_FILE";
    public static final String EXTRA_OPENSLES_ENABLED = "org.appspot.apprtc.OPENSLES";
    public static final String EXTRA_DISABLE_BUILT_IN_AEC = "org.appspot.apprtc.DISABLE_BUILT_IN_AEC";
    public static final String EXTRA_DISABLE_BUILT_IN_AGC = "org.appspot.apprtc.DISABLE_BUILT_IN_AGC";
    public static final String EXTRA_DISABLE_BUILT_IN_NS = "org.appspot.apprtc.DISABLE_BUILT_IN_NS";
    public static final String EXTRA_DISABLE_WEBRTC_AGC_AND_HPF =
            "org.appspot.apprtc.DISABLE_WEBRTC_GAIN_CONTROL";
    public static final String EXTRA_DISPLAY_HUD = "org.appspot.apprtc.DISPLAY_HUD";
    public static final String EXTRA_TRACING = "org.appspot.apprtc.TRACING";
    public static final String EXTRA_CMDLINE = "org.appspot.apprtc.CMDLINE";
    public static final String EXTRA_RUNTIME = "org.appspot.apprtc.RUNTIME";
    public static final String EXTRA_VIDEO_FILE_AS_CAMERA = "org.appspot.apprtc.VIDEO_FILE_AS_CAMERA";
    public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE =
            "org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE";
    public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH =
            "org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE_WIDTH";
    public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT =
            "org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT";
    public static final String EXTRA_USE_VALUES_FROM_INTENT =
            "org.appspot.apprtc.USE_VALUES_FROM_INTENT";
    public static final String EXTRA_DATA_CHANNEL_ENABLED = "org.appspot.apprtc.DATA_CHANNEL_ENABLED";
    public static final String EXTRA_ORDERED = "org.appspot.apprtc.ORDERED";
    public static final String EXTRA_MAX_RETRANSMITS_MS = "org.appspot.apprtc.MAX_RETRANSMITS_MS";
    public static final String EXTRA_MAX_RETRANSMITS = "org.appspot.apprtc.MAX_RETRANSMITS";
    public static final String EXTRA_PROTOCOL = "org.appspot.apprtc.PROTOCOL";
    public static final String EXTRA_NEGOTIATED = "org.appspot.apprtc.NEGOTIATED";
    public static final String EXTRA_ID = "org.appspot.apprtc.ID";
    public static final String EXTRA_ENABLE_RTCEVENTLOG = "org.appspot.apprtc.ENABLE_RTCEVENTLOG";

    private static final int CAPTURE_PERMISSION_REQUEST_CODE = 1;

    // List of mandatory application permissions.
    private static final String[] MANDATORY_PERMISSIONS = {"android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.RECORD_AUDIO", "android.permission.INTERNET"};

    // Peer connection statistics callback period in ms.
    private static final int STAT_CALLBACK_PERIOD = 1000;

    private static class ProxyVideoSink implements VideoSink {
        private VideoSink target;

        @Override
        synchronized public void onFrame(VideoFrame frame) {
            if (target == null) {
                Logging.d(TAG, "Dropping frame in proxy because target is null.");
                return;
            }

            target.onFrame(frame);
        }

        synchronized public void setTarget(VideoSink target) {
            this.target = target;
        }
    }

    private ProxyVideoSink remoteProxyRenderer;
    private ProxyVideoSink localProxyVideoSink;
    @Nullable
    private PeerConnectionClient peerConnectionClient;
    @Nullable
    private AppRTCClient appRtcClient;
    @Nullable
    private AppRTCClient.SignalingParameters signalingParameters;
    @Nullable private AppRTCAudioManager audioManager;
    @Nullable
    private SurfaceViewRenderer pipRenderer;
    @Nullable
    private SurfaceViewRenderer fullscreenRenderer;
    @Nullable
    private VideoFileRenderer videoFileRenderer;
    private final List<VideoSink> remoteSinks = new ArrayList<>();
    private Toast logToast;
    private boolean commandLineRun;
    private boolean activityRunning;
    //private AppRTCClient.RoomConnectionParameters roomConnectionParameters;
    @Nullable
    private PeerConnectionClient.PeerConnectionParameters peerConnectionParameters;
    private boolean connected;
    private boolean isError;
    private boolean callControlFragmentVisible = true;
    private long callStartedTimeMs;
    private boolean micEnabled = true;
    private boolean screencaptureEnabled;
    private static Intent mediaProjectionPermissionResultData;
    private static int mediaProjectionPermissionResultCode;
    // True if local view is in the fullscreen renderer.
    private boolean isSwappedFeeds;

    // Controls
    private CallFragment callFragment;
    private HudFragment hudFragment;
    private CpuMonitor cpuMonitor;

    // Video enabled
    boolean videoCallEnabled;
    Gson gson = new Gson();
    Intent intent;
    Context applicationContext;
    Activity activity;
    Handler handler; // my looper
    RoomResponse room;
    StompWebSocketRTCClient.OnServerMessage onServerMessage;

    public WebRtcWrapper(@NonNull Intent intent, @NonNull Context applicationContext, @Nullable Activity activity) {
        this.intent = intent;
        this.applicationContext = applicationContext;
        this.activity = activity;
        this.handler = new Handler(Looper.myLooper());
    }

    public void setOnServerMessage(StompWebSocketRTCClient.OnServerMessage onServerMessage){
        this.onServerMessage = onServerMessage;
    }

    // TODO(bugs.webrtc.org/8580): LayoutParams.FLAG_TURN_SCREEN_ON and
    // LayoutParams.FLAG_SHOW_WHEN_LOCKED are deprecated.
    @SuppressWarnings("deprecation")
    public void onCreate() {
        final EglBase eglBase = EglBase.create();
        videoCallEnabled = intent.getBooleanExtra(EXTRA_VIDEO_CALL, false);

        //Thread.setDefaultUncaughtExceptionHandler(new UnhandledExceptionHandler(this));

        // Set window styles for fullscreen-window size. Needs to be done before
        // adding content.
        if (activity!=null) {
            activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            activity.getWindow().getDecorView().setSystemUiVisibility(getSystemUiVisibility());
            activity.setContentView(R.layout.activity_call);
        }
        connected = false;
        signalingParameters = null;

        // Create UI controls.
        if (videoCallEnabled && activity!=null) {
            remoteProxyRenderer = new ProxyVideoSink();
            localProxyVideoSink = new ProxyVideoSink();
            pipRenderer = activity.findViewById(R.id.pip_video_view);
            fullscreenRenderer = activity.findViewById(R.id.fullscreen_video_view);
            callFragment = new CallFragment();
            hudFragment = new HudFragment();
            // Show/hide call control fragment on view click.
            View.OnClickListener listener = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    toggleCallControlFragmentVisibility();
                }
            };

            // Swap feeds on pip view click.
            pipRenderer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    setSwappedFeeds(!isSwappedFeeds);
                }
            });

            fullscreenRenderer.setOnClickListener(listener);
            remoteSinks.add(remoteProxyRenderer);

            // Create video renderers.
            pipRenderer.init(eglBase.getEglBaseContext(), null);
            pipRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
            String saveRemoteVideoToFile = intent.getStringExtra(EXTRA_SAVE_REMOTE_VIDEO_TO_FILE);

            // When saveRemoteVideoToFile is set we save the video from the remote to a file.
            if (saveRemoteVideoToFile != null) {
                int videoOutWidth = intent.getIntExtra(EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH, 0);
                int videoOutHeight = intent.getIntExtra(EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT, 0);
                try {
                    videoFileRenderer = new VideoFileRenderer(
                            saveRemoteVideoToFile, videoOutWidth, videoOutHeight, eglBase.getEglBaseContext());
                    remoteSinks.add(videoFileRenderer);
                } catch (IOException e) {
                    throw new RuntimeException(
                            "Failed to open video file for output: " + saveRemoteVideoToFile, e);
                }
            }
            fullscreenRenderer.init(eglBase.getEglBaseContext(), null);
            fullscreenRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);

            pipRenderer.setZOrderMediaOverlay(true);
            pipRenderer.setEnableHardwareScaler(true /* enabled */);
            fullscreenRenderer.setEnableHardwareScaler(false /* enabled */);
            // Start with local feed in fullscreen and swap it to the pip when the call is connected.
            setSwappedFeeds(true /* isSwappedFeeds */);
        }
        // Check for mandatory permissions.
        if (activity!=null) {
            for (String permission : MANDATORY_PERMISSIONS) {
                if (activity.checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    logAndToast("Permission " + permission + " is not granted");
                    activity.setResult(Activity.RESULT_CANCELED);
                    activity.finish();
                    return;
                }
            }
        }
        Uri roomUri = intent.getData();
        if (roomUri == null) {
            logAndToast(applicationContext.getString(R.string.missing_url));
            Log.e(TAG, "Didn't get any URL in intent!");
            if (activity!=null) {
                activity.setResult(Activity.RESULT_CANCELED);
                activity.finish();
            }
            return;
        }

        // Get Intent parameters.
        String roomId = intent.getStringExtra(EXTRA_ROOMID);
        Log.d(TAG, "Room ID: " + roomId);
        if (roomId == null || roomId.length() == 0) {
            logAndToast(applicationContext.getString(R.string.missing_url));
            Log.e(TAG, "Incorrect room ID in intent!");
            if (activity!=null) {
                activity.setResult(Activity.RESULT_CANCELED);
                activity.finish();
            }
            return;
        }

        boolean loopback = intent.getBooleanExtra(EXTRA_LOOPBACK, false);
        boolean tracing = intent.getBooleanExtra(EXTRA_TRACING, false);

        int videoWidth = intent.getIntExtra(EXTRA_VIDEO_WIDTH, 0);
        int videoHeight = intent.getIntExtra(EXTRA_VIDEO_HEIGHT, 0);

        screencaptureEnabled = intent.getBooleanExtra(EXTRA_SCREENCAPTURE, false);
        // If capturing format is not specified for screencapture, use screen resolution.
        if (screencaptureEnabled && videoWidth == 0 && videoHeight == 0) {
            DisplayMetrics displayMetrics = getDisplayMetrics();
            videoWidth = displayMetrics.widthPixels;
            videoHeight = displayMetrics.heightPixels;
        }
        PeerConnectionClient.DataChannelParameters dataChannelParameters = null;
        if (intent.getBooleanExtra(EXTRA_DATA_CHANNEL_ENABLED, false)) {
            dataChannelParameters = new PeerConnectionClient.DataChannelParameters(intent.getBooleanExtra(EXTRA_ORDERED, true),
                    intent.getIntExtra(EXTRA_MAX_RETRANSMITS_MS, -1),
                    intent.getIntExtra(EXTRA_MAX_RETRANSMITS, -1), intent.getStringExtra(EXTRA_PROTOCOL),
                    intent.getBooleanExtra(EXTRA_NEGOTIATED, false), intent.getIntExtra(EXTRA_ID, -1));
        }
        peerConnectionParameters =
                new PeerConnectionClient.PeerConnectionParameters(intent.getBooleanExtra(EXTRA_VIDEO_CALL, true), loopback,
                        tracing, videoWidth, videoHeight, intent.getIntExtra(EXTRA_VIDEO_FPS, 0),
                        intent.getIntExtra(EXTRA_VIDEO_BITRATE, 0), intent.getStringExtra(EXTRA_VIDEOCODEC),
                        intent.getBooleanExtra(EXTRA_HWCODEC_ENABLED, true),
                        intent.getBooleanExtra(EXTRA_FLEXFEC_ENABLED, false),
                        intent.getIntExtra(EXTRA_AUDIO_BITRATE, 0), intent.getStringExtra(EXTRA_AUDIOCODEC),
                        intent.getBooleanExtra(EXTRA_NOAUDIOPROCESSING_ENABLED, false),
                        intent.getBooleanExtra(EXTRA_AECDUMP_ENABLED, false),
                        intent.getBooleanExtra(EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED, false),
                        intent.getBooleanExtra(EXTRA_OPENSLES_ENABLED, false),
                        intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_AEC, false),
                        intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_AGC, false),
                        intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_NS, false),
                        intent.getBooleanExtra(EXTRA_DISABLE_WEBRTC_AGC_AND_HPF, false),
                        intent.getBooleanExtra(EXTRA_ENABLE_RTCEVENTLOG, false), dataChannelParameters);
        commandLineRun = intent.getBooleanExtra(EXTRA_CMDLINE, false);
        int runTimeMs = intent.getIntExtra(EXTRA_RUNTIME, 0);

        Log.d(TAG, "VIDEO_FILE: '" + intent.getStringExtra(EXTRA_VIDEO_FILE_AS_CAMERA) + "'");

        // Create connection client. Use DirectRTCClient if room name is an IP otherwise use the
        // standard WebSocketRTCClient.
        if (loopback || !DirectRTCClient.IP_PATTERN.matcher(roomId).matches()) {
            //appRtcClient = new StompWebSocketRTCClient(this, registerUser);
        } else {
            Log.i(TAG, "Using DirectRTCClient because room name looks like an IP.");
            //appRtcClient = new DirectRTCClient(this);
        }
        // Create connection parameters.
        /*String urlParameters = intent.getStringExtra(EXTRA_URLPARAMETERS);
        roomConnectionParameters =
                new AppRTCClient.RoomConnectionParameters(roomUri.toString(), roomId, loopback, urlParameters);*/

        // Create CPU monitor
        if (CpuMonitor.isSupported()) {
            cpuMonitor = new CpuMonitor(applicationContext);
            if (hudFragment != null) hudFragment.setCpuMonitor(cpuMonitor);
        }

        if (videoCallEnabled && activity != null) {
            // Send intent arguments to fragments.
            callFragment.setArguments(intent.getExtras());
            hudFragment.setArguments(intent.getExtras());
            // Activate call and HUD fragments and start the call.
            FragmentTransaction ft = activity.getFragmentManager().beginTransaction();
            ft.add(R.id.call_fragment_container, callFragment);
            ft.add(R.id.hud_fragment_container, hudFragment);
            ft.commit();
        }
        // For command line execution run connection for <runTimeMs> and exit.
        if (commandLineRun && runTimeMs > 0) {
            (new Handler()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    disconnect();
                }
            }, runTimeMs);
        }

        // Create peer connection client.
        peerConnectionClient = new PeerConnectionClient(
                getApplicationContext(), eglBase, peerConnectionParameters, WebRtcWrapper.this);
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        if (loopback) {
            options.networkIgnoreMask = 0;
        }
        peerConnectionClient.createPeerConnectionFactory(options);

        if (screencaptureEnabled) {
            //startScreenCapture();
        } else {
            //startCall();
        }
    }

    public void setupWithAppRTCClient(AppRTCClient appRTCClient){
        this.appRtcClient = appRTCClient;
    }

    public void startCall() {
        if (appRtcClient == null) {
            Log.e(TAG, "AppRTC client is not allocated for a call.");
            return;
        }
        callStartedTimeMs = System.currentTimeMillis();

        // Start room connection.
        /*logAndToast(applicationContext.getString(R.string.connecting_to, roomConnectionParameters.roomUrl));
        appRtcClient.connectToRoom(roomConnectionParameters);*/

        // Create and audio manager that will take care of audio routing,
        // audio modes, audio device enumeration etc.
        audioManager = AppRTCAudioManager.create(getApplicationContext());
        // Store existing audio settings and change audio mode to
        // MODE_IN_COMMUNICATION for best possible VoIP performance.
        Log.d(TAG, "Starting the audio manager...");
        audioManager.start(new AppRTCAudioManager.AudioManagerEvents() {
            // This method will be called each time the number of available audio
            // devices has changed.
            @Override
            public void onAudioDeviceChanged(
                    AppRTCAudioManager.AudioDevice audioDevice, Set<AppRTCAudioManager.AudioDevice> availableAudioDevices) {
                onAudioManagerDevicesChanged(audioDevice, availableAudioDevices);
            }
        });
    }

    Context getApplicationContext(){
        return applicationContext;
    }

    Application getApplication(){
        return activity.getApplication();
    }

    Intent getIntent(){
        return this.intent;
    }

    @TargetApi(17)
    private DisplayMetrics getDisplayMetrics() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager =
                (WindowManager) getApplication().getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        return displayMetrics;
    }

    @TargetApi(19)
    private static int getSystemUiVisibility() {
        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        return flags;
    }

    @TargetApi(21)
    private void startScreenCapture() {
        MediaProjectionManager mediaProjectionManager =
                (MediaProjectionManager) getApplication().getSystemService(
                        Context.MEDIA_PROJECTION_SERVICE);
        activity.startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(), CAPTURE_PERMISSION_REQUEST_CODE);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != CAPTURE_PERMISSION_REQUEST_CODE)
            return;
        mediaProjectionPermissionResultCode = resultCode;
        mediaProjectionPermissionResultData = data;
        startCall();
    }

    private boolean useCamera2() {
        return Camera2Enumerator.isSupported(applicationContext) && getIntent().getBooleanExtra(EXTRA_CAMERA2, true);
    }

    private boolean captureToTexture() {
        return getIntent().getBooleanExtra(EXTRA_CAPTURETOTEXTURE_ENABLED, false);
    }

    private @Nullable
    VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    @TargetApi(21)
    private @Nullable VideoCapturer createScreenCapturer() {
        if (mediaProjectionPermissionResultCode != Activity.RESULT_OK) {
            reportError("User didn't give permission to capture the screen.");
            return null;
        }
        return new ScreenCapturerAndroid(
                mediaProjectionPermissionResultData, new MediaProjection.Callback() {
            @Override
            public void onStop() {
                reportError("User revoked permission to capture the screen.");
            }
        });
    }

    // Activity interfaces
    public void onStop() {
        activityRunning = false;
        // Don't stop the video when using screencapture to allow user to show other apps to the remote
        // end.
        if (peerConnectionClient != null && !screencaptureEnabled) {
            peerConnectionClient.stopVideoSource();
        }
        if (cpuMonitor != null) {
            cpuMonitor.pause();
        }
    }

    public void onStart() {
        activityRunning = true;
        // Video is not paused for screencapture. See onPause.
        if (peerConnectionClient != null && !screencaptureEnabled) {
            peerConnectionClient.startVideoSource();
        }
        if (cpuMonitor != null) {
            cpuMonitor.resume();
        }
    }

    protected void onDestroy() {
        Thread.setDefaultUncaughtExceptionHandler(null);
        disconnect();
        if (logToast != null) {
            logToast.cancel();
        }
        activityRunning = false;
    }

    // CallFragment.OnCallEvents interface implementation.
    @Override
    public void onCallHangUp() {
        disconnect();
    }

    @Override
    public void onCameraSwitch() {
        if (peerConnectionClient != null) {
            peerConnectionClient.switchCamera();
        }
    }

    @Override
    public void onVideoScalingSwitch(RendererCommon.ScalingType scalingType) {
        if (fullscreenRenderer!=null) fullscreenRenderer.setScalingType(scalingType);
    }

    @Override
    public void onCaptureFormatChange(int width, int height, int framerate) {
        if (peerConnectionClient != null) {
            peerConnectionClient.changeCaptureFormat(width, height, framerate);
        }
    }

    @Override
    public boolean onToggleMic() {
        if (peerConnectionClient != null) {
            micEnabled = !micEnabled;
            peerConnectionClient.setAudioEnabled(micEnabled);
        }
        return micEnabled;
    }

    // Helper functions.
    private void toggleCallControlFragmentVisibility() {
        if (!connected || !videoCallEnabled || !callFragment.isAdded()) {
            return;
        }
        // Show/hide call control fragment
        callControlFragmentVisible = !callControlFragmentVisible;
        FragmentTransaction ft = activity.getFragmentManager().beginTransaction();
        if (callControlFragmentVisible) {
            ft.show(callFragment);
            ft.show(hudFragment);
        } else {
            ft.hide(callFragment);
            ft.hide(hudFragment);
        }
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.commit();
    }

    // Should be called from UI thread
    private void callConnected() {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        Log.i(TAG, "Call connected: delay=" + delta + "ms");
        if (peerConnectionClient == null || isError) {
            Log.w(TAG, "Call is connected in closed or error state");
            return;
        }
        // Enable statistics callback.
        peerConnectionClient.enableStatsEvents(true, STAT_CALLBACK_PERIOD);
        setSwappedFeeds(false /* isSwappedFeeds */);
    }

    // This method is called when the audio manager reports audio device change,
    // e.g. from wired headset to speakerphone.
    private void onAudioManagerDevicesChanged(
            final AppRTCAudioManager.AudioDevice device, final Set<AppRTCAudioManager.AudioDevice> availableDevices) {
        Log.d(TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
                + "selected: " + device);
        // TODO(henrika): add callback handler.
    }

    // Disconnect from remote resources, dispose of local resources, and exit.
    private void disconnect() {
        activityRunning = false;
        if (remoteProxyRenderer != null) remoteProxyRenderer.setTarget(null);
        if (localProxyVideoSink != null) localProxyVideoSink.setTarget(null);
        if (appRtcClient != null) {
            // Now appRtcClient is handled outside
            // appRtcClient.disconnectFromRoom();
            appRtcClient = null;
        }
        if (pipRenderer != null) {
            pipRenderer.release();
            pipRenderer = null;
        }
        if (videoFileRenderer != null) {
            videoFileRenderer.release();
            videoFileRenderer = null;
        }
        if (fullscreenRenderer != null) {
            fullscreenRenderer.release();
            fullscreenRenderer = null;
        }
        if (peerConnectionClient != null) {
            peerConnectionClient.close();
            peerConnectionClient = null;
        }
        if (audioManager != null) {
            audioManager.stop();
            audioManager = null;
        }
        if (activity!=null) {
            if (connected && !isError) {
                activity.setResult(Activity.RESULT_OK);
            } else {
                activity.setResult(Activity.RESULT_CANCELED);
            }
            activity.finish();
        }
    }

    private void disconnectWithErrorMessage(final String errorMessage) {
        if (commandLineRun || !activityRunning) {
            Log.e(TAG, "Critical error: " + errorMessage);
            disconnect();
        } else {
            if (activity != null) {
                new AlertDialog.Builder(activity)
                        .setTitle(activity.getText(R.string.channel_error_title))
                        .setMessage(errorMessage)
                        .setCancelable(false)
                        .setNeutralButton(R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.cancel();
                                        disconnect();
                                    }
                                })
                        .create()
                        .show();
            }
        }
    }

    // Log |msg| and Toast about it.
    private void logAndToast(String msg) {
        Log.d(TAG, msg);
        if (activity != null) {
            if (logToast != null) {
                logToast.cancel();
            }
            logToast = Toast.makeText(activity, msg, Toast.LENGTH_SHORT);
            logToast.show();
        }
    }

    void runOnUiThread(Runnable runnable){
        if(activity!=null) {
            activity.runOnUiThread(runnable);
        } else {
            handler.post(runnable);
        }
    }

    private void reportError(final String description) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isError) {
                    isError = true;
                    disconnectWithErrorMessage(description);
                }
            }
        });
    }

    private @Nullable VideoCapturer createVideoCapturer() {
        final VideoCapturer videoCapturer;
        String videoFileAsCamera = getIntent().getStringExtra(EXTRA_VIDEO_FILE_AS_CAMERA);
        if (videoFileAsCamera != null) {
            try {
                videoCapturer = new FileVideoCapturer(videoFileAsCamera);
            } catch (IOException e) {
                reportError("Failed to open video file for emulated camera");
                return null;
            }
        } else if (screencaptureEnabled) {
            return createScreenCapturer();
        } else if (useCamera2()) {
            if (!captureToTexture()) {
                reportError(applicationContext.getString(R.string.camera2_texture_only_error));
                return null;
            }

            Logging.d(TAG, "Creating capturer using camera2 API.");
            videoCapturer = createCameraCapturer(new Camera2Enumerator(applicationContext));
        } else {
            Logging.d(TAG, "Creating capturer using camera1 API.");
            videoCapturer = createCameraCapturer(new Camera1Enumerator(captureToTexture()));
        }
        if (videoCapturer == null) {
            reportError("Failed to open camera");
            return null;
        }
        return videoCapturer;
    }

    private void setSwappedFeeds(boolean isSwappedFeeds) {
        if (videoCallEnabled ) {
            Logging.d(TAG, "setSwappedFeeds: " + isSwappedFeeds);
            this.isSwappedFeeds = isSwappedFeeds;
            localProxyVideoSink.setTarget(isSwappedFeeds ? fullscreenRenderer : pipRenderer);
            remoteProxyRenderer.setTarget(isSwappedFeeds ? pipRenderer : fullscreenRenderer);
            fullscreenRenderer.setMirror(isSwappedFeeds);
            pipRenderer.setMirror(!isSwappedFeeds);
        }
    }

    // -----Implementation of AppRTCClient.AppRTCSignalingEvents ---------------
    // All callbacks are invoked from websocket signaling looper thread and
    // are routed to UI thread.
    private void onConnectedToRoomInternal(final AppRTCClient.SignalingParameters params) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        Log.d(TAG, "Param from server: \n"+gson.toJson(params));
        if (params.room!=null) {
            room = params.room;
            if (room.isInitiator) {
                // I am the initiator
                logAndToast("Creating OFFER...");
                // Create offer. Offer SDP will be sent to answering client in
                // PeerConnectionEvents.onLocalDescription event.
                peerConnectionClient.createOffer();
            } else {
                if (params.offerSdp != null) {
                    // Offer sdp in response
                    peerConnectionClient.setRemoteDescription(params.offerSdp);
                    logAndToast("Creating ANSWER...");
                    // Create answer. Answer SDP will be sent to offering client in
                    // PeerConnectionEvents.onLocalDescription event.
                    peerConnectionClient.createAnswer();
                }
                if (params.iceCandidates != null) {
                    // Add remote ICE candidates from room.
                    for (IceCandidate iceCandidate : params.iceCandidates) {
                        peerConnectionClient.addRemoteIceCandidate(iceCandidate);
                    }
                }
            }
        }
    }

    public void createPeerConnectionClient(final AppRTCClient.SignalingParameters params){
        messageNotification("createPeerConnectionClient");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                signalingParameters = params;
                VideoCapturer videoCapturer = null;
                if (peerConnectionParameters.videoCallEnabled) {
                    videoCapturer = createVideoCapturer();
                }
                peerConnectionClient.createPeerConnection(
                        localProxyVideoSink, remoteSinks, videoCapturer, signalingParameters);
            }
        });
    }

    @Override
    public void onConnectedToRoom(final AppRTCClient.SignalingParameters params) {
        messageNotification("onConnectedToRoom");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onConnectedToRoomInternal(params);
            }
        });
    }

    @Override
    public void onRemoteDescription(final SessionDescription desc) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
                    return;
                }
                logAndToast("Received remote " + desc.type + ", delay=" + delta + "ms");
                peerConnectionClient.setRemoteDescription(desc);
                if (!room.isInitiator) {
                    logAndToast("Creating ANSWER...");
                    // Create answer. Answer SDP will be sent to offering client in
                    // PeerConnectionEvents.onLocalDescription event.
                    peerConnectionClient.createAnswer();
                }
            }
        });
    }

    @Override
    public void onRemoteIceCandidate(final IceCandidate candidate) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(TAG, "Received ICE candidate for a non-initialized peer connection.");
                    return;
                }
                peerConnectionClient.addRemoteIceCandidate(candidate);
            }
        });
    }

    @Override
    public void onRemoteIceCandidatesRemoved(final IceCandidate[] candidates) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(TAG, "Received ICE candidate removals for a non-initialized peer connection.");
                    return;
                }
                peerConnectionClient.removeRemoteIceCandidates(candidates);
            }
        });
    }

    @Override
    public void onChannelClose() {
        messageNotification("onChannelClose ");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast("Remote end hung up; dropping PeerConnection");
                disconnect();
            }
        });
    }

    @Override
    public void onChannelError(final String description) {
        messageNotification("onChannelError "+description);
        reportError(description);
    }

    // -----Implementation of PeerConnectionClient.PeerConnectionEvents.---------
    // Send local peer connection SDP and ICE candidates to remote party.
    // All callbacks are invoked from peer connection client looper thread and
    // are routed to UI thread.
    @Override
    public void onLocalDescription(final SessionDescription desc) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (appRtcClient != null) {
                    logAndToast("Sending " + desc.type + ", delay=" + delta + "ms");
                    if (room.isInitiator) {
                        appRtcClient.sendOfferSdp(desc);
                    } else {
                        appRtcClient.sendAnswerSdp(desc);
                    }
                }
                if (peerConnectionParameters.videoMaxBitrate > 0) {
                    Log.d(TAG, "Set video maximum bitrate: " + peerConnectionParameters.videoMaxBitrate);
                    peerConnectionClient.setVideoMaxBitrate(peerConnectionParameters.videoMaxBitrate);
                }
            }
        });
    }

    @Override
    public void onIceCandidate(final IceCandidate candidate) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (appRtcClient != null) {
                    appRtcClient.sendLocalIceCandidate(candidate);
                }
            }
        });
    }

    @Override
    public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (appRtcClient != null) {
                    appRtcClient.sendLocalIceCandidateRemovals(candidates);
                }
            }
        });
    }

    @Override
    public void onIceConnected() {
        messageNotification("onIceConnected");
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast("ICE connected, delay=" + delta + "ms");
            }
        });
    }

    @Override
    public void onIceDisconnected() {
        messageNotification("onIceDisconnected");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast("ICE disconnected");
            }
        });
    }

    @Override
    public void onConnected() {
        messageNotification("onPeerConnection Connected");
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast("DTLS connected, delay=" + delta + "ms");
                connected = true;
                callConnected();
            }
        });
    }

    @Override
    public void onDisconnected() {
        messageNotification("onPeerConnectionDisconnected");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast("DTLS disconnected");
                connected = false;
                disconnect();
            }
        });
    }

    @Override
    public void onPeerConnectionClosed() {
        messageNotification("onPeerConnectionClosed");
    }

    @Override
    public void onPeerConnectionStatsReady(final StatsReport[] reports) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isError && connected) {
                    if (hudFragment !=null ) hudFragment.updateEncoderStatistics(reports);
                }
            }
        });
    }

    @Override
    public void onPeerConnectionError(final String description) {
        messageNotification("PeerConnectionError:" +description);
        reportError(description);
    }

    int id = (int) (Math.random() * 1000);
    private void messageNotification(String msg){
        if(onServerMessage!=null) {
            onServerMessage.notifyMessage("[["+id+"]] "+msg);
        }
    }
}