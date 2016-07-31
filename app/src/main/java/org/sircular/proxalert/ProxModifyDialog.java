package org.sircular.proxalert;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.util.Locale;

import javax.xml.datatype.Duration;

/**
 * Created by walt on 5/29/16.
 */
public class ProxModifyDialog extends Dialog {

    private final float MILES_TO_METERS = 1609.34f;
    private final float KM_TO_METERS = 1000f;

    // used to modify an existing point
    public ProxModifyDialog(Context context) {
        super(context);

        setContentView(R.layout.modify_dialog);
        Spinner distanceSpinner = (Spinner)findViewById(R.id.distance_spinner);
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item,
                context.getResources().getStringArray(R.array.distance_array));
        spinnerAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        distanceSpinner.setAdapter(spinnerAdapter);
    }

    public void createNewLocation(final float latitude, final float longitude) {
        setTitle(R.string.create_title);
        final ProxLocation location = new ProxLocation(0, "", latitude, longitude, 0, false);
        // initialize listeners
        Button saveButton = (Button)this.findViewById(R.id.save_button);
        saveButton.setText(R.string.create_btn);
        Button cancelButton = (Button)this.findViewById(R.id.cancel_button);

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancel();
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText titleText = (EditText)findViewById(R.id.title_text);
                EditText radiusText = (EditText)findViewById(R.id.radius_text);
                Spinner distanceSpinner = (Spinner)findViewById(R.id.distance_spinner);
                CheckBox recurringBox = (CheckBox)findViewById(R.id.recurring_box);

                // makre sure everything's filled out
                if (titleText.getText().toString().equals("") || radiusText.getText().toString().equals("")) {
                    Toast.makeText(getContext(), R.string.fill_out_fields_msg, Toast.LENGTH_LONG).show();
                    return;
                }

                location.setTitle(titleText.getText().toString());
                float intermediateRadius = Float.parseFloat(radiusText.getText().toString());
                getContext().getResources().getStringArray(R.array.distance_array);
                String measurementString =
                        getContext().getResources().getStringArray(R.array.distance_array)
                                [distanceSpinner.getSelectedItemPosition()];
                if (measurementString.toLowerCase().contains("mi"))  // hacky, I know
                    intermediateRadius *= MILES_TO_METERS;
                else
                    intermediateRadius *= KM_TO_METERS;
                location.setRadius(intermediateRadius);
                location.setRecurring(recurringBox.isChecked());
                LocationStore.addLocation(location);
                dismiss();
            }
        });

        show();
    }

    public void modifyLocation(final ProxLocation location) {
        setTitle(R.string.edit_title);
        // set up values
        EditText titleText = (EditText)findViewById(R.id.title_text);
        EditText radiusText = (EditText)findViewById(R.id.radius_text);
        Spinner distanceSpinner = (Spinner)findViewById(R.id.distance_spinner);
        CheckBox recurringBox = (CheckBox)findViewById(R.id.recurring_box);

        titleText.setText(location.getTitle());
        double intermediateRadius = location.getRadius();
        // determine whether we want miles or km
        String measurementString =
                getContext().getResources().getStringArray(R.array.distance_array)
                        [distanceSpinner.getSelectedItemPosition()];
        if (measurementString.toLowerCase().contains("mi"))  // hacky, I know
            intermediateRadius /= MILES_TO_METERS;
        else
            intermediateRadius /= KM_TO_METERS;
        radiusText.setText(String.format(Locale.getDefault(), "%.02f", intermediateRadius));
        recurringBox.setChecked(location.isRecurring());

        Button saveButton = (Button)this.findViewById(R.id.save_button);
        saveButton.setText(R.string.save_btn);
        Button cancelButton = (Button)this.findViewById(R.id.cancel_button);

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancel();
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText titleText = (EditText)findViewById(R.id.title_text);
                EditText radiusText = (EditText)findViewById(R.id.radius_text);
                Spinner distanceSpinner = (Spinner)findViewById(R.id.distance_spinner);
                CheckBox recurringBox = (CheckBox)findViewById(R.id.recurring_box);

                location.setTitle(titleText.getText().toString());
                float intermediateRadius = Float.parseFloat(radiusText.getText().toString());
                getContext().getResources().getStringArray(R.array.distance_array);
                String measurementString =
                        getContext().getResources().getStringArray(R.array.distance_array)
                                [distanceSpinner.getSelectedItemPosition()];
                if (measurementString.toLowerCase().contains("mi"))  // hacky, I know
                    intermediateRadius *= MILES_TO_METERS;
                else
                    intermediateRadius *= KM_TO_METERS;
                location.setRadius(intermediateRadius);
                location.setRecurring(recurringBox.isChecked());
                LocationStore.modifyLocation(location);
                dismiss();
            }
        });

        show();
    }
}
