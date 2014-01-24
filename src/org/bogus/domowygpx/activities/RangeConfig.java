package org.bogus.domowygpx.activities;

import android.util.Log;

public class RangeConfig
{
    private final static String LOG_TAG = "RangeConfig";
    
    private final static int MIN_VALUE = 1;
    private final static int MAX_VALUE = 5;
    private int min, max;
    
    protected int ensureRange(int val)
    {
        if (val == -1){
            return -1;
        }
        return Math.max(MIN_VALUE, Math.min(val, MAX_VALUE));
    }

    public void setMin(final int min)
    {
        this.min = ensureRange(min);
        if (min == -1){
            max = -1;
        }
    }

    public void setMax(final int max)
    {
        this.max = ensureRange(max);
        if (max == -1){
            min = -1;
        }
    }

    public void validate()
    {
        if (min > max){
            int min2 = min;
            min = max;
            max = min2;
        }
        if (min == MIN_VALUE && max == MAX_VALUE){
            setAll();
        }
    }    
    
    public boolean isAllSet()
    {
        validate();
        return min == -1 && max == -1;
    }    
    
    public void setAll()
    {
        min = max = -1;
    }
    
    /**
     * Parses range config from preferences string.
     * @param config
     * @see #serializeToConfigString()
     */
    public void parseFromConfigString(String config)
    {
        if (config == null || config.length() == 0 || "ALL".equals(config)){
            setAll();
        } else {
            try{
                String[] t = config.split("\\Q-");
                setMin(Integer.parseInt(t[0]));
                setMax(Integer.parseInt(t[1]));
                validate();
            }catch(Exception e){
                Log.e(LOG_TAG, "Failed to parse task=" + config, e);
            }
        }
    }

    /**
     * Serialize range config to string, that can be saved in user preferences
     * @return
     * @see #parseFromConfigString(String)
     */
    public String serializeToConfigString()
    {
        if (isAllSet()){
            return "ALL";
        } else {
            return serializeToWebServiceString();
        }
    }

    /**
     * Serialize range to web service argument
     * @return
     */
    public String serializeToWebServiceString()
    {
        return isAllSet() ? null : min + "-" + max;
    }    
    
    public int getMin()
    {
        return min;
    }

    public int getMax()
    {
        return max;
    }

}
