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

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Parcel;
import android.os.Parcelable;

public class PointsData implements Parcelable {

	private static final int VERSION = 0;
	
	// Unique name. PointsData send to Locus with same name will be overwrite in Locus
	private String mName;
	// icon applied to whole PointsData
	private Bitmap mBitmap;
	// ArrayList of all points stored in this object
	private ArrayList<Point> mPoints;
	
	public PointsData(String uniqueName) {
		this.mName = uniqueName;
		this.mBitmap = null;
		this.mPoints = new ArrayList<Point>();
	}
	
	public String getName() {
		return mName;
	}
	
	public Bitmap getBitmap() {
		return mBitmap;
	}
	
	public void setBitmap(Bitmap bitmap) {
		this.mBitmap = bitmap;
	}

	public void addPoint(Point point) {
		this.mPoints.add(point);
	}
	
	public ArrayList<Point> getPoints() {
		return mPoints;
	}

	/****************************/
	/*      PARCELABLE PART     */
	/****************************/
	
    public static final Parcelable.Creator<PointsData> CREATOR = new Parcelable.Creator<PointsData>() {
        public PointsData createFromParcel(Parcel in) {
            return new PointsData(in);
        }

        public PointsData[] newArray(int size) {
            return new PointsData[size];
        }
    };
    
	@Override
	public int describeContents() {
		return 0;
	}

    @SuppressWarnings("unchecked")
	private PointsData(Parcel in) {
    	switch (in.readInt()) {
    	case 0:
    		mName = in.readString();
    		int size = in.readInt();
    		if (size > 0) {
    			byte[] data = new byte[size];
    			in.readByteArray(data);
    			mBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
    		} else {
    			mBitmap = null;
    		}
    		
    		mPoints = in.readArrayList(Point.class.getClassLoader());
    		break;
    	}
    }
    
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(VERSION);
		dest.writeString(mName);
		if (mBitmap == null) {
			dest.writeInt(0);
		} else {
			byte[] data = getBitmapAsByte(mBitmap);
			if (data == null || data.length == 0) {
				dest.writeInt(0);
			} else {
				dest.writeInt(data.length);
				dest.writeByteArray(data);
			}
		}
		dest.writeList(mPoints);
	}
	
	private static byte[] getBitmapAsByte(Bitmap bitmap) {
		ByteArrayOutputStream baos = null;
		try {
			baos = new ByteArrayOutputStream();
			bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
			return baos.toByteArray();
		} catch (Exception e) {
			return null;
		} finally {
			try {
				if (baos != null){
					baos.close();
					baos = null;
				}
			} catch (Exception e) {}
		}
	}
	
}
