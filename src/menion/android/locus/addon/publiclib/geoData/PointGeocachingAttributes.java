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

import java.util.Hashtable;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Class for holding geocaching attributes
 * <br /><br />
 * Every instance holds just one attribute, defined by it's unique ID number. If
 * you want to set correct value, use constructor that allow set directly by number
 * or by attribute URL
 * <br /><br />
 * @author menion
 */
public class PointGeocachingAttributes implements Parcelable {

	private static final int VERSION = 0;
	
	private int id;

	public PointGeocachingAttributes(int id, boolean possitive) {
		if (!possitive) {
			this.id = id;
		} else {
			this.id = (id + 100);
		}
	}
	
	public PointGeocachingAttributes(String url) {
		if (url != null && url.length() > 0) {
			id = attrIds.get(url.substring(0, url.lastIndexOf("-")));
			if (url.contains("-yes."))
				id += 100;
		}
	}
	
	public int getId() {
		return id;
	}
	
	/**
	 * Force some ID number to this attribute. Use only if you know what you're doing.
	 * This feature is mainly used directly in Locus to fill data
	 * 
	 * @param id number, already increased by 100 if it's possitive attribute
	 */
	public void setId(int id) {
		this.id = id;
	}
	
	private static Hashtable<String, Integer> attrIds = new Hashtable<String, Integer>();
	static {
		attrIds.put("http://www.geocaching.com/images/attributes/dogs", 1);
		attrIds.put("http://www.geocaching.com/images/attributes/fee", 2);
		attrIds.put("http://www.geocaching.com/images/attributes/rappelling", 3);
		attrIds.put("http://www.geocaching.com/images/attributes/boat", 4);
		attrIds.put("http://www.geocaching.com/images/attributes/scuba", 5);
		attrIds.put("http://www.geocaching.com/images/attributes/kids", 6);
		attrIds.put("http://www.geocaching.com/images/attributes/onehour", 7);
		attrIds.put("http://www.geocaching.com/images/attributes/scenic", 8);
		attrIds.put("http://www.geocaching.com/images/attributes/hiking", 9);
		attrIds.put("http://www.geocaching.com/images/attributes/climbing", 10);
		attrIds.put("http://www.geocaching.com/images/attributes/wading", 11);
		attrIds.put("http://www.geocaching.com/images/attributes/swimming", 12);
		attrIds.put("http://www.geocaching.com/images/attributes/available", 13);
		attrIds.put("http://www.geocaching.com/images/attributes/night", 14);
		attrIds.put("http://www.geocaching.com/images/attributes/winter", 15);
		attrIds.put("http://www.geocaching.com/images/attributes/camping", 16);
		attrIds.put("http://www.geocaching.com/images/attributes/poisonoak", 17);
		attrIds.put("http://www.geocaching.com/images/attributes/snakes", 18);
		attrIds.put("http://www.geocaching.com/images/attributes/ticks", 19);
		attrIds.put("http://www.geocaching.com/images/attributes/mine", 20);
		attrIds.put("http://www.geocaching.com/images/attributes/cliff", 21);
		attrIds.put("http://www.geocaching.com/images/attributes/hunting", 22);
		attrIds.put("http://www.geocaching.com/images/attributes/danger", 23);
		attrIds.put("http://www.geocaching.com/images/attributes/wheelchair", 24);
		attrIds.put("http://www.geocaching.com/images/attributes/parking", 25);
		attrIds.put("http://www.geocaching.com/images/attributes/public", 26);
		attrIds.put("http://www.geocaching.com/images/attributes/water", 27);
		attrIds.put("http://www.geocaching.com/images/attributes/restrooms", 28);
		attrIds.put("http://www.geocaching.com/images/attributes/phone", 29);
		attrIds.put("http://www.geocaching.com/images/attributes/picnic", 30);
		attrIds.put("http://www.geocaching.com/images/attributes/camping", 31);
		attrIds.put("http://www.geocaching.com/images/attributes/bicycles", 32);
		attrIds.put("http://www.geocaching.com/images/attributes/motorcycles", 33);
		attrIds.put("http://www.geocaching.com/images/attributes/quads", 34);
		attrIds.put("http://www.geocaching.com/images/attributes/jeeps", 35);
		attrIds.put("http://www.geocaching.com/images/attributes/snowmobiles", 36);
		attrIds.put("http://www.geocaching.com/images/attributes/horses", 37);
		attrIds.put("http://www.geocaching.com/images/attributes/campfires", 38);
		attrIds.put("http://www.geocaching.com/images/attributes/thorn",39 );
		attrIds.put("http://www.geocaching.com/images/attributes/stealth", 40); 
		attrIds.put("http://www.geocaching.com/images/attributes/stroller", 41);
		attrIds.put("http://www.geocaching.com/images/attributes/firstaid", 42);
		attrIds.put("http://www.geocaching.com/images/attributes/cow", 43);
		attrIds.put("http://www.geocaching.com/images/attributes/flashlight", 44);
		attrIds.put("http://www.geocaching.com/images/attributes/landf", 45);
		attrIds.put("http://www.geocaching.com/images/attributes/rv", 46);
		attrIds.put("http://www.geocaching.com/images/attributes/field_puzzle", 47);
		attrIds.put("http://www.geocaching.com/images/attributes/UV", 48);
		attrIds.put("http://www.geocaching.com/images/attributes/snowshoes", 49);
		attrIds.put("http://www.geocaching.com/images/attributes/skiis", 50);
		attrIds.put("http://www.geocaching.com/images/attributes/s-tool", 51);
		attrIds.put("http://www.geocaching.com/images/attributes/nightcache", 52);
		attrIds.put("http://www.geocaching.com/images/attributes/parkngrab", 53);
		attrIds.put("http://www.geocaching.com/images/attributes/AbandonedBuilding", 54);
		attrIds.put("http://www.geocaching.com/images/attributes/hike_short", 55);
		attrIds.put("http://www.geocaching.com/images/attributes/hike_med", 56);
		attrIds.put("http://www.geocaching.com/images/attributes/hike_long", 57);
		attrIds.put("http://www.geocaching.com/images/attributes/fuel", 58);
		attrIds.put("http://www.geocaching.com/images/attributes/food", 59);
		attrIds.put("http://www.geocaching.com/images/attributes/wirelessbeacon", 60);
		attrIds.put("http://www.geocaching.com/images/attributes/partnership", 61);
		attrIds.put("http://www.geocaching.com/images/attributes/seasonal", 62);
		attrIds.put("http://www.geocaching.com/images/attributes/touristOK", 63);
		attrIds.put("http://www.geocaching.com/images/attributes/treeclimbing", 64);
		attrIds.put("http://www.geocaching.com/images/attributes/frontyard", 65);
		attrIds.put("http://www.geocaching.com/images/attributes/teamwork", 66);
	}
	
	/****************************/
	/*      PARCELABLE PART     */
	/****************************/
	
    public static final Parcelable.Creator<PointGeocachingAttributes> CREATOR = new Parcelable.Creator<PointGeocachingAttributes>() {
        public PointGeocachingAttributes createFromParcel(Parcel in) {
            return new PointGeocachingAttributes(in);
        }

        public PointGeocachingAttributes[] newArray(int size) {
            return new PointGeocachingAttributes[size];
        }
    };
    
    public PointGeocachingAttributes(Parcel in) {
    	switch (in.readInt()) {
    	case 0:
    		id = in.readInt();
    		break;
    	}
    }
    
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(VERSION);
		dest.writeInt(id);
	}
	
	@Override
	public int describeContents() {
		return 0;
	}
}
