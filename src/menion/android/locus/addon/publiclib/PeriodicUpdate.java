package menion.android.locus.addon.publiclib;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Parcelable;

public class PeriodicUpdate {

	private Location mLastMapCenter;
	private Location mLastGps;
	private int mLastZoomLevel;
	
	private double mLocMinDistance;
	
	private static PeriodicUpdate mPU;
	public static PeriodicUpdate getInstance() {
		if (mPU == null) {
			mPU = new PeriodicUpdate();
		}
		return mPU;
	}
	
	private PeriodicUpdate() {
		this.mLocMinDistance = 1.0;
		this.mLastZoomLevel = -1;
	}
	
	/**
	 * Set notification limit used for check if distance between previous and
	 * new location is higher than this value. So new locations is market as NEW 
	 * @param minDistance distance in metres
	 */
	public void setLocNotificationLimit(double locMinDistance) {
		this.mLocMinDistance = locMinDistance;
	}
	
	public Location getLastMapCenter() {
		return mLastMapCenter;
	}
	
	public Location getLastGps() {
		return mLastGps;
	}
	
	public interface OnUpdate {
		
		public void onUpdate(UpdateContainer update);
		
		public void onIncorrectData();
	}
	
	public class UpdateContainer {
		
		// is map currently visible
		public boolean mapVisible = false;

		// is new map center available
		public boolean newMapCenter = false;
		// is new GPS location available
		public boolean newGps = false;
		// is new zoom level on map
		public boolean newZoomLevel;

		// current map zoom level (zoom 8 == whole world (1 tile 256x256px))
		public int mapZoomLevel;
		// location of top-left map corner
		public Location mapTopLeft = null;
		// location of bottom-right map corner
		public Location mapBottomRight = null;
		
		// is track recording enabled
		public boolean trackRecRecording = false;
		// if track rec is enabled, is running or paused
		public boolean trackRecPaused = false;
		// already recorded distance in metres
		public double trackRecDist = 0.0;
		// already recorded times in ms
		public long trackRecTime = 0L;
		// already recorded points
		public int trackRecPoints = 0;
	}
	
	public void onReceive(final Context context, Intent intent, OnUpdate handler) {
		if (context == null || intent == null || handler == null)
			throw new IllegalArgumentException("Incorrect arguments");
		
		if (!intent.getAction().equals(LocusConst.ACTION_PERIODIC_UPDATE)) {
			handler.onIncorrectData();
			return;
		}
		
		// print content of object, for debug only
//		Bundle extra = intent.getExtras();
//		Iterator<String> keys = extra.keySet().iterator();
//		while (keys.hasNext()) {
//			String key = keys.next();
//			Object object = extra.get(key);
//			Log.w("INFO", "key:" + key + ", obj:" + object);	
//		}

		UpdateContainer update = new UpdateContainer();
		
		// check VISIBILITY
		update.mapVisible = intent.getBooleanExtra(
				LocusConst.PUE_VISIBILITY_MAP_SCREEN, false);
		
		// check LOCATIONS
		update.newMapCenter = false;
		if (intent.hasExtra(LocusConst.PUE_LOCATION_MAP_CENTER)) {
			Location loc = intent.getParcelableExtra(LocusConst.PUE_LOCATION_MAP_CENTER);
			if (mLastMapCenter == null ||
					mLastMapCenter.distanceTo(loc) > mLocMinDistance) {
				mLastMapCenter = loc;
				update.newMapCenter = true;
			} 
		}

		update.newGps = false;
		if (intent.hasExtra(LocusConst.PUE_LOCATION_GPS)) {
			Location loc = intent.getParcelableExtra(LocusConst.PUE_LOCATION_GPS);
			if (mLastGps == null ||
					mLastGps.distanceTo(loc) > mLocMinDistance) {
				mLastGps = loc;
				update.newGps = true;
			} 
		}
		
		// check MAP
		update.mapZoomLevel = intent.getIntExtra(LocusConst.PUE_MAP_ZOOM_LEVEL, 0);
		update.newZoomLevel = update.mapZoomLevel != mLastZoomLevel;
		mLastZoomLevel = update.mapZoomLevel;

		if (intent.hasExtra(LocusConst.PUE_MAP_BOUNDING_BOX)) {
			// direct conversion not work, so use this hack
			Parcelable[] locs = intent.getParcelableArrayExtra(
					LocusConst.PUE_MAP_BOUNDING_BOX);
			
			if (locs != null && locs.length == 2) {
				update.mapTopLeft = (Location) locs[0];
				update.mapBottomRight = (Location) locs[1];
			}
		}
		
		// check TRACK RECORD
		update.trackRecRecording = intent.getBooleanExtra(
				LocusConst.PUE_ACTIVITY_TRACK_RECORD_RECORDING, false); 
		update.trackRecPaused = intent.getBooleanExtra(
				LocusConst.PUE_ACTIVITY_TRACK_RECORD_PAUSED, false);
		update.trackRecDist = intent.getDoubleExtra(
				LocusConst.PUE_ACTIVITY_TRACK_RECORD_DISTANCE, 0.0);
		update.trackRecTime = intent.getLongExtra(
				LocusConst.PUE_ACTIVITY_TRACK_RECORD_TIME, 0L);
		update.trackRecPoints = intent.getIntExtra(
				LocusConst.PUE_ACTIVITY_TRACK_RECORD_POINTS, 0);
		
		// send update back by handler
		handler.onUpdate(update);
	}
}
