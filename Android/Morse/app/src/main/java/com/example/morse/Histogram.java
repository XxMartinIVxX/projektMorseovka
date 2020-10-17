package com.example.morse;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.Arrays;

public class Histogram extends Activity {

    TextureView myTexture;

    private String cameraId;
    CameraDevice cameraDevice;
    CameraCaptureSession cameraCaptureSession;
    CaptureRequest captureRequest;
    CaptureRequest.Builder captureRequestBuilder;

    private Size imageDimension;
    Handler mBackgroundHandler;
    HandlerThread mByckgroundTheard;





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.histogram);

        myTexture = (TextureView)findViewById(R.id.cameraView);
        myTexture.setSurfaceTextureListener(textureListener);

        }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == 101)
        {
            if(grantResults[0] == PackageManager.PERMISSION_DENIED)
            {
                Toast.makeText(getApplicationContext(), "Sorry", Toast.LENGTH_LONG).show();
            }
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                cameraDevice = camera;
                try {
                    createCameraPreview();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                cameraDevice.close();
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                cameraDevice.close();
                cameraDevice = null;
            }
        };

    private void createCameraPreview() throws CameraAccessException {
       SurfaceTexture texture = myTexture.getSurfaceTexture();
       texture.setDefaultBufferSize(myTexture.getWidth(), myTexture.getHeight());//imageDimension.getWidth(), imageDimension.getHeight());
       Surface surface = new Surface(texture);

       captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
       captureRequestBuilder.addTarget(surface);

       cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
           @Override
           public void onConfigured(@NonNull CameraCaptureSession session) {
            if(cameraDevice == null)
            {
                return;
            }
            cameraCaptureSession = session;
               try {
                   updatePreview();
               } catch (CameraAccessException e) {
                   e.printStackTrace();
               }
           }

           @Override
           public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                Toast.makeText(getApplicationContext(), "Configuration changed",Toast.LENGTH_LONG).show();
           }
       }, null);
    }

    private void updatePreview() throws CameraAccessException {
        if(cameraDevice == null)
        {
            return;
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(),null,mBackgroundHandler);
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                try {
                    imageSize(myTexture.getWidth(), myTexture.getHeight());
                    openCamera();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

            }
        };

    private void imageSize(int width, int height) {
        if(myTexture == null)
        {
            return;
        }
        Matrix matrix = new Matrix();
        RectF textureRectF = new RectF(0,0,width,height);
        RectF previewRectF = new RectF(0, 0, myTexture.getHeight(), myTexture.getWidth());
        float centerX = textureRectF.centerX();
        float centerY = textureRectF.centerY();


            previewRectF.offset(centerX - previewRectF.centerX(),
                    centerY - previewRectF.centerY());
            matrix.setRectToRect(textureRectF, previewRectF, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float) width / myTexture.getWidth(),
                    (float) height / myTexture.getHeight());
            matrix.postScale(scale, scale, centerX, centerY);



        myTexture.setTransform(matrix);
    }

    private void openCamera() throws CameraAccessException {
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        cameraId = manager.getCameraIdList()[0];
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];

        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!= PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(Histogram.this, new String[]{Manifest.permission.CAMERA},101);
            return;
        }
        manager.openCamera(cameraId,stateCallback, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        startBackgroundTheard();
        if(myTexture.isAvailable())
        {
            try {
                imageSize(myTexture.getWidth(), myTexture.getHeight());
                openCamera();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        else
        {
            myTexture.setSurfaceTextureListener(textureListener);
        }
    }

    private void startBackgroundTheard() {
        mByckgroundTheard = new HandlerThread("Camera Background");
        mByckgroundTheard.start();
        mBackgroundHandler = new Handler(mByckgroundTheard.getLooper());
    }

    @Override
    protected void onPause() {
        try {
            stopBackgroudThread();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        super.onPause();
    }

    private void stopBackgroudThread() throws InterruptedException {
        mByckgroundTheard.quitSafely();

        mByckgroundTheard.join();
        mByckgroundTheard = null;
        mBackgroundHandler = null;
    }
}

