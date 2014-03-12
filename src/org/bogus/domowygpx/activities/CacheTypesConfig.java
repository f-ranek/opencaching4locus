package org.bogus.domowygpx.activities;

import java.util.BitSet;

import org.bogus.geocaching.egpx.R;

/**
 * Responsible for storing data and metadata about available and selected
 * cache types.
 * 
 * @author Boguś
 *
 */
public class CacheTypesConfig
{
    // TODO: make it configurable by okapi server
    private final static int[][] CACHE_TYPES_CONFIG = new int[][]{
        {R.drawable.cache_type_traditional, R.string.cacheTypeTraditional},
        {R.drawable.cache_type_multi, R.string.cacheTypeMulti},
        {R.drawable.cache_type_quiz, R.string.cacheTypeQuiz},
        {R.drawable.cache_type_moving, R.string.cacheTypeMoving},
        {R.drawable.cache_type_virtual, R.string.cacheTypeVirtual},
        {R.drawable.cache_type_unknown, R.string.cacheTypeUnknown},
        {R.drawable.cache_type_event, R.string.cacheTypeEvent},
        {R.drawable.cache_type_webcam, R.string.cacheTypeWebcam},
        {R.drawable.cache_type_own, R.string.cacheTypeOwn},
    };
    private final static String[] CACHE_TYPE_NAMES = new String[] {
        "Traditional",
        "Multi",
        "Quiz",
        "Moving",
        "Virtual",
        "Other",
        "Event",
        "Webcam",
        "Own",
    };
    private BitSet values = new BitSet(CACHE_TYPES_CONFIG.length);
    
    /**
     * Parses from preferences string.
     * @param cacheTypes
     * @see #serializeToConfigString()
     */
    public void parseFromConfigString(String cacheTypes)
    {
        values.clear();
        if (cacheTypes == null || cacheTypes.length() == 0
                || cacheTypes.startsWith("ALL"))
        {
            for (int idx = 0; idx < CACHE_TYPES_CONFIG.length; idx++){
                values.set(idx);
            }
        } 
        String[] types = cacheTypes.split("\\Q|");
        for (String type : types){
            for (int idx = 0; idx < CACHE_TYPES_CONFIG.length; idx++){
                if (type.equals(CACHE_TYPE_NAMES[idx])){
                    values.set(idx);
                    break;
                }
            }
        }
    }
    
    /**
     * Serialize to string, that can be saved in user preferences
     * @return
     * @see #parseFromConfigString(String)
     */
    public String serializeToConfigString()
    {
        StringBuilder result = new StringBuilder();
        if (isAllItemsSet()){
            result.append("ALL"); 
        }
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
    
    /**
     * Serialize to cache_types web service argument
     * @return
     */
    public String serializeToWebServiceString()
    {
        return isAllItemsSet() ? null : serializeToConfigString();
    }
    
    /**
     * Are caches items set?
     * @return
     */
    public boolean isAllItemsSet()
    {
        return getSelectedCount() == CACHE_TYPE_NAMES.length;
    }

    /**
     * Is any cache type (including ALL item) set?
     * @return
     */
    public boolean isAnySet()
    {
        return !values.isEmpty();
    }

    /**
     * Returns count of selected positions, including ALL item
     * @return
     */
    public int getSelectedCount()
    {
        return values.cardinality();
    }
    
    /**
     * Is given type selected?
     * @param index
     * @return
     */
    public boolean get(int index)
    {
        return values.get(index);
    }
    
    /**
     * Changes selection for given type
     * @param index
     * @param value
     */
    public void set(int index, boolean value)
    {
        values.set(index, value);
    }
    
    /**
     * Return the number of configuration items (including ALL item)
     * @return
     */
    public int getCount()
    {
        return CACHE_TYPES_CONFIG.length;
    }
    
    /**
     * Returns array of [icon_id, string_id]. 
     * <br>First dimension - item index, from 0 to {@link #getCount()} exclusive. 
     * Second dimension - icon_id (may be <= 0) and label string_id. Item at index
     * 0 is an ALL item.  
     * @return  
     */
    public int[][] getAndroidConfig()
    {
        return CACHE_TYPES_CONFIG;
    }
    
    /**
     * Returns the string, which can be used as a configuration default value
     * @return
     * @see #parseFromConfigString(String)
     */
    public String getDefaultConfig()
    {
        return "Traditional|Multi|Moving|Virtual|Other|Quiz";
    }
}
