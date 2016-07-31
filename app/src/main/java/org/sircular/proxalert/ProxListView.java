package org.sircular.proxalert;

import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class ProxListView extends ListFragment {

    private ArrayAdapter<ProxLocation> locationAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                Bundle savedInstanceState) {
        //this.setEmptyText(getString(R.string.no_locations_text));

        /* this is synchronous IO, but the store is reading a single JSON file.
         * It may take as much as a second longer to start up. This can be remedied
         * if need be. */
        locationAdapter = new ArrayAdapter<ProxLocation>(getActivity(), android.R.layout.simple_list_item_1,
                    LocationStore.getLocations());
        locationAdapter.setNotifyOnChange(true);
        locationAdapter.notifyDataSetChanged();
        this.setListAdapter(locationAdapter);



        return inflater.inflate(android.R.layout.list_content, container, false);
    }

    @Override
    public void onListItemClick(ListView l, View view, int position, long id) {
        ProxAlertActivity parent = (ProxAlertActivity)getActivity();
        parent.moveMapToLocation(this.locationAdapter.getItem(position).getId());
        // close the drawer
        DrawerLayout drawer = (DrawerLayout) parent.findViewById(R.id.drawer_layout);
        if (drawer != null)
            drawer.closeDrawer(GravityCompat.START);
    }
}
