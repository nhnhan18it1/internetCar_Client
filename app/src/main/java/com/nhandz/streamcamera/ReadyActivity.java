package com.nhandz.streamcamera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

//import com.github.nkzawa.socketio.client.IO;
//import com.github.nkzawa.socketio.client.Socket;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;

public class ReadyActivity extends AppCompatActivity {

    private static final int ALL_PERMISSIONS_CODE = 1 ;
    private Button btnControl;
    private Button btnCar;
    public static String serverNode="http://40.74.112.141";
    public static Socket socket;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ready);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.INTERNET,Manifest.permission.READ_EXTERNAL_STORAGE}, ALL_PERMISSIONS_CODE);
        } else {
            // all permissions already granted
            //start();
        }
        try {
            socket= IO.socket(serverNode);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        socket.connect();
        Log.e("ready", "onCreate: "+socket.connected() );

        btnCar=findViewById(R.id.btn_car);
        btnControl=findViewById(R.id.btn_control);
        btnCar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(ReadyActivity.this,Main2Activity.class);
                startActivity(intent);
            }
        });
        btnControl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(ReadyActivity.this,CarActivity.class);
                startActivity(intent);
            }
        });
        while (socket.connected() == false) {
            //socket.disconnect();
            socket.connect();
            Log.e("ready", "onCreate: " + socket.connected());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == ALL_PERMISSIONS_CODE
                && grantResults.length == 2
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            // all permissions granted
            //start();
        } else {
            //finish();
        }
    }
}