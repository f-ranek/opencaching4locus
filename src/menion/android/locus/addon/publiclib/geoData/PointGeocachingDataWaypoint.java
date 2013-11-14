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

public class PointGeocachingDataWaypoint implements Parcelable {
	
	private static final int VERSION = 1;
	
	/* code of wpt */
	public String code;
	/* name of waypoint */
	public String name;
	/* description (may be HTML code) */
	public String description;
	/* type of waypoint (defined in PointGeocachingData) */
	public String type;
	/* image URL to this wpt (not needed) */
	public String typeImagePath;
	/* latitude of waypoint */
	public double lat;
	/* longitude of waypoint */
	public double lon;
	
	public PointGeocachingDataWaypoint() {
		code = "";
		name = "";
		description = "";
		type = "";
		typeImagePath = "";
		lat = 0.0;
		lon = 0.0;
	}

	/****************************/
	/*      PARCELABLE PART     */
	/****************************/
	
    public static final Parcelable.Creator<PointGeocachingDataWaypoint> CREATOR = new Parcelable.Creator<PointGeocachingDataWaypoint>() {
        public PointGeocachingDataWaypoint createFromParcel(Parcel in) {
            return new PointGeocachingDataWaypoint(in);
        }

        public PointGeocachingDataWaypoint[] newArray(int size) {
            return new PointGeocachingDataWaypoint[size];
        }
    };
    
    public PointGeocachingDataWaypoint(Parcel in) {
    	switch (in.readInt()) {
    	case 1:
    		code = in.readString();
    	case 0:
    		name = in.readString();
    		description = in.readString();
    		type = in.readString();
    		typeImagePath = in.readString();
    		lat = in.readDouble();
    		lon = in.readDouble();
    		break;
    	}
    }
    
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(VERSION);
		dest.writeString(code);
		dest.writeString(name);
		dest.writeString(description);
		dest.writeString(type);
		dest.writeString(typeImagePath);
		dest.writeDouble(lat);
		dest.writeDouble(lon);
	}
	
	@Override
	public int describeContents() {
		return 0;
	}
}