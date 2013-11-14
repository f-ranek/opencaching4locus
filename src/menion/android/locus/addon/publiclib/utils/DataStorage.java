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

package menion.android.locus.addon.publiclib.utils;

import java.util.ArrayList;

import menion.android.locus.addon.publiclib.geoData.PointsData;

public class DataStorage {

	private static ArrayList<PointsData> mData;
	
	public static ArrayList<PointsData> getData() {
		return mData;
	}
	
	public static void setData(ArrayList<PointsData> data) {
		DataStorage.mData = data;
	}
	
	public static void setData(PointsData data) {
		DataStorage.mData = new ArrayList<PointsData>();
		DataStorage.mData.add(data);
	}
	
	public static void clearData() {
		DataStorage.mData.clear();
		DataStorage.mData = null;
	}
}
