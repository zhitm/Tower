package org.droidplanner.android.proxy.mission.item.markers;

import com.o3dr.services.android.lib.drone.mission.item.spatial.Waypoint;

import org.droidplanner.android.R;
import org.droidplanner.android.proxy.mission.item.MissionItemProxy;

/**
 * This implements the marker source for a waypoint mission item.
 */
class WaypointMarkerInfo extends MissionItemMarkerInfo {

	protected WaypointMarkerInfo(MissionItemProxy origin) {
		super(origin);
	}

	@Override
	protected int getSelectedIconResource() {
		return R.drawable.ic_wp_map_selected;
	}

	@Override
	protected int getIconResource() {
		return R.drawable.ic_wp_map;
	}

}
