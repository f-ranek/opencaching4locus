package org.bogus.domowygpx.activities;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.bogus.ToStringBuilder;
import org.bogus.android.AndroidUtils;
import org.bogus.domowygpx.utils.LocationUtils;
import org.bogus.domowygpx.utils.Pair;
import org.bogus.geocaching.egpx.R;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Parcel;
import android.os.Parcelable;

public class TaskConfiguration implements java.io.Serializable, Parcelable
{
    private static final long serialVersionUID = 8437257680737553390L;
    
    private final static Pattern spaces = Pattern.compile("[ \t\u00A0]+");

    public final static String DOWNLOAD_IMAGES_STRATEGY_NEVER = "never"; 
    public final static String DOWNLOAD_IMAGES_STRATEGY_ALWAYS = "always";
    public final static String DOWNLOAD_IMAGES_STRATEGY_ON_WIFI = "onlyOnWIFI";
    
    public final static String FOUND_STRATEGY_SKIP = "skip";
    public final static String FOUND_STRATEGY_MARK = "mark";

    // user input
    private String latitude;
    private String longitude;
    private String maxNumOfCaches;
    private String maxCacheDistance;
    private boolean doLocusImport;
    private String targetFileName;
    private String cacheTypes;
    private String cacheTaskDifficulty;
    private String cacheTerrainDifficulty;
    private String cacheRatings;
    private String cacheRecommendations;

    // config
    private String gpxTargetDirName;
    private String gpxTargetDirNameTemp;
    private String imagesTargetDirName;
    private String downloadImagesStrategy;
    
    private double outLatitude = Double.NaN;
    private double outLongitude = Double.NaN;
    private int outMaxNumOfCaches;
    private double outMaxCacheDistance;
    
    private boolean outDownloadImages;
    private File outTargetFileName;
    private File outTargetDirName;
    
    private List<Pair<String, Integer>> errors;
    private List<Pair<String, Integer>> warnings;
    private List<String> modifiedFields;
    
    protected boolean hasErrorInField(String code)
    {
        for (Pair<String, Integer> error : errors){
            if (error.first == code || (error.first != null && error.first.equals(code))){
                return true;
            }
        }
        return false;
    }
    
    public void initFromConfig(Context context)
    {
        SharedPreferences config = context.getSharedPreferences("egpx", Context.MODE_PRIVATE);
        
        gpxTargetDirName = config.getString("gpxTargetDirName", null);
        gpxTargetDirNameTemp = config.getString("gpxTargetDirNameTemp", null);
        imagesTargetDirName = config.getString("imagesTargetDirName", null);
        downloadImagesStrategy = config.getString("downloadImagesStrategy", TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ON_WIFI);
    }
    
    @SuppressLint("SimpleDateFormat")
    public void parseAndValidate(Context context)
    {
        errors = new ArrayList<Pair<String,Integer>>();
        warnings = new ArrayList<Pair<String,Integer>>(2);
        modifiedFields = new ArrayList<String>(2);
        
        boolean hasInGeoLocation = (latitude != null && latitude.length() > 0) || 
                (longitude != null && longitude.length() > 0);
        boolean hasInGeoLocationOk = false; 
        if (hasInGeoLocation){
            outLatitude = outLongitude = Double.NaN;
            if ((latitude != null && latitude.length() > 0) ^ 
                (longitude != null && longitude.length() > 0))
            {
                errors.add(Pair.makePair("LOCATION", R.string.validationIncompleteLocation));
            } else {
                try{
                    Pair<Integer, Double> lat = LocationUtils.parseLocation(latitude);
                    Pair<Integer, Double> lon = LocationUtils.parseLocation(longitude);
                    
                    if (lat.first == LocationUtils.FORMAT_WGS84_LON || lon.first == LocationUtils.FORMAT_WGS84_LAT){
                        throw new IllegalArgumentException();
                    }
                    
                    outLatitude = lat.second;
                    outLongitude = lon.second;
                    hasInGeoLocationOk = true;
                }catch(IllegalArgumentException e){
                    errors.add(Pair.makePair("LOCATION", R.string.validationInvalidLocation));
                }
            }
        }

        boolean hasOutGeoLocationOk = outLatitude != Double.NaN && outLongitude != Double.NaN;
        
        if (!hasInGeoLocationOk && !hasOutGeoLocationOk){
            outLatitude = outLongitude = Double.NaN;
            if (!hasErrorInField("LOCATION")){
                errors.add(Pair.makePair("LOCATION", R.string.validationMissingLocation));
            }
        }
        
        outMaxNumOfCaches = -1;
        String mnoc = maxNumOfCaches == null ? null : spaces.matcher(maxNumOfCaches).replaceAll("");
        if (mnoc != null && mnoc.length() > 0){
            try{
                outMaxNumOfCaches = Integer.parseInt(mnoc);
                if (outMaxNumOfCaches < 1 || outMaxNumOfCaches > 500){
                    warnings.add(Pair.makePair("CACHE_COUNT_LIMIT", R.string.validationInvalidMaxCachesLimit));
                    modifiedFields.add("CACHE_COUNT_LIMIT");
                    if (outMaxNumOfCaches < 1){
                        outMaxNumOfCaches = 1;
                    } else {
                        outMaxNumOfCaches = 500;
                    }
                }
            }catch(NumberFormatException nfe){
                errors.add(Pair.makePair("CACHE_COUNT_LIMIT", R.string.validationInvalidMaxCachesLimit));
            }
        }
        outMaxCacheDistance = -1;
        String mcd = maxCacheDistance == null ? null : spaces.matcher(maxCacheDistance).replaceAll("");
        if (mcd != null && mcd.length() > 0){
            boolean meters = false;
            if (mcd.endsWith("km")){
                mcd = mcd.substring(0, maxCacheDistance.length()-2);
            } else if (mcd.endsWith("m")){
                mcd = mcd.substring(0, maxCacheDistance.length()-1);
                meters = true;
            }
            try{
                outMaxCacheDistance = Double.parseDouble(mcd.trim());
                if (meters){
                    outMaxCacheDistance = outMaxCacheDistance / 1000.0;
                }
                if (outMaxCacheDistance < 0.02){
                    outMaxCacheDistance = 0.02;
                    modifiedFields.add("MAX_CACHE_DISTANCE");
                    warnings.add(Pair.makePair("MAX_CACHE_DISTANCE", R.string.validationMinCacheDistanceWarning));
                }
            }catch(NumberFormatException nfe){
                errors.add(Pair.makePair("MAX_CACHE_DISTANCE", R.string.validationInvalidCacheDistance));
            }
        }
        
        if (outMaxCacheDistance <= 0 && outMaxNumOfCaches < 0)
        {
            errors.add(Pair.makePair("CACHE_COUNT_LIMIT", R.string.validationInvalidNoCacheDistanceNeitherLimit));
        }
        
        final boolean hasLocus = locus.api.android.utils.LocusUtils.isLocusAvailable(context, 200);
        if (!hasLocus){
            if (doLocusImport){
                doLocusImport = false;
                modifiedFields.add("DO_LOCUS_IMPORT");
            }
        }
        
        outTargetFileName = null;
        if (targetFileName == null || targetFileName.length() == 0){
            if (doLocusImport){
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH.mm'.gpx'");
                final Date now = new Date();
                do{
                    final String fileName = sdf.format(now);
                    outTargetFileName = new File(gpxTargetDirNameTemp, fileName);
                    if (outTargetFileName.exists()){
                        now.setTime(now.getTime()+60*1000);
                    } else {
                        break;
                    }
                }while(true);
            } else {
                if (hasLocus){
                    errors.add(Pair.makePair("TARGET_FILE", R.string.validationMissingFileNameOrLocusImport));
                } else {
                    errors.add(Pair.makePair("TARGET_FILE", R.string.validationMissingFileName));
                }
            }
        } else {
            if (targetFileName.toLowerCase(Locale.US).endsWith(".gpx")){
                outTargetFileName = new File(targetFileName);
            } else {
                outTargetFileName = new File(targetFileName + ".gpx");
            }
            //final 
            if (!outTargetFileName.isAbsolute()){
                outTargetFileName = new File(gpxTargetDirName, outTargetFileName.getPath()); 
                outTargetFileName = outTargetFileName.getAbsoluteFile();
            }
        }
        if (outTargetFileName != null){
            final File _parent = outTargetFileName.getParentFile();
            if (outTargetFileName.isDirectory()){
                errors.add(Pair.makePair("TARGET_FILE", R.string.validationTargetFileIsDir));
            } else if (outTargetFileName.exists()){
                if (!outTargetFileName.canWrite() || _parent != null && !_parent.canWrite()){
                    errors.add(Pair.makePair("TARGET_FILE", R.string.validationTargetFileIsReadOnly));
                } else {
                    warnings.add(Pair.makePair("TARGET_FILE", R.string.validationTargetFileExistsWarning));
                }
            } else {
                if (_parent != null){
                    _parent.mkdirs();
                    if (!_parent.exists()){
                        errors.add(Pair.makePair("TARGET_FILE", R.string.validationCantCreateTargetDir));
                    } else
                    if (!_parent.canWrite()){
                        errors.add(Pair.makePair("TARGET_FILE", R.string.validationTargetDirIsReadOnly));
                    } else
                    if (!_parent.isDirectory()){
                        errors.add(Pair.makePair("TARGET_FILE", R.string.validationTargetDirIsFile));
                    }
                }
            }
        }
        
        outDownloadImages = false;
        if (downloadImagesStrategy != null && downloadImagesStrategy.length() > 0){
            if (downloadImagesStrategy.equals(DOWNLOAD_IMAGES_STRATEGY_NEVER)){
                outDownloadImages = false;
            } else 
            if (downloadImagesStrategy.equals(DOWNLOAD_IMAGES_STRATEGY_ALWAYS)){
                outDownloadImages = true;
            } else
            if (downloadImagesStrategy.equals(DOWNLOAD_IMAGES_STRATEGY_ON_WIFI)){
                // check, if WiFi is enabled and in use    
                ConnectivityManager conectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo wifiInfo = conectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                boolean hasWiFi = wifiInfo.isConnected();
                outDownloadImages = hasWiFi;
            } else {
                errors.add(Pair.makePair("DOWNLOAD_IMAGES_STRATEGY", R.string.validationInvalidImagesDownloadStrategy));
            }
        }

        // we still need this directory for images embed in GPX data: uris 
        outTargetDirName = new File(imagesTargetDirName);
        if (!outTargetDirName.isAbsolute() && outTargetFileName != null){
            outTargetDirName = new File(outTargetFileName.getParentFile(), imagesTargetDirName);
        }
        outTargetDirName.mkdirs();
        if (!outTargetDirName.exists()){
            errors.add(Pair.makePair("TARGET_DIR", R.string.validationCantCreateTargetImagesDir));                        
        } else if (!outTargetDirName.canWrite()){
            errors.add(Pair.makePair("TARGET_DIR", R.string.validationTargetImagesDirIsReadOnly));
        } else if (!outTargetDirName.isDirectory()){
            errors.add(Pair.makePair("TARGET_FILE", R.string.validationTargetImagesDirIsFile));
        }
    }

    public CharSequence getLatitude()
    {
        return latitude;
    }

    public CharSequence getLongitude()
    {
        return longitude;
    }

    public CharSequence getMaxNumOfCaches()
    {
        return maxNumOfCaches;
    }

    public CharSequence getMaxCacheDistance()
    {
        return maxCacheDistance;
    }

    public String getDownloadImagesStrategy()
    {
        return downloadImagesStrategy;
    }

    public CharSequence getTargetFileName()
    {
        return targetFileName;
    }

    public double getOutLatitude()
    {
        return outLatitude;
    }

    public double getOutLongitude()
    {
        return outLongitude;
    }

    public int getOutMaxNumOfCaches()
    {
        return outMaxNumOfCaches;
    }

    public double getOutMaxCacheDistance()
    {
        return outMaxCacheDistance;
    }

    public File getOutTargetFileName()
    {
        return outTargetFileName;
    }

    public File getOutTargetDirName()
    {
        return outTargetDirName;
    }

    public List<Pair<String, Integer>> getErrors()
    {
        return errors;
    }

    public List<Pair<String, Integer>> getWarnings()
    {
        return warnings;
    }

    public List<String> getModifiedFields()
    {
        return modifiedFields;
    }

    public void setLatitude(CharSequence latitude)
    {
        this.latitude = AndroidUtils.toString(latitude);
    }

    public void setLongitude(CharSequence longitude)
    {
        this.longitude = AndroidUtils.toString(longitude);
    }

    public void setMaxNumOfCaches(CharSequence maxNumOfCaches)
    {
        this.maxNumOfCaches = AndroidUtils.toString(maxNumOfCaches);
    }

    public void setMaxCacheDistance(CharSequence maxCacheDistance)
    {
        this.maxCacheDistance = AndroidUtils.toString(maxCacheDistance);
    }

    public void setTargetFileName(CharSequence targetFileName)
    {
        this.targetFileName = AndroidUtils.toString(targetFileName);
    }

    public boolean isOutDownloadImages()
    {
        return outDownloadImages;
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(latitude);
        dest.writeString(longitude);
        dest.writeString(maxNumOfCaches);
        dest.writeString(maxCacheDistance);
        dest.writeByte((byte)(doLocusImport ? 1 : 0));
        dest.writeString(targetFileName);
        dest.writeString(cacheTypes);
        dest.writeString(cacheTaskDifficulty);
        dest.writeString(cacheTerrainDifficulty);
        dest.writeString(cacheRatings);
        dest.writeString(cacheRecommendations);
        
        dest.writeString(gpxTargetDirName);
        dest.writeString(gpxTargetDirNameTemp);
        dest.writeString(imagesTargetDirName);
        dest.writeString(downloadImagesStrategy);
        
        dest.writeDouble(outLatitude);
        dest.writeDouble(outLongitude);

        dest.writeInt(outMaxNumOfCaches);
        dest.writeDouble(outMaxCacheDistance);
        
        dest.writeByte((byte)(outDownloadImages ? 1 : 0));
        dest.writeString(AndroidUtils.toString(outTargetFileName));
        dest.writeString(AndroidUtils.toString(outTargetDirName));
    }
    
    protected static TaskConfiguration fromParcel(Parcel src)
    {
        TaskConfiguration result = new TaskConfiguration();
        
        result.latitude = src.readString();
        result.longitude = src.readString();
        result.maxNumOfCaches = src.readString();
        result.maxCacheDistance = src.readString();
        result.doLocusImport = src.readByte() == 1;
        result.targetFileName = src.readString();
        result.cacheTypes = src.readString();
        result.cacheTaskDifficulty = src.readString();
        result.cacheTerrainDifficulty = src.readString();
        result.cacheRatings = src.readString();
        result.cacheRecommendations = src.readString();
        
        result.gpxTargetDirName = src.readString();
        result.gpxTargetDirNameTemp = src.readString();
        result.imagesTargetDirName = src.readString();
        result.downloadImagesStrategy = src.readString();
        
        result.outLatitude = src.readDouble();
        result.outLongitude = src.readDouble();

        result.outMaxNumOfCaches = src.readInt();
        result.outMaxCacheDistance = src.readDouble();
        
        result.outDownloadImages = src.readByte() == 1;
        String s = src.readString();
        if (s != null){
            result.outTargetFileName = new File(s);
        }
        s = src.readString();
        if (s != null){
            result.outTargetDirName = new File(s);
        }

        return result;
    }

    public static final Parcelable.Creator<TaskConfiguration> CREATOR =
            new Parcelable.Creator<TaskConfiguration>() {
        @Override
        public TaskConfiguration createFromParcel(Parcel src) {
            return TaskConfiguration.fromParcel(src);
        }

        @Override
        public TaskConfiguration[] newArray(int size) {
            return new TaskConfiguration[size];
        }
    };

    public void setDownloadImagesStrategy(String downloadImagesStrategy)
    {
        this.downloadImagesStrategy = downloadImagesStrategy;
    }

    public boolean isDoLocusImport()
    {
        return doLocusImport;
    }

    public void setDoLocusImport(boolean doLocusImport)
    {
        this.doLocusImport = doLocusImport;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + (doLocusImport ? 1231 : 1237);
        result = prime * result + ((downloadImagesStrategy == null) ? 0 : downloadImagesStrategy.hashCode());
        result = prime * result + (outDownloadImages ? 1231 : 1237);
        long temp;
        temp = Double.doubleToLongBits(outLatitude);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(outLongitude);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(outMaxCacheDistance);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        result = prime * result + outMaxNumOfCaches;
        result = prime * result + ((outTargetDirName == null) ? 0 : outTargetDirName.hashCode());
        result = prime * result + ((outTargetFileName == null) ? 0 : outTargetFileName.hashCode());
        result = prime * result + ((cacheTypes == null) ? 0 : cacheTypes.hashCode());
        result = prime * result + ((cacheTaskDifficulty == null) ? 0 : cacheTaskDifficulty.hashCode());
        result = prime * result + ((cacheTerrainDifficulty == null) ? 0 : cacheTerrainDifficulty.hashCode());
        result = prime * result + ((cacheRatings == null) ? 0 : cacheRatings.hashCode());
        result = prime * result + ((cacheRecommendations == null) ? 0 : cacheRecommendations.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof TaskConfiguration))
            return false;
        TaskConfiguration other = (TaskConfiguration)obj;
        if (doLocusImport != other.doLocusImport)
            return false;
        if (downloadImagesStrategy == null) {
            if (other.downloadImagesStrategy != null)
                return false;
        } else if (!downloadImagesStrategy.equals(other.downloadImagesStrategy))
            return false;
        if (outDownloadImages != other.outDownloadImages)
            return false;
        if (Double.doubleToLongBits(outLatitude) != Double.doubleToLongBits(other.outLatitude))
            return false;
        if (Double.doubleToLongBits(outLongitude) != Double.doubleToLongBits(other.outLongitude))
            return false;
        if (Double.doubleToLongBits(outMaxCacheDistance) != Double.doubleToLongBits(other.outMaxCacheDistance))
            return false;
        if (outMaxNumOfCaches != other.outMaxNumOfCaches)
            return false;
        if (outTargetDirName == null) {
            if (other.outTargetDirName != null)
                return false;
        } else if (!outTargetDirName.equals(other.outTargetDirName))
            return false;
        if (outTargetFileName == null) {
            if (other.outTargetFileName != null)
                return false;
        } else if (!outTargetFileName.equals(other.outTargetFileName))
            return false;
        if (cacheTypes == null) {
            if (other.cacheTypes != null)
                return false;
        } else if (!cacheTypes.equals(other.cacheTypes))
            return false;
        if (cacheTaskDifficulty == null) {
            if (other.cacheTaskDifficulty != null)
                return false;
        } else if (!cacheTaskDifficulty.equals(other.cacheTaskDifficulty))
            return false;
        if (cacheTerrainDifficulty == null) {
            if (other.cacheTerrainDifficulty != null)
                return false;
        } else if (!cacheTerrainDifficulty.equals(other.cacheTerrainDifficulty))
            return false;
        if (cacheRatings == null) {
            if (other.cacheRatings != null)
                return false;
        } else if (!cacheRatings.equals(other.cacheRatings))
            return false;
        if (cacheRecommendations == null) {
            if (other.cacheRecommendations != null)
                return false;
        } else if (!cacheRecommendations.equals(other.cacheRecommendations))
            return false;

        return true;
    }

    @Override
    public String toString()
    {
        ToStringBuilder builder = new ToStringBuilder(this);
        builder.add("latitude", latitude);
        builder.add("longitude", longitude);
        builder.add("maxNumOfCaches", maxNumOfCaches);
        builder.add("maxCacheDistance", maxCacheDistance);
        builder.add("cacheTypes", cacheTypes);
        builder.add("cacheTaskDifficulty", cacheTaskDifficulty); 
        builder.add("cacheTerrainDifficulty", cacheTerrainDifficulty); 
        builder.add("cacheRatings", cacheRatings);
        builder.add("cacheRecommendations", cacheRecommendations);
        builder.add("doLocusImport", doLocusImport);
        builder.add("targetFileName", targetFileName);
        builder.add("gpxTargetDirName", gpxTargetDirName);
        builder.add("gpxTargetDirNameTemp", gpxTargetDirNameTemp);
        builder.add("imagesTargetDirName", imagesTargetDirName);
        builder.add("downloadImagesStrategy", downloadImagesStrategy);
        builder.add("outLatitude", outLatitude, Double.NaN);
        builder.add("outLongitude", outLongitude, Double.NaN);
        builder.add("outMaxNumOfCaches", outMaxNumOfCaches);
        builder.add("outMaxCacheDistance", outMaxCacheDistance);
        builder.add("outDownloadImages", outDownloadImages);
        builder.add("outTargetFileName", outTargetFileName);
        builder.add("outTargetDirName", outTargetDirName);
        builder.add("errors", errors);
        builder.add("warnings", warnings);
        builder.add("modifiedFields", modifiedFields);
        return builder.toString();
    }

    public void setOutLongitude(double outLongitude)
    {
        this.outLongitude = outLongitude;
    }

    public void setOutLatitude(double outLatitude)
    {
        this.outLatitude = outLatitude;
    }

    public String getCacheTypes()
    {
        return cacheTypes;
    }

    public void setCacheTypes(String cacheTypes)
    {
        this.cacheTypes = cacheTypes;
    }

    public String getCacheTaskDifficulty()
    {
        return cacheTaskDifficulty;
    }

    public String getCacheTerrainDifficulty()
    {
        return cacheTerrainDifficulty;
    }

    public void setCacheTaskDifficulty(String cacheTaskDifficulty)
    {
        this.cacheTaskDifficulty = cacheTaskDifficulty;
    }

    public void setCacheTerrainDifficulty(String cacheTerrainDifficulty)
    {
        this.cacheTerrainDifficulty = cacheTerrainDifficulty;
    }

    public String getCacheRatings()
    {
        return cacheRatings;
    }

    public String getCacheRecommendations()
    {
        return cacheRecommendations;
    }

    public void setCacheRatings(String cacheRatings)
    {
        this.cacheRatings = cacheRatings;
    }

    public void setCacheRecommendations(String cacheRecommendations)
    {
        this.cacheRecommendations = cacheRecommendations;
    }

}
