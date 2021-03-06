/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.appspot.apprtc.wrapper;

import android.os.Handler;
import androidx.annotation.Nullable;
import android.util.Log;

import com.google.gson.Gson;

import io.reactivex.CompletableTransformer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.StompClient;
import ua.naiksoftware.stomp.dto.StompHeader;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.appspot.apprtc.util.AsyncHttpURLConnection;
import org.appspot.apprtc.util.AsyncHttpURLConnection.AsyncHttpEvents;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * WebSocket client implementation.
 *
 * <p>All public methods should be called from a looper executor thread
 * passed in a constructor, otherwise exception will be thrown.
 * All events are dispatched on the same thread.
 */
public class StompWebSocketChannelClient {
  private static final String TAG = "WSChannelRTCClient";
  public static final String SEND_URL = "/app/signal";
  public static final String REGISTER_SOCKET = "/app/register";
  public static final String RANDOM_CHAT_URL = "/app/randomChat";
  public static final String LEAVE_ROOM_URL = "/app/leaveRoom";
  private static final int CLOSE_TIMEOUT = 1000;
  String signature = UUID.randomUUID().toString().replace("-","");
  private final WebSocketChannelEvents events;
  private final Handler handler; // Local thread of WebRTCClient, not main thread
  private StompClient ws;
  private CompositeDisposable compositeDisposable;
  private Gson gson = new Gson();
  private RegisterUser registerUser;

  private String wsServerUrl;
  private String postServerUrl;
  //@Nullable
  //private String roomID;
  //@Nullable
  //private String clientID;
  private WebSocketConnectionState state;
  // Do not remove this member variable. If this is removed, the observer gets garbage collected and
  // this causes test breakages.
  private WebSocketObserver wsObserver;
  private final Object closeEventLock = new Object();
  private boolean closeEvent;
  // WebSocket send queue. Messages are added to the queue when WebSocket
  // client is not registered and are consumed in register() call.
  private final List<String> wsSendQueue = new ArrayList<>();

  /**
   * Possible WebSocket connection states.
   */
  public enum WebSocketConnectionState { NEW, CONNECTED, REGISTERED, CLOSED, ERROR }

  /**
   * Callback interface for messages delivered on WebSocket.
   * All events are dispatched from a looper executor thread.
   */
  public interface WebSocketChannelEvents {
    void onWebSocketMessage(final String message);
    void onWebSocketClose();
    void onWebSocketError(final String description);
  }

  public StompWebSocketChannelClient(Handler handler, WebSocketChannelEvents events) {
    this.handler = handler;
    this.events = events;
    //roomID = null;
    //clientID = null;
    state = WebSocketConnectionState.NEW;
  }

  public WebSocketConnectionState getState() {
    return state;
  }

  public void connect(final String wsUrl, final String postUrl, final String myUuid) {
    checkIfCalledOnValidThread();
    if (state != WebSocketConnectionState.NEW) {
      Log.e(TAG, "WebSocket is already connected.");
      return;
    }
    wsServerUrl = wsUrl;
    postServerUrl = postUrl;
    closeEvent = false;

    Log.d(TAG, "Connecting WebSocket to: " + wsUrl + ". Post URL: " + postUrl);
    ws = Stomp.over(Stomp.ConnectionProvider.OKHTTP, wsServerUrl);
    wsObserver = new WebSocketObserver();
    try {
      connectStomp(ws, wsObserver, myUuid);
    } catch (Exception e) {
      reportError("WebSocket connection error: " + e.getMessage());
    }
  }

  public static final String LOGIN = "login";

  public static final String PASSCODE = "passcode";

  public void connectStomp(StompClient ws, WebSocketObserver wsObserver, String myUuid) {
    List<StompHeader> headers = new ArrayList<>();
    headers.add(new StompHeader(LOGIN, "guest"));
    headers.add(new StompHeader(PASSCODE, "passcode"));
    ws.withClientHeartbeat(30000).withServerHeartbeat(30000);

    resetSubscriptions();

    Disposable dispLifecycle = ws.lifecycle()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(lifecycleEvent -> {
              switch (lifecycleEvent.getType()) {
                case OPENED:
                  Log.d(TAG, "Stomp connection opened");
                  wsObserver.onOpen();
                  break;
                case ERROR:
                  Log.e(TAG, "Stomp connection error", lifecycleEvent.getException());
                  break;
                case CLOSED:
                  Log.d(TAG, "Stomp connection closed");
                  resetSubscriptions();
                  wsObserver.onClose("CLOSED");
                  break;
                case FAILED_SERVER_HEARTBEAT:
                  Log.d(TAG,"Stomp failed server heartbeat");
                  break;
              }
            });
    compositeDisposable.add(dispLifecycle);

    // Receive greetings
    Disposable dispTopic = ws.topic("/user/"+myUuid+"/queue/messages")
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(topicMessage -> {
              wsObserver.onTextMessage(topicMessage.getPayload());
              Log.d(TAG, "Received " + topicMessage.getPayload());
            }, throwable -> {
              Log.e(TAG, "Error on subscribe topic", throwable);
            });

    compositeDisposable.add(dispTopic);

    ws.connect(headers);
  }

  private void resetSubscriptions() {
    if (compositeDisposable != null) {
      compositeDisposable.dispose();
    }
    compositeDisposable = new CompositeDisposable();
  }

  public void register(final String roomID, final String clientID, RegisterUser registerUser) {
    checkIfCalledOnValidThread();
    //this.roomID = roomID;
    //this.clientID = clientID;
    this.registerUser = registerUser;
    if (state != WebSocketConnectionState.CONNECTED) {
      Log.w(TAG, "WebSocket register() in state " + state);
      return;
    }
    try {
      String msg = gson.toJson(registerUser);
      Log.d(TAG, "Send: " + msg);
      registerSocket(msg);
      /*
      state = WebSocketConnectionState.REGISTERED;
      */
      // Send any previously accumulated messages.
      for (String sendMessage : wsSendQueue) {
        send(sendMessage);
      }
      wsSendQueue.clear();

    } catch (Exception e) {
      reportError("WebSocket register JSON error: " + e.getMessage());
    }
    /*Log.d(TAG, "Registering WebSocket for room " + roomID + ". ClientID: " + clientID);
    JSONObject json = new JSONObject();
    try {
      json.put("cmd", "register");
      json.put("roomid", roomID);
      json.put("clientid", clientID);
      Log.d(TAG, "C->WSS: " + json.toString());
      sendSocket(json.toString());
      state = WebSocketConnectionState.REGISTERED;
      // Send any previously accumulated messages.
      for (String sendMessage : wsSendQueue) {
        send(sendMessage);
      }
      wsSendQueue.clear();
    } catch (JSONException e) {
      reportError("WebSocket register JSON error: " + e.getMessage());
    }*/
  }

  void registerSocket(String msg){
    Log.d(TAG, "REGISTER SOCKET: " + msg);
    compositeDisposable.add(ws.send(REGISTER_SOCKET, msg)
            .compose(applySchedulers())
            .subscribe(() -> {
              Log.d(TAG, "STOMP echo send successfully");
            }, throwable -> {
              Log.e(TAG, "Error send STOMP echo", throwable);
            }));
  }

  private void sendSocket(String msg, String sendUrl){
    Log.d(TAG, "SEND SOCKET: " + msg);
    compositeDisposable.add(ws.send(sendUrl, msg)
            .compose(applySchedulers())
            .subscribe(() -> {
              Log.d(TAG, "STOMP echo send successfully");
            }, throwable -> {
              Log.e(TAG, "Error send STOMP echo", throwable);
            }));
  }

  protected CompletableTransformer applySchedulers() {
    return upstream -> upstream
            .unsubscribeOn(Schedulers.newThread())
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread());
  }

  public void send(String message) {
    send(message, SEND_URL);
  }

  public void send(String message, String sendUrl) {
    checkIfCalledOnValidThread();
    switch (state) {
      case NEW:
      case CONNECTED:
        // Store outgoing messages and send them after websocket client
        // is registered.
        Log.d(TAG, "WS ACC: " + message);
        wsSendQueue.add(message);
        return;
      case ERROR:
      case CLOSED:
        Log.e(TAG, "WebSocket send() in error or closed state : " + message);
        return;
      case REGISTERED:
        sendSocket(message, sendUrl);
        break;
    }
  }

  // This call can be used to send WebSocket messages before WebSocket
  // connection is opened.
  public void post(String message) {
    checkIfCalledOnValidThread();
    sendWSSMessage("POST", message);
  }

  public void disconnect(boolean waitForComplete) {
    checkIfCalledOnValidThread();
    Log.d(TAG, "Disconnect WebSocket. State: " + state);
    if (state == WebSocketConnectionState.REGISTERED) {
      // Send "bye" to WebSocket server.
      //send("{\"type\": \"bye\"}");
      state = WebSocketConnectionState.CONNECTED;
      // Send http DELETE to http WebSocket server.
      sendWSSMessage("DELETE", "");
    }
    // Close WebSocket in CONNECTED or ERROR states only.
    if (state == WebSocketConnectionState.CONNECTED || state == WebSocketConnectionState.ERROR) {
      ws.disconnect();
      state = WebSocketConnectionState.CLOSED;

      // Wait for websocket close event to prevent websocket library from
      // sending any pending messages to deleted looper thread.
      if (waitForComplete) {
        synchronized (closeEventLock) {
          while (!closeEvent) {
            try {
              closeEventLock.wait(CLOSE_TIMEOUT);
              break;
            } catch (InterruptedException e) {
              Log.e(TAG, "Wait error: " + e.toString());
            }
          }
        }
      }
    }
    resetSubscriptions();
    Log.d(TAG, "Disconnecting WebSocket done.");
  }

  private void reportError(final String errorMessage) {
    Log.e(TAG, errorMessage);
    handler.post(new Runnable() {
      @Override
      public void run() {
        if (state != WebSocketConnectionState.ERROR) {
          state = WebSocketConnectionState.ERROR;
          events.onWebSocketError(errorMessage);
        }
      }
    });
  }

  // Asynchronously send POST/DELETE to WebSocket server.
  private void sendWSSMessage(final String method, final String message) {
    /*String postUrl = postServerUrl + "/" + roomID + "/" + clientID;
    Log.d(TAG, "WS " + method + " : " + postUrl + " : " + message);
    AsyncHttpURLConnection httpConnection =
        new AsyncHttpURLConnection(method, postUrl, message, new AsyncHttpEvents() {
          @Override
          public void onHttpError(String errorMessage) {
            reportError("WS " + method + " error: " + errorMessage);
          }

          @Override
          public void onHttpComplete(String response) {}
        });
    httpConnection.send();*/
  }

  // Helper method for debugging purposes. Ensures that WebSocket method is
  // called on a looper thread.
  private void checkIfCalledOnValidThread() {
    if (Thread.currentThread() != handler.getLooper().getThread()) {
      throw new IllegalStateException("WebSocket method is not called on valid thread");
    }
  }

  public interface WebSocketConnectionObserver {
    void onOpen();

    void onClose(String var2);

    void onTextMessage(String var1);

    void onRawTextMessage(byte[] var1);

    void onBinaryMessage(byte[] var1);

    public static enum WebSocketCloseNotification {
      NORMAL,
      CANNOT_CONNECT,
      CONNECTION_LOST,
      PROTOCOL_ERROR,
      INTERNAL_ERROR,
      SERVER_ERROR,
      RECONNECT;

      private WebSocketCloseNotification() {
      }
    }
  }

  private class WebSocketObserver implements WebSocketConnectionObserver {
    @Override
    public void onOpen() {
      Log.d(TAG, "WebSocket connection opened to: " + wsServerUrl);
      handler.post(new Runnable() {
        @Override
        public void run() {
          state = WebSocketConnectionState.CONNECTED;
          // Check if we have pending register request.
          //if (roomID != null && clientID != null && registerUser != null) {
            register(null, null, registerUser);
          //}
        }
      });
    }

    @Override
    public void onClose(String reason) {
      Log.d(TAG, "WebSocket connection closed. Reason: " + reason + ". State: "
              + state);
      synchronized (closeEventLock) {
        closeEvent = true;
        closeEventLock.notify();
      }
      handler.post(new Runnable() {
        @Override
        public void run() {
          if (state != WebSocketConnectionState.CLOSED) {
            events.onWebSocketClose();
            state = WebSocketConnectionState.CLOSED;
          }
        }
      });
    }

    @Override
    public void onTextMessage(String payload) {
      Log.d(TAG, "WSS->C: " + payload);
      final String message = payload;
      handler.post(new Runnable() {
        @Override
        public void run() {
          if (state == WebSocketConnectionState.CONNECTED) {
            // Handle registered response message
            RegisteredResponse response = gson.fromJson(payload, RegisteredResponse.class);
            if (response!=null && response.isSuccess()) {
              if (Code.REGISTERED.equals(response.code)) {
                state = WebSocketConnectionState.REGISTERED;
                Log.d(TAG, "Registered Success");
                events.onWebSocketMessage(message);
              }
            } else {
              Log.d(TAG, "Socket failed with message: "+payload);
            }
          } else if (state == WebSocketConnectionState.REGISTERED) {
            // handle message
            events.onWebSocketMessage(message);
          }
        }
      });
    }

    @Override
    public void onRawTextMessage(byte[] payload) {}

    @Override
    public void onBinaryMessage(byte[] payload) {}
  }
}
