/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.systemui.biometrics;

import android.content.Context;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;

import com.android.systemui.R;

/**
 * This class loads the view for the system-provided dialog. The view consists of:
 * Application Icon, Title, Subtitle, Description, Fingerprint Icon, Error/Help message area,
 * and positive/negative buttons.
 */
public class FingerprintDialogView extends BiometricDialogView {
    private static final String TAG = "FingerprintDialogView";

    @Override
    protected int getLayoutResourceId() {
        return R.layout.fingerprint_dialog;
    }

    @Override
    protected int getHintStringResource() {
        return R.string.fingerprint_dialog_touch_sensor;
    }

    @Override
    protected float getAnimationTranslationOffset() {
        return getResources()
                .getDimension(R.dimen.fingerprint_dialog_animation_translation_offset);
    }

    @Override
    protected void updateIcon(int lastState, int newState) {
        Drawable icon = getAnimationForTransition(lastState, newState);

        if (icon == null) {
            Log.e(TAG, "Animation not found");
            return;
        }

        final AnimatedVectorDrawable animation = icon instanceof AnimatedVectorDrawable
                ? (AnimatedVectorDrawable) icon
                : null;

        final ImageView fingerprint_icon = getLayout().findViewById(R.id.fingerprint_icon);
        fingerprint_icon.setImageDrawable(icon);

        if (animation != null && shouldAnimateForTransition(lastState, newState)) {
            animation.forceAnimationOnUI();
            animation.start();
        }
    }

    public FingerprintDialogView(Context context,
            DialogViewCallback callback) {
        super(context, callback);
    }

    private boolean shouldAnimateForTransition(int oldState, int newState) {
        if (oldState == STATE_NONE && newState == STATE_AUTHENTICATING) {
            return false;
        } else if (oldState == STATE_AUTHENTICATING && newState == STATE_ERROR) {
            return true;
        } else if (oldState == STATE_ERROR && newState == STATE_AUTHENTICATING) {
            return true;
        } else if (oldState == STATE_AUTHENTICATING && newState == STATE_AUTHENTICATED) {
            // TODO(b/77328470): add animation when fingerprint is authenticated
            return false;
        }
        return false;
    }

    private Drawable getAnimationForTransition(int oldState, int newState) {
        int iconRes;
        if (oldState == STATE_NONE && newState == STATE_AUTHENTICATING) {
            iconRes = R.drawable.fingerprint_dialog_fp_to_error;
        } else if (oldState == STATE_AUTHENTICATING && newState == STATE_ERROR) {
            iconRes = R.drawable.fingerprint_dialog_fp_to_error;
        } else if (oldState == STATE_ERROR && newState == STATE_AUTHENTICATING) {
            iconRes = R.drawable.fingerprint_dialog_error_to_fp;
        } else if (oldState == STATE_AUTHENTICATING && newState == STATE_AUTHENTICATED) {
            // TODO(b/77328470): add animation when fingerprint is authenticated
            iconRes = R.drawable.fingerprint_dialog_error_to_fp;
        } else {
            return null;
        }
        return mContext.getDrawable(iconRes);
    }
}