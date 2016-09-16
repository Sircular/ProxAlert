package org.sircular.proxalert.background;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.sircular.proxalert.LocationStore;
import org.sircular.proxalert.ProxAlertActivity;
import org.sircular.proxalert.ProxLocation;
import org.sircular.proxalert.R;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by walt on 8/2/16.
 */
public class LocationService extends Service implements LocationStore.UpdateListener,
        GoogleApiClient.ConnectionCallbacks {
    private final long MIN_DELAY = TimeUnit.SECONDS.toMillis(30);
    private final long MAX_DELAY = TimeUnit.MINUTES.toMillis(20);
    private final double ESTIMATED_VELOCITY = (100*1000.0)/TimeUnit.HOURS.toMillis(1); // m/ms

    private GoogleApiClient apiClient;
    private PendingIntent requestCallback;
    private Lock modificationLock;
    private long lastDelay = 1L; // to prevent divide-by-zero errors
    private Location lastLocation = null;

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
        LocationStore.registerListener(this);
        LocationStore.initialize(this);

        modificationLock = new ReentrantLock();

        Intent intent = new Intent(this, LocationService.class);
        requestCallback = PendingIntent.getService(this, 50, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!(apiClient.isConnected() || apiClient.isConnecting())) {
            apiClient.connect();
            apiClient.registerConnectionCallbacks(this);
        }
        if (LocationResult.hasResult(intent)) {
            if (modificationLock.tryLock()) {
                processLocations(LocationResult.extractResult(intent).getLastLocation(),
                        LocationStore.getLocations());
                modificationLock.unlock();
            }
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
            scheduleLocationUpdates(0L);
    }

    private void processLocations(Location currentLocation, List<ProxLocation> proxLocations) {
        if (currentLocation == null) {
            scheduleLocationUpdates(TimeUnit.SECONDS.toMillis(10));
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
                        if (lastLocation == null) {
                            SharedPreferences sharedPreferences = getSharedPreferences(
                                    getString(R.string.location_prefs_file), Context.MODE_PRIVATE);
                            if (sharedPreferences.contains("lat") && sharedPreferences.contains("lon")) {
                                double lat = Double.longBitsToDouble(sharedPreferences.getLong("lat", 0L));
                                double lon = Double.longBitsToDouble(sharedPreferences.getLong("lon", 0L));
                                lastLocation = new Location("");
                                lastLocation.setLatitude(lat);
                                lastLocation.setLongitude(lon);
                            }
                        }
                        if (lastLocation == null || lastLocation.distanceTo(proxLocation.getLocation())
                                > proxLocation.getRadius()) {
                            triggerNotification(proxLocation.getTitle());
                        }
                    } else {
                        triggerNotification(proxLocation.getTitle());
                        LocationStore.removeLocation(proxLocation);
                        removed = true;
                    }
                }
                if (!removed) {
                    if (distanceToThreshold < closestLocationThresholdDistance) {
                        closestLocationThresholdDistance = distanceToThreshold;
                    }
                }
            }
            if (closestLocationThresholdDistance != Double.POSITIVE_INFINITY) {
                scheduleLocationUpdates(estimateDelay(closestLocationThresholdDistance));
            }
            if (proxLocations.size() == 0) // it could have changed in te previous code
                unscheduleLocationUpdates();
            LocationStore.saveLocations();
        }
        lastLocation = currentLocation;
        // save the location in case its memory gets freed
        SharedPreferences sharedPreferences = getSharedPreferences(
                getString(R.string.location_prefs_file), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong("lat", Double.doubleToRawLongBits(lastLocation.getLatitude())); //Wtf why
        editor.putLong("lon", Double.doubleToRawLongBits(lastLocation.getLongitude()));
        editor.apply();

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

    private void scheduleLocationUpdates(long time) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            if (time < MIN_DELAY) { // assume we want a one-off request
                unscheduleLocationUpdates();
                final LocationRequest request = new LocationRequest()
                        .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                        .setNumUpdates(1);
                LocationServices.FusedLocationApi.requestLocationUpdates(apiClient, request, requestCallback);
            } else {
                double percentChange = Math.abs(time-lastDelay)/(double)lastDelay;
                if (percentChange > 0.1) {
                    unscheduleLocationUpdates();
                    final LocationRequest request = new LocationRequest()
                            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                            .setInterval(time)
                            .setFastestInterval(time/2)
                            .setMaxWaitTime((long)(time*1.2));
                    LocationServices.FusedLocationApi.requestLocationUpdates(apiClient, request, requestCallback);
                    lastDelay = time;
                }
            }
        } else {
            // this activity will request permission when it opens
            Intent mainActivityIntent = new Intent(this, ProxAlertActivity.class);
            this.startActivity(mainActivityIntent);
        }
    }

    private void unscheduleLocationUpdates() {
        if (apiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(apiClient, requestCallback);
            lastDelay = 1L;
        } else if (!apiClient.isConnecting())
            apiClient.connect();
    }

    private long estimateDelay(double distance) {
        // extrapolate and halve
        double estimatedTime = distance/(ESTIMATED_VELOCITY*2);
        return (long)Math.max(MIN_DELAY, Math.min(MAX_DELAY, estimatedTime));
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        scheduleLocationUpdates(1000L);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }
}
