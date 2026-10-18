package com.lumenchord.pianoweave.midi;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * Test-only "unplug / replug" switch for {@link LoopbackMidiService}. Runs in the test APK's process, the
 * only one allowed to enable or disable the service, which adds or removes the virtual MIDI device.
 */
public class LoopbackToggleReceiver extends BroadcastReceiver {
    public static final String EXTRA_ENABLED = "enabled";

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean enabled = intent.getBooleanExtra(EXTRA_ENABLED, true);
        context.getPackageManager().setComponentEnabledSetting(
            new ComponentName(context, LoopbackMidiService.class),
            enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        );
    }
}
