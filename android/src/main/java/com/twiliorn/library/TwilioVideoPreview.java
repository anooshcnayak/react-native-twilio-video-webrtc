/**
 * Component for Twilio Video local views.
 * <p>
 * Authors:
 * Jonathan Chang <slycoder@gmail.com>
 */

package com.twiliorn.library;
import android.util.Log;

import com.facebook.react.uimanager.ThemedReactContext;

public class TwilioVideoPreview extends RNVideoViewGroup {

    private static final String TAG = "TwilioVideoPreview";
    public boolean enabled = true;
    public String roomName = null;
    public boolean initialized = false;
    public int attrInit = 0;

    public TwilioVideoPreview(ThemedReactContext themedReactContext, boolean enabled) {
        super(themedReactContext);
        Log.i("CustomTwilioVideoView", "Inside VideoPreview Constructor");
//        CustomTwilioVideoView.registerThumbnailVideoView(this.getSurfaceViewRenderer(), enabled);
        this.getSurfaceViewRenderer().applyZOrder(true);
    }

    public void setEnabled(boolean enabled) {
        attrInit++;
        this.enabled = enabled;

        if(attrInit == 2) initialized = true;
    }

    public void setRoomName(String roomName) {
        attrInit++;
        this.roomName = roomName;

        if(attrInit == 2) initialized = true;
    }
}
