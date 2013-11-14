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

import menion.android.locus.addon.publiclib.geoData.Point;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;

public class LocusIntents {

	private static final String TAG = "LocusIntents";
	
	/*
	   Add POI from your application
 	  -------------------------------
 	   - on places where is location needed, you may add link to your application. So for example, when
 	   you display Edit point dialog, you may see next to coordinates button "New". This is list of
 	   location sources and also place when your application appear with this method
 	   
 	   1. register intent-filter for your activity
 	   
       	<intent-filter>
          	<action android:name="menion.android.locus.GET_POINT" />
          	<category android:name="android.intent.category.DEFAULT" />
       	</intent-filter>
       
       2. register intent receiver in your application

		if (getIntent().getAction().equals(LocusConst.INTENT_GET_POINT)) {
   			// get some data here and finally return value back, more below
		}
	 */
	
	public static boolean isIntentGetLocation(Intent intent) {
		return isRequiredAction(intent, LocusConst.INTENT_GET_LOCATION);
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
			OnIntentGetLocation handler) 
			throws NullPointerException {
		// check source data
		if (intent == null)
			throw new NullPointerException("Intent cannot be null");
		// check intent itself
		if (!isIntentGetLocation(intent)) {
			handler.onFailed();
			return;
		}

		// variables that may be obtain from intent (since locus pro 68/ free 130)
		if (LocusUtils.isLocusAvailable(context, 68, 130)) {
			handler.onReceived(
					(Location) intent.getParcelableExtra("locGps"),
					(Location) intent.getParcelableExtra("locCenter"));
		} else {
			handler.onReceived(null, null);
		}
	}
	
	public static boolean sendGetLocationData(Activity activity, 
			String name, double lat, double lon, double alt, double acc) {
		if (lat == 0.0 && lon == 0.0) {
			return false;
		} else {
			Intent intent = new Intent();
			// string value name
			intent.putExtra("name", name); // optional
			// rest are all DOUBLE values (to avoid problems even when for acc and alt isn't double needed)
			intent.putExtra("latitude", lat); // required, not 0.0
			intent.putExtra("longitude", lon); // required, not 0.0
			intent.putExtra("altitude", alt); // optional
			intent.putExtra("accuracy", acc); // optional
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
      		<action android:name="menion.android.locus.ON_POINT_ACTION" />
      		<category android:name="android.intent.category.DEFAULT" />
   		</intent-filter>
   		
   		- extra possibility to act only on geocache point
		<intent-filter>
      		<action android:name="menion.android.locus.ON_POINT_ACTION" />
      		<category android:name="android.intent.category.DEFAULT" />
      		<data android:scheme="locus" />
      		<data android:path="menion.android.locus/point_geocache" />
		</intent-filter>
       
       2. register intent receiver in your application or use functions below

		if (getIntent().getAction().equals(LocusConst.INTENT_ON_POINT_ACTION)) {
   			double lat = getIntent().getDoubleExtra("latitude", 0.0);
   			double lon = getIntent().getDoubleExtra("longitude", 0.0);
   			double alt = getIntent().getDoubleExtra("altitude", 0.0);
   			double acc = getIntent().getDoubleExtra("accuracy", 0.0);
   
   			// do what you want with this ...
		}
	 */
	
	public static boolean isIntentOnPointAction(Intent intent) {
		return isRequiredAction(intent, LocusConst.INTENT_ON_POINT_ACTION);
	}
	
	public static Point handleIntentOnPointAction(Intent intent) 
			throws NullPointerException {
		// check source data
		if (intent == null)
			throw new NullPointerException("Intent cannot be null");
		// check intent itself
		if (!isIntentOnPointAction(intent)) {
			return null;
		}
		
		// last version (Locus 1.15.0 and more)
		if (intent.hasExtra("object")) {
			return intent.getParcelableExtra("object");
		}
		// or use this in older version
		else {
			String name = intent.getStringExtra("name");
			Location loc;
			// in new version is already whole location as parcelable
			if (intent.getParcelableExtra("loc") != null) {
				loc = intent.getParcelableExtra("loc");
			} else {
				loc = new Location(TAG);
				loc.setLatitude(intent.getDoubleExtra("latitude", 0.0));
				loc.setLongitude(intent.getDoubleExtra("longitude", 0.0));
				loc.setAltitude(intent.getDoubleExtra("altitude", 0.0));
				loc.setAccuracy((float) intent.getDoubleExtra("accuracy", 0.0));
			}

			return new Point(name, loc);			
		}
	}
	
	/*
	   Add action under main function menu
 	  -------------------------------------
 	   - when you display menu->functions, your application appear here. Also you application (activity) may
 	    be added to right quick menu. Application will be called with current map center coordinates
 	   
 	   1. register intent-filter for your activity
 	   
		<intent-filter>
      		<action android:name="menion.android.locus.MAIN_FUNCTION" />
      		<category android:name="android.intent.category.DEFAULT" />
   		</intent-filter>
       
       2. register intent receiver in your application

		if (getIntent().getAction().equals(LocusConst.INTENT_MAIN_FUNCTION)) {
   			// more below ...
		}
	 */
	
	public static boolean isIntentMainFunction(Intent intent) {
		return isRequiredAction(intent, LocusConst.INTENT_MAIN_FUNCTION);
	}
	
	public interface OnIntentMainFunction {
		public void onLocationReceived(boolean gpsEnabled, Location locGps, Location locMapCenter);
		public void onFailed();
	}
	
	public static void handleIntentMainFunction(Intent intent, OnIntentMainFunction handler) 
			throws NullPointerException {
		// check source data
		if (intent == null)
			throw new NullPointerException("Intent cannot be null");
		if (handler == null)
			throw new NullPointerException("Handler cannot be null");
		// check intent itself
		if (!isIntentMainFunction(intent)) {
			handler.onFailed();
			return;
		}
		
		getLocationFromIntent(intent, handler);
	}
	
	public static void getLocationFromIntent(Intent intent, OnIntentMainFunction handler) {
		boolean gpsEnabled = intent.getBooleanExtra("gpsEnabled", false);
		handler.onLocationReceived(gpsEnabled,
				gpsEnabled ? (Location)intent.getParcelableExtra("locGps") : null,
				(Location) intent.getParcelableExtra("locCenter"));
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
   			<action android:name="android.intent.action.ON_LOCATION_RECEIVE" />
   			<category android:name="android.intent.category.DEFAULT" />
		</intent-filter>
    
		2. register intent receiver in your application
		
		check sample application, where this functionality is implemented

	 */
	
	public static boolean isIntentReceiveLocation(Intent intent) {
		return isRequiredAction(intent, LocusConst.ACTION_RECEIVE_LOCATION);
	}
	
	public static Point handleActionReceiveLocation(Intent intent) 
			throws NullPointerException {
		// check source data
		if (intent == null)
			throw new NullPointerException("Intent cannot be null");
		// check intent itself
		if (!isIntentReceiveLocation(intent)) {
			return null;
		}
		
		// last version (Locus 1.15.0 and more)
		if (intent.hasExtra("location")) {
			return intent.getParcelableExtra("location");
		} else {
			return null;
		}
	}
	
	/**********************************/
	/*      SOME HANDY FUNCTIONS      */
	/**********************************/
	
	private static boolean isRequiredAction(Intent intent, String action) {
		return intent != null && intent.getAction() != null &&
				intent.getAction().equals(action);
	}
	
	public static void addPointToIntent(Intent intent, Point point) {
		intent.putExtra(LocusConst.EXTRA_POINT, point);
	}
	
	public static Intent prepareResultExtraOnDisplayIntent(Point p, boolean overridePoint) {
		Intent intent = new Intent();
		intent.putExtra(LocusConst.EXTRA_POINT, p);
		intent.putExtra(LocusConst.EXTRA_POINT_OVERWRITE, overridePoint);
		return intent;
	}
}
