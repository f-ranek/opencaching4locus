package org.bogus.domowygpx.activities;

import android.util.Log;


/**
 * Responsible for storing data for cache ratings filtering
 * 
 * @author Boguś
 *
 */
public class CacheRatingsConfig
{
    private final static String LOG_TAG = "CacheRatingsConfig";
    
    private int minRating = 4;
    private boolean includeUnrated = true;
    private boolean all;
    
    /**
     * Parses from preferences string.
     * @param config
     * @see #serializeToConfigString()
     */
    public void parseFromConfigString(String config)
    {
        if (config == null || 
                config.length() == 0)
        {
            all = true;
            return ;
        }
        if (config.startsWith("ALL|")){
            all = true;
            config = config.substring(4);
        } else {
            all = false;
        }
        includeUnrated = config.endsWith("|X");
        if (includeUnrated){
            config = config.substring(0, config.length()-2);
        }
        try{
            int value = Integer.parseInt(config);
            setMinRating(value);
        }catch(NumberFormatException nfe){
            Log.e(LOG_TAG, "Failed to parse CacheRatingsConfig=" + config, nfe);
        }
    }
    
    /**
     * Serialize to string, that can be saved in user preferences
     * @return
     * @see #parseFromConfigString(String)
     */
    public String serializeToConfigString()
    {
        StringBuilder sb = new StringBuilder(7);
        if (isAll()){
            sb.append("ALL|");
        }
        sb.append(minRating);
        if (includeUnrated){
            sb.append("|X");
        }
        return sb.toString();
        
    }
    
    /**
     * Serialize to rating web service argument
     * @return
     */
    public String serializeToWebServiceString()
    {
        if (isAll()){ 
            return null;
        }
        StringBuilder sb = new StringBuilder(5);
        sb.append(minRating).append("-5");
        if (includeUnrated){
            sb.append("|X");
        }
        return sb.toString();

    }
    
    /**
     * Returns minimum allowed rating, between 1 and 5
     * @return
     */
    public int getMinRating()
    {
        return minRating;
    }

    /**
     * Return if we want unrated geocaches to be included
     * @return
     */
    public boolean isIncludeUnrated()
    {
        return includeUnrated;
    }

    /**
     * Is ALL ratings set? (i.e. we doesn't care)
     * @return
     */
    public boolean isAll()
    {
        return all;
    }

    public void setMinRating(int minRating)
    {
        this.minRating = Math.max(1, Math.min(5, minRating));
    }

    public void setIncludeUnrated(boolean includeUnrated)
    {
        this.includeUnrated = includeUnrated;
    }

    public void setAll(boolean all)
    {
        this.all = all;
    }
    
    
}
