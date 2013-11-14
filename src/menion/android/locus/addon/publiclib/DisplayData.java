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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;

import menion.android.locus.addon.publiclib.geoData.PointsData;
import menion.android.locus.addon.publiclib.geoData.Track;
import menion.android.locus.addon.publiclib.utils.DataStorage;
import menion.android.locus.addon.publiclib.utils.RequiredVersionMissingException;
import android.content.Context;
import android.content.Intent;
import android.os.Parcel;
import android.util.Log;

public class DisplayData {

	private static final String TAG = "DisplayData";
	
	/*******************************/
	/*        DISPLAY POINTS       */
	/*******************************/
	
	/**
	 * Simple way how to send data over intent to Locus. Count that intent in
	 * Android have some size limits so for larger data, use another method
	 * @param context actual {@link Context}
	 * @param data {@link PointsData} object that should be send to Locus
	 * @param callImport whether import with this data should be called after Locus starts
	 * @return true if success
	 * @throws RequiredVersionMissingException 
	 */
	public static boolean sendData(Context context, PointsData data, boolean callImport)
			throws RequiredVersionMissingException {
		return sendData(LocusConst.INTENT_DISPLAY_DATA, context, data, callImport);
	}
	
	public static boolean sendDataSilent(Context context, PointsData data, boolean callImport)
			throws RequiredVersionMissingException {
		return sendData(LocusConst.ACTION_DISPLAY_DATA_SILENTLY, context, data, callImport);
	}
	
	private static boolean sendData(String action, Context context, PointsData data, boolean callImport)
			throws RequiredVersionMissingException {
		if (data == null)
			return false;
		Intent intent = new Intent();
		intent.putExtra(LocusConst.EXTRA_POINTS_DATA, data);
		return sendData(action, context, intent, callImport);
	}
	
	/**
	 * Simple way how to send ArrayList<PointsData> object over intent to Locus. Count that
	 * intent in Android have some size limits so for larger data, use another method
	 * @param context actual {@link Context}
	 * @param data {@link ArrayList} of data that should be send to Locus
	 * @return true if success
	 * @throws RequiredVersionMissingException 
	 */
	public static boolean sendData(Context context, ArrayList<PointsData> data, boolean callImport)
			throws RequiredVersionMissingException {
		return sendData(LocusConst.INTENT_DISPLAY_DATA, context, data, callImport);
	}
	
	public static boolean sendDataSilent(Context context, ArrayList<PointsData> data, boolean callImport)
			throws RequiredVersionMissingException {
		return sendData(LocusConst.ACTION_DISPLAY_DATA_SILENTLY, context, data, callImport);
	}
	
	private static boolean sendData(String action, Context context, ArrayList<PointsData> data, boolean callImport)
			throws RequiredVersionMissingException {
		if (data == null)
			return false;
		Intent intent = new Intent();
		intent.putParcelableArrayListExtra(LocusConst.EXTRA_POINTS_DATA_ARRAY, data);
		return sendData(action, context, intent, callImport);
	}
	
	private static final int FILE_VERSION = 1;
	
	/**
	 * Allow to send data to locus, by storing serialized version of data into file. This method
	 * can have advantage over cursor in simplicity of implementation and also that filesize is
	 * not limited as in Cursor method. On second case, need permission for disk access and should
	 * be slower due to IO operations. Be careful about size of data. This method can cause OutOfMemory
	 * error on Locus side if data are too big
	 *   
	 * @param context
	 * @param data
	 * @param filepath
	 * @param callImport
	 * @return
	 * @throws RequiredVersionMissingException 
	 */
	public static boolean sendDataFile(Context context, ArrayList<PointsData> data, String filepath, boolean callImport)
			throws RequiredVersionMissingException {
		return sendDataFile(LocusConst.INTENT_DISPLAY_DATA, context, data, filepath, callImport);
	}
	
	public static boolean sendDataFileSilent(Context context, ArrayList<PointsData> data, String filepath, boolean callImport) 
			throws RequiredVersionMissingException {
		return sendDataFile(LocusConst.ACTION_DISPLAY_DATA_SILENTLY, context, data, filepath, callImport);
	}
	
	private static boolean sendDataFile(String action, Context context, ArrayList<PointsData> data, String filepath,
			boolean callImport) throws RequiredVersionMissingException {
		if (data == null || data.size() == 0)
			return false;
		
		FileOutputStream os = null;
		DataOutputStream dos = null;
		try {
			File file = new File(filepath);
			file.getParentFile().mkdirs();

			if (file.exists())
				file.delete();
			if (!file.exists()) {
				file.createNewFile();
			}

			os = new FileOutputStream(file, false);
			dos = new DataOutputStream(os);
	
			// write current version
			dos.writeInt(FILE_VERSION);
			
			// write data
			for (int i = 0; i < data.size(); i++) {
				// get byte array
				Parcel par = Parcel.obtain();
				data.get(i).writeToParcel(par, 0);
				byte[] byteData = par.marshall();
				
				// write data
				dos.writeInt(byteData.length);
				dos.write(byteData);
			}
				
			os.flush();
		} catch (Exception e) {
			Log.e(TAG, "saveBytesInstant(" + filepath + ", " + data + ")", e);
			return false;
		} finally {
			try {
				if (dos != null) {
					dos.close();
				}
			} catch (Exception e) {
				Log.e(TAG, "saveBytesInstant(" + filepath + ", " + data + ")", e);
			}
		}
		
		// store data to file
		Intent intent = new Intent();
		intent.putExtra(LocusConst.EXTRA_POINTS_FILE_PATH, filepath);
		return sendData(action, context, intent, callImport);
	}

	/**
	 * Invert method to {@link #sendDataFile}. This load serialized data from file object
	 * @param filepath
	 * @return
	 */
	public static ArrayList<PointsData> getDataFile(String filepath) {
		ArrayList<PointsData> returnData = new ArrayList<PointsData>();
		
		// check file
		File file = new File(filepath);
		if (!file.exists())
			return returnData;
		
		DataInputStream dis = null;
		try {
			dis = new DataInputStream(new FileInputStream(file));
			
			// check version
			if (dis.readInt() != FILE_VERSION) {
				Log.e(TAG, "getDataFile(" + filepath + "), unsupported (old) version!");
				return returnData;
			}
			
			while (true) {
				if (dis.available() == 0)
					break;
				
				int size = dis.readInt();
				byte[] data = new byte[size];
				dis.read(data);
				
				Parcel par = Parcel.obtain();
				par.unmarshall(data, 0, data.length);
				par.setDataPosition(0);
				
				PointsData pd = PointsData.CREATOR.createFromParcel(par);
				if (pd != null)
					returnData.add(pd);
				
				// BSz
				par.recycle();
			}
			return returnData;
		} catch (Exception e) {
			Log.e(TAG, "getDataFile(" + filepath + ")", e);
			return null;
		} finally {
			try {
				if (dis != null)
					dis.close();
			} catch (Exception e) {}
		}
	}
	
	/**
	 * Way how to send ArrayList<PointsData> object over intent to Locus. Data are
	 * stored in ContentProvider so don't forget to register it in manifest. More in
	 * sample application. This is recommended way how to send huge data to Locus 
	 * @param context actual context
	 * @param data ArrayList of data that should be send to Locus
	 * @param callImport whether import with this data should be called after Locus starts
	 * @return true if success
	 * @throws RequiredVersionMissingException 
	 */
	public static boolean sendDataCursor(Context context, PointsData data, String uri, boolean callImport)
			throws RequiredVersionMissingException {
		return sendDataCursor(LocusConst.INTENT_DISPLAY_DATA, context, data, uri, callImport);
	}
	
	public static boolean sendDataCursorSilent(Context context, PointsData data, String uri,
			boolean callImport) throws RequiredVersionMissingException {
		return sendDataCursor(LocusConst.ACTION_DISPLAY_DATA_SILENTLY, context, data, uri, callImport);
	}
	
	private static boolean sendDataCursor(String action, Context context, PointsData data, String uri,
			boolean callImport) throws RequiredVersionMissingException {
		if (data == null)
			return false;
		// set data
		DataStorage.setData(data);
		Intent intent = new Intent();
		intent.putExtra(LocusConst.EXTRA_POINTS_CURSOR_URI, uri);
		return sendData(action, context, intent, callImport);
	}
	
	/**
	 * Way how to send ArrayList<PointsData> object over intent to Locus. Data are
	 * stored in ContentProvider so don't forget to register it in manifest. More in
	 * sample application. This is recommended way how to send huge data to Locus 
	 * @param context actual context
	 * @param data ArrayList of data that should be send to Locus
	 * @return true if success
	 * @throws RequiredVersionMissingException 
	 */
	public static boolean sendDataCursor(Context context, ArrayList<PointsData> data,
			String uri, boolean callImport) throws RequiredVersionMissingException {
		return sendDataCursor(LocusConst.INTENT_DISPLAY_DATA, context, data, uri, callImport);
	}
	
	public static boolean sendDataCursorSilent(Context context, ArrayList<PointsData> data,
			String uri, boolean callImport) throws RequiredVersionMissingException {
		return sendDataCursor(LocusConst.ACTION_DISPLAY_DATA_SILENTLY, context, data, uri, callImport);
	}
	
	private static boolean sendDataCursor(String action, Context context, ArrayList<PointsData> data,
			String uri, boolean callImport) throws RequiredVersionMissingException {
		if (data == null)
			return false;
		// set data
		DataStorage.setData(data);
		Intent intent = new Intent();
		intent.putExtra(LocusConst.EXTRA_POINTS_CURSOR_URI, uri);
		return sendData(action, context, intent, callImport);
	}

	/*******************************/
	/*        DISPLAY TRACKS       */
	/*******************************/
	
	public static boolean sendData(Context context, Track track, boolean callImport)
			throws RequiredVersionMissingException {
		return sendData(LocusConst.INTENT_DISPLAY_DATA, context, track, callImport);
	}
	
	public static boolean sendDataSilent(Context context, Track track, boolean callImport)
			throws RequiredVersionMissingException {
		return sendData(LocusConst.ACTION_DISPLAY_DATA_SILENTLY, context, track, callImport);
	}
	
	private static boolean sendData(String action, Context context, Track track, boolean callImport)
			throws RequiredVersionMissingException {
		if (track == null)
			return false;
		Intent intent = new Intent();
		intent.putExtra(LocusConst.EXTRA_TRACKS_SINGLE, track);
		return sendData(action, context, intent, callImport, 64, 125);
	}
	
	public static boolean sendDataTracks(Context context, ArrayList<Track> tracks, boolean callImport)
			throws RequiredVersionMissingException {
		return sendDataTracks(LocusConst.INTENT_DISPLAY_DATA, context, tracks, callImport);
	}
	
	public static boolean sendDataTracksSilent(Context context, ArrayList<Track> tracks, boolean callImport)
			throws RequiredVersionMissingException {
		return sendDataTracks(LocusConst.ACTION_DISPLAY_DATA_SILENTLY, context, tracks, callImport);
	}
	
	private static boolean sendDataTracks(String action, Context context, ArrayList<Track> tracks, boolean callImport)
			throws RequiredVersionMissingException {
		if (tracks == null || tracks.size() == 0)
			return false;
		Intent intent = new Intent();
		intent.putParcelableArrayListExtra(LocusConst.EXTRA_TRACKS_MULTI, tracks);
		return sendData(action, context, intent, callImport, 69, 131);
	}
	
	/*******************************/
	/*        PRIVATE CALLS        */
	/*******************************/
	
	private static boolean sendData(String action, Context context, Intent intent,
			boolean callImport) throws RequiredVersionMissingException{
		return sendData(action, context, intent, callImport, -1, -1);
	}
	
	private static boolean sendData(String action, Context context, Intent intent,
			boolean callImport, int versionPro, int versionFree) throws RequiredVersionMissingException {
		// set correct versions (mainly due to new functionality)
		if (action.equals(LocusConst.ACTION_DISPLAY_DATA_SILENTLY)) {
			versionFree = Math.max(versionFree, 202);
			versionPro = Math.max(versionFree, 202);
		}
		
		// really exist locus?
		if (!LocusUtils.isLocusAvailable(context, versionPro, versionFree)) {
			throw new RequiredVersionMissingException(versionPro, versionFree);
		}
		
		// check intent firstly
		if (!hasData(intent)) {
			Log.w(TAG, "Intent 'null' or not contain any data");
			return false;
		}
		
		// create intent with right calling method
		intent.setAction(action);
		
		// set import tag
		if (action.equals(LocusConst.ACTION_DISPLAY_DATA_SILENTLY)) {
			context.sendBroadcast(intent);
		} else {
			// set import tag
			intent.putExtra(LocusConst.EXTRA_CALL_IMPORT, callImport);
			// finally start activity
			context.startActivity(intent);			
		}
		
		return true;
	}
	
	private static boolean hasData(Intent intent) {
		if (intent == null)
			return false;
		
		return !(
				intent.getParcelableArrayListExtra(LocusConst.EXTRA_POINTS_DATA_ARRAY) == null && 
				intent.getParcelableExtra(LocusConst.EXTRA_POINTS_DATA) == null &&
				intent.getStringExtra(LocusConst.EXTRA_POINTS_CURSOR_URI) == null && 
				intent.getStringExtra(LocusConst.EXTRA_POINTS_FILE_PATH) == null &&
				intent.getParcelableExtra(LocusConst.EXTRA_TRACKS_SINGLE) == null &&
				intent.getParcelableArrayListExtra(LocusConst.EXTRA_TRACKS_MULTI) == null);
	}
}
