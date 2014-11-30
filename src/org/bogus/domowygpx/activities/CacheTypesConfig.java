package org.bogus.domowygpx.activities;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.bogus.domowygpx.oauth.OKAPI;
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
    private final static int[][] CACHE_TYPES_CONFIG_PL = new int[][]{
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
    private final static int[][] CACHE_TYPES_CONFIG_DE = new int[][]{
        {R.drawable.cache_type_traditional, R.string.cacheTypeTraditional},
        {R.drawable.cache_type_drive_in, R.string.cacheTypeDriveIn},
        {R.drawable.cache_type_multi, R.string.cacheTypeMulti},
        {R.drawable.cache_type_quiz, R.string.cacheTypeQuiz},
        {R.drawable.cache_type_math, R.string.cacheTypeMath},
        {R.drawable.cache_type_moving, R.string.cacheTypeMoving},
        {R.drawable.cache_type_virtual, R.string.cacheTypeVirtual},
        {R.drawable.cache_type_event, R.string.cacheTypeEvent},
        {R.drawable.cache_type_webcam, R.string.cacheTypeWebcam},
        {R.drawable.cache_type_unknown, R.string.cacheTypeUnknown},
    };
    final static String[] CACHE_TYPE_NAMES_PL = new String[] {
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
    final static String[] CACHE_TYPE_NAMES_DE = new String[] {
        "Traditional",
        "Drive-In", // XXX
        "Multi",
        "Quiz",
        "Math", // XXX
        "Moving",
        "Virtual",
        "Event",
        "Webcam",
        "Other",
    };
    
    private final static Map<String, int[][]> CACHE_TYPES_CONFIG;
    private final static Map<String, String[]> CACHE_TYPE_NAMES;
    private final static Map<String, String> DEFAULT_CONFIG;
    static {
        CACHE_TYPES_CONFIG = new HashMap<String, int[][]>(2);
        CACHE_TYPES_CONFIG.put("pl", CACHE_TYPES_CONFIG_PL);
        CACHE_TYPES_CONFIG.put("de", CACHE_TYPES_CONFIG_DE);
        CACHE_TYPE_NAMES = new HashMap<String, String[]>(2);
        CACHE_TYPE_NAMES.put("pl", CACHE_TYPE_NAMES_PL);
        CACHE_TYPE_NAMES.put("de", CACHE_TYPE_NAMES_DE);
        DEFAULT_CONFIG = new HashMap<String, String>(2);
        DEFAULT_CONFIG.put("pl", "Traditional|Multi|Moving|Virtual|Other|Quiz");
        DEFAULT_CONFIG.put("de", "Traditional|Multi|Moving|Virtual|Other|Quiz|Drive-In|Math");
    }
    
    private final BitSet values;
    private final Set<String> selectedTypes = new HashSet<String>(16);
    
    private final int[][] cacheTypesConfig;
    private final String[] cacheTypeNames;
    
    private final OKAPI okapi;
    
    public CacheTypesConfig(OKAPI okapi)
    {
        this.okapi = okapi;
        this.cacheTypesConfig = CACHE_TYPES_CONFIG.get(okapi.getBranchCode());
        this.cacheTypeNames = CACHE_TYPE_NAMES.get(okapi.getBranchCode());
        this.values = new BitSet(cacheTypesConfig.length);
    }
    
    /**
     * Parses from preferences string.
     * @param cacheTypes
     * @see #serializeToConfigString()
     */
    public void parseFromConfigString(String cacheTypes)
    {
        values.clear();
        selectedTypes.clear();
        if (cacheTypes == null || cacheTypes.length() == 0 || cacheTypes.startsWith("ALL")){
            values.set(0, cacheTypesConfig.length);
            for (String[] val : CACHE_TYPE_NAMES.values()){
                selectedTypes.addAll(Arrays.asList(val));
            }
            return ;
        }
        String[] typeNames = cacheTypes.split("\\Q|");
        for (String typeName : typeNames){
            if (typeName.startsWith("All_")){
                String branch = typeName.substring(4);
                String[] allTypes = CACHE_TYPE_NAMES.get(branch);
                if (allTypes != null){
                    selectedTypes.addAll(Arrays.asList(allTypes));
                }
                if (branch.equals(okapi.getBranchCode())){
                    values.set(0, cacheTypesConfig.length);
                }
            } else {
                selectedTypes.add(typeName);
                for (int idx = 0; idx < cacheTypesConfig.length; idx++){
                    if (typeName.equals(cacheTypeNames[idx])){
                        values.set(idx);
                        break;
                    }
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
        StringBuilder result = new StringBuilder(128);
        for (int idx = 0; idx < cacheTypeNames.length; idx++){
            if (values.get(idx)){
                selectedTypes.add(cacheTypeNames[idx]);
            } else {
                selectedTypes.remove(cacheTypeNames[idx]);
            }
        }
        for (String typeName : selectedTypes){
            if (result.length() > 0){
                result.append('|');
            }
            result.append(typeName);
        }
        for (Entry<String, String[]> entry : CACHE_TYPE_NAMES.entrySet()){
            boolean hasAll = true;
            for (String type : entry.getValue()){
                if (!selectedTypes.contains(type)){
                    hasAll = false;
                    break;
                }
            }
            if (hasAll){
                result.append('|');
                result.append("All_").append(entry.getKey());
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
        if (isAllItemsSet()){
            return null;
        } else {
            StringBuilder result = new StringBuilder(64);
            for (int idx = 0; idx < cacheTypeNames.length; idx++){
                if (values.get(idx)){
                    if (result.length() > 0){
                        result.append('|');
                    }
                    result.append(cacheTypeNames[idx]);
                }
            }
            return result.toString();
        }
    }
    
    /**
     * Are caches items set?
     * @return
     */
    public boolean isAllItemsSet()
    {
        return getSelectedCount() == cacheTypeNames.length;
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
        return cacheTypesConfig.length;
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
        return cacheTypesConfig;
    }
    
    /**
     * Returns the string, which can be used as a configuration default value
     * @return
     * @see #parseFromConfigString(String)
     */
    public String getDefaultConfig()
    {
        return DEFAULT_CONFIG.get(okapi.getBranchCode());
    }
}
