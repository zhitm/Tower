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
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
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
        if (Drone.currentLongPressState == Drone.LongPressState.NO_SELECTED) {
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
                                if (Drone.currentLongPressState == Drone.LongPressState.LOOK_AT) {
                                    ControlApi.getApi(drone).lookAt(new LatLongAlt(coord.latitude, coord.longitude, 0), false, null);         // LatLong != LatLng WTF?!
                                }
                                if (Drone.currentLongPressState == Drone.LongPressState.GOTO) {
                                    listener.onForcedGuidedPoint(coord);
                                }
                                if (Drone.currentLongPressState == Drone.LongPressState.NO_SELECTED) {

                                }
                                //
                            }
                        }
                    }).setNegativeButton(android.R.string.cancel, null);
        }
        if (Drone.currentLongPressState == Drone.LongPressState.LOOK_AT || Drone.currentLongPressState == Drone.LongPressState.GOTO) {
            if (coord != null) {
                drone = ((DroidPlannerApp) Objects.requireNonNull(getActivity()).getApplication()).getDrone();
                Type droneType = drone.getAttribute(AttributeType.TYPE);

                if (droneType.getDroneType() == Type.TYPE_COPTER) {
                    VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_GUIDED);

                    //Record the attempt to change flight modes
                    HitBuilders.EventBuilder eventBuilder = new HitBuilders.EventBuilder().setCategory(GAUtils.Category.FLIGHT).setAction("Flight mode changed").setLabel(String.valueOf(VehicleMode.COPTER_GUIDED));
                    GAUtils.sendEvent(eventBuilder);
                }
                if (Drone.currentLongPressState == Drone.LongPressState.LOOK_AT) {
                    ControlApi.getApi(drone).lookAt(new LatLongAlt(coord.latitude, coord.longitude, 0), false, null);         // LatLong != LatLng WTF?!
                }
                if (Drone.currentLongPressState == Drone.LongPressState.GOTO) {
                    listener.onForcedGuidedPoint(coord);
                }
                if (Drone.currentLongPressState == Drone.LongPressState.NO_SELECTED) {

                }
                //
            }
        }
        return builder.create();
    }


    @Override
    public void onPause() {
        super.onPause();
        dismiss();
    }
}