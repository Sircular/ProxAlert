package org.sircular.proxalert.background;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.media.AudioManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import org.sircular.proxalert.LocationStore;
import org.sircular.proxalert.ProxAlertActivity;
import org.sircular.proxalert.ProxLocation;
import org.sircular.proxalert.R;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * Created by walt on 7/30/16.
 */
public class LocationService extends Service implements LocationStore.UpdateListener {
    private final long MIN_DELAY = TimeUnit.SECONDS.toMillis(20);
    private final long MAX_DELAY = TimeUnit.MINUTES.toMillis(10);
    private long lastDelay = 0L;
    private Location lastLocation = null;
    private GoogleApiClient apiClient;
    private AlarmManager alarmManager;
    boolean currentlyInside = false;
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        if (apiClient == null) {
            apiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .build();
        }
        alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        LocationStore.registerListener(this);
        LocationStore.initialize(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        apiClient.connect();
        triggerLocationCheck();

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        apiClient.disconnect();
    }

    @Override
    public void onLocationUpdated(LocationStore.UPDATE_TYPE type, ProxLocation location) {
        triggerLocationCheck();
    }

    public void triggerLocationCheck() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            Location currentLocation = LocationServices.FusedLocationApi.getLastLocation(apiClient);
            scheduleLocationCheck(currentLocation, lastLocation);
            lastLocation = currentLocation;
        } else {
            // this activity will request permission when it opens
            Intent mainActivityIntent = new Intent(this, ProxAlertActivity.class);
            this.startActivity(mainActivityIntent);
        }

    }

    private void scheduleLocationCheck(Location currentLocation, Location lastLocation) {
        if (currentLocation == null) {
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime()+TimeUnit.SECONDS.toMillis(10), generateTriggerPendingIntent());
            return;
        }
        List<ProxLocation> locations = LocationStore.getLocations();
        if (locations.size() > 0) {
            // select the nearest location
            ProxLocation closestLocation = selectNearest(currentLocation, locations);
            if (closestLocation != null) { // to be safe
                boolean removed = false;
                if (currentLocation.distanceTo(closestLocation.getLocation()) <= closestLocation.getRadius()) {
                    if (!currentlyInside) {
                        triggerNotification(closestLocation);
                        if (closestLocation.isRecurring()) {
                            currentlyInside = true;
                        } else {
                            LocationStore.removeLocation(closestLocation);
                            LocationStore.saveLocations();
                            removed = true;
                        }
                    }
                } else {
                    currentlyInside = false;
                }
                if (!removed) {
                    long milliDelay = determineDelay(currentLocation, lastLocation, closestLocation, lastDelay);
                    lastDelay = milliDelay;
                    alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime()+milliDelay, generateTriggerPendingIntent());
                } // else the listener will reschedule
            }
        } else {
            currentlyInside = false; // just to make sure it's initialized properly
        }
        // otherwise, nothing needs to be scheduled
    }

    private void triggerNotification(ProxLocation location) {
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this);
        notificationBuilder.setContentText(location.getTitle());
        notificationBuilder.setContentTitle(this.getString(R.string.app_name));
        notificationBuilder.setSmallIcon(R.drawable.icon_small);
        // set up sound/vibrate
        AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        switch (audio.getRingerMode()) {
            case AudioManager.RINGER_MODE_NORMAL:
                Uri notifySound = Uri.parse("android.resource://"+this.getPackageName()
                        +"/"+R.raw.notification); // wtf android
                notificationBuilder.setSound(notifySound);
                break;
            case AudioManager.RINGER_MODE_VIBRATE:
                notificationBuilder.setVibrate(new long[]{333, 333, 333, 333, 333, 333, 333});
        }
        notificationBuilder.setLights(Color.WHITE, 750, 3000);
        notificationBuilder.setAutoCancel(true);
        Notification notification = notificationBuilder.build();

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(1, notification);
    }

    private long determineDelay(Location currentLocation, Location lastLocation,
                                    ProxLocation destLocation, long lastDelay) {
        // m/ms
        double velocity = (100*1000.0)/TimeUnit.HOURS.toMillis(1);
        if (lastDelay > 0 && lastLocation != null) {
            velocity = Math.max(velocity,
                    currentLocation.distanceTo(lastLocation)/lastDelay);
        }
        // extrapolate and halve
        double estimatedTime = Math.abs(currentLocation.distanceTo(destLocation.getLocation())
                -destLocation.getRadius())/(velocity*2);
        return (long)Math.max(MIN_DELAY, Math.min(MAX_DELAY, estimatedTime));
    }

    private ProxLocation selectNearest(Location currentLocation, List<ProxLocation> locations) {
        ProxLocation closestLocation = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (ProxLocation location : locations) {
            double distance = currentLocation.distanceTo(location.getLocation());
            if (closestLocation == null) {
                closestLocation = location;
                closestDistance = distance;
            } else {

                if (distance < closestDistance) {
                    closestLocation = location;
                    closestDistance = distance;
                }
            }
        }
        return closestLocation;
    }

    private PendingIntent generateTriggerPendingIntent() {
        return PendingIntent.getService(this, 79, new Intent(this.getApplicationContext(),
                LocationService.class), PendingIntent.FLAG_CANCEL_CURRENT);
    }
}
