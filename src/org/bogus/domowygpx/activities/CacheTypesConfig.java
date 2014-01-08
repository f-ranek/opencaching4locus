package org.bogus.domowygpx.activities;

import java.util.BitSet;

import org.bogus.geocaching.egpx.R;

public class CacheTypesConfig
{
    // TODO: make it configurable by okapi server
    private final static int[][] CACHE_TYPES_CONFIG = new int[][]{
        {-1, R.string.cacheTypeAll},
        {R.drawable.cache_type_traditional, R.string.cacheTypeTraditional},
        {R.drawable.cache_type_multi, R.string.cacheTypeMulti},
        {R.drawable.cache_type_quiz, R.string.cacheTypeQuiz},
        {R.drawable.cache_type_moving, R.string.cacheTypeMoving},
        {R.drawable.cache_type_unknown, R.string.cacheTypeUnknown},
        {R.drawable.cache_type_event, R.string.cacheTypeEvent},
        {R.drawable.cache_type_webcam, R.string.cacheTypeWebcam},
        {R.drawable.cache_type_virtual, R.string.cacheTypeVirtual},
        {R.drawable.cache_type_own, R.string.cacheTypeOwn},
    };
    private final static String[] CACHE_TYPE_NAMES = new String[] {
        "ALL",
        "Traditional",
        "Multi",
        "Quiz",
        "Moving",
        "Other",
        "Event",
        "Webcam",
        "Virtual",
        "Own",
    };
    private BitSet values = new BitSet(CACHE_TYPES_CONFIG.length);
    
    public void parseFromString(String cacheTypes)
    {
        values.clear();
        if (cacheTypes == null || cacheTypes.length() == 0){
            cacheTypes = "ALL";
        } 
        String[] types = cacheTypes.split("\\Q|");
        for (String type : types){
            for (int idx = 0; idx < CACHE_TYPES_CONFIG.length; idx++){
                if (type.equals(CACHE_TYPE_NAMES[idx])){
                    values.set(idx);
                }
            }
        }
    }
    
    public String serializeToString()
    {
        StringBuilder result = new StringBuilder();
        for (int idx = 0; idx < CACHE_TYPE_NAMES.length; idx++){
            if (values.get(idx)){
                if (result.length() > 0){
                    result.append('|');
                }
                result.append(CACHE_TYPE_NAMES[idx]);
            }
        }
        return result.toString();
    }
    
    public boolean isAllSet()
    {
        return values.get(0);
    }
    
    public boolean get(int index)
    {
        return values.get(index);
    }
    
    public void set(int index, boolean value)
    {
        values.set(index, value);
    }
    
    public int getCount()
    {
        return CACHE_TYPES_CONFIG.length;
    }
    
    public int[][] getAndroidConfig()
    {
        return CACHE_TYPES_CONFIG;
    }
}
