package com.nhandz.streamcamera;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.VideoView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.squareup.picasso.Picasso;

import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.api.SequenceEncoder;
import org.jcodec.api.android.AndroidSequenceEncoder;
import org.jcodec.common.Codec;
import org.jcodec.common.Format;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rational;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Base64.Encoder;

//import io.socket.client.IO;
//import io.socket.client.Socket;

import static org.jcodec.common.io.NIOUtils.writableFileChannel;

public class MainActivity extends AppCompatActivity {

    public static final int REQUEST_CODE_CAMERA=101;
    private Button btnS;
    private Camera mCamera;
    private CameraPreview mPreview;
    private FrameLayout frCamera;
    private MediaRecorder mediaRecorder;
    private ImageView imageView;
    private VideoView videoView;
    public static String Nodeserver="http://192.168.43.75:3000/";//"http://192.168.1.2:3000";
    public static Socket mSocket;
    int timeRequest=0;
    private int frame=0;
    private ArrayList<String> ArrStrings;
    File file;

    private ArrayList<Bitmap> bitmapIMG;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE},
                1);
        btnS=findViewById(R.id.btnStart);
        imageView=findViewById(R.id.imageView);
        videoView=findViewById(R.id.videoView);
        bitmapIMG=new ArrayList<>();
        try {
            mSocket= IO.socket(Nodeserver);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
       mSocket.connect();
        file=new File(Environment.getExternalStorageDirectory()+File.separator+"output2.mp4");
        if (!file.exists()){
            try {
                if (file.createNewFile()) Log.e("createFile", "onCreate: "+file.getAbsolutePath() );
            } catch (IOException e) {
                e.printStackTrace();
            }
            ;
        }
        btnS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //RunCameraIntent();
               Camera checkCam=  getCameraInstance();
                if (checkCam==null){
                    Log.e("cam", "onClick: null" );
                }
                else {
                    Log.e("cam", "onClick: "+checkCam );
                }
            }
        });
        mCamera=getCameraInstance();
        mPreview=new CameraPreview(this,mCamera,previewCallback);
        frCamera=findViewById(R.id.cameraFrame);
        frCamera.addView(mPreview);


    }

    public void SendData(byte[] s){
        if (frame<=20){

            //ArrStrings.add(imageString);
            frame++;
        }
        else if (frame>20&&frame<30){
            frame++;
        }
        else if (frame==30){
            frame=0;

        }
        String imageString = android.util.Base64.encodeToString(s, android.util.Base64.DEFAULT);
        CheckConnect();
        mSocket.emit("stream",imageString);
        //new RunEmit(imageString).execute();
        //Log.e("TAG", "onPreviewFrame: " + timeRequest);

    }

    public void CheckConnect(){
        if (mSocket.connected()){
            return;
        }
        else {
            Log.e("TAG", "nodeserver disconnect");
            mSocket.connect();
            if (mSocket.connected()){return;}
            else CheckConnect();
        }
    }

    Camera.PictureCallback pictureCallback=new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

        }
    };



    private final Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {

            Camera.Parameters parameters = camera.getParameters();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            YuvImage yuvImage = new YuvImage(data, parameters.getPreviewFormat(), parameters.getPreviewSize().width, parameters.getPreviewSize().height, null);
            yuvImage.compressToJpeg(new Rect(0, 0, parameters.getPreviewSize().width, parameters.getPreviewSize().height), 20, out);
            byte[] imageBytes = out.toByteArray();
            SendData(imageBytes);

            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

//            if (bitmapIMG.size()<20){
//                bitmapIMG.add(bitmap);
//            }
//            else if (bitmapIMG.size()==20){
//                createVideo(bitmapIMG);
//            }

            BitmapDrawable bitmapDrawable=new BitmapDrawable(bitmap);
            imageView.setBackground(bitmapDrawable);
            imageView.setImageDrawable(bitmapDrawable);
            //videoView.setBackground(bitmapDrawable);

            try {
                out.flush();
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

    };

    public void createVideo(ArrayList<Bitmap> bitmaps){
        SeekableByteChannel outS=null;
        try {

            outS= writableFileChannel(file.getAbsolutePath());
            AndroidSequenceEncoder encoder=new AndroidSequenceEncoder(outS, Rational.R(20, 1));
            for (int i=0;i< bitmaps.size();i++){
                encoder.encodeImage(bitmaps.get(i));
            }
            encoder.finish();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            NIOUtils.closeQuietly(outS);
            Log.e("success", "createVideo: "+file.canExecute() );
            videoView.setVideoURI(Uri.parse(file.getAbsolutePath()));
        }
    }

    public Camera getCameraInstance(){
        Camera c = null;
        try {

            c = Camera.open(); // attempt to get a Camera instance
            Camera.Parameters params = c.getParameters();
            params.setJpegQuality(30);
            c.setParameters(params);


        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
            e.printStackTrace();
        }
        return c; // returns null if camera is unavailable
    }

    public void RunCameraIntent(){
        Intent intent=new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT,CameraFileUltility.getOutputMediaFileUri(CameraFileUltility.MEDIA_TYPE_VIDEO).getPath());
        startActivityForResult(intent,REQUEST_CODE_CAMERA);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

    }

    @Override

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 101) {

            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();

            } else {

                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();

            }

        }
}

    public class RunEmit extends AsyncTask<Void,Void,Void>{
        String imageString;

        public RunEmit(String imageString) {
            this.imageString = imageString;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            mSocket.emit("stream",imageString);
            timeRequest++;
            return null;
        }
    }
}
