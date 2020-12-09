package com.twiliorn.library;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.twilio.video.Camera2Capturer;

import tvi.webrtc.CapturerObserver;
import tvi.webrtc.SurfaceTextureHelper;
import tvi.webrtc.VideoFrame;

public class CroppedCameraCapturer extends Camera2Capturer {

    class Cropper implements CapturerObserver {

        private CapturerObserver capturerObserver;

        Cropper(CapturerObserver capturerObserver) {
            this.capturerObserver = capturerObserver;
        }

        @Override
        public void onCapturerStarted(boolean b) {
            capturerObserver.onCapturerStarted(b);
        }

        @Override
        public void onCapturerStopped() {
            capturerObserver.onCapturerStopped();
        }

        @Override
        public void onFrameCaptured(VideoFrame videoFrame) {
//             capturerObserver.onFrameCaptured(videoFrame);

            Log.i("YOYO", videoFrame.getBuffer().getWidth() + " " + videoFrame.getBuffer().getHeight());

//            if(true) return;

            if(videoFrame != null) {
                int cropWidth = 50;
                int cropHeight = 50;
                int scaledWidth = 50;
                int scaledHeight = 50;
                int cropX = (videoFrame.getBuffer().getWidth() / 2) - (cropWidth / 2);
                int cropY = (videoFrame.getBuffer().getHeight() / 2) - (cropHeight / 2);
//                VideoFrame.Buffer croppedBuffer = videoFrame.getBuffer().cropAndScale(cropX, cropY, cropWidth, cropHeight, scaledWidth, scaledHeight);

//                Log.i("YOYO", "cropX " + cropX + " cropY " + cropY + " CroppedBuffer Width & Height " + croppedBuffer.getWidth() + " " + croppedBuffer.getHeight());
                capturerObserver.onFrameCaptured(new VideoFrame(videoFrame.getBuffer(), videoFrame.getRotation(), videoFrame.getTimestampNs()));
            }
        }
    }

    public CroppedCameraCapturer(@NonNull Context context, @NonNull String cameraId) {
        super(context, cameraId);
    }

    public CroppedCameraCapturer(@NonNull Context context, @NonNull String cameraId, @Nullable Listener listener) {
        super(context, cameraId, listener);
    }

    @Override
    public void initialize(@NonNull SurfaceTextureHelper surfaceTextureHelper, @NonNull Context context, @NonNull CapturerObserver capturerObserver) {
        super.initialize(surfaceTextureHelper, context, new Cropper(capturerObserver));
    }
}
