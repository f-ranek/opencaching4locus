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

import android.os.Parcel;
import android.os.Parcelable;

public class PointGeocachingDataLog  implements Parcelable {
	
	private static final int VERSION = 1;
	
	public int id;
	public int type;
	/**
	 * Date of log in format "yyyy-MM-dd'T'HH:mm:ss'Z'"
	 */
	public String date;
	public String finder;
	public int finderFound;
	public String logText;

	public PointGeocachingDataLog() {
		id = 0;
		type = PointGeocachingData.CACHE_LOG_TYPE_UNKNOWN;
		date = "";
		finder = "";
		finderFound = 0;
		logText = "";
	}
	
	/****************************/
	/*      PARCELABLE PART     */
	/****************************/
	
    public static final Parcelable.Creator<PointGeocachingDataLog> CREATOR = new Parcelable.Creator<PointGeocachingDataLog>() {
        public PointGeocachingDataLog createFromParcel(Parcel in) {
            return new PointGeocachingDataLog(in);
        }

        public PointGeocachingDataLog[] newArray(int size) {
            return new PointGeocachingDataLog[size];
        }
    };
    
    public PointGeocachingDataLog(Parcel in) {
    	switch (in.readInt()) {
    	case 1:
    		id = in.readInt();
    	case 0:
    		type = in.readInt();
    		date = in.readString();
    		finder = in.readString();
    		finderFound = in.readInt();
    		logText = in.readString();
    		break;
    	}
    }
    
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(VERSION);
		dest.writeInt(id);
		dest.writeInt(type);
		dest.writeString(date);
		dest.writeString(finder);
		dest.writeInt(finderFound);
		dest.writeString(logText);
	}
	
	@Override
	public int describeContents() {
		return 0;
	}
}