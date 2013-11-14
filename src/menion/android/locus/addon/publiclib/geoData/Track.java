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

import java.util.ArrayList;

import menion.android.locus.addon.publiclib.LocusUtils;

import android.graphics.Color;
import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;

public class Track implements Parcelable {

	private static final int VERSION = 1;
	
	/* name of object, have to be unique */
	private String mName;
	/* description of object */
	private String mDesc;
	/* locations of this track */
	private ArrayList<Location> mLocs;
	/* extra points (also may include routing data - TODO) */
	private ArrayList<Point> mPoints;

	/* color of displayed track */
	private int mDrawColor;
	/* width in pixels */
	private float mDrawWidth;
	
	public Track() {
		mDrawColor = Color.BLUE;
		mDrawWidth = 5.0f;
	}

	public String getName() {
		return mName;
	}
	
	public void setName(String name) {
		this.mName = name;
	}
	
	public String getDescription() {
		return mDesc;
	}
	
	public void setDescription(String desc) {
		this.mDesc = desc;
	}

	public void addLocation(Location loc) {
		if (mLocs == null)
			mLocs = new ArrayList<Location>();
		mLocs.add(loc);
	}
	
	public ArrayList<Location> getLocations() {
		return mLocs;
	}
	
	public void setLocations(ArrayList<Location> locs) {
		this.mLocs = locs;
	}
	
	public void addPoint(Point p) {
		if (mPoints == null)
			mPoints = new ArrayList<Point>();
		mPoints.add(p);
	}
	
	public ArrayList<Point> getPoints() {
		return mPoints;
	}
	
	public void setPoints(ArrayList<Point> pts) {
		this.mPoints = pts;
	}
	
	public int getColor() {
		return mDrawColor;
	}
	
	public float getWidth() {
		return mDrawWidth;
	}
	
	public void setStyle(int color, float width) {
		mDrawColor = color;
		mDrawWidth = width;
	}
	
	/****************************/
	/*      PARCELABLE PART     */
	/****************************/
	
    public static final Parcelable.Creator<Track> CREATOR = new Parcelable.Creator<Track>() {
        public Track createFromParcel(Parcel in) {
            return new Track(in);
        }

        public Track[] newArray(int size) {
            return new Track[size];
        }
    };
    
    private Track(Parcel in) {
    	@SuppressWarnings("unused")
		int version = in.readInt();
		// load name
		mName = in.readString();
		// load description
		mDesc = in.readString();
		// load separate location
		int size = in.readInt();
		mLocs = new ArrayList<Location>(size);
		for (int i = 0; i < size; i++) {
			mLocs.add(LocusUtils.readLocation(in));
		}
		// load points
		size = in.readInt();
		mPoints = new ArrayList<Point>(size);
		for (int i = 0; i < size; i++) {
			mPoints.add(Point.CREATOR.createFromParcel(in));
		}
		// read style
		mDrawColor = in.readInt();
		mDrawWidth = in.readFloat();
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
		// write locations
		int size = mLocs == null ? 0 : mLocs.size();
		dest.writeInt(size);
		for (int i = 0; i < size; i++) {
			LocusUtils.writeLocation(dest, mLocs.get(i));
		}
		// write extra points
		size = mPoints == null ? 0 : mPoints.size();
		dest.writeInt(size);
		for (int i = 0; i < size; i++) {
			mPoints.get(i).writeToParcel(dest, flags);
		}
		// write style
		dest.writeInt(mDrawColor);
		dest.writeFloat(mDrawWidth);
	}
}
