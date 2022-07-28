package org.droidplanner.android.fragments.control;

import static com.o3dr.services.android.lib.drone.mission.action.MissionActions.ACTION_GOTO_WAYPOINT;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.MAVLink.enums.MAV_GOTO;
import com.google.android.gms.analytics.HitBuilders;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.FollowApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeEventExtra;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.follow.FollowState;
import com.o3dr.services.android.lib.model.SimpleCommandListener;

import org.droidplanner.android.DroidPlannerApp;
import org.droidplanner.android.DroneState;
import org.droidplanner.android.R;
import org.droidplanner.android.activities.helpers.SuperUI;
import org.droidplanner.android.dialogs.SlideToUnlockDialog;
import org.droidplanner.android.dialogs.SupportYesNoDialog;
import org.droidplanner.android.dialogs.SupportYesNoWithPrefsDialog;
import org.droidplanner.android.proxy.mission.MissionProxy;
import org.droidplanner.android.utils.analytics.GAUtils;
import org.droidplanner.android.utils.prefs.DroidPlannerPrefs;

/**
 * Provide functionality for flight action button specific to copters.
 */
public class CopterFlightControlFragment extends BaseFlightControlFragment implements SupportYesNoDialog.Listener {

    private static final String ACTION_FLIGHT_ACTION_BUTTON = "Copter flight action button";

    private static final String DRONIE_CREATION_DIALOG_TAG = "Confirm dronie creation";

    private static final IntentFilter eventFilter = new IntentFilter();

    static {
        eventFilter.addAction(AttributeEvent.STATE_ARMING);
        eventFilter.addAction(AttributeEvent.STATE_CONNECTED);
        eventFilter.addAction(AttributeEvent.STATE_DISCONNECTED);
        eventFilter.addAction(AttributeEvent.STATE_UPDATED);
        eventFilter.addAction(AttributeEvent.STATE_VEHICLE_MODE);
        eventFilter.addAction(AttributeEvent.FOLLOW_START);
        eventFilter.addAction(AttributeEvent.FOLLOW_STOP);
        eventFilter.addAction(AttributeEvent.FOLLOW_UPDATE);
        eventFilter.addAction(AttributeEvent.MISSION_DRONIE_CREATED);
        eventFilter.addAction(AttributeEvent.SOUND_SERVO_STATE_UPDATE);
    }

    private final BroadcastReceiver eventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case AttributeEvent.STATE_ARMING:
                case AttributeEvent.STATE_CONNECTED:
                case AttributeEvent.STATE_DISCONNECTED:
                case AttributeEvent.STATE_UPDATED:
                    setupButtonsByFlightState();

                    break;

                case AttributeEvent.STATE_VEHICLE_MODE:
                    updateFlightModeButtons();
                    break;

                case AttributeEvent.FOLLOW_START:
                case AttributeEvent.FOLLOW_STOP:
                    final FollowState followState = getDrone().getAttribute(AttributeType.FOLLOW_STATE);
                    if (followState != null) {
                        String eventLabel = null;
                        switch (followState.getState()) {
                            case FollowState.STATE_START:
                                eventLabel = "FollowMe enabled";
                                break;

                            case FollowState.STATE_RUNNING:
                                eventLabel = "FollowMe running";
                                break;

                            case FollowState.STATE_END:
                                eventLabel = "FollowMe disabled";
                                break;

                            case FollowState.STATE_INVALID:
                                eventLabel = "FollowMe error: invalid state";
                                break;

                            case FollowState.STATE_DRONE_DISCONNECTED:
                                eventLabel = "FollowMe error: drone not connected";
                                break;

                            case FollowState.STATE_DRONE_NOT_ARMED:
                                eventLabel = "FollowMe error: drone not armed";
                                break;
                        }

                        if (eventLabel != null) {
                            HitBuilders.EventBuilder eventBuilder = new HitBuilders.EventBuilder()
                                    .setCategory(GAUtils.Category.FLIGHT)
                                    .setAction(ACTION_FLIGHT_ACTION_BUTTON)
                                    .setLabel(eventLabel);
                            GAUtils.sendEvent(eventBuilder);

                            Toast.makeText(getActivity(), eventLabel, Toast.LENGTH_SHORT).show();
                        }
                    }

                    /* FALL - THROUGH */
                case AttributeEvent.FOLLOW_UPDATE:
                    updateFlightModeButtons();
                    //updateFollowButton();
                    updateGuidedModeButtons();
                    break;

                case AttributeEvent.MISSION_DRONIE_CREATED:
                    //Get the bearing of the dronie mission.
                    float bearing = intent.getFloatExtra(AttributeEventExtra.EXTRA_MISSION_DRONIE_BEARING, -1);
                    if (bearing >= 0) {
                        final FlightControlManagerFragment parent = (FlightControlManagerFragment) getParentFragment();
                        if (parent != null) {
                            parent.updateMapBearing(bearing);
                        }
                    }
                    break;
                case AttributeEvent.SOUND_SERVO_STATE_UPDATE:
                    updateSoundButton();
                    break;
            }
        }
    };

    private MissionProxy missionProxy;

    private View mDisconnectedButtons;
    private View mDisarmedButtons;
    private View mArmedButtons;
    private View mInFlightButtons;

    private Button gotoBtn;
    private Button homeBtn;
    private Button landBtn;
    private Button pauseBtn;
    private Button autoBtn;
    private Button lookAt;
    private Button soundBtn;
    private Button soundBtn2;
    private Button showCordsBtn;
    private Button upTo60m;

    private int orangeColor;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_copter_mission_control, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        int isVisibleInDroneMode = DroidPlannerApp.droneState == DroneState.UsualDrone ? View.VISIBLE : View.GONE;
        int isVisibleInAntennaMode = DroidPlannerApp.droneState == DroneState.UsualDrone ? View.GONE : View.VISIBLE;

        orangeColor = getResources().getColor(R.color.orange);

        mDisconnectedButtons = view.findViewById(R.id.mc_disconnected_buttons);
        mDisarmedButtons = view.findViewById(R.id.mc_disarmed_buttons);
        mArmedButtons = view.findViewById(R.id.mc_armed_buttons);
        mInFlightButtons = view.findViewById(R.id.mc_in_flight_buttons);

        final View connectBtn = view.findViewById(R.id.mc_connectBtn);
        connectBtn.setOnClickListener(this);

        homeBtn = (Button) view.findViewById(R.id.mc_homeBtn);
        homeBtn.setOnClickListener(this);
        homeBtn.setVisibility(isVisibleInDroneMode);

        final Button armBtn = (Button) view.findViewById(R.id.mc_armBtn);
        armBtn.setOnClickListener(this);

        final Button disarmBtn = (Button) view.findViewById(R.id.mc_disarmBtn);
        disarmBtn.setOnClickListener(this);

        landBtn = (Button) view.findViewById(R.id.mc_land);
        landBtn.setOnClickListener(this);

        final Button takeoffBtn = (Button) view.findViewById(R.id.mc_takeoff);
        takeoffBtn.setOnClickListener(this);

        pauseBtn = (Button) view.findViewById(R.id.mc_pause);
        pauseBtn.setOnClickListener(this);

        autoBtn = (Button) view.findViewById(R.id.mc_autoBtn);
        autoBtn.setOnClickListener(this);
        autoBtn.setVisibility(isVisibleInDroneMode);

        final Button takeoffInAuto = (Button) view.findViewById(R.id.mc_TakeoffInAutoBtn);
        takeoffInAuto.setOnClickListener(this);
        takeoffInAuto.setVisibility(isVisibleInDroneMode);

        gotoBtn = (Button) view.findViewById(R.id.mc_goto);
        gotoBtn.setOnClickListener(this);
        gotoBtn.setVisibility(isVisibleInDroneMode);

        upTo60m = (Button) view.findViewById(R.id.mc_goto_60m);
        upTo60m.setOnClickListener(this);
        upTo60m.setVisibility(isVisibleInAntennaMode);

        lookAt = (Button) view.findViewById(R.id.mc_lookAt);
        lookAt.setOnClickListener(this);

        final Button dronieBtn = (Button) view.findViewById(R.id.mc_dronieBtn);
        dronieBtn.setOnClickListener(this);

        soundBtn = (Button) view.findViewById(R.id.mc_change_voice);
        soundBtn.setOnClickListener(this);
        soundBtn.setVisibility(isVisibleInDroneMode);

        showCordsBtn = (Button) view.findViewById(R.id.mc_show_coords);
        showCordsBtn.setOnClickListener(this);
        showCordsBtn.setVisibility(isVisibleInDroneMode);

        soundBtn2 = (Button) view.findViewById(R.id.mc_change_voice2);
        soundBtn2.setOnClickListener(this);
        soundBtn2.setVisibility(isVisibleInDroneMode);

    }

    @Override
    public void onApiConnected() {
        super.onApiConnected();
        missionProxy = getMissionProxy();

        setupButtonsByFlightState();
        updateFlightModeButtons();
        //updateFollowButton();
        //updateGotoButton();
        updateGuidedModeButtons();
        getBroadcastManager().registerReceiver(eventReceiver, eventFilter);
    }

    @Override
    public void onApiDisconnected() {
        super.onApiDisconnected();
        getBroadcastManager().unregisterReceiver(eventReceiver);
    }

    @Override
    public void onClick(View v) {
        HitBuilders.EventBuilder eventBuilder = new HitBuilders.EventBuilder()
                .setCategory(GAUtils.Category.FLIGHT);

        final Drone drone = getDrone();
        switch (v.getId()) {
            case R.id.mc_connectBtn:
                ((SuperUI) getActivity()).toggleDroneConnection();
                break;

            case R.id.mc_armBtn:
                VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_STABILIZE);
                getArmingConfirmation();
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel("Arm");
                break;

            case R.id.mc_disarmBtn:
                VehicleApi.getApi(drone).arm(false);
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel("Disarm");
                break;

            case R.id.mc_land:
                VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_LAND);
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel(VehicleMode
                        .COPTER_LAND.getLabel());
                break;

            case R.id.mc_takeoff:
                getTakeOffConfirmation();
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel("Takeoff");
                break;

            case R.id.mc_homeBtn:
                VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_RTL);
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel(VehicleMode.COPTER_RTL
                        .getLabel());
                break;

            case R.id.mc_pause: {
                final FollowState followState = drone.getAttribute(AttributeType.FOLLOW_STATE);
                if (followState.isEnabled()) {
                    FollowApi.getApi(drone).disableFollowMe();
                }

                //VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_GUIDED);
                ControlApi.getApi(drone).pauseAtCurrentLocation(null);
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel("Pause");
                VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_BRAKE);
                break;
            }

            case R.id.mc_autoBtn:
                VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_AUTO);
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel(VehicleMode.COPTER_AUTO.getLabel());
                break;

            case R.id.mc_TakeoffInAutoBtn:
                getTakeOffInAutoConfirmation();
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel(VehicleMode.COPTER_AUTO.getLabel());
                break;

            case R.id.mc_goto:
                Drone.currentLongPressState = Drone.currentLongPressState == Drone.LongPressState.GOTO? Drone.LongPressState.NO_SELECTED : Drone.LongPressState.GOTO;
                //drone.lookAtMode = !drone.lookAtMode;
                //System.out.println("drone.lookAtMode changed to "+drone.lookAtMode);
                updateGuidedModeButtons();
                //toggleFollowMe();
                break;
            case R.id.mc_goto_60m:
                Toast.makeText(v.getContext(), "Not implemented yet", Toast.LENGTH_SHORT).show();
//                ControlApi.getApi(getDrone()).goTo(new LatLong(10,10), true, null);
                break;
            case R.id.mc_lookAt:
                Drone.currentLongPressState = Drone.currentLongPressState == Drone.LongPressState.LOOK_AT? Drone.LongPressState.NO_SELECTED : Drone.LongPressState.LOOK_AT;
                //drone.lookAtMode = !drone.lookAtMode;
                //System.out.println("drone.lookAtMode changed to "+drone.lookAtMode);
                updateGuidedModeButtons();
                break;
                //toggleFollowMe();
            case R.id.mc_dronieBtn:
                getDronieConfirmation();
                eventBuilder.setAction(ACTION_FLIGHT_ACTION_BUTTON).setLabel("Dronie uploaded");
                break;
            case R.id.mc_change_voice:
            case R.id.mc_change_voice2:
                if (Drone.soundEnabled) {
                    VehicleApi.getApi(drone).setServo(6, 0, null);
                }
                else{
                    VehicleApi.getApi(drone).setServo(6, 20000, null);
                }
                updateSoundButton();
                break;
            case R.id.mc_show_coords:

                final Gps droneGps = drone.getAttribute(AttributeType.GPS);
                if (droneGps.isValid()) {
                    String text = "GPS: " + droneGps.getPosition().getLatitude() +" "+droneGps.getPosition().getLongitude();
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("TAG",text);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(getActivity(), text + ".\n Скопировано в буфер обмена." , Toast.LENGTH_LONG).show();
                }
                break;
            default:
                eventBuilder = null;
                break;
        }

        if (eventBuilder != null) {
            GAUtils.sendEvent(eventBuilder);
        }

    }

    private void getDronieConfirmation() {
        SupportYesNoWithPrefsDialog ynd = SupportYesNoWithPrefsDialog.newInstance(getActivity()
                        .getApplicationContext(), DRONIE_CREATION_DIALOG_TAG,
                getString(R.string.pref_dronie_creation_title),
                getString(R.string.pref_dronie_creation_message), DroidPlannerPrefs.PREF_WARN_ON_DRONIE_CREATION, this);

        if (ynd != null) {
            ynd.show(getChildFragmentManager(), DRONIE_CREATION_DIALOG_TAG);
        }
    }

    private void getTakeOffConfirmation(double altitude){
        final SlideToUnlockDialog unlockDialog = SlideToUnlockDialog.newInstance("take off", new Runnable() {
            @Override
            public void run() {
                ControlApi.getApi(getDrone()).takeoff(altitude, null);
            }
        });
        unlockDialog.show(getChildFragmentManager(), "Slide to take off");
    }

    private void getTakeOffConfirmation() {
        final double takeOffAltitude = getAppPrefs().getDefaultAltitude();
        getTakeOffConfirmation(takeOffAltitude);
//        final SlideToUnlockDialog unlockDialog = SlideToUnlockDialog.newInstance("take off", new Runnable() {
//            @Override
//            public void run() {
//                final double takeOffAltitude = getAppPrefs().getDefaultAltitude();
//                ControlApi.getApi(getDrone()).takeoff(takeOffAltitude, null);
//            }
//        });
//        unlockDialog.show(getChildFragmentManager(), "Slide to take off");
    }

    private void getTakeOffInAutoConfirmation() {
        final SlideToUnlockDialog unlockDialog = SlideToUnlockDialog.newInstance("take off in auto", new Runnable() {
            @Override
            public void run() {

                final double takeOffAltitude = getAppPrefs().getDefaultAltitude();

                final Drone drone = getDrone();
                ControlApi.getApi(drone).takeoff(takeOffAltitude, new SimpleCommandListener() {
                    @Override
                    public void onSuccess() {
                        VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_AUTO);
                    }
                });
            }
        });
        unlockDialog.show(getChildFragmentManager(), "Slide to take off in auto");
    }

    private void getArmingConfirmation() {
        SlideToUnlockDialog unlockDialog = SlideToUnlockDialog.newInstance("arm", new Runnable() {
            @Override
            public void run() {
                VehicleApi.getApi(getDrone()).arm(true);
            }
        });
        unlockDialog.show(getChildFragmentManager(), "Slide To Arm");
    }

    private void updateFlightModeButtons() {
        resetFlightModeButtons();

        State droneState = getDrone().getAttribute(AttributeType.STATE);
        if (droneState == null)
            return;

        final VehicleMode flightMode = droneState.getVehicleMode();
        if (flightMode == null)
            return;

        pauseBtn.setBackgroundResource(R.drawable.flight_action_row_bg_selector);
        pauseBtn.setActivated(false);
        switch (flightMode) {
            case COPTER_AUTO:
                autoBtn.setActivated(true);
                break;

            case COPTER_BRAKE:
                pauseBtn.setActivated(true);
                break;
            case COPTER_GUIDED:
                pauseBtn.setBackgroundColor(orangeColor);
                break;

            case COPTER_RTL:
                homeBtn.setActivated(true);
                break;

            case COPTER_LAND:
                landBtn.setActivated(true);
                break;
            default:
                break;
        }

        updateSoundButton();
    }

    private void updateSoundButton(){
        soundBtn.setBackgroundResource(R.drawable.flight_action_row_bg_selector);
        soundBtn.setActivated(false);
        soundBtn2.setBackgroundResource(R.drawable.flight_action_row_bg_selector);
        soundBtn2.setActivated(false);
        if (Drone.soundEnabled) {
            soundBtn.setBackgroundColor(orangeColor);
            soundBtn2.setBackgroundColor(orangeColor);
        }
    }

    private void resetFlightModeButtons() {
        homeBtn.setActivated(false);
        landBtn.setActivated(false);
        pauseBtn.setActivated(false);
        autoBtn.setActivated(false);
    }

    private void updateGuidedModeButtons(){
        updateGoToButton();
        updateLookAtButton();
    }

    private void updateGoToButton(){
        final Drone drone = getDrone();

        if (Drone.currentLongPressState == Drone.LongPressState.GOTO){
            //VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_GUIDED);
            gotoBtn.setBackgroundColor(orangeColor);
        }
        else{
            gotoBtn.setActivated(false);
            gotoBtn.setBackgroundResource(R.drawable.flight_action_row_bg_selector);
        }
    }

    private void updateLookAtButton(){
        final Drone drone = getDrone();

        if (Drone.currentLongPressState == Drone.LongPressState.LOOK_AT){
            //VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_GUIDED);
            lookAt.setBackgroundColor(orangeColor);
        }
        else{
            lookAt.setActivated(false);
            lookAt.setBackgroundResource(R.drawable.flight_action_row_bg_selector);
        }
    }

    private void updateFollowButton() {
        FollowState followState = getDrone().getAttribute(AttributeType.FOLLOW_STATE);
        if (followState == null)
            return;

        switch (followState.getState()) {
            case FollowState.STATE_START:
                gotoBtn.setBackgroundColor(orangeColor);
                break;

            case FollowState.STATE_RUNNING:
                gotoBtn.setActivated(true);
                gotoBtn.setBackgroundResource(R.drawable.flight_action_row_bg_selector);
                break;

            default:
                gotoBtn.setActivated(false);
                gotoBtn.setBackgroundResource(R.drawable.flight_action_row_bg_selector);
                break;
        }
        updateGuidedModeButtons();
    }

    private void resetButtonsContainerVisibility() {
        mDisconnectedButtons.setVisibility(View.GONE);
        mDisarmedButtons.setVisibility(View.GONE);
        mArmedButtons.setVisibility(View.GONE);
        mInFlightButtons.setVisibility(View.GONE);
    }

    private void setupButtonsByFlightState() {
        final State droneState = getDrone().getAttribute(AttributeType.STATE);
        if (droneState != null && droneState.isConnected()) {
            if (droneState.isArmed()) {
                if (droneState.isFlying()) {
                    setupButtonsForFlying();
                } else {
                    setupButtonsForArmed();
                }
            } else {
                setupButtonsForDisarmed();
            }
        } else {
            setupButtonsForDisconnected();
        }
    }

    private void setupButtonsForDisconnected() {
        resetButtonsContainerVisibility();
        mDisconnectedButtons.setVisibility(View.VISIBLE);
    }

    private void setupButtonsForDisarmed() {
        resetButtonsContainerVisibility();
        mDisarmedButtons.setVisibility(View.VISIBLE);
    }

    private void setupButtonsForArmed() {
        resetButtonsContainerVisibility();
        mArmedButtons.setVisibility(View.VISIBLE);
    }

    private void setupButtonsForFlying() {
        resetButtonsContainerVisibility();
        mInFlightButtons.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean isSlidingUpPanelEnabled(Drone drone) {
        if (!drone.isConnected())
            return false;

        final State droneState = drone.getAttribute(AttributeType.STATE);
        return droneState.isArmed() && droneState.isFlying();
    }

    @Override
    public void onDialogYes(String dialogTag) {
        switch (dialogTag) {
            case DRONIE_CREATION_DIALOG_TAG:
                missionProxy.makeAndUploadDronie(getDrone());
                break;
        }
    }

    @Override
    public void onDialogNo(String dialogTag) {

    }
}
