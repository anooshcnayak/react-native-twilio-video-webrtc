/**
 * Component to orchestrate the Twilio Video connection and the various video
 * views.
 * <p>
 * Authors:
 * Ralph Pina <ralph.pina@gmail.com>
 * Jonathan Chang <slycoder@gmail.com>
 */
package com.twiliorn.library;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.NonNull;
import androidx.annotation.StringDef;
import android.util.Log;
import android.view.View;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;


import com.twilio.audioswitch.AudioDevice;
import com.twilio.audioswitch.AudioSwitch;
import com.twilio.video.AudioOptions;
import com.twilio.video.AudioTrackPublication;
import com.twilio.video.BaseTrackStats;
import com.twilio.video.ConnectOptions;
import com.twilio.video.IceCandidatePairStats;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.LocalAudioTrackPublication;
import com.twilio.video.LocalAudioTrackStats;
import com.twilio.video.LocalDataTrackPublication;
import com.twilio.video.LocalParticipant;
import com.twilio.video.LocalTrackStats;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.LocalVideoTrackPublication;
import com.twilio.video.LocalVideoTrackStats;
import com.twilio.video.NetworkQualityConfiguration;
import com.twilio.video.NetworkQualityLevel;
import com.twilio.video.NetworkQualityVerbosity;
import com.twilio.video.Participant;
import com.twilio.video.RemoteAudioTrack;
import com.twilio.video.RemoteAudioTrackPublication;
import com.twilio.video.RemoteAudioTrackStats;
import com.twilio.video.LocalDataTrack;
import com.twilio.video.RemoteDataTrack;
import com.twilio.video.RemoteDataTrackPublication;
import com.twilio.video.RemoteParticipant;
import com.twilio.video.RemoteTrackStats;
import com.twilio.video.RemoteVideoTrack;
import com.twilio.video.RemoteVideoTrackPublication;
import com.twilio.video.RemoteVideoTrackStats;
import com.twilio.video.Room;
import com.twilio.video.StatsListener;
import com.twilio.video.StatsReport;
import com.twilio.video.TrackPublication;
import com.twilio.video.TwilioException;
import com.twilio.video.Video;
import com.twilio.video.VideoDimensions;
import com.twilio.video.EncodingParameters;
import com.twilio.video.VideoBandwidthProfileOptions;
import com.twilio.video.BandwidthProfileOptions;
import com.twilio.video.BandwidthProfileMode;
import com.twilio.video.VideoFormat;
import com.twilio.video.VideoTrackPublication;

import kotlin.Unit;
import tvi.webrtc.voiceengine.WebRtcAudioManager;
import tvi.webrtc.voiceengine.WebRtcAudioUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_AUDIO_CHANGED;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_CAMERA_SWITCHED;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_CONNECTED;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_CONNECT_FAILURE;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_DISCONNECTED;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_DATATRACK_MESSAGE_RECEIVED;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_NETWORK_QUALITY_LEVELS_CHANGED;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_PARTICIPANT_ADDED_DATA_TRACK;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_PARTICIPANT_ADDED_AUDIO_TRACK;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_PARTICIPANT_ADDED_VIDEO_TRACK;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_PARTICIPANT_CONNECTED;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_PARTICIPANT_DISABLED_AUDIO_TRACK;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_PARTICIPANT_DISABLED_VIDEO_TRACK;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_PARTICIPANT_DISCONNECTED;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_PARTICIPANT_ENABLED_AUDIO_TRACK;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_PARTICIPANT_ENABLED_VIDEO_TRACK;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_PARTICIPANT_REMOVED_DATA_TRACK;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_PARTICIPANT_REMOVED_AUDIO_TRACK;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_PARTICIPANT_REMOVED_VIDEO_TRACK;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_STATS_RECEIVED;
import static com.twiliorn.library.CustomTwilioVideoView.Events.ON_VIDEO_CHANGED;

public class CustomTwilioVideoView extends View implements LifecycleEventListener {
    private static final String TAG = "CustomTwilioVideoView";
    private static final String DATA_TRACK_MESSAGE_THREAD_NAME = "DataTrackMessages";
    private boolean enableRemoteAudio = false;
    private boolean enableNetworkQualityReporting = false;
    private boolean isVideoEnabled = false;
    private int maxVideoBitrate = 100;
    private int maxAudioBitrate = 16;
    private int maxFps = 30;

    private static boolean soundEffectsInitialized = false;

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({Events.ON_CAMERA_SWITCHED,
            Events.ON_VIDEO_CHANGED,
            Events.ON_AUDIO_CHANGED,
            Events.ON_CONNECTED,
            Events.ON_CONNECT_FAILURE,
            Events.ON_DISCONNECTED,
            Events.ON_PARTICIPANT_CONNECTED,
            Events.ON_PARTICIPANT_DISCONNECTED,
            Events.ON_PARTICIPANT_ADDED_VIDEO_TRACK,
            Events.ON_DATATRACK_MESSAGE_RECEIVED,
            Events.ON_PARTICIPANT_ADDED_DATA_TRACK,
            Events.ON_PARTICIPANT_REMOVED_DATA_TRACK,
            Events.ON_PARTICIPANT_REMOVED_VIDEO_TRACK,
            Events.ON_PARTICIPANT_ADDED_AUDIO_TRACK,
            Events.ON_PARTICIPANT_REMOVED_AUDIO_TRACK,
            Events.ON_PARTICIPANT_ENABLED_VIDEO_TRACK,
            Events.ON_PARTICIPANT_DISABLED_VIDEO_TRACK,
            Events.ON_PARTICIPANT_ENABLED_AUDIO_TRACK,
            Events.ON_PARTICIPANT_DISABLED_AUDIO_TRACK,
            Events.ON_STATS_RECEIVED,
            Events.ON_NETWORK_QUALITY_LEVELS_CHANGED})
    public @interface Events {
        String ON_CAMERA_SWITCHED = "onCameraSwitched";
        String ON_VIDEO_CHANGED = "onVideoChanged";
        String ON_AUDIO_CHANGED = "onAudioChanged";
        String ON_CONNECTED = "onRoomDidConnect";
        String ON_CONNECT_FAILURE = "onRoomDidFailToConnect";
        String ON_DISCONNECTED = "onRoomDidDisconnect";
        String ON_PARTICIPANT_CONNECTED = "onRoomParticipantDidConnect";
        String ON_PARTICIPANT_DISCONNECTED = "onRoomParticipantDidDisconnect";
        String ON_DATATRACK_MESSAGE_RECEIVED = "onDataTrackMessageReceived";
        String ON_PARTICIPANT_ADDED_DATA_TRACK = "onParticipantAddedDataTrack";
        String ON_PARTICIPANT_REMOVED_DATA_TRACK = "onParticipantRemovedDataTrack";
        String ON_PARTICIPANT_ADDED_VIDEO_TRACK = "onParticipantAddedVideoTrack";
        String ON_PARTICIPANT_REMOVED_VIDEO_TRACK = "onParticipantRemovedVideoTrack";
        String ON_PARTICIPANT_ADDED_AUDIO_TRACK = "onParticipantAddedAudioTrack";
        String ON_PARTICIPANT_REMOVED_AUDIO_TRACK = "onParticipantRemovedAudioTrack";
        String ON_PARTICIPANT_ENABLED_VIDEO_TRACK = "onParticipantEnabledVideoTrack";
        String ON_PARTICIPANT_DISABLED_VIDEO_TRACK = "onParticipantDisabledVideoTrack";
        String ON_PARTICIPANT_ENABLED_AUDIO_TRACK = "onParticipantEnabledAudioTrack";
        String ON_PARTICIPANT_DISABLED_AUDIO_TRACK = "onParticipantDisabledAudioTrack";
        String ON_STATS_RECEIVED = "onStatsReceived";
        String ON_NETWORK_QUALITY_LEVELS_CHANGED = "onNetworkQualityLevelsChanged";
    }

    private final ThemedReactContext themedReactContext;
    private final RCTEventEmitter eventEmitter;

    private Handler handler = new Handler();

    /*
     * A Room represents communication between the client and one or more participants.
     */
    private Room room;
    private String roomName = null;
    private String accessToken = null;
    private LocalParticipant localParticipant;
    private AudioSwitch audioSwitch;

    private static Map<String, Room> allRooms = new HashMap<>();
    private static Map<String, PatchedVideoView> allThumbnailViews = new HashMap<>();
    private static Map<String, CustomTwilioVideoView> allViews = new HashMap<>();

    /*
     * A VideoView receives frames from a local or remote video track and renders them
     * to an associated view.
     */
    private PatchedVideoView thumbnailVideoView;
    private LocalVideoTrack localVideoTrack;

    private CameraCapturerCompat cameraCapturerCompat;

    private LocalAudioTrack localAudioTrack;
    private boolean disconnectedFromOnDestroy;

    private boolean dataTrackEnabled = false;

    // Dedicated thread and handler for messages received from a RemoteDataTrack
    private final HandlerThread dataTrackMessageThread =
            new HandlerThread(DATA_TRACK_MESSAGE_THREAD_NAME);
    private Handler dataTrackMessageThreadHandler;

    private LocalDataTrack localDataTrack;

    private Map<String, Double> videoDimensions;

    // Map used to map remote data tracks to remote participants
    private final Map<RemoteDataTrack, RemoteParticipant> dataTrackRemoteParticipantMap =
            new HashMap<>();

    private static Map<String, PatchedVideoView> trackVideoSinkMap = new HashMap<>();

    public CustomTwilioVideoView(ThemedReactContext context) {
        super(context);

        this.themedReactContext = context;
        this.eventEmitter = themedReactContext.getJSModule(RCTEventEmitter.class);

        // add lifecycle for onResume and on onPause
        themedReactContext.addLifecycleEventListener(this);

        /*
         * Enable changing the volume using the up/down keys during a conversation
         */
        if (themedReactContext.getCurrentActivity() != null) {
            themedReactContext.getCurrentActivity().setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        }

        // Create the local data track
        // localDataTrack = LocalDataTrack.create(this);
        if(dataTrackEnabled)
            localDataTrack = LocalDataTrack.create(getContext());


        // Init Sound Effects

        if(!soundEffectsInitialized) {
            Set<String> HARDWARE_AEC_BLACKLIST = new HashSet<String>() {{
                add("Pixel");
                add("Pixel XL");
                add("Moto G5");
                add("Moto G (5S) Plus");
                add("Moto G4");
                add("TA-1053");
                add("Mi A1");
                add("Mi A2");
                add("E5823"); // Sony z5 compact
                add("Redmi Note 5");
                add("FP2"); // Fairphone FP2
                add("MI 5");
            }};

            Set<String> OPEN_SL_ES_WHITELIST = new HashSet<String>() {{
                add("Pixel");
                add("Pixel XL");
            }};

            if (HARDWARE_AEC_BLACKLIST.contains(Build.MODEL)) {
                WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
            }

            if (!OPEN_SL_ES_WHITELIST.contains(Build.MODEL)) {
                WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true);
            }

            soundEffectsInitialized = true;
        }


        // Audioswitch preference

        List<Class<? extends AudioDevice>> preferredDevices = new ArrayList<>();
        preferredDevices.add(AudioDevice.BluetoothHeadset.class);
        preferredDevices.add(AudioDevice.WiredHeadset.class);
        preferredDevices.add(AudioDevice.Speakerphone.class);

//        audioSwitch = new AudioSwitch(getContext(), true, focusChange -> {}, preferredDevices);
        audioSwitch = new AudioSwitch(getContext(), false, focusChange ->  {
            Log.i(TAG, "Audioswitch:: onAudioFocusChange: focuschange: " + focusChange);

            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:

                    if(audioSwitch != null) {
                        Log.i(TAG, "Audioswitch:: onAudioFocusChange: stopping AS: ");
                        audioSwitch.stop();

                        Log.i(TAG, "Audioswitch:: onAudioFocusChange: starting AS ");
                        startAudioswitch();
                        try {
                            Log.i(TAG, "Audioswitch:: onAudioFocusChange: activating AS ");
                            audioSwitch.activate();
                        } catch (Exception e) {
                            Log.e(TAG, "Audioswitch:: onAudioFocusChange: audioswitch activate exception: ", e);
                        }
                    }

                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:

//                    audioSwitch.deactivate();
                    break;

                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    // ... pausing or ducking depends on your app
//                    audioSwitch.deactivate();
                    break;
            }
        }, preferredDevices);

        // Start the thread where data messages are received

        if(dataTrackEnabled) {
            dataTrackMessageThread.start();
            dataTrackMessageThreadHandler = new Handler(dataTrackMessageThread.getLooper());
        }
    }

    // ===== SETUP =================================================================================

    private VideoFormat buildVideoFormat() {
        VideoFormat videoFormat = new VideoFormat(new VideoDimensions(50, 50), this.maxFps);
        return videoFormat;
    }

    private boolean createLocalVideo(boolean enableVideo) {
        isVideoEnabled = enableVideo;

        // Share your camera
        cameraCapturerCompat = new CameraCapturerCompat(getContext(), CameraCapturerCompat.Source.FRONT_CAMERA);

        if (cameraCapturerCompat == null){
            cameraCapturerCompat = new CameraCapturerCompat(getContext(), CameraCapturerCompat.Source.BACK_CAMERA);
        }
        if (cameraCapturerCompat == null){
            WritableMap event = new WritableNativeMap();
            event.putString("error", "No camera is supported on this device");
//            pushEvent(CustomTwilioVideoView.this, ON_CONNECT_FAILURE, event);
            return true;
        }

        localVideoTrack = LocalVideoTrack.create(getContext(), enableVideo, cameraCapturerCompat, buildVideoFormat());
        if(thumbnailVideoView == null) {
            thumbnailVideoView = allThumbnailViews.get(roomName);
        }

        if (thumbnailVideoView != null && localVideoTrack != null) {
            localVideoTrack.addSink(thumbnailVideoView);
        }
        setThumbnailMirror();

        return true;
    }

    // ===== LIFECYCLE EVENTS ======================================================================

    private void startAudioswitch() {
        if(audioSwitch != null) {
            audioSwitch.start((audioDevices, audioDevice) -> {
                Log.i(TAG, "Audioswitch:: start: " + ((audioDevice != null) ? audioDevice.getName() : ""));
                return Unit.INSTANCE;
            });
        }
    }


    @Override
    public void onHostResume() {

        Log.i("CustomTwilioVideoView", "onHostResume " + roomName);
        /*
         * In case it wasn't set.
         */
        if (themedReactContext.getCurrentActivity() != null) {
            /*
             * If the local video track was released when the app was put in the background, recreate.
             */
            if (cameraCapturerCompat != null && localVideoTrack == null) {
//                 localVideoTrack = LocalVideoTrack.create(getContext(), isVideoEnabled, cameraCapturer, buildVideoConstraints());
                localVideoTrack = LocalVideoTrack.create(getContext(), isVideoEnabled, cameraCapturerCompat, buildVideoFormat());
            }

            if (localVideoTrack != null) {
                if (thumbnailVideoView != null) {
                    localVideoTrack.addSink(thumbnailVideoView);
                }

                /*
                 * If connected to a Room then share the local video track.
                 */
                if (localParticipant != null) {
                    localParticipant.publishTrack(localVideoTrack);
                }
            }

            themedReactContext.getCurrentActivity().setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        }
    }

    @Override
    public void onHostPause() {

        Log.i("CustomTwilioVideoView", "onHostPause " + roomName);
        /*
         * Release the local video track before going in the background. This ensures that the
         * camera can be used by other applications while this app is in the background.
         */
        if (localVideoTrack != null) {
            /*
             * If this local video track is being shared in a Room, remove from local
             * participant before releasing the video track. Participants will be notified that
             * the track has been removed.
             */
            if (localParticipant != null) {
                localParticipant.unpublishTrack(localVideoTrack);
            }

            localVideoTrack.release();
            localVideoTrack = null;
        }
    }

    @Override
    public void onHostDestroy() {

        Log.i("CustomTwilioVideoView", "onHostDestroy " + roomName);
        /*
         * Always disconnect from the room before leaving the Activity to
         * ensure any memory allocated to the Room resource is freed.
         */
        this.disconnect();

        // Quit the data track message thread
        if(dataTrackEnabled) {
            dataTrackMessageThread.quit();
        }


        if(audioSwitch != null) {
            audioSwitch.stop();
        }
    }

    public void releaseResource() {
        themedReactContext.removeLifecycleEventListener(this);
        room = null;
        localVideoTrack = null;
        thumbnailVideoView = null;
        cameraCapturerCompat = null;
        trackVideoSinkMap.clear();
    }

    // ====== CONNECTING ===========================================================================

    public void connectToRoomWrapper(
            String roomName, String accessToken, boolean enableAudio, boolean enableVideo,
            boolean enableRemoteAudio, boolean enableNetworkQualityReporting,
            int maxVideoBitrate, int maxAudioBitrate, int maxFps) {
        Log.i("CustomTwilioVideoView", "Connecting to Room " + roomName);

        if(roomName == null) return;

        allViews.put(roomName, this);

        this.roomName = roomName;
        this.accessToken = accessToken;
        this.enableRemoteAudio = enableRemoteAudio;
        this.enableNetworkQualityReporting = enableNetworkQualityReporting;
        this.maxVideoBitrate = maxVideoBitrate;
        this.maxAudioBitrate = maxAudioBitrate;
        this.maxFps = maxFps;

        // Share your microphone
        localAudioTrack = LocalAudioTrack.create(getContext(), enableAudio, buildAudioOptions());

        if (cameraCapturerCompat == null) {
            boolean createVideoStatus = createLocalVideo(enableVideo);
            if (!createVideoStatus) {
                // No need to connect to room if video creation failed
                return;
            }
        }

        connectToRoom(enableRemoteAudio);
    }

    private AudioOptions buildAudioOptions() {
        AudioOptions.Builder builder = new AudioOptions.Builder();
        builder.echoCancellation(true).noiseSuppression(true);
        return builder.build();
    }

    public void connectToRoom(boolean enableAudio) {
        /*
         * Create a VideoClient allowing you to connect to a Room
         */

        // Start AudioSwitch
        startAudioswitch();

        setAudioFocus(enableAudio);
        ConnectOptions.Builder connectOptionsBuilder = new ConnectOptions.Builder(this.accessToken).region("in1");

        if (this.roomName != null) {
            connectOptionsBuilder.roomName(this.roomName);
        }

        if (localAudioTrack != null) {
            connectOptionsBuilder.audioTracks(Collections.singletonList(localAudioTrack));
        }

        if (localVideoTrack != null) {
            connectOptionsBuilder.videoTracks(Collections.singletonList(localVideoTrack));
        }

        //LocalDataTrack localDataTrack = LocalDataTrack.create(getContext());

        if (localDataTrack != null) {
//            connectOptionsBuilder.dataTracks(Collections.singletonList(localDataTrack));
        }

        if (enableNetworkQualityReporting) {
            connectOptionsBuilder.enableNetworkQuality(true);
            connectOptionsBuilder.networkQualityConfiguration(new NetworkQualityConfiguration(
                    NetworkQualityVerbosity.NETWORK_QUALITY_VERBOSITY_MINIMAL,
                    NetworkQualityVerbosity.NETWORK_QUALITY_VERBOSITY_MINIMAL));
        }

        VideoBandwidthProfileOptions videoBandwidthProfileOptions = new VideoBandwidthProfileOptions.Builder()
                .mode(BandwidthProfileMode.GRID)
                .build();
        BandwidthProfileOptions bandwidthProfileOptions = new BandwidthProfileOptions(videoBandwidthProfileOptions);

        connectOptionsBuilder.encodingParameters(new EncodingParameters(this.maxAudioBitrate, this.maxVideoBitrate));
        connectOptionsBuilder.bandwidthProfile(bandwidthProfileOptions);

        Log.i("CustomTwilioVideoView", "Connecting to Room");
        room = Video.connect(getContext(), connectOptionsBuilder.build(), roomListener());
        allRooms.put(roomName, room);
    }

    private void setAudioFocus(boolean focus) {

        if (focus) {
            try {
                Log.i(TAG, "Audioswitch:: setAudioFocus: activating AS ");
                if(audioSwitch != null) {
                    audioSwitch.activate();
                }
            } catch (Exception e) {
                Log.e(TAG, "Audioswitch:: setAudioFocus: audioswitch activate exception: ", e);
            }
        } else {
            if(audioSwitch != null) {
                audioSwitch.deactivate();
            }
        }
    }

    // ====== DISCONNECTING ========================================================================

    public void disconnect() {
        Log.i("CustomTwilioVideoView", "Disconnect room: " + roomName);

        if(roomName == null) {
            return;
        }

        allRooms.remove(roomName);
        allViews.remove(roomName);
        allThumbnailViews.remove(roomName);

        if (room != null) {
            Log.i("CustomTwilioVideoView", "Disconnect room: Room disc " + room.getName());
            room.disconnect();
        }
        if (localAudioTrack != null) {
            localAudioTrack.release();
            localAudioTrack = null;
        }
        if (localVideoTrack != null) {
            localVideoTrack.release();
            localVideoTrack = null;
        }
        setAudioFocus(false);

        Log.i("CustomTwilioVideoView", "Disconnect: Audio Abandoned: " + roomName);
        if (cameraCapturerCompat != null) {
            try {
                cameraCapturerCompat.stopCapture();
            } catch (InterruptedException e) {
                // Log the exception
            }
            cameraCapturerCompat = null;
        }

        Log.i("CustomTwilioVideoView", "Camera Stopped:: " + roomName);
        trackVideoSinkMap.clear();
    }

    // ===== SEND STRING ON DATA TRACK ======================================================================
    public void sendString(String message) {
        if (localDataTrack != null) {
            localDataTrack.send(message);
        }
    }

    // ===== BUTTON LISTENERS ======================================================================
    private void setThumbnailMirror() {
        if (cameraCapturerCompat != null) {
            CameraCapturerCompat.Source source = cameraCapturerCompat.getCameraSource();
            final boolean isBackCamera = source == CameraCapturerCompat.Source.BACK_CAMERA;
            if (thumbnailVideoView != null && thumbnailVideoView.getVisibility() == View.VISIBLE) {
                thumbnailVideoView.setMirror(!isBackCamera);
            }
        }
    }

    public void switchCamera() {
        if (cameraCapturerCompat != null) {
            cameraCapturerCompat.switchCamera();
            CameraCapturerCompat.Source source = cameraCapturerCompat.getCameraSource();
            final boolean isBackCamera = source == CameraCapturerCompat.Source.BACK_CAMERA;
            WritableMap event = new WritableNativeMap();
            event.putBoolean("isBackCamera", isBackCamera);
            pushEvent(CustomTwilioVideoView.this, ON_CAMERA_SWITCHED, event);
        }
    }

    public void toggleVideo(boolean enabled) {

        isVideoEnabled = enabled;

        // Recommended Approach for Toggling Video is to Unpublish it
        // https://github.com/twilio/twilio-video-app-react/issues/180
        if(localParticipant != null) {
            if(enabled) {
                // TODO Do we need to check whether aleradyR present in published tracks

                if(cameraCapturerCompat == null)
                    cameraCapturerCompat = new CameraCapturerCompat(getContext(), CameraCapturerCompat.Source.FRONT_CAMERA);

                if (cameraCapturerCompat == null){
                    cameraCapturerCompat = new CameraCapturerCompat(getContext(), CameraCapturerCompat.Source.BACK_CAMERA);
                }

                if(cameraCapturerCompat != null && localVideoTrack != null) {
                    localParticipant.unpublishTrack(localVideoTrack);
                    localVideoTrack.release();
                    localVideoTrack = null;
                }

                if (cameraCapturerCompat != null && localVideoTrack == null) {
                    localVideoTrack = LocalVideoTrack.create(getContext(), isVideoEnabled, cameraCapturerCompat, buildVideoFormat());
                }

                if (localVideoTrack != null) {
                    if (thumbnailVideoView != null) {
                        localVideoTrack.addSink(thumbnailVideoView);
                    }

                    /*
                     * If connected to a Room then share the local video track.
                     */
                    localParticipant.publishTrack(localVideoTrack);
                    setThumbnailMirror();
                }
            } else {
                if(localVideoTrack != null) {
                    localParticipant.unpublishTrack(localVideoTrack);
                    localVideoTrack.release();
                    localVideoTrack = null;
                }
            }

            WritableMap event = new WritableNativeMap();
            event.putBoolean("videoEnabled", enabled);
            pushEvent(CustomTwilioVideoView.this, ON_VIDEO_CHANGED, event);
        }

    }

    public void toggleSoundSetup(boolean speaker){
        AudioManager audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        if(speaker){
            audioManager.setSpeakerphoneOn(true);
        } else {
            audioManager.setSpeakerphoneOn(false);
        }
    }

    public void toggleAudio(boolean enabled) {

        if(localParticipant != null) {
            if(enabled) {

                if(localAudioTrack != null) {
                    localParticipant.unpublishTrack(localAudioTrack);
                    localAudioTrack.release();
                    localAudioTrack = null;
                }

                if(localAudioTrack == null) {
                    localAudioTrack = LocalAudioTrack.create(getContext(), enabled, buildAudioOptions());
                }

                localParticipant.publishTrack(localAudioTrack);

                WritableMap event = new WritableNativeMap();
                event.putBoolean("audioEnabled", enabled);
                pushEvent(CustomTwilioVideoView.this, ON_AUDIO_CHANGED, event);
            } else {
                if(localAudioTrack != null) {
                    localParticipant.unpublishTrack(localAudioTrack);
                    localAudioTrack.release();
                    localAudioTrack = null;

                    WritableMap event = new WritableNativeMap();
                    event.putBoolean("audioEnabled", enabled);
                    pushEvent(CustomTwilioVideoView.this, ON_AUDIO_CHANGED, event);
                }
            }
        }
    }

    public void toggleBluetoothHeadset(boolean enabled) {
        AudioManager audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        if(enabled){
            audioManager.startBluetoothSco();
        } else {
            audioManager.stopBluetoothSco();
        }
    }

    public void toggleRemoteAudio(boolean enabled) {
        if (room != null) {
            for (RemoteParticipant rp : room.getRemoteParticipants()) {
                for(AudioTrackPublication at : rp.getAudioTracks()) {
                    if(at.getAudioTrack() != null) {
                        ((RemoteAudioTrack)at.getAudioTrack()).enablePlayback(enabled);
                    }
                }
            }
        }
    }

    public void toggleParticipantAudio(String participantSid, boolean enabled) {
        if (room != null) {
            for (RemoteParticipant rp : room.getRemoteParticipants()) {
                if(rp.getSid() != null && rp.getSid().equals(participantSid)) {
                    for(AudioTrackPublication at : rp.getAudioTracks()) {
                        if(at.getAudioTrack() != null) {
                            RemoteAudioTrack remoteAudioTrack = (RemoteAudioTrack)at.getAudioTrack();
                            remoteAudioTrack.enablePlayback(enabled);
                        }
                    }
                }
            }
        }
    }

    public void toggleParticipantVideo(String participantSid, boolean enabled) {
        if (room != null) {
            for (RemoteParticipant rp : room.getRemoteParticipants()) {
                if(rp.getSid() != null && rp.getSid().equals(participantSid)) {
                    for(RemoteVideoTrackPublication vt : rp.getRemoteVideoTracks()) {
                        if(vt.getVideoTrack() != null) {
                            RemoteVideoTrack remoteVideoTrack = vt.getRemoteVideoTrack();
                            String trackSid = remoteVideoTrack.getSid();

                            if(enabled) {
                                PatchedVideoView sink = trackVideoSinkMap.get(trackSid);
                                // Remove First to not add duplicate Sink
                                if(sink != null) {
                                    remoteVideoTrack.removeSink(sink);
                                    remoteVideoTrack.addSink(sink);
                                    trackVideoSinkMap.put(trackSid, sink);
                                }
                            } else {
                                PatchedVideoView sink = trackVideoSinkMap.get(trackSid);
                                if(sink != null) {
                                    remoteVideoTrack.removeSink(sink);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void publishLocalVideo(boolean enabled) {
        if (localParticipant != null && localVideoTrack != null) {
            if (enabled) {
                localParticipant.publishTrack(localVideoTrack);
            } else {
                localParticipant.unpublishTrack(localVideoTrack);
            }
        }
    }

    public void publishLocalAudio(boolean enabled) {
        if (localParticipant != null && localAudioTrack != null) {
            if (enabled) {
                localParticipant.publishTrack(localAudioTrack);
            } else {
                localParticipant.unpublishTrack(localAudioTrack);
            }
        }
    }


    private void convertBaseTrackStats(BaseTrackStats bs, WritableMap result) {
        result.putString("codec", bs.codec);
        result.putInt("packetsLost", bs.packetsLost);
        result.putString("ssrc", bs.ssrc);
        result.putDouble("timestamp", bs.timestamp);
        result.putString("trackSid", bs.trackSid);
    }

    private void convertLocalTrackStats(LocalTrackStats ts, WritableMap result) {
        result.putDouble("bytesSent", ts.bytesSent);
        result.putInt("packetsSent", ts.packetsSent);
        result.putDouble("roundTripTime", ts.roundTripTime);
    }

    private void convertRemoteTrackStats(RemoteTrackStats ts, WritableMap result) {
        result.putDouble("bytesReceived", ts.bytesReceived);
        result.putInt("packetsReceived", ts.packetsReceived);
    }

    private WritableMap convertAudioTrackStats(RemoteAudioTrackStats as) {
        WritableMap result = new WritableNativeMap();
        result.putInt("audioLevel", as.audioLevel);
        result.putInt("jitter", as.jitter);
        convertBaseTrackStats(as, result);
        convertRemoteTrackStats(as, result);
        return result;
    }

    private WritableMap convertLocalAudioTrackStats(LocalAudioTrackStats as) {
        WritableMap result = new WritableNativeMap();
        result.putInt("audioLevel", as.audioLevel);
        result.putInt("jitter", as.jitter);
        convertBaseTrackStats(as, result);
        convertLocalTrackStats(as, result);
        return result;
    }

    private WritableMap convertVideoTrackStats(RemoteVideoTrackStats vs) {
        WritableMap result = new WritableNativeMap();
        WritableMap dimensions = new WritableNativeMap();
        dimensions.putInt("height", vs.dimensions.height);
        dimensions.putInt("width", vs.dimensions.width);
        result.putMap("dimensions", dimensions);
        result.putInt("frameRate", vs.frameRate);
        convertBaseTrackStats(vs, result);
        convertRemoteTrackStats(vs, result);
        return result;
    }

    private WritableMap convertLocalVideoTrackStats(LocalVideoTrackStats vs) {
        WritableMap result = new WritableNativeMap();
        WritableMap dimensions = new WritableNativeMap();
        dimensions.putInt("height", vs.dimensions.height);
        dimensions.putInt("width", vs.dimensions.width);

        WritableMap captureDimensions = new WritableNativeMap();
        captureDimensions.putInt("height", vs.captureDimensions.height);
        captureDimensions.putInt("width", vs.captureDimensions.width);

        result.putMap("captureDimensions", captureDimensions);
        result.putMap("dimensions", dimensions);
        result.putInt("frameRate", vs.frameRate);
        result.putInt("capturedFrameRate", vs.capturedFrameRate);

        convertBaseTrackStats(vs, result);
        convertLocalTrackStats(vs, result);
        return result;
    }

    private WritableMap convertIceCandidatePairStats(IceCandidatePairStats is) {
        WritableMap result = new WritableNativeMap();

        result.putString("localCandidateId", is.localCandidateId);
        result.putString("remoteCandidateId", is.remoteCandidateId);
        result.putDouble("availableOutgoingBitrate", is.availableOutgoingBitrate);
        result.putDouble("availableIncomingBitrate", is.availableIncomingBitrate);

        return result;
    }

    public void getStats() {
        if (room != null) {
            room.getStats(new StatsListener() {
                @Override
                public void onStats(List<StatsReport> statsReports) {
                    WritableMap event = new WritableNativeMap();
                    for (StatsReport sr : statsReports) {
                        WritableMap connectionStats = new WritableNativeMap();
                        WritableArray as = new WritableNativeArray();
                        for (RemoteAudioTrackStats s : sr.getRemoteAudioTrackStats()) {
                            as.pushMap(convertAudioTrackStats(s));
                        }
                        connectionStats.putArray("remoteAudioTrackStats", as);

                        WritableArray vs = new WritableNativeArray();
                        for (RemoteVideoTrackStats s : sr.getRemoteVideoTrackStats()) {
                            vs.pushMap(convertVideoTrackStats(s));
                        }
                        connectionStats.putArray("remoteVideoTrackStats", vs);

                        WritableArray las = new WritableNativeArray();
                        for (LocalAudioTrackStats s : sr.getLocalAudioTrackStats()) {
                            las.pushMap(convertLocalAudioTrackStats(s));
                        }
                        connectionStats.putArray("localAudioTrackStats", las);

                        WritableArray lvs = new WritableNativeArray();
                        for (LocalVideoTrackStats s : sr.getLocalVideoTrackStats()) {
                            lvs.pushMap(convertLocalVideoTrackStats(s));
                        }
                        connectionStats.putArray("localVideoTrackStats", lvs);

                        WritableArray iceStats = new WritableNativeArray();
                        for (IceCandidatePairStats s : sr.getIceCandidatePairStats()) {
                            iceStats.pushMap(convertIceCandidatePairStats(s));
                        }
                        connectionStats.putArray("iceCandidatePairStats", iceStats);

                        event.putMap(sr.getPeerConnectionId(), connectionStats);
                    }
                    pushEvent(CustomTwilioVideoView.this, ON_STATS_RECEIVED, event);
                }
            });
        }
    }

    public void disableOpenSLES() {
        WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true);
    }

    // ====== ROOM LISTENER ========================================================================

    /*
     * Room events listener
     */
    private Room.Listener roomListener() {
        return new Room.Listener() {
            @Override
            public void onConnected(Room room) {
                Log.i("CustomTwilioVideoView", "onConnected called:: " + room.getName());
                localParticipant = room.getLocalParticipant();
                localParticipant.setListener(localListener());

                WritableMap event = new WritableNativeMap();
                event.putString("roomName", room.getName());
                event.putString("roomSid", room.getSid());
                List<RemoteParticipant> participants = room.getRemoteParticipants();

                WritableArray participantsArray = new WritableNativeArray();
                for (RemoteParticipant participant : participants) {
                    participantsArray.pushMap(buildParticipant(participant));
                }
                participantsArray.pushMap(buildParticipant(localParticipant));
                event.putArray("participants", participantsArray);

                pushEvent(CustomTwilioVideoView.this, ON_CONNECTED, event);

                //There is not .publish it's publishTrack
//                localParticipant.publishTrack(localDataTrack);

                for (RemoteParticipant participant : participants) {
                    addParticipant(room, participant);
                }

                setAudioFocus(enableRemoteAudio);
            }

            @Override
            public void onConnectFailure(Room room, TwilioException e) {
                WritableMap event = new WritableNativeMap();
                event.putString("roomName", room.getName());
                event.putString("roomSid", room.getSid());
                event.putString("error", e.getMessage());
                pushEvent(CustomTwilioVideoView.this, ON_CONNECT_FAILURE, event);
            }

            @Override
            public void onReconnecting(@NonNull Room room, @NonNull TwilioException twilioException) {

            }

            @Override
            public void onReconnected(@NonNull Room room) {

            }

            @Override
            public void onDisconnected(Room room, TwilioException e) {
                Log.i("CustomTwilioVideoView", "onDisconnected called:: " + room.getName());
                WritableMap event = new WritableNativeMap();

                if (localParticipant != null) {
                    event.putString("participant", localParticipant.getIdentity());
                }
                event.putString("roomName", room.getName());
                event.putString("roomSid", room.getSid());
                if (e != null) {
                    event.putString("error", e.getMessage());
                }
                pushEvent(CustomTwilioVideoView.this, ON_DISCONNECTED, event);

//                roomName = null;
//                CustomTwilioVideoView.room = null;
                // Only reinitialize the UI if disconnect was not called from onDestroy()
                if (!disconnectedFromOnDestroy) {
//                    Log.i("CustomTwilioVideoView", "Setting Audio focus false from disconnected");
//                    setAudioFocus(false);
                }
            }

            @Override
            public void onParticipantConnected(Room room, RemoteParticipant participant) {
                addParticipant(room, participant);

            }

            @Override
            public void onParticipantDisconnected(Room room, RemoteParticipant participant) {
                removeParticipant(room, participant);
            }

            @Override
            public void onRecordingStarted(Room room) {
            }

            @Override
            public void onRecordingStopped(Room room) {
            }
        };
    }

    /*
     * Called when participant joins the room
     */
    private void addParticipant(Room room, RemoteParticipant remoteParticipant) {

        WritableMap event = new WritableNativeMap();
        event.putString("roomName", room.getName());
        event.putString("roomSid", room.getSid());
        event.putMap("participant", buildParticipant(remoteParticipant));

        pushEvent(this, ON_PARTICIPANT_CONNECTED, event);

        /*
         * Start listening for participant media events
         */
        remoteParticipant.setListener(mediaListener());

        for (final RemoteDataTrackPublication remoteDataTrackPublication :
                remoteParticipant.getRemoteDataTracks()) {
            /*
             * Data track messages are received on the thread that calls setListener. Post the
             * invocation of setting the listener onto our dedicated data track message thread.
             */
            if (remoteDataTrackPublication.isTrackSubscribed()) {
                if(dataTrackEnabled) {
                    dataTrackMessageThreadHandler.post(() -> addRemoteDataTrack(remoteParticipant,
                            remoteDataTrackPublication.getRemoteDataTrack()));
                }
            }
        }
    }

    /*
     * Called when participant leaves the room
     */
    private void removeParticipant(Room room, RemoteParticipant participant) {
        WritableMap event = new WritableNativeMap();
        event.putString("roomName", room.getName());
        event.putString("roomSid", room.getSid());
        event.putMap("participant", buildParticipant(participant));
        pushEvent(this, ON_PARTICIPANT_DISCONNECTED, event);
        //something about this breaking.
        //participant.setListener(null);
    }

    private void addRemoteDataTrack(RemoteParticipant remoteParticipant, RemoteDataTrack remoteDataTrack) {
        dataTrackRemoteParticipantMap.put(remoteDataTrack, remoteParticipant);
        remoteDataTrack.setListener(remoteDataTrackListener());
    }

    // ====== MEDIA LISTENER =======================================================================

    private RemoteParticipant.Listener mediaListener() {
        return new RemoteParticipant.Listener() {
            @Override
            public void onAudioTrackSubscribed(RemoteParticipant participant, RemoteAudioTrackPublication publication, RemoteAudioTrack audioTrack) {
                audioTrack.enablePlayback(enableRemoteAudio);
                WritableMap event = buildParticipantVideoEvent(participant, publication);
                pushEvent(CustomTwilioVideoView.this, ON_PARTICIPANT_ADDED_AUDIO_TRACK, event);
            }

            @Override
            public void onAudioTrackUnsubscribed(RemoteParticipant participant, RemoteAudioTrackPublication publication, RemoteAudioTrack audioTrack) {
                WritableMap event = buildParticipantVideoEvent(participant, publication);
                pushEvent(CustomTwilioVideoView.this, ON_PARTICIPANT_REMOVED_AUDIO_TRACK, event);
            }

            @Override
            public void onAudioTrackSubscriptionFailed(RemoteParticipant participant, RemoteAudioTrackPublication publication, TwilioException twilioException) {

            }

            @Override
            public void onAudioTrackPublished(RemoteParticipant participant, RemoteAudioTrackPublication publication) {
            }

            @Override
            public void onAudioTrackUnpublished(RemoteParticipant participant, RemoteAudioTrackPublication publication) {

            }



            @Override
            public void onDataTrackSubscribed(RemoteParticipant remoteParticipant, RemoteDataTrackPublication remoteDataTrackPublication, RemoteDataTrack remoteDataTrack) {
                WritableMap event = buildParticipantDataEvent(remoteParticipant);
                pushEvent(CustomTwilioVideoView.this, ON_PARTICIPANT_ADDED_DATA_TRACK, event);
                if(dataTrackEnabled)
                    dataTrackMessageThreadHandler.post(() -> addRemoteDataTrack(remoteParticipant, remoteDataTrack));
            }

            @Override
            public void onDataTrackUnsubscribed(RemoteParticipant remoteParticipant, RemoteDataTrackPublication publication, RemoteDataTrack remoteDataTrack) {
                WritableMap event = buildParticipantDataEvent(remoteParticipant);
                pushEvent(CustomTwilioVideoView.this, ON_PARTICIPANT_REMOVED_DATA_TRACK, event);
            }

            @Override
            public void onDataTrackSubscriptionFailed(RemoteParticipant participant, RemoteDataTrackPublication publication, TwilioException twilioException) {

            }

            @Override
            public void onDataTrackPublished(RemoteParticipant participant, RemoteDataTrackPublication publication) {

            }

            @Override
            public void onDataTrackUnpublished(RemoteParticipant participant, RemoteDataTrackPublication publication) {

            }

            @Override
            public void onVideoTrackSubscribed(RemoteParticipant participant, RemoteVideoTrackPublication publication, RemoteVideoTrack videoTrack) {
                addParticipantVideo(participant, publication);
            }

            @Override
            public void onVideoTrackUnsubscribed(RemoteParticipant participant, RemoteVideoTrackPublication publication, RemoteVideoTrack videoTrack) {
                removeParticipantVideo(participant, publication);
            }

            @Override
            public void onVideoTrackSubscriptionFailed(RemoteParticipant participant, RemoteVideoTrackPublication publication, TwilioException twilioException) {
            }

            @Override
            public void onVideoTrackPublished(RemoteParticipant participant, RemoteVideoTrackPublication publication) {

            }

            @Override
            public void onVideoTrackUnpublished(RemoteParticipant participant, RemoteVideoTrackPublication publication) {

            }

            @Override
            public void onAudioTrackEnabled(RemoteParticipant participant, RemoteAudioTrackPublication publication) {//                Log.i(TAG, "onAudioTrackEnabled");
//                publication.getRemoteAudioTrack().enablePlayback(false);
                WritableMap event = buildParticipantVideoEvent(participant, publication);
                pushEvent(CustomTwilioVideoView.this, ON_PARTICIPANT_ENABLED_AUDIO_TRACK, event);
            }

            @Override
            public void onAudioTrackDisabled(RemoteParticipant participant, RemoteAudioTrackPublication publication) {
                WritableMap event = buildParticipantVideoEvent(participant, publication);
                pushEvent(CustomTwilioVideoView.this, ON_PARTICIPANT_DISABLED_AUDIO_TRACK, event);
            }

            @Override
            public void onVideoTrackEnabled(RemoteParticipant participant, RemoteVideoTrackPublication publication) {
                WritableMap event = buildParticipantVideoEvent(participant, publication);
                pushEvent(CustomTwilioVideoView.this, ON_PARTICIPANT_ENABLED_VIDEO_TRACK, event);
            }

            @Override
            public void onVideoTrackDisabled(RemoteParticipant participant, RemoteVideoTrackPublication publication) {
                WritableMap event = buildParticipantVideoEvent(participant, publication);
                pushEvent(CustomTwilioVideoView.this, ON_PARTICIPANT_DISABLED_VIDEO_TRACK, event);
            }

            @Override
            public void onNetworkQualityLevelChanged(RemoteParticipant remoteParticipant, NetworkQualityLevel networkQualityLevel) {
                WritableMap event = new WritableNativeMap();
                event.putMap("participant", buildParticipant(remoteParticipant));
                event.putBoolean("isLocalUser", false);

                // Twilio SDK defines Enum 0 as UNKNOWN and 1 as Quality ZERO, so we subtract one to get the correct quality level as an integer
                event.putInt("quality", networkQualityLevel.ordinal() - 1);

                pushEvent(CustomTwilioVideoView.this, ON_NETWORK_QUALITY_LEVELS_CHANGED, event);
            }
        };
    }

    // ====== LOCAL LISTENER =======================================================================
    private LocalParticipant.Listener localListener() {
        return new LocalParticipant.Listener() {

            @Override
            public void onAudioTrackPublished(LocalParticipant localParticipant, LocalAudioTrackPublication localAudioTrackPublication) {

            }

            @Override
            public void onAudioTrackPublicationFailed(LocalParticipant localParticipant, LocalAudioTrack localAudioTrack, TwilioException twilioException) {

            }

            @Override
            public void onVideoTrackPublished(LocalParticipant localParticipant, LocalVideoTrackPublication localVideoTrackPublication) {

            }

            @Override
            public void onVideoTrackPublicationFailed(LocalParticipant localParticipant, LocalVideoTrack localVideoTrack, TwilioException twilioException) {

            }

            @Override
            public void onDataTrackPublished(LocalParticipant localParticipant, LocalDataTrackPublication localDataTrackPublication) {

            }

            @Override
            public void onDataTrackPublicationFailed(LocalParticipant localParticipant, LocalDataTrack localDataTrack, TwilioException twilioException) {

            }

            @Override
            public void onNetworkQualityLevelChanged(LocalParticipant localParticipant, NetworkQualityLevel networkQualityLevel) {
                WritableMap event = new WritableNativeMap();
                event.putMap("participant", buildParticipant(localParticipant));
                event.putBoolean("isLocalUser", true);

                // Twilio SDK defines Enum 0 as UNKNOWN and 1 as Quality ZERO, so we subtract one to get the correct quality level as an integer
                event.putInt("quality", networkQualityLevel.ordinal() - 1);

                pushEvent(CustomTwilioVideoView.this, ON_NETWORK_QUALITY_LEVELS_CHANGED, event);
            }
        };
    }

    private WritableMap buildParticipant(Participant participant) {
        WritableMap participantMap = new WritableNativeMap();
        participantMap.putString("identity", participant.getIdentity());
        participantMap.putString("sid", participant.getSid());
        return participantMap;
    }


    private WritableMap buildParticipantDataEvent(Participant participant) {
        WritableMap participantMap = buildParticipant(participant);
        WritableMap participantMap2 = buildParticipant(participant);

        WritableMap event = new WritableNativeMap();
        event.putMap("participant", participantMap);
        event.putMap("track", participantMap2);
        return event;
    }

    private WritableMap buildParticipantVideoEvent(Participant participant, TrackPublication publication) {
        WritableMap participantMap = buildParticipant(participant);

        WritableMap trackMap = new WritableNativeMap();
        trackMap.putString("trackSid", publication.getTrackSid());
        trackMap.putString("trackName", publication.getTrackName());
        trackMap.putBoolean("enabled", publication.isTrackEnabled());

        WritableMap event = new WritableNativeMap();
        event.putMap("participant", participantMap);
        event.putMap("track", trackMap);
        return event;
    }

    private WritableMap buildDataTrackEvent(String message) {
        WritableMap event = new WritableNativeMap();
        event.putString("message", message);
        return event;
    }

    private void addParticipantVideo(Participant participant, RemoteVideoTrackPublication publication) {
        WritableMap event = this.buildParticipantVideoEvent(participant, publication);
        pushEvent(CustomTwilioVideoView.this, ON_PARTICIPANT_ADDED_VIDEO_TRACK, event);
    }

    private void removeParticipantVideo(Participant participant, RemoteVideoTrackPublication deleteVideoTrack) {
        WritableMap event = this.buildParticipantVideoEvent(participant, deleteVideoTrack);
        pushEvent(CustomTwilioVideoView.this, ON_PARTICIPANT_REMOVED_VIDEO_TRACK, event);
    }
    // ===== EVENTS TO RN ==========================================================================

    void pushEvent(View view, String name, WritableMap data) {
        eventEmitter.receiveEvent(view.getId(), name, data);
    }

    public static void registerPrimaryVideoView(PatchedVideoView v, String trackSid) {
        for(Map.Entry<String, Room> entry : allRooms.entrySet()) {
            String roomName = entry.getKey();
            Room roomObj = entry.getValue();
            if(roomObj != null) {
                Log.i("CustomTwilioVideoView", "registerPrimaryVideoView " + trackSid + " Room:: " + roomName);
                for (RemoteParticipant participant : roomObj.getRemoteParticipants()) {
                    for (RemoteVideoTrackPublication publication : participant.getRemoteVideoTracks()) {
                        RemoteVideoTrack track = publication.getRemoteVideoTrack();
                        if (track == null) {
                            continue;
                        }
                        if (publication.getTrackSid().equals(trackSid)) {
                            track.addSink(v);
                            trackVideoSinkMap.put(trackSid, v);
                        } else {
                            track.removeSink(v);
                        }
                    }
                }
            }
        }
    }

    public static void registerThumbnailVideoView(PatchedVideoView v, boolean enabled, String roomName) {

        Log.i("CustomTwilioVideoView", "registerThumbnailVideoView:: " + roomName);

        allThumbnailViews.put(roomName, v);

        CustomTwilioVideoView view = allViews.get(roomName);
        if(view != null) {
            view.registerThumbnail(v, enabled);
        }
    }

    public void registerThumbnail(PatchedVideoView v, boolean enabled) {
        Log.i("CustomTwilioVideoView", "registerThumbnail:: " + roomName);
        if(thumbnailVideoView != null & localVideoTrack != null) {
            localVideoTrack.removeSink(thumbnailVideoView);
        }

        thumbnailVideoView = v;
        if (localVideoTrack != null) {
            Log.i("CustomTwilioVideoView", "adding Sink to LocalVideoTrack:: " + roomName);
            localVideoTrack.addSink(v);
            localVideoTrack.enable(enabled);
        }
        setThumbnailMirror();
    }

    private RemoteDataTrack.Listener remoteDataTrackListener() {
        return new RemoteDataTrack.Listener() {

            @Override
            public void onMessage(RemoteDataTrack remoteDataTrack, ByteBuffer byteBuffer) {

            }


            @Override
            public void onMessage(RemoteDataTrack remoteDataTrack, String message) {
                WritableMap event = buildDataTrackEvent(message);
                pushEvent(CustomTwilioVideoView.this, ON_DATATRACK_MESSAGE_RECEIVED, event);
            }
        };
    }
}
