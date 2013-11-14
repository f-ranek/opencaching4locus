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

package menion.android.locus.addon.publiclib.geoData;

import menion.android.locus.addon.publiclib.LocusUtils;
import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;

public class Point implements Parcelable {
	
	private static final int VERSION = 1;
	
	/* mName of object */
	private String mName;
	/* mDesc of object */
	private String mDesc;
	/* mLoc of this point */
	private Location mLoc;
	/* extra intent data */
	private String mExtraCallback;
	/* extra data used when point is displayed */
	private String mExtraOnDisplay;
	/* additional geoCaching data */
	private PointGeocachingData mGeoCachingData;
	
	public Point(String name, Location loc) {
		this.mName = name;
		this.mDesc = null;
		this.mLoc = loc;
		this.mExtraCallback = null;
		this.mExtraOnDisplay = null;
		this.mGeoCachingData = null;
	}

	public String getName() {
		return mName;
	}

	public void setName(String name) {
		if (name != null && name.length() > 0)
			this.mName = name;
	}
	
	public String getDescription() {
		return mDesc;
	}
	
	public void setDescription(String desc) {
		if (desc != null && desc.length() > 0)
			this.mDesc = desc;
	}
	
	public Location getLocation() {
		return mLoc;
	}
	
	public String getExtraCallback() {
		return mExtraCallback;
	}
	
	public String getExtraOnDisplay() {
		return mExtraOnDisplay;
	}
	
	/**
	 * Simply allow set callback value on point. This appear when you click on point
	 * and then under last button will be your button. Clicking on it, launch by you,
	 * defined intent
	 * <br /><br />
	 * Do not forget to set this http://developer.android.com/guide/topics/manifest/activity-element.html#exported
	 * to your activity, if you'll set callback to other then launcher activity
	 * @param btnName Name displayed on button
	 * @param packageName this value is used for creating intent that
	 *  will be called in callback (for example com.super.application)
	 * @param className the name of the class inside of com.super.application
	 *  that implements the component (for example com.super.application.Main)
	 * @param returnDataName String under which data will be stored. Can be
	 *  retrieved by String data = getIntent.getStringExtra("returnData");
	 * @param returnDataValue String under which data will be stored. Can be
	 *  retrieved by String data = getIntent.getStringExtra("returnData");
	 */
	public void setExtraCallback(String btnName, String packageName, String className,
			String returnDataName, String returnDataValue) {
		StringBuffer buff = new StringBuffer();
		buff.append("intent").append(";");
		buff.append(btnName).append(";");
		buff.append(packageName).append(";");
		buff.append(className).append(";");
		buff.append(returnDataName).append(";");
		buff.append(returnDataValue).append(";");
		this.mExtraCallback = buff.toString();
	}
	
	/**********************************/
	/*      EXTRA_ON_DISPLAY PART     */
	/**********************************/
	
	/**
	 * Extra feature that allow to send to locus only partial point data. When you click on
	 * point (in time when small point dialog should appear), locus send intent to your app,
	 * you can then fill complete point and send it back to Locus. Clear and clever
	 * <br /><br />
	 * Do not forget to set this http://developer.android.com/guide/topics/manifest/activity-element.html#exported
	 * to your activity, if you'll set callback to other then launcher activity
	 * 
	 * @param btnName Name displayed on button
	 * @param packageName this value is used for creating intent that
	 *  will be called in callback (for example com.super.application)
	 * @param className the name of the class inside of com.super.application
	 *  that implements the component (for example com.super.application.Main)
	 * @param returnDataName String under which data will be stored. Can be
	 *  retrieved by String data = getIntent.getStringExtra("returnData");
	 * @param returnDataValue String under which data will be stored. Can be
	 *  retrieved by String data = getIntent.getStringExtra("returnData");
	 */
	public void setExtraOnDisplay(String packageName, String className,
			String returnDataName, String returnDataValue) {
		StringBuffer buff = new StringBuffer();
		buff.append("intent").append(";");
		buff.append(packageName).append(";");
		buff.append(className).append(";");
		buff.append(returnDataName).append(";");
		buff.append(returnDataValue).append(";");
		this.mExtraOnDisplay = buff.toString();
	}
	
	public PointGeocachingData getGeocachingData() {
		return mGeoCachingData;
	}
	
	public void setGeocachingData(PointGeocachingData gcData) {
		this.mGeoCachingData = gcData;
	}
	
	/****************************/
	/*      PARCELABLE PART     */
	/****************************/
	
    public static final Parcelable.Creator<Point> CREATOR = new Parcelable.Creator<Point>() {
        public Point createFromParcel(Parcel in) {
            return new Point(in);
        }

        public Point[] newArray(int size) {
            return new Point[size];
        }
    };
    
    private Point(Parcel in) {
    	int version = in.readInt();
		// load name
		mName = in.readString();
		// load description
		mDesc = in.readString();
		// load separate location
		mLoc = LocusUtils.readLocation(in);
    	
    	switch (version) {
    	case 0:
    		// load extra data
    		mExtraCallback = in.readString();
			break;
    	case 1:
    		// load extra data
    		mExtraCallback = in.readString();
    		mExtraOnDisplay = in.readString();
    		break;
    	}
		// load geocaching data
		mGeoCachingData = in.readParcelable(PointGeocachingData.class.getClassLoader());//PointGeocachingData.CREATOR.createFromParcel(in);
    }
    
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(VERSION);
		// write name
		dest.writeString(mName);
		// write description
		dest.writeString(mDesc);
		// write location as separate values (due to some problems with 'magic number'
		LocusUtils.writeLocation(dest, mLoc);
		// write extra
		dest.writeString(mExtraCallback);
		dest.writeString(mExtraOnDisplay);
		// write geocaching data
		dest.writeParcelable(mGeoCachingData, flags);
	}
}
