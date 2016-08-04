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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by walt on 8/2/16.
 */
public class LocationService extends Service implements LocationStore.UpdateListener {
    private final long MIN_DELAY = TimeUnit.SECONDS.toMillis(20);
    private final long MAX_DELAY = TimeUnit.MINUTES.toMillis(10);
    private final double MIN_VELOCITY = (100*1000.0)/TimeUnit.HOURS.toMillis(1); // m/ms

    private Location lastLocation = null;
    private GoogleApiClient apiClient;
    private AlarmManager alarmManager;
    private long lastTimestamp;
    private List<ProxLocation> currentlyInside;

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

        lastTimestamp = SystemClock.elapsedRealtime();

        currentlyInside = new ArrayList<>();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        apiClient.connect();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            Location currentLocation = LocationServices.FusedLocationApi.getLastLocation(apiClient);
            processLocations(currentLocation, lastLocation,
                    SystemClock.elapsedRealtime(), lastTimestamp, LocationStore.getLocations());
            lastLocation = currentLocation;
            lastTimestamp = SystemClock.elapsedRealtime();
        } else {
            // this activity will request permission when it opens
            Intent mainActivityIntent = new Intent(this, ProxAlertActivity.class);
            this.startActivity(mainActivityIntent);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        apiClient.disconnect();
    }

    @Override
    public void onLocationUpdated(LocationStore.UPDATE_TYPE type, ProxLocation location) {
        if (type != LocationStore.UPDATE_TYPE.REMOVED) // no need to immediately reschedule
            scheduleLocationCheck(0L);
    }

    private void processLocations(Location currentLocation, Location lastLocation,
                                  long currentTime, long lastTime, List<ProxLocation> proxLocations) {
        // check for any changes and remove unused locations
        for (ProxLocation proxLocation : currentlyInside) {
            if (!proxLocations.contains(proxLocation)) {
                currentlyInside.remove(proxLocation);
            }
        }
        if (currentLocation == null) {
            scheduleLocationCheck(TimeUnit.SECONDS.toMillis(10));
        } else if (proxLocations.size() > 0) {
            // used later for calculating delay
            double closestLocationThresholdDistance = Double.POSITIVE_INFINITY;
            // determine which radii we're inside
            for (ProxLocation proxLocation : proxLocations) {
                boolean removed = false;
                double distance = currentLocation.distanceTo(proxLocation.getLocation())
                        -proxLocation.getRadius();
                boolean inside = distance <= 0;
                double distanceToThreshold = Math.abs(distance);
                if (inside) {
                    if (proxLocation.isRecurring()) {
                        if (!currentlyInside.contains(proxLocation)) {
                            triggerNotification(proxLocation.getTitle());
                            currentlyInside.add(proxLocation);
                        }
                    } else {
                        triggerNotification(proxLocation.getTitle());
                        LocationStore.removeLocation(proxLocation);
                        removed = true;
                    }
                } else {
                    currentlyInside.remove(proxLocation);
                }
                if (!removed) {
                    if (distanceToThreshold < closestLocationThresholdDistance) {
                        closestLocationThresholdDistance = distanceToThreshold;
                    }
                }
            }
            if (closestLocationThresholdDistance != Double.POSITIVE_INFINITY) {
                scheduleLocationCheck(estimateDelay(currentLocation, lastLocation,
                        closestLocationThresholdDistance, currentTime-lastTime));
            }
        }
    }

    private void triggerNotification(String message) {
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this);
        notificationBuilder.setContentText(message);
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

    private void scheduleLocationCheck(long time) {
        PendingIntent intent = PendingIntent.getService(this, 79, new Intent(this.getApplicationContext(),
                LocationService.class), PendingIntent.FLAG_CANCEL_CURRENT);
        alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime()+time, intent);
    }

    private long estimateDelay(Location currentLocation, Location lastLocation, double distance,
                               long lastDelay) {
        double velocity = MIN_VELOCITY;
        if (lastDelay > 0 && lastLocation != null) {
            velocity = Math.max(velocity,
                    currentLocation.distanceTo(lastLocation)/lastDelay);
        }
        // extrapolate and halve
        double estimatedTime = distance/(velocity*2);
        return (long)Math.max(MIN_DELAY, Math.min(MAX_DELAY, estimatedTime));
    }
}
