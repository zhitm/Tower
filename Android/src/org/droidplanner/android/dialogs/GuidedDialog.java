package org.droidplanner.android.dialogs;

import org.droidplanner.android.DroidPlannerApp;
import org.droidplanner.android.R;
import org.droidplanner.android.fragments.actionbar.ActionBarTelemFragment;
import org.droidplanner.android.utils.analytics.GAUtils;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.maps.model.LatLng;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;

import java.util.Objects;

public class GuidedDialog extends DialogFragment {

    public interface GuidedDialogListener {
        void onForcedGuidedPoint(LatLng coord);
    }

    private Drone drone;

    private GuidedDialogListener listener;
    private LatLng coord;

    public void setCoord(LatLng coord) {
        this.coord = coord;
    }

    public void setListener(GuidedDialogListener mListener) {
        this.listener = mListener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setMessage(R.string.guided_mode_warning)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        if (coord != null) {
							drone = ((DroidPlannerApp) Objects.requireNonNull(getActivity()).getApplication()).getDrone();
							Type droneType = drone.getAttribute(AttributeType.TYPE);

							if (droneType.getDroneType() == Type.TYPE_COPTER) {
                                VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_GUIDED);

                                //Record the attempt to change flight modes
                                HitBuilders.EventBuilder eventBuilder = new HitBuilders.EventBuilder().setCategory(GAUtils.Category.FLIGHT).setAction("Flight mode changed").setLabel(String.valueOf(VehicleMode.COPTER_GUIDED));
                                GAUtils.sendEvent(eventBuilder);
                            }

							listener.onForcedGuidedPoint(coord);
                        }
                    }
                }).setNegativeButton(android.R.string.cancel, null);

        return builder.create();
    }


    @Override
    public void onPause() {
        super.onPause();
        dismiss();
    }
}