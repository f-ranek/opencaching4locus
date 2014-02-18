package locus.api.android.utils;

import locus.api.android.ActionTools;
import locus.api.objects.extra.Location;
import locus.api.objects.extra.Waypoint;
import locus.api.utils.Logger;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;

/**
 * Changes against @version 4e92fabf7cf9 
 */
public class LocusUtils {
	
	private static final String TAG = "LocusUtils";
	
	/**************************************************/
	/*                 CHECKING PART                  */
	/**************************************************/
	
	/**
	 * Locus Pro package name
	 */
	private static final String LOCUS_PRO_PACKAGE_NAME = "menion.android.locus.pro";
	/**
	 * Locus Free package name
	 */
	private static final String LOCUS_FREE_PACKAGE_NAME = "menion.android.locus";
	
	public static final int LOCUS_API_SINCE_VERSION = 235;
	
	/**
	 * all Locus package names
	 */
	public static final String[] LOCUS_PACKAGE_NAMES = new String[] {
			LOCUS_PRO_PACKAGE_NAME, LOCUS_FREE_PACKAGE_NAME };

	/**
	 * Returns <code>true</code> if Locus Pro or Locus Free is installed.
	 * 
	 * @param context
	 *            actual {@link Context}
	 * @return true or false
	 */
	public static boolean isLocusAvailable(Context context) {
		return isLocusAvailable(context, LOCUS_API_SINCE_VERSION);
	}
	
	/**
	 * Returns <code>true</code> if Locus Pro or Locus Free, in required version is installed. 
	 * @param context
	 * @param version
	 * @return
	 */
	public static boolean isLocusAvailable(Context context, int version) {
		if (isLocusProAvailable(context, version))
			return true;
		if (isLocusFreeAvailable(context, version))
			return true;
		return false;
	}
	
	public static boolean isLocusProAvailable(Context context, int version) {
		return isAppAvailable(context, LOCUS_PRO_PACKAGE_NAME, version);
	}

	public static boolean isLocusFreeAvailable(Context context, int version) {
		return isAppAvailable(context, LOCUS_FREE_PACKAGE_NAME, version);
	}

	public static boolean isAppAvailable(Context context, String packageName, int version) {
		//Log.i(TAG, "isAppAvailable(" + context + ", " + packageName + ", " + version + ")");
		try {
			PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
			if (info == null || info.applicationInfo.enabled == false)
				return false;
			return info.versionCode >= version;
		} catch (PackageManager.NameNotFoundException e) {
			return false;
		}
	}

	/**
	 * Returns {@link PackageInfo} with information about Locus or null if Locus
	 * is not installed.
	 * 
	 * @param context
	 *            actual {@link Context}
	 * @return instance of PackageInfo object
	 */
	public static PackageInfo getLocusPackageInfo(Context context) {
		PackageInfo info = null;
		for (String p : LOCUS_PACKAGE_NAMES) {
			try {
				info = context.getPackageManager().getPackageInfo(p, 0);
				break;
			} catch (PackageManager.NameNotFoundException e) {
			}
		}

		return info;
	}
	
	/**
	 * Returns a package name of Locus (Pro prefered). If Locus is not installed returns
	 * package name of Locus Pro. This functions is useful for sending broadcast intents,
	 * not for checking if Locus is installed.
	 * 
	 * @param context
	 *            actual {@link Context}
	 * @return package name
	 */
	public static String getLocusPackageName(Context context) {
		PackageInfo info = getLocusPackageInfo(context);
		if (info == null) {
			return LOCUS_PRO_PACKAGE_NAME;
		} else {
			return info.packageName;
		}
	}

	/**
	 * Returns Locus version, e.g. <code>"1.9.5.1"</code>. If Locus is not
	 * installed returns <code>null</code>.
	 * 
	 * @param context
	 *            actual {@link Context}
	 * @return version
	 */
	public static String getLocusVersion(Context context) {
		PackageInfo info = getLocusPackageInfo(context);

		if (info == null)
			return null;

		return info.versionName;
	}
	
	/**
	 * Display shot that allow install Free version of Locus
	 * @param act
	 */
	public static void callInstallLocus(Activity act) {
		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(
	            "http://market.android.com/details?id=" + 
	            LOCUS_FREE_PACKAGE_NAME));
		act.startActivity(intent);
	}
	
	/**************************************************/
	/*               RESPONSE HANDLING                */
	/**************************************************/
	
	/*
	   Add POI from your application
	  -------------------------------
	   - on places where is location needed, you may add link to your application. So for example, when
	   you display Edit point dialog, you may see next to coordinates button "New". This is list of
	   location sources and also place when your application appear with this method
	   
		1. register intent-filter for your activity
	   
		<intent-filter>
			<action android:name="locus.api.android.INTENT_ITEM_GET_LOCATION" />
			<category android:name="android.intent.category.DEFAULT" />
		</intent-filter>
    
		2. register intent receiver in your application

		if (getIntent().getAction().equals(LocusConst.INTENT_GET_POINT)) {
			// get some data here and finally return value back, more below
		}
	 */
	
	public static boolean isIntentGetLocation(Intent intent) {
		return isRequiredAction(intent, LocusConst.INTENT_ITEM_GET_LOCATION);
	}
	
	public interface OnIntentGetLocation {
		/**
		 * Handle received request
		 * @param locGps if GPS is enabled, location is included (may be null)
		 * @param locMapCenter center location of displayed map (may be null)
		 */
		public void onReceived(Location locGps, Location locMapCenter);
		/**
		 * If intent is not INTENT_GET_LOCATION intent or other problem occur
		 */
		public void onFailed();
	}
	
	public static void handleIntentGetLocation(Context context, Intent intent,
			OnIntentGetLocation handler) throws NullPointerException {
		// check source data
		if (intent == null)
			throw new NullPointerException("Intent cannot be null");
		// check intent itself
		if (!isIntentGetLocation(intent)) {
			handler.onFailed();
			return;
		}

		// variables that may be obtain from intent
		handler.onReceived(
				getLocationFromIntent(intent, LocusConst.INTENT_EXTRA_LOCATION_GPS),
				getLocationFromIntent(intent, LocusConst.INTENT_EXTRA_LOCATION_MAP_CENTER));
	}
	
	public static boolean sendGetLocationData(Activity activity, String name, Location loc) {
		if (loc == null) {
			return false;
		} else {
			Intent intent = new Intent();
			// string value name - OPTIONAL
			if (!TextUtils.isEmpty(name))
				intent.putExtra(LocusConst.INTENT_EXTRA_NAME, name);
			intent.putExtra(LocusConst.INTENT_EXTRA_LOCATION, loc.getAsBytes());
			activity.setResult(Activity.RESULT_OK, intent);
			activity.finish();
			return true;
		}
	}
	
	/*
	   Add action under point sub-menu
	  -------------------------------
	   - when you tap on any point on map or in Point screen, under last bottom button, are functions for 
	   calling to some external application. Under this menu appear also your application. If you want specify
	   action only on your points displayed in Locus, use 'setExtraCallback' function on 'Point' object instead
	   of this. It has same functionality but allow displaying only on yours points.
	   
	   1. register intent-filter for your activity
	   
		<intent-filter>
			<action android:name="locus.api.android.INTENT_ITEM_POINT_TOOLS" />
			<category android:name="android.intent.category.DEFAULT" />
		</intent-filter>
		
		- extra possibility to act only on geocache point
		<intent-filter>
			<action android:name="locus.api.android.INTENT_ITEM_POINT_TOOLS" />
			<category android:name="android.intent.category.DEFAULT" />
			
			<data android:scheme="locus" />
			<data android:path="menion.android.locus/point_geocache" />
		</intent-filter>
    
	   2. register intent receiver in your application or use functions below

		if (isIntentOnPointAction(intent) {
			Waypoint wpt = LocusUtils.getWaypointFromIntent(intent);
        	if (wpt == null) {
        		... problem
        	} else {
         		... handle waypoint
        	}
		}
	 */
	
	public static boolean isIntentPointTools(Intent intent) {
		return isRequiredAction(intent, LocusConst.INTENT_ITEM_POINT_TOOLS);
	}
	
	public static Waypoint handleIntentPointTools(Context context, Intent intent) 
			throws RequiredVersionMissingException {
		long wptId = intent.getLongExtra(LocusConst.INTENT_EXTRA_ITEM_ID, -1L);
		if (wptId < 0) {
			return null;
		} else {
			return ActionTools.getLocusWaypoint(context, wptId);
		}
	}
	
	/*
	   Add action under MAIN function menu or SEARCH list 
	  -------------------------------------
	   - when you display menu->functions, your application appear here. Also you application (activity) may
	    be added to right quick menu. Application will be called with current map center coordinates
	   
	   1. register intent-filter for your activity
	   
		<intent-filter>
			<action android:name="locus.api.android.INTENT_ITEM_MAIN_FUNCTION" />
			<category android:name="android.intent.category.DEFAULT" />
		</intent-filter>
		
		<intent-filter>
			<action android:name="locus.api.android.INTENT_ITEM_SEARCH_LIST" />
			<category android:name="android.intent.category.DEFAULT" />
		</intent-filter>
    
    2. register intent receiver in your application

		if (isIntentMainFunction(LocusConst.INTENT_ITEM_MAIN_FUNCTION)) {
			// more below ...
		}
	 */

	public static boolean isIntentMainFunction(Intent intent) {
		return isRequiredAction(intent, LocusConst.INTENT_ITEM_MAIN_FUNCTION);
	}
	
	public static void handleIntentMainFunction(Intent intent, OnIntentMainFunction handler) 
			throws NullPointerException {
		handleIntentMenuItem(intent, handler, LocusConst.INTENT_ITEM_MAIN_FUNCTION);
	}
	
	public static boolean isIntentSearchList(Intent intent) {
		return isRequiredAction(intent, LocusConst.INTENT_ITEM_SEARCH_LIST);
	}
	
	public static void handleIntentSearchList(Intent intent, OnIntentMainFunction handler) 
			throws NullPointerException {
		handleIntentMenuItem(intent, handler, LocusConst.INTENT_ITEM_SEARCH_LIST);
	}
	
	private static void handleIntentMenuItem(Intent intent, OnIntentMainFunction handler, String item) 
			throws NullPointerException {
		// check source data
		if (intent == null)
			throw new NullPointerException("Intent cannot be null");
		if (handler == null)
			throw new NullPointerException("Handler cannot be null");
		// check intent itself
		if (!isRequiredAction(intent, item)) {
			handler.onFailed();
			return;
		}
		
		handler.onReceived(
				getLocationFromIntent(intent, LocusConst.INTENT_EXTRA_LOCATION_GPS),
				getLocationFromIntent(intent, LocusConst.INTENT_EXTRA_LOCATION_MAP_CENTER));
	}
	
	public interface OnIntentMainFunction {
		/**
		 * When intent really contain location, result is returned by this function
		 * @param gpsEnabled true/false if GPS in Locus is enabled
		 * @param locGps if gpsEnabled is true, variable contain location, otherwise null
		 * @param locMapCenter contain current map center location
		 */
		public void onReceived(Location locGps, Location locMapCenter);
		public void onFailed();
	}
	

	public static boolean isIntentPointsScreenTools(Intent intent) {
		return isRequiredAction(intent, LocusConst.INTENT_ITEM_POINTS_SCREEN_TOOLS);
	}

	public static long[] handleIntentPointsScreenTools(Intent intent) {
		long[] waypointIds = null;
		if (intent.hasExtra(LocusConst.INTENT_EXTRA_ITEMS_ID)) {
			waypointIds = intent.getLongArrayExtra(LocusConst.INTENT_EXTRA_ITEMS_ID);
		}
		return waypointIds;
	}
	
	/*
	   Pick location from Locus
	  -------------------------------
	   - this feature can be used to obtain location from Locus, from same dialog (locus usually pick location). 
	   Because GetLocation dialog, used in Locus need to have already initialized whole core of Locus, this dialog
	   cannot be called directly, but needs to be started from Main map screen. This screen have anyway flag
	   android:launchMode="singleTask", so there is no possibility to use startActivityForResult in this way.
	   
	   Be careful with this function, because Locus will after "pick location" action, call new intent with 
	   ON_LOCATION_RECEIVE action, which will start your activity again without "singleTask" or similar flag
	   
	   Current functionality can be created by
	   
	   1. register intent-filter for your activity
	   
		<intent-filter>
			<action android:name="locus.api.android.ACTION_RECEIVE_LOCATION" />
			<category android:name="android.intent.category.DEFAULT" />
		</intent-filter>
 
		2. register intent receiver in your application
		
		check sample application, where this functionality is implemented

	 */
	
	public static boolean isIntentReceiveLocation(Intent intent) {
		return isRequiredAction(intent, LocusConst.ACTION_RECEIVE_LOCATION);
	}
	
	/**************************************************/
	/*              SOME HANDY FUNCTIONS              */
	/**************************************************/
	
	private static boolean isRequiredAction(Intent intent, String action) {
		return intent != null && intent.getAction() != null &&
				intent.getAction().equals(action);
	}
	
	public static Intent prepareResultExtraOnDisplayIntent(Waypoint wpt, boolean overridePoint) {
		Intent intent = new Intent();
		addWaypointToIntent(intent, wpt);
		intent.putExtra(LocusConst.INTENT_EXTRA_POINT_OVERWRITE, overridePoint);
		return intent;
	}
	
	public static void addWaypointToIntent(Intent intent, Waypoint wpt) {
		intent.putExtra(LocusConst.INTENT_EXTRA_POINT, wpt.getAsBytes());
	}
	
	public static Waypoint getWaypointFromIntent(Intent intent) {
		try {
			return new Waypoint(intent.getByteArrayExtra(LocusConst.INTENT_EXTRA_POINT));
		} catch (Exception e) {
			Logger.logE(TAG, "getWaypointFromIntent(" + intent + ")", e);
			return null;
		}
	}
	
	public static Location getLocationFromIntent(Intent intent, String intentExtra) {
		try {
			return new Location(intent.getByteArrayExtra(intentExtra));
		} catch (Exception e) {
			Logger.logE(TAG, "getLocationFromIntent(" + intent + ")", e);
			return null;
		}
	}
	
	/**************************************************/
	/*               LOCATION CONVERSION              */
	/**************************************************/
	
	/**
	 * Convert a Location object from Android to Locus format
	 * @param oldLoc
	 * @return
	 */
	public static Location convertToL(android.location.Location oldLoc) {
		Location loc = new Location(oldLoc.getProvider());
		loc.setLongitude(oldLoc.getLongitude());
		loc.setLatitude(oldLoc.getLatitude());
		loc.setTime(oldLoc.getTime());
		if (oldLoc.hasAccuracy()) {
			loc.setAccuracy(oldLoc.getAccuracy());
		}
		if (oldLoc.hasAltitude()) {
			loc.setAltitude(oldLoc.getAltitude());
		}
		if (oldLoc.hasBearing()) {
			loc.setBearing(oldLoc.getBearing());
		}
		if (oldLoc.hasSpeed()) {
			loc.setSpeed(oldLoc.getSpeed());
		}
		return loc;
	}
	
	/**
	 * Convert a Location object from Locus to Android format
	 * @param oldLoc 
	 * @return
	 */
	public static android.location.Location convertToA(Location oldLoc) {
		android.location.Location loc = new android.location.Location(oldLoc.getProvider());
		loc.setLongitude(oldLoc.getLongitude());
		loc.setLatitude(oldLoc.getLatitude());
		loc.setTime(oldLoc.getTime());
		if (oldLoc.hasAccuracy()) {
			loc.setAccuracy(oldLoc.getAccuracy());
		}
		if (oldLoc.hasAltitude()) {
			loc.setAltitude(oldLoc.getAltitude());
		}
		if (oldLoc.hasBearing()) {
			loc.setBearing(oldLoc.getBearing());
		}
		if (oldLoc.hasSpeed()) { 
			loc.setSpeed(oldLoc.getSpeed());
		}
		return loc;
	}
}
