/**
 * Component for Twilio Video local views.
 * <p>
 * Authors:
 * Jonathan Chang <slycoder@gmail.com>
 */

package com.twiliorn.library;

import android.util.Log;

import androidx.annotation.Nullable;

import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;

import java.util.Map;


import tvi.webrtc.RendererCommon;

import static com.twiliorn.library.RNVideoViewGroup.Events.ON_FRAME_DIMENSIONS_CHANGED;

public class TwilioVideoPreviewManager extends SimpleViewManager<TwilioVideoPreview> {

    public static final String REACT_CLASS = "RNTwilioVideoPreview";

    public boolean enabled = false;

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @ReactProp(name = "scaleType")
    public void setScaleType(TwilioVideoPreview view, @Nullable String scaleType) {
      if (scaleType.equals("fit")) {
        view.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
      } else {
        view.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
      }
    }

    @ReactProp(name = "enabled")
    public void setEnabled(TwilioVideoPreview view, @Nullable boolean enabled) {

        Log.i("CustomTwilioVideoView", "Initialize Twilio Local: setEnabled:: " + enabled);

        view.setEnabled(enabled);
        if(view.initialized)
            CustomTwilioVideoView.registerThumbnailVideoView(view.getSurfaceViewRenderer(), view.enabled, view.roomName);
    }

    @ReactProp(name = "roomName")
    public void setRoomName(TwilioVideoPreview view, @Nullable String roomName) {

        Log.i("CustomTwilioVideoView", "Initialize Twilio Local: setRoomName:: " + roomName);

        view.setRoomName(roomName);

        if(view.initialized)
            CustomTwilioVideoView.registerThumbnailVideoView(view.getSurfaceViewRenderer(), view.enabled, view.roomName);
    }

    @Override
    @Nullable
    public Map getExportedCustomDirectEventTypeConstants() {
        Map<String, Map<String, String>> map = MapBuilder.of(
                ON_FRAME_DIMENSIONS_CHANGED, MapBuilder.of("registrationName", ON_FRAME_DIMENSIONS_CHANGED)
        );

        return map;
    }

    @Override
    protected TwilioVideoPreview createViewInstance(ThemedReactContext reactContext) {
        return new TwilioVideoPreview(reactContext, this.enabled);
    }
}
