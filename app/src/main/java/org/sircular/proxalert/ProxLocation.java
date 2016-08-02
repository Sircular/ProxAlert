package org.sircular.proxalert;

import android.location.Location;

/**
 * Created by walt on 5/26/16.
 */
public class ProxLocation {
    private int id;
    private String title;
    private Location location;
    private double radius; // in meters
    private boolean recurring;

    public ProxLocation(int id, String title, float latitude, float longitude, float radius, boolean recurring) {
        this.id = id;
        this.title = title;
        this.location = new Location("");
        this.location.setLatitude(latitude);
        this.location.setLongitude(longitude);
        this.radius = radius;
        this.recurring = recurring;
    }

    public ProxLocation(int id, ProxLocation location) {
        this.id = id;
        this.title = location.getTitle();
        this.location = new Location("");
        this.location.setLatitude(location.getLatitude());
        this.location.setLongitude(location.getLongitude());
        this.radius = location.getRadius();
        this.recurring = location.isRecurring();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Location getLocation() {
        return this.location;
    }

    public double getLatitude() {
        return location.getLatitude();
    }

    public void setLatitude(double latitude) {
        location.setLatitude(latitude);
    }

    public double getLongitude() {
        return location.getLongitude();
    }

    public void setLongitude(double longitude) {
        location.setLongitude(longitude);
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return title;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof ProxLocation) {
            ProxLocation other = (ProxLocation) object;
            return this.title.equals(other.getTitle()) &&
                    this.id == other.getId();
        }
        return false;
    }
}
