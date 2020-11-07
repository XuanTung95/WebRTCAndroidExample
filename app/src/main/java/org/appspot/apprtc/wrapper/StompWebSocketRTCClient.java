package org.appspot.apprtc.wrapper;

import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.Nullable;
import android.util.Log;

import com.google.gson.Gson;

import org.appspot.apprtc.AppRTCClient;
import org.appspot.apprtc.RoomParametersFetcher;
import org.appspot.apprtc.RoomParametersFetcher.RoomParametersFetcherEvents;
import org.appspot.apprtc.util.AsyncHttpURLConnection;
import org.appspot.apprtc.util.AsyncHttpURLConnection.AsyncHttpEvents;
import org.appspot.apprtc.wrapper.StompWebSocketChannelClient.WebSocketConnectionState;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.util.UUID;

/**
 * Negotiates signaling for chatting with https://appr.tc "rooms".
 * Uses the client<->server specifics of the apprtc AppEngine webapp.
 *
 * <p>To use: create an instance of this object (registering a message handler) and
 * call connectToRoom().  Once room connection is established
 * onConnectedToRoom() callback with room parameters is invoked.
 * Messages to other party (with local Ice candidates and answer SDP) can
 * be sent after WebSocket connection is established.
 */
public class StompWebSocketRTCClient implements AppRTCClient, StompWebSocketChannelClient.WebSocketChannelEvents {
    private static final String TAG = "SWSRTCClient";
    private static final String ROOM_JOIN = "join";
    private static final String ROOM_MESSAGE = "message";
    private static final String ROOM_LEAVE = "leave";

    public enum ConnectionState { NEW, CONNECTED, CLOSED, ERROR }

    private enum MessageType { MESSAGE, LEAVE }

    private final Handler handler; // Local thread for this client
    private boolean initiator;
    private SignalingEvents events;
    private StompWebSocketChannelClient wsClient;
    public ConnectionState roomState;
    private RoomConnectionParameters connectionParameters;
    private String messageUrl;
    private String leaveUrl;
    private RegisterUser registerUser;
    private final Gson gson = new Gson();
    private RoomResponse room;
    private RoomResponse prevRoom;
    private OnServerMessage onServerMessage;

    public interface OnServerMessage {
        void onServerMessage(String msg, int type);
        void onSocketClosed();
        void onLeaveRoom();
        void notifyMessage(String msg);
    }

    public StompWebSocketRTCClient(OnServerMessage onServerMessage, RegisterUser registerUser) {
        this.onServerMessage = onServerMessage;
        //this.events = events;
        this.registerUser = registerUser;
        roomState = ConnectionState.NEW;
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    public void setSignalingEvents(SignalingEvents events){
        this.events = events;
    }

    // --------------------------------------------------------------------
    // AppRTCClient interface implementation.
    // Asynchronously connect to an AppRTC room URL using supplied connection
    // parameters, retrieves room parameters and connect to WebSocket server.
    @Override
    public void connectToRoom(RoomConnectionParameters connectionParameters) {
        this.connectionParameters = connectionParameters;
        if (onServerMessage!=null) onServerMessage.notifyMessage("WebSocket -> Connect to room");
        handler.post(new Runnable() {
            @Override
            public void run() {
                connectToRoomInternal();
            }
        });
    }

    @Override
    public void disconnectFromRoom() {
        if (onServerMessage!=null) onServerMessage.notifyMessage("WebSocket -> Disconnect to room");
        handler.post(new Runnable() {
            @Override
            public void run() {
                disconnectFromRoomInternal();
                handler.getLooper().quit();
            }
        });
    }

    // Connects to room - function runs on a local looper thread.
    private void connectToRoomInternal() {
        roomState = ConnectionState.NEW;
        createWebSocketChannelClient();

        // Fire connection and signaling parameters events.
        // events.onConnectedToRoom(signalingParameters);

        // Connect and register WebSocket client.
        wsClient.connect(this.connectionParameters.roomUrl, null, registerUser.signature);
        roomState = ConnectionState.CONNECTED;
        wsClient.register(null, null, registerUser);
        /*RoomParametersFetcherEvents callbacks = new RoomParametersFetcherEvents() {
            @Override
            public void onSignalingParametersReady(final SignalingParameters params) {
                StompWebSocketRTCClient.this.handler.post(new Runnable() {
                    @Override
                    public void run() {
                        StompWebSocketRTCClient.this.signalingParametersReady(params);
                    }
                });
            }

            @Override
            public void onSignalingParametersError(String description) {
                StompWebSocketRTCClient.this.reportError(description);
                onServerMessage.onSocketClosed();
            }
        };

        new RoomParametersFetcher(*//*connectionUrl*//* "http://192.168.6.105:8080/signalingParam", null, callbacks).makeRequest();*/
    }

    private void createWebSocketChannelClient(){
        if (wsClient==null || wsClient.getState()==WebSocketConnectionState.CLOSED) {
            wsClient = new StompWebSocketChannelClient(handler, this);
        } else if (wsClient.getState()==WebSocketConnectionState.ERROR) {
            wsClient.disconnect(true);
            wsClient = new StompWebSocketChannelClient(handler, this);
        } else {
            // no need to create new client
        }
    }

    // Disconnect from room and send bye messages - runs on a local looper thread.
    private void disconnectFromRoomInternal() {
        Log.d(TAG, "Disconnect. Room state: " + roomState);
        if (roomState == ConnectionState.CONNECTED) {
            Log.d(TAG, "Closing room.");
            sendPostMessage(MessageType.LEAVE, leaveUrl, null);
        }
        roomState = ConnectionState.CLOSED;
        if (wsClient != null) {
            wsClient.disconnect(true);
            wsClient = null;
        }
    }

    // Helper functions to get connection, post message and leave message URLs
    private String getConnectionUrl(RoomConnectionParameters connectionParameters) {
        return connectionParameters.roomUrl + "/" + ROOM_JOIN + "/" + connectionParameters.roomId
                + getQueryString(connectionParameters);
    }

    private String getMessageUrl(
            RoomConnectionParameters connectionParameters, SignalingParameters signalingParameters) {
        return connectionParameters.roomUrl + "/" + ROOM_MESSAGE + "/" + connectionParameters.roomId
                + "/" + signalingParameters.clientId + getQueryString(connectionParameters);
    }

    private String getLeaveUrl(
            RoomConnectionParameters connectionParameters, SignalingParameters signalingParameters) {
        return connectionParameters.roomUrl + "/" + ROOM_LEAVE + "/" + connectionParameters.roomId + "/"
                + signalingParameters.clientId + getQueryString(connectionParameters);
    }

    private String getQueryString(RoomConnectionParameters connectionParameters) {
        if (connectionParameters.urlParameters != null) {
            return "?" + connectionParameters.urlParameters;
        } else {
            return "";
        }
    }

    // Callback issued when room parameters are extracted. Runs on local
    // looper thread.
    /*private void signalingParametersReady(final SignalingParameters signalingParameters) {
        Log.d(TAG, "Room connection completed.");
        if (connectionParameters.loopback
                && (*//*!signalingParameters.room.isInitiator ||*//* signalingParameters.offerSdp != null)) {
            reportError("Loopback room is busy.");
            return;
        }
        if (!connectionParameters.loopback *//*&& !signalingParameters.room.isInitiator*//*
                && signalingParameters.offerSdp == null) {
            Log.w(TAG, "No offer SDP in room response.");
        }
        //initiator = signalingParameters.room.isInitiator;
        // Use to send offer, ice candidate to server via post
        // messageUrl = getMessageUrl(connectionParameters, signalingParameters); // https://appr.tc/message/375970/84454575
        // Use to send leave message
        // leaveUrl = getLeaveUrl(connectionParameters, signalingParameters); // https://appr.tc/leave/375970/84454575
        // Log.d(TAG, "Message URL: " + messageUrl);
        // Log.d(TAG, "Leave URL: " + leaveUrl);
        roomState = ConnectionState.CONNECTED;

        // Fire connection and signaling parameters events.
        // events.onConnectedToRoom(signalingParameters);

        // Connect and register WebSocket client.
        wsClient.connect(signalingParameters.wssUrl *//*"ws://192.168.70.14:8080/ws/websocket"*//*, signalingParameters.wssPostUrl, registerUser.signature);
        wsClient.register(connectionParameters.roomId, signalingParameters.clientId, registerUser);
    }*/

    public void sendRandomChatRequest(final RandomChatReq req){
        handler.post(() -> wsClient.send(gson.toJson(req), StompWebSocketChannelClient.RANDOM_CHAT_URL));
    }

    public void sendLeaveRoomMessage(){
        handler.post(() -> wsClient.send("{}", StompWebSocketChannelClient.LEAVE_ROOM_URL));
        if (room!=null) {
            prevRoom = room;
            room = null;
        }
    }

    // Send local offer SDP to the other participant.
    @Override
    public void sendOfferSdp(final SessionDescription sdp) {
        Log.d(TAG, "SEND OFFER SDP: "+sdp.description);
        if (onServerMessage!=null) onServerMessage.notifyMessage("WebSocket -> Send Offer Sdp");
        handler.post(() -> {
            if (roomState != ConnectionState.CONNECTED) {
                reportError("Sending offer SDP in non connected state.");
                return;
            }
            JSONObject json = new JSONObject();
            jsonPut(json, "sdp", sdp.description);
            jsonPut(json, "type", "offer");
            //sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
            sendMessageToPartner(json.toString());
            if (connectionParameters.loopback) {
                // In loopback mode rename this offer to answer and route it back.
                SessionDescription sdpAnswer = new SessionDescription(
                        SessionDescription.Type.fromCanonicalForm("answer"), sdp.description);
                if (events!=null)
                    events.onRemoteDescription(sdpAnswer);
            }
        });
    }

    // Send local answer SDP to the other participant.
    @Override
    public void sendAnswerSdp(final SessionDescription sdp) {
        Log.d(TAG, "SEND ANSWER SDP: "+sdp.description);
        if (onServerMessage!=null) onServerMessage.notifyMessage("WebSocket -> Send Answer Sdp");
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (connectionParameters.loopback) {
                    Log.e(TAG, "Sending answer in loopback mode.");
                    return;
                }
                JSONObject json = new JSONObject();
                jsonPut(json, "sdp", sdp.description);
                jsonPut(json, "type", "answer");
                //wsClient.send(json.toString());
                sendMessageToPartner(json.toString());
            }
        });
    }

    // Send Ice candidate to the other participant.
    @Override
    public void sendLocalIceCandidate(final IceCandidate candidate) {
        Log.d(TAG, "SEND LOCAL ICE CANDIDATE: "+candidate.sdp);
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "type", "candidate");
                jsonPut(json, "label", candidate.sdpMLineIndex);
                jsonPut(json, "id", candidate.sdpMid);
                jsonPut(json, "candidate", candidate.sdp);
                if (initiator) {
                    // Call initiator sends ice candidates to GAE server.
                    if (roomState != ConnectionState.CONNECTED) {
                        reportError("Sending ICE candidate in non connected state.");
                        return;
                    }
                    //sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
                    sendMessageToPartner(json.toString());
                    if (connectionParameters.loopback) {
                        if (events!=null)
                            events.onRemoteIceCandidate(candidate);
                    }
                } else {
                    // Call receiver sends ice candidates to websocket server.
                    //wsClient.send(json.toString());
                    sendMessageToPartner(json.toString());
                }
            }
        });
    }

    // Send removed Ice candidates to the other participant.
    @Override
    public void sendLocalIceCandidateRemovals(final IceCandidate[] candidates) {
        Log.d(TAG, "SEND LOCAL ICE CANDIDATE REMOVALS");
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "type", "remove-candidates");
                JSONArray jsonArray = new JSONArray();
                for (final IceCandidate candidate : candidates) {
                    jsonArray.put(toJsonCandidate(candidate));
                }
                jsonPut(json, "candidates", jsonArray);
                if (initiator) {
                    // Call initiator sends ice candidates to GAE server.
                    if (roomState != ConnectionState.CONNECTED) {
                        reportError("Sending ICE candidate removals in non connected state.");
                        return;
                    }
                    //sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
                    sendMessageToPartner(json.toString());
                    if (connectionParameters.loopback) {
                        if (events!=null)
                            events.onRemoteIceCandidatesRemoved(candidates);
                    }
                } else {
                    // Call receiver sends ice candidates to websocket server.
                    //wsClient.send(json.toString());
                    sendMessageToPartner(json.toString());
                }
            }
        });
    }

    // --------------------------------------------------------------------
    // WebSocketChannelEvents interface implementation.
    // All events are called by WebSocketChannelClient on a local looper thread
    // (passed to WebSocket client constructor).
    @Override
    public void onWebSocketMessage(final String msg) {
        Log.d(TAG, "ON WEB SOCKET MESSAGE: "+msg);
        if (wsClient.getState() != WebSocketConnectionState.REGISTERED) {
            Log.e(TAG, "Got WebSocket message in non registered state.");
            return;
        }
        try {
            JSONObject json = new JSONObject(msg);
            int responseType = json.getInt("type");
            if (onServerMessage!=null) {
                onServerMessage.onServerMessage(msg, responseType);
            }
            switch (responseType) {
                case BaseResponse.TYPE_CREATE_USER:
                    if (onServerMessage!=null) onServerMessage.notifyMessage("Server TYPE_CREATE_USER");
                    break;
                case BaseResponse.TYPE_LEAVE_ROOM:
                    RoomResponse leaveRoom = gson.fromJson(msg, RoomResponse.class);
                    if(room!=null && leaveRoom.roomId.equals(room.roomId)) {
                        if (onServerMessage!=null) onServerMessage.notifyMessage("Server LEAVE_ROOM ID = "+room.roomId);
                        if(onServerMessage!=null) onServerMessage.onLeaveRoom();
                    } else {
                        if (onServerMessage!=null) onServerMessage.notifyMessage("Server REJECT LEAVE_ROOM ID = "+leaveRoom.roomId);
                    }
                    return;
                case BaseResponse.TYPE_REGISTER_USR:
                    if (onServerMessage!=null) onServerMessage.notifyMessage("REGISTER USER");
                    return;
                case BaseResponse.TYPE_ROOM_CHAT:
                    RoomResponse roomResponse = gson.fromJson(msg, RoomResponse.class);
                    if (roomResponse.isSuccess()) {
                        // check
                        this.room = roomResponse;
                        this.initiator = room.isInitiator;
                        SignalingParameters param = new SignalingParameters();
                        param.room = room;
                        if (this.events!=null) {
                            this.events.onConnectedToRoom(param);
                        }
                        if (onServerMessage!=null) onServerMessage.notifyMessage("Server ROOM_CHAT ID = "+room.roomId);
                    } else {
                        if (onServerMessage!=null) onServerMessage.notifyMessage("Server ROOM_CHAT ERROR = "+roomResponse.code);
                        Log.e(TAG, "Error Code: " + roomResponse.code);
                    }
                    return;
                case BaseResponse.TYPE_SIGNAL_MSG:
                    break;
            }
            String msgText = json.getString("msg");
            String errorText = json.optString("error");
            //
            String toSID = json.getString("toSID");
            String fromSID = json.getString("fromSID");
            String roomID = json.getString("roomID");

            if (msgText.length() > 0) {
                json = new JSONObject(msgText);
                String type = json.optString("type");
                if (type.equals("candidate")) {
                    if (events!=null)
                        events.onRemoteIceCandidate(toJavaCandidate(json));
                } else if (type.equals("remove-candidates")) {
                    JSONArray candidateArray = json.getJSONArray("candidates");
                    IceCandidate[] candidates = new IceCandidate[candidateArray.length()];
                    for (int i = 0; i < candidateArray.length(); ++i) {
                        candidates[i] = toJavaCandidate(candidateArray.getJSONObject(i));
                    }
                    if (events!=null)
                        events.onRemoteIceCandidatesRemoved(candidates);
                } else if (type.equals("answer")) {
                    if (initiator) {
                        SessionDescription sdp = new SessionDescription(
                                SessionDescription.Type.fromCanonicalForm(type), json.getString("sdp"));
                        if (events!=null)
                            events.onRemoteDescription(sdp);
                    } else {
                        reportError("Received answer for call initiator: " + msg);
                    }
                } else if (type.equals("offer")) {
                    if (!initiator) {
                        SessionDescription sdp = new SessionDescription(
                                SessionDescription.Type.fromCanonicalForm(type), json.getString("sdp"));
                        if (events!=null)
                            events.onRemoteDescription(sdp);
                    } else {
                        reportError("Received offer for call receiver: " + msg);
                    }
                } else if (type.equals("bye")) {
                    if (events!=null)
                        events.onChannelClose();
                } else {
                    reportError("Unexpected WebSocket message: " + msg);
                }
            } else {
                if (errorText != null && errorText.length() > 0) {
                    reportError("WebSocket error message: " + errorText);
                } else {
                    reportError("Unexpected WebSocket message: " + msg);
                }
            }
        } catch (JSONException e) {
            reportError("WebSocket message JSON parsing error: " + e.toString());
        }
    }

    @Override
    public void onWebSocketClose() {
        Log.d(TAG, "ON WEB SOCKET CLOSED");
        if (onServerMessage!=null) onServerMessage.notifyMessage("ON WEB SOCKET CLOSED");
        if (events!=null)
            events.onChannelClose();
        roomState = ConnectionState.CLOSED;
        if (onServerMessage!=null) onServerMessage.onSocketClosed();
        wsClient = null;
        events = null;
    }

    @Override
    public void onWebSocketError(String description) {
        Log.d(TAG, "ON WEB SOCKET ERROR "+description);
        reportError("WebSocket error: " + description);
    }

    // --------------------------------------------------------------------
    // Helper functions.
    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);
        if (onServerMessage!=null) onServerMessage.notifyMessage("REPORT ERROR: "+errorMessage);
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.ERROR) {
                    roomState = ConnectionState.ERROR;
                    if (events!=null)
                        events.onChannelError(errorMessage);
                    events = null;
                }
            }
        });
    }

    // Put a |key|->|value| mapping in |json|.
    private static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    // Send SDP or ICE candidate to a room server.
    private void sendPostMessage(
            final MessageType messageType, final String url, @Nullable final String message) {
        String logInfo = url;
        if (message != null) {
            logInfo += ". Message: " + message;
        }
        Log.d(TAG, "SEND POST MESSAGE: " + logInfo);
        /*AsyncHttpURLConnection httpConnection =
                new AsyncHttpURLConnection("POST", url, message, new AsyncHttpEvents() {
                    @Override
                    public void onHttpError(String errorMessage) {
                        reportError("GAE POST error: " + errorMessage);
                    }

                    @Override
                    public void onHttpComplete(String response) {
                        if (messageType == MessageType.MESSAGE) {
                            try {
                                JSONObject roomJson = new JSONObject(response);
                                String result = roomJson.getString("result");
                                if (!result.equals("SUCCESS")) {
                                    reportError("GAE POST error: " + result);
                                }
                            } catch (JSONException e) {
                                reportError("GAE POST JSON error: " + e.toString());
                            }
                        }
                    }
                });
        httpConnection.send();*/
    }

    private void sendMessageToPartner(final String message){
        JSONObject json = new JSONObject();

        jsonPut(json, "cmd", "send");
        jsonPut(json, "msg", message);
        jsonPut(json, "toSID", room.partnerSID);
        jsonPut(json, "roomID", room.roomId);

        wsClient.send(json.toString());
    }

    // Converts a Java candidate to a JSONObject.
    private JSONObject toJsonCandidate(final IceCandidate candidate) {
        JSONObject json = new JSONObject();
        jsonPut(json, "label", candidate.sdpMLineIndex);
        jsonPut(json, "id", candidate.sdpMid);
        jsonPut(json, "candidate", candidate.sdp);
        return json;
    }

    // Converts a JSON candidate to a Java object.
    IceCandidate toJavaCandidate(JSONObject json) throws JSONException {
        return new IceCandidate(
                json.getString("id"), json.getInt("label"), json.getString("candidate"));
    }
}

