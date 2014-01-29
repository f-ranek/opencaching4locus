package org.bogus.domowygpx.activities;

import android.util.Log;

public class CacheRecommendationsConfig
{
    private final static String LOG_TAG = "CacheRecommendationsCfg";

    private int value;
    private boolean percent;
    private boolean all = true;
    
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
        percent = config.endsWith("%");
        if (percent){
            config = config.substring(0, config.length()-1);
        }
        try{
            int value = Integer.parseInt(config);
            setValue(value);
        }catch(NumberFormatException nfe){
            Log.e(LOG_TAG, "Failed to parse RecommendationsConfig=" + config, nfe);
        }
    }
    
    /**
     * Serialize to string, that can be saved in user preferences
     * @return
     * @see #parseFromConfigString(String)
     */
    public String serializeToConfigString()
    {
        StringBuilder sb = new StringBuilder(8);
        if (isAll()){
            sb.append("ALL|");
        }
        sb.append(value);
        if (percent){
            sb.append('%');
        }
        return sb.toString();
    }
    
    /**
     * Serialize to cache_types web service argument
     * @return
     */
    public String serializeToWebServiceString()
    {
        return isAll() ? null : serializeToConfigString();
    }

    public int getValue()
    {
        return value;
    }

    public boolean isPercent()
    {
        return percent;
    }

    public boolean isAll()
    {
        return all;
    }

    public void setValue(int value)
    {
        this.value = Math.max(0, Math.min(value, 999));
        if (this.value == 0){
            all = true;
        } else {
            if (percent){
                this.value = Math.min(this.value, 100);
            }
        }
    }

    public void setPercent(boolean percent)
    {
        this.percent = percent;
        if (percent){
            this.value = Math.min(this.value, 100);
        }
    }

    public void setAll(boolean all)
    {
        this.all = all;
    }
    

}
