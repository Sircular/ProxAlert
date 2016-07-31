package org.sircular.proxalert.mapview;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.ViewTreeObserver;

import com.google.android.gms.location.places.Place;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.sircular.proxalert.LocationStore;
import org.sircular.proxalert.Manifest;
import org.sircular.proxalert.ProxAlertActivity;
import org.sircular.proxalert.ProxLocation;

import java.util.ArrayList;
import java.util.List;

public class ProxMapFragment extends SupportMapFragment implements LocationStore.UpdateListener {

    private List<ProxMapMarker> markers = new ArrayList<>();
    private GoogleMap map;
    private Marker searchMarker;

    @Override
    public void onViewCreated(final View view, Bundle bundle) {
        super.onViewCreated(view, bundle);
        // set up a convoluted listener scheme
        ViewTreeObserver observer = view.getViewTreeObserver();
        final ProxMapFragment mapParent = this; // reference for inside
        observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                mapParent.getMapAsync(new OnMapReadyCallback() {
                    @Override
                    public void onMapReady(GoogleMap googleMap) {
                        map = googleMap;
                        initMap(googleMap);
                    }
                });
            }
        });

        LocationStore.registerListener(this);

    }

    public void moveToMarker(int id) {
        for (ProxMapMarker marker : markers) {
            if (marker.getLocation().getId() == id) {
                CameraUpdate zoom = CameraUpdateFactory.newLatLngZoom(marker.getMarker().getPosition(),
                        calculateZoom(marker.getLocation().getRadius()));
                map.animateCamera(zoom);
                marker.getMarker().showInfoWindow();
                break;
            }
        }
    }

    public void moveToPlace(Place place) {
        CameraUpdate zoom;
        if (place.getViewport() != null) {
            zoom = CameraUpdateFactory.newLatLngBounds(place.getViewport(), 64);
        } else {
            zoom = CameraUpdateFactory.newLatLngZoom(place.getLatLng(), 16);
        }
        map.animateCamera(zoom);
        searchMarker.setPosition(place.getLatLng());
        searchMarker.setTitle(place.getName().toString());
        searchMarker.setVisible(true);
        searchMarker.showInfoWindow();
    }

    // calculates the optimal zoom level (seriously, a logarithmic factor Google??)
    private float calculateZoom(double radius) {
        double scale = radius / 100d;
        return (float) (16 - (Math.log(scale) / Math.log(2)));
    }

    private void initMap(GoogleMap map) {
        map.getUiSettings().setMapToolbarEnabled(false);
        map.getUiSettings().setZoomControlsEnabled(false);
        // initialize all markers and zoom to contain all of them
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (ProxLocation location : LocationStore.getLocations()) {
            ProxMapMarker marker = new ProxMapMarker(location, map);
            markers.add(marker);
            builder.include(marker.getMarker().getPosition());
        }
        if (markers.size() == 1) {
            moveToMarker(markers.get(0).getLocation().getId());
        } else if (markers.size() > 1) {
            LatLngBounds bounds = builder.build();
            CameraUpdate boundsMotion = CameraUpdateFactory.newLatLngBounds(bounds, 92);
            map.animateCamera(boundsMotion);
        }
        // set up the search marker
        MarkerOptions searchOptions = new MarkerOptions();
        searchOptions.alpha(0.8f);
        searchOptions.title("");
        searchOptions.position(new LatLng(0, 0));
        searchOptions.visible(false);
        searchMarker = map.addMarker(searchOptions);

        initializeListeners(map);

        // attempt to show location
        try {
            this.enableShowLocation();
        } catch (SecurityException e) {
            // this shouldn't happen
            // if it does, the service should take care of it.
        }
    }

    private void initializeListeners(GoogleMap map) {
        // marker click listener
        map.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                marker.showInfoWindow();
                ProxAlertActivity parent = (ProxAlertActivity) getActivity();
                ProxMapMarker proxMarker = getProxMarkerFromMapMarker(marker);
                if (proxMarker != null) {
                    parent.editLocation(proxMarker.getLocation());
                } else if (marker.equals(searchMarker)) {
                    LatLng position = searchMarker.getPosition();
                    parent.createLocation((float)position.latitude, (float)position.longitude);
                    searchMarker.setVisible(false);
                } else {
                    marker.remove(); // it's a trash marker
                }
                return true;
            }
        });

        map.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {}

            @Override
            public void onMarkerDrag(Marker marker) {}

            @Override
            public void onMarkerDragEnd(Marker marker) {
                ProxMapMarker proxMarker = getProxMarkerFromMapMarker(marker);
                if (proxMarker != null) {
                    LocationStore.modifyLocation(proxMarker.getLocation());
                } else {
                    marker.remove();
                }
            }
        });

        map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                ((ProxAlertActivity) getActivity()).createLocation((float) latLng.latitude,
                        (float) latLng.longitude);
            }
        });
    }

    public void enableShowLocation() throws SecurityException {
        map.setMyLocationEnabled(true);
    }

    private ProxMapMarker getProxMarkerFromMapMarker(Marker marker) {
        for (ProxMapMarker proxMarker : markers) {
            if (marker.equals(proxMarker.getMarker()))
                return proxMarker;
        }
        return null;
    }

    private ProxMapMarker getProxMarkerFromLocation(ProxLocation location) {
        for (ProxMapMarker proxMarker : markers) {
            if (location.equals(proxMarker.getLocation()))
                return proxMarker;
        }
        return null;
    }

    @Override
    public void onLocationUpdated(final LocationStore.UPDATE_TYPE type, final ProxLocation location) {
        final ProxMapFragment parent = this;
        this.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ProxMapMarker marker;
                switch (type) {
                    case ADDED:
                        markers.add(new ProxMapMarker(location, parent.map));
                        break;
                    case REMOVED:
                        marker = getProxMarkerFromLocation(location);
                        if (marker != null) {
                            marker.remove();
                            markers.remove(marker);
                        }
                        break;
                    case MODIFIED:
                        marker = getProxMarkerFromLocation(location);
                        if (marker != null) {
                            marker.update();
                        }
                        break;
                }
            }
        });

    }
}
