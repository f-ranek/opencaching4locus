/*  
 * Copyright 2011, Asamm Software, s.r.o.
 * 
 * This file is part of LocusAddonPublicLib.
 * 
 * LocusAddonPublicLib is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * LocusAddonPublicLib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *  
 * You should have received a copy of the GNU General Public License
 * along with LocusAddonPublicLib.  If not, see <http://www.gnu.org/licenses/>.
 */

package menion.android.locus.addon.publiclib;

public class LocusConst {

	/* 
	 * these intent are used for extending functionality of Locus. All description is 
	 * in 'LocusIntents' class 
	 */
	public static final String INTENT_GET_LOCATION = "menion.android.locus.GET_POINT";
	public static final String INTENT_ON_POINT_ACTION = "menion.android.locus.ON_POINT_ACTION";
	public static final String INTENT_MAIN_FUNCTION = "menion.android.locus.MAIN_FUNCTION";
	
	/**
	 * Intent used for getting location from Locus to your application. This one, is used just to start
	 * Locus with this request.
	 * Available since Locus 1.15.4 (Pro 64, Free 126).
	 */
	public static final String ACTION_PICK_LOCATION = "android.intent.action.LOCUS_PICK_LOCATION";
	
	/**
	 * Action used for receiving Location from Locus
	 */
	public static final String ACTION_RECEIVE_LOCATION = "android.intent.action.ON_LOCATION_RECEIVE";

	/**
	 * Basic intent used for display data. Use API for creating intent and not directly
	 */
	public static final String INTENT_DISPLAY_DATA = "android.intent.action.LOCUS_PUBLIC_LIB_DATA";

	/* one PointData object, send over intent */
	public static final String EXTRA_POINTS_DATA = "EXTRA_POINTS_DATA";
	/* array of PointData objects, send over intent */
	public static final String EXTRA_POINTS_DATA_ARRAY = "EXTRA_POINTS_DATA_ARRAY";
	/* data stored in ContentProvider and send request as URI over intent */
	public static final String EXTRA_POINTS_CURSOR_URI = "EXTRA_POINTS_CURSOR_URI";
	/* sends points data serialized as byte[] through file stored on SD card */
	public static final String EXTRA_POINTS_FILE_PATH = "EXTRA_POINTS_FILE_PATH";
	
	/**
	 * Sends one single track to Locus
	 * Available since Locus 1.15.4 (Pro 64, Free 126).
	 */
	public static final String EXTRA_TRACKS_SINGLE = "EXTRA_TRACKS_SINGLE";
	
	/**
	 * Sends multiple tracks to Locus
	 * Available since Locus 1.16.3 (Pro 69, Free 131).
	 */
	public static final String EXTRA_TRACKS_MULTI = "EXTRA_TRACKS_MULTI";
	
	/**
	 * Extra parameter that set if data should be firstly imported. This is used in intent 
	 * that sends also 
	 */ 
	public static final String EXTRA_CALL_IMPORT = "EXTRA_CALL_IMPORT";
	
	/**
	 * If you set to any point "setExtraOnDisplay" callback, then when Locus display points and
	 * ask for extended version, return result as Point object included in extra place in intent
	 */
	public static final String EXTRA_POINT = "EXTRA_POINT";
	/** 
	 * Optional boolean value in returning intent. Settings to true, Locus will overwrite point
	 * in database. If you want to call "setExtraOnDisplay" next time, don't forget to set it
	 * in updated waypoint!
	 */
	public static final String EXTRA_POINT_OVERWRITE = "EXTRA_POINT_OVERWRITE";
	
	/**********************************/
	/*        BROADCAST PART          */
	/**********************************/
	
	/**
	 * Used for receiving locations from Locus
	 */
	public static final String ACTION_PERIODIC_UPDATE = "menion.android.locus.PERIODIC_UPDATE";

	// content of PERIODIC UPDATE event (PERIDIC_UPDATE_EXTRA)
	public static final String PUE_VISIBILITY_MAP_SCREEN = "visibility_map_screen";

	public static final String PUE_LOCATION_MAP_CENTER = "location_map_center";
	public static final String PUE_LOCATION_GPS = "location_gps";
	
	public static final String PUE_MAP_BOUNDING_BOX = "map_bounding_box";
	public static final String PUE_MAP_ZOOM_LEVEL = "map_zoom_level";
	
	public static final String PUE_ACTIVITY_TRACK_RECORD_RECORDING = "activity_track_record_recording";
	public static final String PUE_ACTIVITY_TRACK_RECORD_PAUSED = "activity_track_record_paused";
	public static final String PUE_ACTIVITY_TRACK_RECORD_DISTANCE = "activity_track_record_distance";
	public static final String PUE_ACTIVITY_TRACK_RECORD_TIME = "activity_track_record_time";
	public static final String PUE_ACTIVITY_TRACK_RECORD_POINTS = "activity_track_record_points";
	
	/**
	 * Used for sending data to Locus. These data should be small (and fast).
	 * 
	 * Content is same as with sending data by INTENT_DISPLAY_DATA anyway think mainly on best user
	 * experience!! So suggestion is to send intent filled by EXTRA_POINTS_DATA or EXTRA_POINTS_DATA_ARRAY.
	 * These are fastest methods and should be enough for sending useful amount of informations
	 * 
	 * Also, these data will have automatically EXTRA_CALL_IMPORT set to false, so they'll be directly
	 * displayed on map without possibility for import! Also after display, map will not be centered.  
	 */
	public static final String ACTION_DISPLAY_DATA_SILENTLY = "android.intent.action.DISPLAY_DATA_SILENTLY";
}
