package org.sircular.proxalert.background;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by walt on 7/9/16.
 */
public class BootCompleteReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent locationServiceIntent = new Intent(context, LocationService.class);
        context.startService(locationServiceIntent);
    }
}
