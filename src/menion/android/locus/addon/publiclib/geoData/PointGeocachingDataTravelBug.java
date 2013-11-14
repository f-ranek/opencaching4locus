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

public class PointGeocachingDataTravelBug implements Parcelable {
	
	private static final int VERSION = 0;
	
	/* name of travel bug */
	public String name;
	/* image url to this travel bug */
	public String imgUrl;
	/* original page data */
	public String srcDetails;
	
	/* owner of TB */
	public String owner;
	/* String date of release */
	public String released;
	/* origin place */
	public String origin;
	/* goal of this TB */
	public String goal;
	/* details */
	public String details;
	
	public PointGeocachingDataTravelBug() {
		name = "";
		imgUrl = "";
		srcDetails = "";
		
		owner = "";
		released = "";
		origin = "";
		goal = "";
		details = "";
	}

	/****************************/
	/*      PARCELABLE PART     */
	/****************************/
	
    public static final Parcelable.Creator<PointGeocachingDataTravelBug> CREATOR = new Parcelable.Creator<PointGeocachingDataTravelBug>() {
        public PointGeocachingDataTravelBug createFromParcel(Parcel in) {
            return new PointGeocachingDataTravelBug(in);
        }

        public PointGeocachingDataTravelBug[] newArray(int size) {
            return new PointGeocachingDataTravelBug[size];
        }
    };
    
    public PointGeocachingDataTravelBug(Parcel in) {
    	switch (in.readInt()) {
    	case 0:
    		name = in.readString();
    		imgUrl = in.readString();
    		srcDetails = in.readString();
    		owner = in.readString();
    		released = in.readString();
    		origin = in.readString();
    		goal = in.readString();
    		details = in.readString();
    		break;
    	}
    }
    
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(VERSION);
		dest.writeString(name);
		dest.writeString(imgUrl);
		dest.writeString(srcDetails);
		dest.writeString(owner);
		dest.writeString(released);
		dest.writeString(origin);
		dest.writeString(goal);
		dest.writeString(details);
	}
	
	@Override
	public int describeContents() {
		return 0;
	}
}