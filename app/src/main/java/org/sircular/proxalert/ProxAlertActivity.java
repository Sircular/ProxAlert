package org.sircular.proxalert;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.location.places.ui.SupportPlaceAutocompleteFragment;

import org.sircular.proxalert.background.LocationService;
import org.sircular.proxalert.mapview.ProxMapFragment;

public class ProxAlertActivity extends AppCompatActivity {

    private ActionBarDrawerToggle toggle;
    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LocationStore.initialize(this);
        setContentView(R.layout.activity_prox_alert);
        // initialize the actionbar
        Toolbar toolbar = (Toolbar) this.findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = (DrawerLayout) this.findViewById(R.id.drawer_layout);
        if (drawerLayout == null)
            throw new NullPointerException();
        toggle = new ActionBarDrawerToggle(this, drawerLayout,
                R.string.access_show_locations, R.string.access_hide_locations);
        drawerLayout.addDrawerListener(toggle);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        // set up search bar listener
        final ProxAlertActivity parent = this;
        SupportPlaceAutocompleteFragment searchBar =
                (SupportPlaceAutocompleteFragment)getSupportFragmentManager()
                        .findFragmentById(R.id.places_search);
        searchBar.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                ProxMapFragment mapFragment = (ProxMapFragment)getSupportFragmentManager().findFragmentById(R.id.map_fragment);
                mapFragment.moveToPlace(place);
            }

            @Override
            public void onError(Status status) {
                // TODO
            }
        });

    }

    @Override
    protected void onPostCreate(Bundle bundle) {
        super.onPostCreate(bundle);
        toggle.syncState();
        if (!isLocationServiceRunning()) {
            Intent locationServiceIntent = new Intent(this.getBaseContext(), LocationService.class);
            this.getBaseContext().startService(locationServiceIntent);
        }
        requestLocationPermission();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        toggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onPause() {
        super.onPause();
        LocationStore.saveLocations();
    }

    @Override
    public void onStop() {
        super.onStop();
        LocationStore.saveLocations();
    }

    public void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                final Activity parent = this; // good god, Haskell programmers would have aneurisms
                builder.setMessage(R.string.use_location_msg);
                builder.setPositiveButton(R.string.ok_btn, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(parent, new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION
                        }, 0);
                    }
                });
                builder.create().show();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, 0);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 0) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ProxMapFragment mapFragment = (ProxMapFragment)getSupportFragmentManager().findFragmentById(R.id.map_fragment);
                mapFragment.enableShowLocation();
            }
        }
    }

    // communication methods
    public void moveMapToLocation(int id) {
        ProxMapFragment mapFragment = (ProxMapFragment)getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        mapFragment.moveToMarker(id);
    }

    public void createLocation(float latitude, float longitude) {
        ProxModifyDialog dialog = new ProxModifyDialog(this);
        dialog.createNewLocation(latitude, longitude);
    }

    public void editLocation(final ProxLocation location) {
        // create an edit/delete/cancel dialog
        final ProxAlertActivity parent = this;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.edit_marker);
        builder.setNeutralButton(R.string.cancel_btn, null);
        builder.setNegativeButton(R.string.delete_marker_btn, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                LocationStore.removeLocation(location);
            }
        });
        builder.setPositiveButton(R.string.edit_marker_btn, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ProxModifyDialog modifyDialog = new ProxModifyDialog(parent);
                modifyDialog.modifyLocation(location);
            }
        });
        builder.create().show();
    }

    private boolean isLocationServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo info : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (info.service.getClassName().equals(LocationService.class.getName())) {
                return true;
            }
        }
        return false;
    }
}
