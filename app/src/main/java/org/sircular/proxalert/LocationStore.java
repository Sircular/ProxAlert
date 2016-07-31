package org.sircular.proxalert;

import android.content.Context;
import android.util.JsonReader;
import android.util.JsonWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by walt on 5/26/16.
 * Static singleton used to save and load locations
 */
public abstract class LocationStore {
    public enum UPDATE_TYPE {
        ADDED,
        REMOVED,
        MODIFIED
    }

    private static final String OUTPUT_FILE = "locations.json";
    private static Context context;

    private static ArrayList<ProxLocation> locations = null;
    private static LinkedList<UpdateListener> listeners = new LinkedList<>();

    public static void initialize(Context context) {
        LocationStore.context = context;
        loadLocations();
    }

    public synchronized static List<ProxLocation> getLocations() {
        if (locations == null) {
            loadLocations();
        }
        return Collections.unmodifiableList(locations);
    }

    public static ProxLocation getLocationById(int id) {
        for (ProxLocation loc : locations) {
            if (loc.getId() == id) {
                return loc;
            }
        }
        return null;
    }

    public static boolean registerListener(UpdateListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            return true;
        }
        return false;
    }

    public static boolean unregisterListener(UpdateListener listener) {
        return listeners.remove(listener);
    }

    public static void addLocation(ProxLocation location) {
        // make sure that the id does not clash
        int id = 0;
        if (locations.size() > 0)
            id = locations.get(locations.size()-1).getId()+1;
        ProxLocation newLoc = new ProxLocation(id, location);
        locations.add(newLoc);
        triggerUpdate(UPDATE_TYPE.ADDED, newLoc);
    }

    public static boolean removeLocation(ProxLocation location) {
        if (locations.remove(location)) {
            triggerUpdate(UPDATE_TYPE.REMOVED, location);
            return true;
        }
        return false;
    }

    // assumes that it has already been updated, and the listeners
    // just need to be notified
    public static void modifyLocation(ProxLocation location) {
        triggerUpdate(UPDATE_TYPE.MODIFIED, location);
    }

    public static boolean saveLocations() {
        try {
            FileOutputStream outputStream = context.openFileOutput(OUTPUT_FILE, Context.MODE_PRIVATE);
            JsonWriter writer = new JsonWriter(new OutputStreamWriter(outputStream, "UTF-8"));
            writer.setIndent("    ");
            writer.beginArray();
            for (ProxLocation location : locations) {
                writer.beginObject();
                writer.name("title").value(location.getTitle());
                writer.name("latitude").value(location.getLatitude());
                writer.name("longitude").value(location.getLongitude());
                writer.name("radius").value(location.getRadius());
                writer.name("recurring").value(location.isRecurring());
                writer.endObject();
            }
            writer.endArray();
            writer.close();
            outputStream.close();
            return true;
        } catch (FileNotFoundException e) {
            return false;
        } catch (UnsupportedEncodingException e) { // this should never happen
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static void triggerUpdate(UPDATE_TYPE type, ProxLocation location) {
        for (UpdateListener listener : listeners) {
            listener.onLocationUpdated(type, location);
        }
    }

    private static void loadLocations() {
        if (locations == null) {
            locations = new ArrayList<>();
        } else {
            locations.clear();
        }

        if (new File(context.getFilesDir(), OUTPUT_FILE).exists()) {
            try {
                FileInputStream inputStream = context.openFileInput(OUTPUT_FILE);
                JsonReader reader = new JsonReader(new InputStreamReader(inputStream, "UTF-8"));

                reader.beginArray();
                int id = 0;
                while (reader.hasNext()) {
                    ProxLocation builder = new ProxLocation(id, "", 0, 0, 0, false);
                    reader.beginObject();
                    while (reader.hasNext()) { // haskell programmers would have hemorrhages
                        String name = reader.nextName();
                        switch (name) {
                            case "title":
                                builder.setTitle(reader.nextString());
                                break;
                            case "latitude":
                                builder.setLatitude((float) reader.nextDouble());
                                break;
                            case "longitude":
                                builder.setLongitude((float) reader.nextDouble());
                                break;
                            case "radius":
                                builder.setRadius((float) reader.nextDouble());
                                break;
                            case "recurring":
                                builder.setRecurring(reader.nextBoolean());
                                break;
                            default:
                                reader.skipValue();
                        }
                    }
                    reader.endObject();
                    locations.add(builder);
                    id++;
                }
                reader.endArray();
                reader.close();
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public interface UpdateListener {

        void onLocationUpdated(UPDATE_TYPE type, ProxLocation location);

    }

}
