package org.sircular.proxalert.mapview;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.sircular.proxalert.ProxLocation;

/**
 * Created by walt on 5/28/16.
 */
public class ProxMapMarker {
    private Marker marker;
    private Circle circle;

    private ProxLocation location;

    public ProxMapMarker(ProxLocation location, GoogleMap map) {
        this.location = location;

        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(new LatLng(location.getLatitude(), location.getLongitude()));
        markerOptions.title(location.getTitle());
        this.marker = map.addMarker(markerOptions);
        this.marker.showInfoWindow();

        CircleOptions circleOptions = new CircleOptions();
        circleOptions.center(markerOptions.getPosition());
        circleOptions.strokeColor(0xff05abff);
        circleOptions.fillColor(circleOptions.getStrokeColor() & 0x88ffffff);
        circleOptions.radius(location.getRadius());
        this.circle = map.addCircle(circleOptions);
    }

    public Marker getMarker() {
        return marker;
    }

    public ProxLocation getLocation() {
        return location;
    }

    public void update() {
        marker.setPosition(new LatLng(location.getLatitude(), location.getLongitude()));
        marker.setTitle(location.getTitle());
        circle.setCenter(marker.getPosition());
        circle.setRadius(location.getRadius());
    }

    public void remove() {
        marker.remove();
        circle.remove();
    }
}
