package com.nhandz.streamcamera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

//import io.socket.client.IO;
//import io.socket.client.Socket;
//import io.socket.emitter.Emitter;

/**
 * Webrtc_Step3
 * Created by vivek-3102 on 11/03/17.
 */

class SignallingClient {
    private static SignallingClient instance;
    private String roomName = "123";
    public Socket socket;
    private String ServerNode=ReadyActivity.serverNode;
    boolean isChannelReady = true;
    boolean isInitiator = false;
    boolean isStarted = false;
    Context context;
    private SignalingInterface callback;

    //This piece of code should not go into production!!
    //This will help in cases where the node server is running in non-https server and you want to ignore the warnings
    @SuppressLint("TrustAllX509TrustManager")
    private final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[]{};
        }

        public void checkClientTrusted(X509Certificate[] chain,
                                       String authType) {
        }

        public void checkServerTrusted(X509Certificate[] chain,
                                       String authType) {
        }
    }};

    public static SignallingClient getInstance() {
        if (instance == null) {
            instance = new SignallingClient();
        }
        if (instance.roomName == null) {
            //set the room name here
            instance.roomName = "123";
        }
        return instance;
    }

    public boolean checkconnect(){
        if (socket.hasListeners("connection")){
            //Toast.makeText(context, "isConnected", Toast.LENGTH_SHORT).show();
            return true;
        }
        else {
            //Toast.makeText(context, "not Connected", Toast.LENGTH_SHORT).show();
            socket.connect();
            return  false;
            //checkconnect();
        }
    }

    public void init(SignalingInterface signalingInterface) {
        this.callback = signalingInterface;
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(null, trustAllCerts, null);
            IO.setDefaultHostnameVerifier((hostname, session) -> true);
            IO.setDefaultSSLContext(sslcontext);
            //set the socket.io url here

            socket = IO.socket(ServerNode);
            socket.connect();
            new Reconnect().execute();
            Log.e("SignallingClient", "init() called "+socket.connected());

            if (!roomName.isEmpty()) {
                //emitInitStatement(roomName);
            }

            //room created event.
            socket.on("created", args -> {
                Log.e("SignallingClient-created", "created call() called with: args = [" + Arrays.toString(args) + "]");
                isInitiator = true;
                callback.onCreatedRoom();
            });

            //room is full event
            socket.on("full", args -> Log.d("SignallingClient", "full call() called with: args = [" + Arrays.toString(args) + "]"));

            //peer joined event
            socket.on("join", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    Log.e("SignallingClient-join", "join call() called with: args = [" + Arrays.toString(args) + "]");
                    isChannelReady = true;
                    callback.onNewPeerJoined();
                }
            });

            //when you joined a chat room successfully
            socket.on("joined", args -> {
                Log.e("SignallingClient-joined", "joined call() called with: args = [" + Arrays.toString(args) + "]");
                isChannelReady = true;
                callback.onJoinedRoom();
            });

            //log event
            socket.on("log", args -> Log.d("SignallingClient-log", "log call() called with: args = [" + Arrays.toString(args) + "]"));

            //bye event
            //socket.on("bye", args -> callback.onRemoteHangUp((String) args[0]));

            //messages - SDP and ICE candidates are transferred through this
            socket.on("message", args -> {
                Log.e("SignallingClient", "message call() called with: args = [" + Arrays.toString(args) + "]");
                if (args[0] instanceof String) {
                    Log.e("SignallingClient", "String received :: " + args[0]);
                    String data = (String) args[0];
                    if (data.equalsIgnoreCase("got user media")) {
                        callback.onTryToStart();
                    }
                    if (data.equalsIgnoreCase("bye")) {
                        callback.onRemoteHangUp(data);
                    }
                } else if (args[0] instanceof JSONObject) {
                    try {

                        JSONObject data = (JSONObject) args[0];
                        Log.e("SignallingClient", "Json Received :: " + data.toString());
                        String type = data.getString("type");
                        if (type.equalsIgnoreCase("offer")) {
                            callback.onOfferReceived(data);
                        } else if (type.equalsIgnoreCase("answer") && isStarted) {
                            callback.onAnswerReceived(data);
                        } else if (type.equalsIgnoreCase("candidate") && isStarted) {
                            callback.onIceCandidateReceived(data);
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (URISyntaxException | NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
    }

    public void emitInitStatement_create(String message) {
        Log.e("SignallingClient", "emitInitStatement() called with: event = [" + "create" + "], message = [" + message + "]");
        socket.emit("create", message);
    }

    public void emitInitStatement_join(String message) {
        Log.e("SignallingClient", "emitInitStatement() called with: event = [" + "join" + "], message = [" + message + "]");
        socket.emit("join", message);
    }

    public void emitMessage(String message) {
        Log.e("SignallingClient", "emitMessage() called with: message = [" + message + "]");
        socket.emit("message", message);
    }

    public void emitMessage(SessionDescription message) {
        try {
            Log.d("SignallingClient", "emitMessage() called with: message = [" + message + "]");
            JSONObject obj = new JSONObject();
            obj.put("type", message.type.canonicalForm());
            obj.put("sdp", message.description);
            Log.d("emitMessage", obj.toString());
            socket.emit("message", obj);
            Log.e("vivek1794", obj.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void emitIceCandidate(IceCandidate iceCandidate) {
        try {
            JSONObject object = new JSONObject();
            object.put("type", "candidate");
            object.put("label", iceCandidate.sdpMLineIndex);
            object.put("id", iceCandidate.sdpMid);
            object.put("candidate", iceCandidate.sdp);
            socket.emit("message", object);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        socket.emit("bye", roomName);
        socket.disconnect();
        socket.close();
    }

    interface SignalingInterface {
        void onRemoteHangUp(String msg);

        void onOfferReceived(JSONObject data);

        void onAnswerReceived(JSONObject data);

        void onIceCandidateReceived(JSONObject data);

        void onTryToStart();

        void onCreatedRoom();

        void onJoinedRoom();

        void onNewPeerJoined();
    }

    public class Reconnect extends AsyncTask<Void,Void,Boolean>{

        @Override
        protected Boolean doInBackground(Void... voids) {

            return checkconnect();
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            if (aBoolean){Log.e("node","isconnected");}
            else {
                try {
                    if (socket==null){
                        socket=IO.socket(ServerNode);
                        socket.connect();
                    }


                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
                Log.e("node","not connected");
                new Reconnect().execute();

            }

        }
    }
}