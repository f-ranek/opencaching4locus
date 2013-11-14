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

import java.io.File;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Parcel;
import android.util.Log;

/**
 * Locus Helper class
 * 
 * @author Menion Asamm
 * @author Arcao
 * 
 */
public class LocusUtils {

	private static final String TAG = "LocusUtils";
	
	/***********************************/
	/*           CHECK PART            */
	/***********************************/
	
	/** Locus Free package name */
	public static final String LOCUS_FREE_PACKAGE_NAME = "menion.android.locus";
	/** Locus Pro package name */
	public static final String LOCUS_PRO_PACKAGE_NAME = "menion.android.locus.pro";
	/** All Locus package names */
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
		return getLocusPackageInfo(context) != null;
	}
	
	/**
	 * Use for versions above 200 (So 2.0.0 and above). They have same ID now 
	 * @param context
	 * @param version
	 * @return
	 */
	public static boolean isLocusAvailable(Context context, int version) {
		return isLocusAvailable(context, version, version);
	}
	
	public static boolean isLocusAvailable(Context context, int versionPro, int versionFree) {
		PackageInfo pi = getLocusPackageInfo(context);
		if (pi == null) {
			return false;
		} else {
			if (pi.packageName.equalsIgnoreCase(LOCUS_PRO_PACKAGE_NAME)) {
				Log.i(TAG, "isLocusAvailable(), Locus Pro, available:" + pi.versionCode + ", needed:" + versionPro);
				return pi.versionCode >= versionPro;
			} else if (pi.packageName.equalsIgnoreCase(LOCUS_FREE_PACKAGE_NAME)) {
				Log.i(TAG, "isLocusAvailable(), Locus Free, available:" + pi.versionCode + ", needed:" + versionPro);
				return pi.versionCode >= versionFree;
			} else {
				return false;
			}
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

	// use direct check isLocusAvailable()
	@Deprecated
	public static int getLocusVersionCode(Context context) {
		PackageInfo info = getLocusPackageInfo(context);

		if (info == null)
			return -1;

		return info.versionCode;
	}

	/**
	 * Returns <code>true</code> if Locus Pro is installed.
	 * 
	 * @param context
	 *            actual {@link Context}
	 * @return true or false
	 */
	public static boolean isLocusProInstalled(Context context) {
		try {
			context.getPackageManager().getPackageInfo(LOCUS_PRO_PACKAGE_NAME, 0);
			return true;
		} catch (PackageManager.NameNotFoundException e) {
			return false;
		}
	}

	/**
	 * Returns a package name of Locus (Pro). If Locus is not installed returns
	 * null.
	 * 
	 * @param context
	 *            actual {@link Context}
	 * @return package name
	 */
	public static String getLocusPackageName(Context context) {
		PackageInfo info = getLocusPackageInfo(context);
		if (info == null)
			return null;
		return info.packageName;
	}

	/**
	 * Returns a package name like {@link #getLocusPackageName(Context)} but if
	 * Locus is not installed returns a default package name constant
	 * {@link #LOCUS_FREE_PACKAGE_NAME}.
	 * 
	 * @param context
	 *            actual {@link Context}
	 * @return package name
	 */
	public static String getLocusDefaultPackageName(Context context) {
		String name = getLocusPackageName(context);
		if (name == null)
			return LOCUS_FREE_PACKAGE_NAME;
		return name;
	}
	
	/***********************************/
	/*        SHARE DATA PART          */
	/***********************************/
	
	/**
	 * Generic call to system for applications that can import your file.
	 * @param context
	 * @param file
	 * @return
	 */
	public static boolean importFileSystem(Context context, File file) {
		if (!isReadyForImport(context, file))
			return false;
		
    	Intent sendIntent = new Intent(Intent.ACTION_VIEW);
    	Uri uri = Uri.fromFile(file);
    	sendIntent.setDataAndType(uri, getMimeType(file));
    	context.startActivity(sendIntent);
    	return true;
	}

	/**
	 * Import GPX/KML files directly into Locus application. 
	 * Return false if file don't exist or Locus is not installed
	 * @param context
	 * @param file
	 */
	public static boolean importFileLocus(Context context, File file) {
		return importFileLocus(context, file, true);
	}
	
	/**
	 * Import GPX/KML files directly into Locus application. 
	 * Return false if file don't exist or Locus is not installed
	 * @param context
	 * @param file
     * @param callImport
	 */
	public static boolean importFileLocus(Context context, File file, boolean callImport) {
		if (!isReadyForImport(context, file))
			return false;
		
    	Intent sendIntent = new Intent(Intent.ACTION_VIEW);
    	PackageInfo pi = getLocusPackageInfo(context);
    	sendIntent.setClassName(pi.packageName, "menion.android.locus.core.MainActivity");
    	Uri uri = Uri.fromFile(file);
    	sendIntent.setDataAndType(uri, getMimeType(file));
    	sendIntent.putExtra(LocusConst.EXTRA_CALL_IMPORT, callImport);
    	context.startActivity(sendIntent);
    	return true;
	}
	
	private static boolean isReadyForImport(Context context, File file) {
		if (file == null || !file.exists() || !isLocusAvailable(context))
			return false;
		return true;
	}
	
	private static String getMimeType(File file) {
		String name = file.getName();
		int index = name.lastIndexOf(".");
		if (index == -1)
			return "*/*";
		return "application/" + name.substring(index + 1);
	}
	
	/***********************************/
	/*          USEFUL TOOLS           */
	/***********************************/
	
	/**
	 * Allow to call activity for File pick. You can use Locus picker for this purpose, but
	 * check if Locus version 202 and above are installed <b>isLocusAvailable(context, 202)</b>!
	 * @param activity
	 * @param id
	 * @throws ActivityNotFoundException
	 */
	public static void intentPickFile(Activity activity, int requestCode) 
			throws ActivityNotFoundException {
		intentPick("org.openintents.action.PICK_FILE",
				activity, requestCode, null, null);
	}
	
	public static void intentPickFile(Activity activity, int requestCode, String title, String[] filter) 
			throws ActivityNotFoundException {
		intentPick("org.openintents.action.PICK_FILE",
				activity, requestCode, title, filter);
	}
	
	public static void intentPickDir(Activity activity, int requestCode) 
			throws ActivityNotFoundException {
		intentPick("org.openintents.action.PICK_DIRECTORY",
				activity, requestCode, null, null);
	}
	
	public static void intentPickDir(Activity activity, int requestCode, String title) 
			throws ActivityNotFoundException {
		intentPick("org.openintents.action.PICK_DIRECTORY",
				activity, requestCode, title, null);
	}
	
	private static void intentPick(String action, Activity activity, int requestCode, String title, String[] filter) {
		Intent intent = new Intent(action);
		if (title != null && title.length() > 0)
			intent.putExtra("org.openintents.extra.TITLE", title);
		if (filter != null && filter.length > 0)
			intent.putExtra("org.openintents.extra.FILTER", filter);
		activity.startActivityForResult(intent, requestCode);
	}
	
	
	/***********************************/
	/*        PARCELABLE PART          */
	/***********************************/
	
	public static void writeLocation(Parcel dest, Location loc) {
		dest.writeString(loc.getProvider());
		dest.writeLong(loc.getTime());
		dest.writeDouble(loc.getLatitude());
		dest.writeDouble(loc.getLongitude());
		dest.writeDouble(loc.getAltitude());
		dest.writeFloat(loc.getAccuracy());
		dest.writeFloat(loc.getBearing());
		dest.writeFloat(loc.getSpeed());
	}
	
	public static Location readLocation(Parcel in) {
		Location loc = new Location(in.readString());
		loc.setTime(in.readLong());
		loc.setLatitude(in.readDouble());
		loc.setLongitude(in.readDouble());
		loc.setAltitude(in.readDouble());
		loc.setAccuracy(in.readFloat());
		loc.setBearing(in.readFloat());
		loc.setSpeed(in.readFloat());
		return loc;
	}
}
