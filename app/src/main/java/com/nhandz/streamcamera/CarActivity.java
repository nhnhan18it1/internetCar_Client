package com.nhandz.streamcamera;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.Socket;
import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

//import io.socket.client.IO;
//import io.socket.client.Socket;
//import io.socket.emitter.Emitter;

public class CarActivity extends AppCompatActivity implements SignallingClient.SignalingInterface{

    private Socket socket;
    private SeekBar seekBarX, seekBarY;

    List<PeerConnection.IceServer> peericeServers=new ArrayList<>();
    List<IceServer> iceServers;
    PeerConnectionFactory peerConnectionFactory;
    MediaConstraints audioConstraints;
    MediaConstraints videoConstraints;
    MediaConstraints sdpConstraints;
    VideoSource videoSource;
    VideoTrack localvideoTrack;
    AudioSource audioSource;
    AudioTrack localAudioTrack;

    SurfaceViewRenderer remoteVideoView;
    VideoCapturer videoCapturer;
    EglBase eglBase;
    boolean gotUserMedia;

    PeerConnection localPeer;
    Button btn_create, btn_TryStart, btn_Offer, btn_start;
    TextView txtTem,txthun;

    View mainView;
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_car);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("TIÊU ĐỀ ACTIVITY"); //Thiết lập tiêu đề nếu muốn
        String title = actionBar.getTitle().toString(); //Lấy tiêu đề nếu muốn
        actionBar.hide(); //Ẩn ActionBar nếu muốn
//        try {
//            socket = IO.socket(ReadyActivity.serverNode);//http://40.74.112.141
//            socket.connect();
//        } catch (URISyntaxException e) {
//            e.printStackTrace();
//        }
        SignallingClient.getInstance().context=getApplicationContext();
        SignallingClient.getInstance().init(this);
        socket=SignallingClient.getInstance().socket;
        initView();

        start();
        new TemAndHun().execute();
        socket.on("dht", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data=(JSONObject) args[0];
                Log.e("carActivity", "call: t*, hun "+args[0] );
                try {
                    txtTem.setText(data.getString("temperater"));
                    txthun.setText(data.getString("hunnidity"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });


    }

    private void start(){

        //getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //getIceServers();
//        VideoCapturer ss =createCameraCapturer(new Camera1Enumerator(false));
//        if (ss!=null){
//            videoCapturer=ss;
//            Log.e("TAG", "VideoCapturer: != null" );
//        }
//        else {
//            Log.e("TAG", "VideoCapturer: == null" );
//
//        }

        initVideos();


        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this).setEnableInternalTracer(true).createInitializationOptions());
        //peerConnectionFactory=PeerConnectionFactory.builder().createPeerConnectionFactory();



        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
                eglBase.getEglBaseContext(),  /* enableIntelVp8Encoder */true,  /* enableH264HighProfile */true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .createPeerConnectionFactory();

        //videoCapturer=createVideoCapturer();

        MediaConstraints mediaConstraints=new MediaConstraints();
        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());
        //videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        //videoCapturer.initialize(surfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
        //localvideoTrack=peerConnectionFactory.createVideoTrack("100",videoSource);

        audioSource=peerConnectionFactory.createAudioSource(mediaConstraints);
        localAudioTrack=peerConnectionFactory.createAudioTrack("101",audioSource);

        remoteVideoView.setEnabled(true);


        gotUserMedia=true;
        if (SignallingClient.getInstance().isInitiator){
            onTryToStart();
        }
        //onTryToStart();

    }

    public void call(){
        sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        localPeer.createOffer(new CustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocalDesc"), sessionDescription);
                Log.d("onCreateSuccess", "SignallingClient emit ");
                SignallingClient.getInstance().emitMessage(sessionDescription);
            }
        }, sdpConstraints);
    }

    public void onIceCandidateReceived(PeerConnection localPeer, IceCandidate iceCandidate) {
        //we have received ice candidate. We can set it to the other peer.
        SignallingClient.getInstance().emitIceCandidate(iceCandidate);
    }

    public void initView(){
        seekBarX = findViewById(R.id.seek_bar_x);
        seekBarX.setMax(0);
        seekBarX.setMax(100);
        seekBarX.setProgress(50);
        seekBarY = findViewById(R.id.seek_bar_y);
        seekBarY.setMax(0);
        seekBarY.setMax(100);
        seekBarY.setProgress(50);
        mainView = findViewById(R.id.main_view);
        remoteVideoView=findViewById(R.id.camera_render_car);
        btn_create=findViewById(R.id.btn_car_createR);
        btn_Offer=findViewById(R.id.btn_offer);
        btn_start=findViewById(R.id.btn_setStart);
        btn_TryStart=findViewById(R.id.btn_trytoS);
        txtTem=findViewById(R.id.txtTem);
        txthun=findViewById(R.id.txtHun);
        SetEvent();
    }
    public void SetEvent(){
        btn_create.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SignallingClient.getInstance().emitInitStatement_create("123");
            }
        });

        btn_TryStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SignallingClient.getInstance().emitMessage("get user media");
                SignallingClient.getInstance().isStarted=true;
                onTryToStart();
            }
        });

        btn_Offer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                call();
            }
        });

        seekBarX.setOnTouchListener(new View.OnTouchListener() {
            int value = 90;
            JSONObject data = new JSONObject();
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                try {
                    data.put("type", "servo");
                    data.put("value", value);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                switch(event.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        seekBarX.setProgress(seekBarX.getProgress());
                        int tmp = seekBarX.getProgress()+40;
                        if(tmp%10 == 0 && tmp != value) {
                            value = tmp;
                            try {
                                data.put("value", value);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            socket.emit("control", data);
                        }
                        return false;

                    case MotionEvent.ACTION_UP:
                        seekBarX.setProgress(50);
                        try {
                            data.put("value", 90);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        socket.emit("control", data);
                        break;
                }
                return true;
            }
        });

        seekBarY.setOnTouchListener(new View.OnTouchListener() {
            int value = 0;
            int oldValue = 1;
            JSONObject data = new JSONObject();
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {

                    case MotionEvent.ACTION_MOVE:
                        seekBarY.setProgress(seekBarY.getProgress());
                        int tmp = seekBarY.getProgress();
                        if(tmp%10 == 0 && oldValue != tmp){
                            String type = "";
                            if(tmp < 50) {
                                value = (int) ((50-tmp)*21.2);
                                type = "back";
                            }
                            else if(tmp > 50){
                                value = (int) ((tmp-50)*21.2);
                                type = "ahead";
                            }
                            else {
                                value = 0;
                                type = "stop";
                            }
                            oldValue = tmp;
                            try {
                                data.put("type", type);
                                data.put("value", value);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            socket.emit("control", data);
                        }

                        return false;

                    case MotionEvent.ACTION_UP:
                        seekBarY.setProgress(50);
                        try {
                            data.put("type", "stop");
                            data.put("value", 0);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        socket.emit("control", data);
                        break;
                }
                return true;
            }
        });
    }
    private void initVideos() {
        eglBase = EglBase.create();
        remoteVideoView.init(eglBase.getEglBaseContext(), null);
        remoteVideoView.setZOrderMediaOverlay(true);
    }

    private void hangup() {
        if (localPeer!=null){
            localPeer.close();
        }
        localPeer = null;
        //start.setEnabled(true);
        //call.setEnabled(false);
        //hangup.setEnabled(false);
    }

    private void gotRemoteStream(MediaStream stream) {
        //we have remote video stream. add to the renderer.
        final VideoTrack videoTrack = stream.videoTracks.get(0);
        AudioTrack audioTrack = stream.audioTracks.get(0);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    //remoteRenderer = new VideoRenderer(remoteVideoView);
                    remoteVideoView.setVisibility(View.VISIBLE);
                    videoTrack.addSink(remoteVideoView);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

    }

    private void addStreamToLocalPeer() {
        //creating local mediastream
        MediaStream stream = peerConnectionFactory.createLocalMediaStream("102");
        //stream.addTrack(localAudioTrack);
        //stream.addTrack(localvideoTrack);
        localPeer.addStream(stream);
    }

    private void createPeerConnection() {

        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(peericeServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

        localPeer = peerConnectionFactory.createPeerConnection(rtcConfig, new CustomPeerConnectionObserver("localPeerCreation"){
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                onIceCandidateReceived(localPeer,iceCandidate);
                Log.e("main2", "createPeerConnection: "+iceCandidate );
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                Log.e("main2", "onAddStream: Received Remote stream" );
                super.onAddStream(mediaStream);
                gotRemoteStream(mediaStream);
            }
        });

        addStreamToLocalPeer();
    }

    private void doAnswer() {
        localPeer.createAnswer(new CustomSdpObserver("localCreateAns") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocal"), sessionDescription);
                SignallingClient.getInstance().emitMessage(sessionDescription);
            }
        }, new MediaConstraints());
    }

    @Override
    public void onRemoteHangUp(String msg) {

    }

    @Override
    public void onOfferReceived(JSONObject data) {
        runOnUiThread(() -> {
            if (!SignallingClient.getInstance().isInitiator && !SignallingClient.getInstance().isStarted) {
                onTryToStart();
            }

            try {
                if (localPeer==null){
                    createPeerConnection();
                }
                localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(SessionDescription.Type.OFFER, data.getString("sdp")));
                doAnswer();
                //updateVideoViews(true);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onAnswerReceived(JSONObject data) {
        Log.e("CarAt", "onAnswerReceived: ");
        try {
            if (localPeer==null){
                createPeerConnection();
            }
            localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(SessionDescription.Type.fromCanonicalForm(data.getString("type").toLowerCase()), data.getString("sdp")));
            //updateVideoViews(true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onIceCandidateReceived(JSONObject data) {
        Log.e("CarAt", "onIceCandidateReceived: " );
        try {
            localPeer.addIceCandidate(new IceCandidate(data.getString("id"), data.getInt("label"), data.getString("candidate")));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onTryToStart() {
        Log.e("CarAt", "onTryToStart: localvideoTrack="+localvideoTrack+"--isInitiator="+SignallingClient.getInstance().isInitiator+" isStart="+SignallingClient.getInstance().isStarted+" isChanelReady="+ SignallingClient.getInstance().isChannelReady);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!SignallingClient.getInstance().isStarted
                        &&localvideoTrack!=null
                        &&SignallingClient.getInstance().isChannelReady
                ){
                    createPeerConnection();
                    SignallingClient.getInstance().isStarted=true;
                    if (SignallingClient.getInstance().isInitiator){
                        call();
                    }
                }
            }
        });

    }

    @Override
    public void onCreatedRoom() {
        //Toast.makeText(getApplicationContext(), "You create a romm "+gotUserMedia, Toast.LENGTH_SHORT).show();
        Log.e("CarAt", "onCreatedRoom: "+gotUserMedia);
        if (gotUserMedia){
            SignallingClient.getInstance().emitMessage("get user media");
        }
    }

    @Override
    public void onJoinedRoom() {
        //Toast.makeText(this, "You join a romm "+gotUserMedia, Toast.LENGTH_SHORT).show();
        Log.e("CarAt", "onJoinedRoom: "+gotUserMedia );
        if (gotUserMedia){
            SignallingClient.getInstance().emitMessage("get user media");
        }
    }

    @Override
    public void onNewPeerJoined() {
//        Toast.makeText(this, "new peer join", Toast.LENGTH_SHORT).show();
        Log.e("CarAt", "onNewPeerJoined: " );
    }



    public class TemAndHun extends AsyncTask<Void,Void,Void>{

        @Override
        protected Void doInBackground(Void... voids) {
            int i=0;
            while (true){
                i++;
                if (i==5){
                    JSONObject jsonObject=new JSONObject();
                    try {
                        jsonObject.put("type","dnt");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    socket.emit("control",jsonObject);
                    Log.e("car-", "doInBackground: "+jsonObject );
                    i=0;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            //return null;
        }
    }
}