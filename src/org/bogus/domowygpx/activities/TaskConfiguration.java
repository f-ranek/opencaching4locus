package org.bogus.domowygpx.activities;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.commons.io.FileUtils;
import org.bogus.ToStringBuilder;
import org.bogus.domowygpx.utils.LocationUtils;
import org.bogus.domowygpx.utils.Pair;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Parcel;
import android.os.Parcelable;

public class TaskConfiguration implements java.io.Serializable, Parcelable
{
    private static final long serialVersionUID = 8437257680737553390L;

    public final static String DOWNLOAD_IMAGES_STRATEGY_NEVER = "never"; 
    public final static String DOWNLOAD_IMAGES_STRATEGY_ALWAYS = "always";
    public final static String DOWNLOAD_IMAGES_STRATEGY_ON_WIFI = "onlyOnWIFI";
    
    public final static String FOUND_STRATEGY_SKIP = "skip";
    public final static String FOUND_STRATEGY_MARK = "mark";

    private String latitude;
    private String longitude;
    private String maxNumOfCaches;
    private String maxCacheDistance;
    private String userName;
    
    private String foundStrategy;
    private String downloadImagesStrategy;
    
    private String targetFileName;
    private String targetDirName;
    
    private String sourceCachesList;
    
    private boolean hasGeoLocation;
    private double outLatitude;
    private double outLongitude;
    private int outMaxNumOfCaches;
    private double outMaxCacheDistance;
    
    private boolean outDownloadImages;
    private File outTargetFileName;
    private File outTargetDirName;
    
    private List<String> outSourceCaches;
    
    private List<Pair<String, String>> errors;
    private List<Pair<String, String>> warnings;
    private List<String> modifiedFields;
    
    protected boolean hasErrorInField(String code)
    {
        for (Pair<String, String> error : errors){
            if (error.first == code || (error.first != null && error.first.equals(code))){
                return true;
            }
        }
        return false;
    }
    
    public void parseAndValidate(Context context)
    {
        errors = new ArrayList<Pair<String,String>>();
        warnings = new ArrayList<Pair<String,String>>(2);
        modifiedFields = new ArrayList<String>(2);
        
        hasGeoLocation = (latitude != null && latitude.length() > 0) || 
                (longitude != null && longitude.length() > 0);
        if (hasGeoLocation){
            try{
                Pair<Integer, Double> lat = LocationUtils.parseLocation(latitude);
                Pair<Integer, Double> lon = LocationUtils.parseLocation(longitude);
                
                if (lat.first == LocationUtils.FORMAT_WGS84_LON || lon.first == LocationUtils.FORMAT_WGS84_LAT){
                    throw new IllegalArgumentException();
                }
                
                outLatitude = lat.second;
                outLongitude = lon.second;
            }catch(IllegalArgumentException e){
                errors.add(Pair.makePair("LOCATION", "Położenie geograficzne jest w niepoprwnym formacie"));
            }catch(NullPointerException e){
                errors.add(Pair.makePair("LOCATION", "Położenie geograficzne jest niepełne"));
            }
        } else {
            outLatitude = outLongitude = 0;
            errors.add(Pair.makePair("LOCATION", "Brak położenia geograficznego"));
        }
        
        outMaxNumOfCaches = -1;
        if (maxNumOfCaches != null && maxNumOfCaches.length() > 0){
            try{
                outMaxNumOfCaches = Integer.parseInt(maxNumOfCaches);
                if (outMaxNumOfCaches < 1 || outMaxNumOfCaches > 500){
                    warnings.add(Pair.makePair("LIMIT", "Limit musi być w przedziale 1 do 500"));
                    modifiedFields.add("LIMIT");
                    if (outMaxNumOfCaches < 1){
                        outMaxNumOfCaches = 1;
                    } else {
                        outMaxNumOfCaches = 500;
                    }
                }
            }catch(NumberFormatException nfe){
                errors.add(Pair.makePair("LIMIT", "Limit musi być w przedziale 1 do 500"));
            }
        }
        outMaxCacheDistance = -1;
        if (maxCacheDistance != null && maxCacheDistance.length() > 0){
            boolean meters = false;
            String mcd;
            if (maxCacheDistance.endsWith("km")){
                mcd = maxCacheDistance.substring(0, maxCacheDistance.length()-2);
            } else if (maxCacheDistance.endsWith("m")){
                mcd = maxCacheDistance.substring(0, maxCacheDistance.length()-1);
                meters = true;
            } else {
                mcd = maxCacheDistance;
            }
            try{
                outMaxCacheDistance = Double.parseDouble(mcd.trim());
                if (meters){
                    outMaxCacheDistance = outMaxCacheDistance / 1000.0;
                }
                if (outMaxCacheDistance < 0.02){
                    outMaxCacheDistance = 0.02;
                    modifiedFields.add("MAX_CACHE_DISTANCE");
                    warnings.add(Pair.makePair("MAX_CACHE_DISTANCE", "Minimalna odległość to 20m"));
                } else
                if (outMaxCacheDistance > 10 && outMaxNumOfCaches != -1){
                    outMaxNumOfCaches = 500;
                    modifiedFields.add("LIMIT");
                    warnings.add(Pair.makePair("MAX_CACHE_DISTANCE", "Ograniczono limit do 500 keszy"));
                }
            }catch(NumberFormatException nfe){
                errors.add(Pair.makePair("MAX_CACHE_DISTANCE", "Niepoprawna wartość odległości"));
            }
        }
        
        if (userName == null || userName.length() == 0){
            userName = foundStrategy = null;
        }
        if (foundStrategy != null){
            if (!foundStrategy.equals(FOUND_STRATEGY_SKIP) && !foundStrategy.equals(FOUND_STRATEGY_MARK)){
                errors.add(Pair.makePair("FOUND_STRATEGY", "Niepoprawna wartość strategii oznaczania znalezionych skrzynek"));
            }
        } else {
            userName = null;
        }
        
        if (targetFileName == null || targetFileName.length() == 0){
            outTargetFileName = null;
            errors.add(Pair.makePair("TARGET_FILE", "Brak nazwy docelowego pliku"));
        } else {
            outTargetFileName = new File(targetFileName);
            final File _parent = outTargetFileName.getParentFile();
            if (!outTargetFileName.isAbsolute()){
                warnings.add(Pair.makePair("TARGET_FILE", "Nazwa docelowego pliku nie jest pełną ścieżką"));
                outTargetFileName = outTargetFileName.getAbsoluteFile();
                targetFileName = outTargetFileName.getPath();
                modifiedFields.add("TARGET_FILE");
            }
            if (outTargetFileName.isDirectory()){
                errors.add(Pair.makePair("TARGET_FILE", "Docelowy plik istnieje, i jest katalogiem"));
            } else if (outTargetFileName.exists()){
                if (!outTargetFileName.canWrite() || !_parent.canWrite()){
                    errors.add(Pair.makePair("TARGET_FILE", "Docelowy plik istnieje, i nie można go zastąpić"));
                } else {
                    warnings.add(Pair.makePair("TARGET_FILE", "Docelowy plik istnieje"));
                }
            } else {
                _parent.mkdirs();
                if (!_parent.exists()){
                    errors.add(Pair.makePair("TARGET_FILE", "Nie można utworzyć katalogu na docelowy plik"));
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
                errors.add(Pair.makePair("DOWNLOAD_IMAGES_STRATEGY", "Niepoprawna wartość strategii pobierania obrazów"));
            }
            //if (outDownloadImages){
            // we still need this directory for images embed in GPX data: uris 
                if (targetDirName == null || targetDirName.length() == 0){
                    targetDirName = "images";
                    modifiedFields.add("TARGET_DIR");
                }
                outTargetDirName = new File(targetDirName);
                if (!outTargetDirName.isAbsolute() && !hasErrorInField("TARGET_FILE")){
                    outTargetDirName = new File(outTargetFileName.getParentFile(), targetDirName);
                }
                if (outTargetDirName.isAbsolute()){
                    outTargetDirName.mkdirs();
                    if (!outTargetDirName.exists()){
                        errors.add(Pair.makePair("TARGET_DIR", "Nie można utworzyć katalogu na pobierane obrazy"));                        
                    } else if (!outTargetDirName.canWrite()){
                        errors.add(Pair.makePair("TARGET_DIR", "Nie można zapisywać plików w katalogu obrazów"));
                    }
                }
            //} else {
            //    outTargetDirName = null;
            //}
        }
        if (sourceCachesList != null && sourceCachesList.length() > 0){
            final File f = new File(sourceCachesList);
            String sourceCaches = null;
            if (f.exists()){
                long len = f.length();
                if (len > 20*1024){
                    errors.add(Pair.makePair("CACHE_LIST", "Plik z listą keszy jest zbyt duży"));
                }
                try{
                    sourceCaches = FileUtils.readFileToString(f);
                }catch(IOException e){
                    errors.add(Pair.makePair("CACHE_LIST", "Nie można odczytać listy keszy z pliku"));
                }
            } else {
                sourceCaches = sourceCachesList;
            }
            if (sourceCaches != null){
                String[] caches = sourceCaches.split("[^A-Za-z0-9]+");
                if (caches.length == 0){
                    warnings.add(Pair.makePair("CACHE_LIST", "Brak keszy na liście"));
                } else {
                    outSourceCaches = new ArrayList<String>(Math.min(caches.length, 500));
                    for (String cacheCode : caches){
                        cacheCode = cacheCode.toUpperCase(Locale.US);
                        if (!outSourceCaches.contains(cacheCode)){
                            outSourceCaches.add(cacheCode);
                        }
                        if (outSourceCaches.size() > 500){
                            warnings.add(Pair.makePair("CACHE_LIST", "Lista keszy do pobrania została ograniczona do 500 pozycji"));
                        }
                    }
                }
            }
        }
        
        if (hasGeoLocation  && 
                outMaxCacheDistance <= 0 && 
                outMaxNumOfCaches < 0)
        {
            errors.add(Pair.makePair((String)null, "Wprowadź kryteria limitujące wyszukiwanie"));
            return ;
        }
        
        if ((outSourceCaches == null || outSourceCaches.isEmpty()) && !hasGeoLocation)
        {
            errors.add(Pair.makePair((String)null, "Wprowadź kryteria wyszukiwania lub listę keszy"));
            return ;
        }
    }

    public String getLatitude()
    {
        return latitude;
    }

    public String getLongitude()
    {
        return longitude;
    }

    public String getMaxNumOfCaches()
    {
        return maxNumOfCaches;
    }

    public String getMaxCacheDistance()
    {
        return maxCacheDistance;
    }

    public String getUserName()
    {
        return userName;
    }

    public String getFoundStrategy()
    {
        return foundStrategy;
    }

    public String getDownloadImagesStrategy()
    {
        return downloadImagesStrategy;
    }

    public String getTargetFileName()
    {
        return targetFileName;
    }

    public String getTargetDirName()
    {
        return targetDirName;
    }

    public String getSourceCachesList()
    {
        return sourceCachesList;
    }

    public boolean isHasGeoLocation()
    {
        return hasGeoLocation;
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

    public List<String> getOutSourceCaches()
    {
        return outSourceCaches != null ? outSourceCaches : Collections.<String>emptyList();
    }

    public List<Pair<String, String>> getErrors()
    {
        return errors;
    }

    public List<Pair<String, String>> getWarnings()
    {
        return warnings;
    }

    public List<String> getModifiedFields()
    {
        return modifiedFields;
    }

    public void setLatitude(String latitude)
    {
        this.latitude = latitude;
    }

    public void setLongitude(String longitude)
    {
        this.longitude = longitude;
    }

    public void setMaxNumOfCaches(String maxNumOfCaches)
    {
        this.maxNumOfCaches = maxNumOfCaches;
    }

    public void setMaxCacheDistance(String maxCacheDistance)
    {
        this.maxCacheDistance = maxCacheDistance;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public void setFoundStrategy(String foundStrategy)
    {
        this.foundStrategy = foundStrategy;
    }

    public void setDownloadImagesStrategy(String downloadImagesStrategy)
    {
        this.downloadImagesStrategy = downloadImagesStrategy;
    }

    public void setTargetFileName(String targetFileName)
    {
        this.targetFileName = targetFileName;
    }

    public void setTargetDirName(String targetDirName)
    {
        this.targetDirName = targetDirName;
    }

    public void setSourceCachesList(String sourceCachesList)
    {
        this.sourceCachesList = sourceCachesList;
    }

    public boolean isOutDownloadImages()
    {
        return outDownloadImages;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((downloadImagesStrategy == null) ? 0 : downloadImagesStrategy.hashCode());
        result = prime * result + ((foundStrategy == null) ? 0 : foundStrategy.hashCode());
        result = prime * result + ((latitude == null) ? 0 : latitude.hashCode());
        result = prime * result + ((longitude == null) ? 0 : longitude.hashCode());
        result = prime * result + ((maxCacheDistance == null) ? 0 : maxCacheDistance.hashCode());
        result = prime * result + ((maxNumOfCaches == null) ? 0 : maxNumOfCaches.hashCode());
        result = prime * result + ((targetDirName == null) ? 0 : targetDirName.hashCode());
        result = prime * result + ((targetFileName == null) ? 0 : targetFileName.hashCode());
        result = prime * result + ((userName == null) ? 0 : userName.hashCode());
        result = prime * result + ((outSourceCaches == null) ? 0 : outSourceCaches.hashCode());
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
        if (downloadImagesStrategy == null) {
            if (other.downloadImagesStrategy != null)
                return false;
        } else if (!downloadImagesStrategy.equals(other.downloadImagesStrategy))
            return false;
        if (foundStrategy == null) {
            if (other.foundStrategy != null)
                return false;
        } else if (!foundStrategy.equals(other.foundStrategy))
            return false;
        if (latitude == null) {
            if (other.latitude != null)
                return false;
        } else if (!latitude.equals(other.latitude))
            return false;
        if (longitude == null) {
            if (other.longitude != null)
                return false;
        } else if (!longitude.equals(other.longitude))
            return false;
        if (maxCacheDistance == null) {
            if (other.maxCacheDistance != null)
                return false;
        } else if (!maxCacheDistance.equals(other.maxCacheDistance))
            return false;
        if (maxNumOfCaches == null) {
            if (other.maxNumOfCaches != null)
                return false;
        } else if (!maxNumOfCaches.equals(other.maxNumOfCaches))
            return false;
        if (targetDirName == null) {
            if (other.targetDirName != null)
                return false;
        } else if (!targetDirName.equals(other.targetDirName))
            return false;
        if (targetFileName == null) {
            if (other.targetFileName != null)
                return false;
        } else if (!targetFileName.equals(other.targetFileName))
            return false;
        if (userName == null) {
            if (other.userName != null)
                return false;
        } else if (!userName.equals(other.userName))
            return false;
        if (outSourceCaches == null) {
            if (other.outSourceCaches != null)
                return false;
        } else if (!outSourceCaches.equals(other.outSourceCaches))
            return false;
        return true;
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
        
        dest.writeString(foundStrategy);
        dest.writeString(downloadImagesStrategy);
        
        dest.writeString(targetFileName);
        dest.writeString(targetDirName);
        
        dest.writeString(sourceCachesList);
        
        dest.writeDouble(outLatitude);
        dest.writeDouble(outLongitude);
        dest.writeByte((byte)(hasGeoLocation ? 1 : 0));

        dest.writeString(userName);
        dest.writeInt(outMaxNumOfCaches);
        dest.writeDouble(outMaxCacheDistance);
        
        dest.writeByte((byte)(outDownloadImages ? 1 : 0));
        dest.writeString(outTargetFileName == null ? null : outTargetFileName.getPath());
        dest.writeString(outTargetDirName == null ? null : outTargetDirName.getPath());
        dest.writeStringList(outSourceCaches);
        
    }
    
    protected static TaskConfiguration fromParcel(Parcel src)
    {
        TaskConfiguration result = new TaskConfiguration();
        
        result.latitude = src.readString();
        result.longitude = src.readString();
        result.maxNumOfCaches = src.readString();
        result.maxCacheDistance = src.readString();
        
        result.foundStrategy = src.readString();
        result.downloadImagesStrategy = src.readString();
        
        result.targetFileName = src.readString();
        result.targetDirName = src.readString();

        result.sourceCachesList = src.readString();
        
        result.outLatitude = src.readDouble();
        result.outLongitude = src.readDouble();
        result.hasGeoLocation = src.readByte() == 1;

        result.userName = src.readString();
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
        result.outSourceCaches = src.createStringArrayList();

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

    @Override
    public String toString()
    {
        ToStringBuilder builder = new ToStringBuilder(this);
        builder.add("latitude", latitude);
        builder.add("longitude", longitude);
        builder.add("maxNumOfCaches", maxNumOfCaches);
        builder.add("maxCacheDistance", maxCacheDistance);
        builder.add("userName", userName);
        builder.add("foundStrategy", foundStrategy);
        builder.add("downloadImagesStrategy", downloadImagesStrategy);
        builder.add("targetFileName", targetFileName);
        builder.add("targetDirName", targetDirName);
        builder.add("sourceCachesList", sourceCachesList);
        builder.add("hasGeoLocation", hasGeoLocation);
        if (hasGeoLocation){
            builder.add("outLatitude", outLatitude);
            builder.add("outLongitude", outLongitude);
        }
        builder.add("outMaxNumOfCaches", outMaxNumOfCaches, -1);
        builder.add("outMaxCacheDistance", outMaxCacheDistance, -1.0);
        builder.add("outDownloadImages", outDownloadImages);
        builder.add("outTargetFileName", outTargetFileName);
        builder.add("outTargetDirName", outTargetDirName);
        builder.add("outSourceCaches", outSourceCaches);
        builder.add("errors", errors);
        builder.add("warnings", warnings);
        builder.add("modifiedFields", modifiedFields);
        return builder.toString();
    }
}
