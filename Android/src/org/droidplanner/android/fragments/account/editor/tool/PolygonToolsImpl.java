package org.droidplanner.android.fragments.account.editor.tool;

import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.drone.mission.MissionItemType;
import com.o3dr.services.android.lib.drone.mission.item.complex.Survey;
import com.o3dr.services.android.lib.drone.mission.item.spatial.BaseSpatialItem;

import org.droidplanner.android.R;
import org.droidplanner.android.proxy.mission.item.MissionItemProxy;
import org.droidplanner.services.android.impl.core.mission.MissionImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Fredia Huya-Kouadio on 8/25/15.
 */
public class PolygonToolsImpl extends EditorToolsImpl implements AdapterView.OnItemSelectedListener {

    static final MissionItemType[] POLYGON_ITEMS_TYPE = {
            MissionItemType.SURVEY,
            MissionItemType.SPLINE_SURVEY
    };

    private final static String EXTRA_SELECTED_POLYGON_MISSION_ITEM_TYPE = "extra_selected_polygon_mission_item_type";
    private final static String EXTRA_SELECTED_POLYGON_ID = "extra_selected_polygon_id";
    private final static String EXTRA_SELECTED_POLYGON_ID_GENERATOR = "extra_selected_polygon_id_generator";
    private final static String EXTRA_SELECTED_POLYGON_POINTS = "extra_selected_polygon_points";


    private MissionItemType selectedType = POLYGON_ITEMS_TYPE[0];

    private static List<LatLong> polygonPoints = new ArrayList<>();

    private static int polygonId;

    private static AtomicInteger idGenerator = new AtomicInteger(0);

    public static int getPolygonId() {
        return polygonId;
    }

    PolygonToolsImpl(EditorToolsFragment fragment) {
        super(fragment);
    }

    void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedType != null) {
            outState.putString(EXTRA_SELECTED_POLYGON_MISSION_ITEM_TYPE, selectedType.name());
            outState.putInt(EXTRA_SELECTED_POLYGON_ID, polygonId);
            outState.putInt(EXTRA_SELECTED_POLYGON_ID_GENERATOR, idGenerator.get());
        }
        if (polygonPoints != null) {
            outState.putParcelableArrayList(EXTRA_SELECTED_POLYGON_POINTS, (ArrayList<? extends Parcelable>) polygonPoints);
        }
    }

    void onRestoreInstanceState(Bundle savedState) {
        super.onRestoreInstanceState(savedState);
        final String selectedTypeName = savedState.getString(EXTRA_SELECTED_POLYGON_MISSION_ITEM_TYPE,
                POLYGON_ITEMS_TYPE[0].name());
        selectedType = MissionItemType.valueOf(selectedTypeName);
        polygonId = savedState.getInt(EXTRA_SELECTED_POLYGON_ID, 0);
        idGenerator.set(savedState.getInt(EXTRA_SELECTED_POLYGON_ID_GENERATOR, 0));
        polygonPoints = new ArrayList<>(savedState.getParcelableArrayList(EXTRA_SELECTED_POLYGON_POINTS));
    }

    @Override
    public void onSelectionUpdate(List<MissionItemProxy> selected) {
        super.onSelectionUpdate(selected);
        if (selected == null)
            return;

        selected.stream()
                .filter(proxy -> proxy.getMissionItem() instanceof Survey)
                .map(proxy -> (Survey) proxy.getMissionItem())
                .findAny().ifPresent(PolygonToolsImpl::selectedSurveySetup);
    }

    @Override
    public EditorToolsFragment.EditorTools getEditorTools() {
        return EditorToolsFragment.EditorTools.POLYGON;
    }

    @Override
    public void onMapClick(LatLong point) {
        if (missionProxy == null) return;

        // If an mission item is selected, unselect it.
        missionProxy.selection.clearSelection();

        if (selectedType == null)
            return;

        polygonPoints.add(point);

//        BaseSpatialItem spatialItem = (BaseSpatialItem) selectedType.getNewItem();
//        missionProxy.addSpatialWaypoint(spatialItem, point);

        if (missionProxy != null && polygonPoints.size() > 0) {
            switch (selectedType) {
                case SURVEY:
//                    if (polygonPoints.size() > 2) {
                    missionProxy.addSurveyPolygon(polygonPoints, false, polygonId);
//                    } else {
//                        editorToolsFragment.setTool(EditorToolsFragment.EditorTools.POLYGON);
//                        return;
//                    }
                    break;

                case SPLINE_SURVEY:
                    if (polygonPoints.size() > 2) {
                        missionProxy.addSurveyPolygon(polygonPoints, true, polygonId);
                    } else {
                        editorToolsFragment.setTool(EditorToolsFragment.EditorTools.POLYGON);
                        return;
                    }
                    break;
            }
        }
//        editorToolsFragment.setTool(EditorToolsFragment.EditorTools.NONE);
    }


    @Override
    public void setup() {
        EditorToolsFragment.EditorToolListener listener = editorToolsFragment.listener;
        if (listener != null) {
            listener.enableGestureDetection(false);
        }

        if (missionProxy != null)
            missionProxy.selection.clearSelection();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        selectedType = (MissionItemType) parent.getItemAtPosition(position);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        selectedType = POLYGON_ITEMS_TYPE[0];
    }

    MissionItemType getSelected() {
        return selectedType;
    }

    public static void selectedSurveySetup(Survey survey) {
        polygonId = survey.getPolygonId();
        polygonPoints = survey.getPolygonPoints();
    }

    public static void reset() {
        polygonId = idGenerator.getAndIncrement();
        polygonPoints.clear();
    }

    public static boolean shouldEnableZoomToFit(){
        return !(polygonPoints.size() > 0 && polygonPoints.size() < 3);
    }
}
