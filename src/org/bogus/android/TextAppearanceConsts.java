package org.bogus.android;

import android.util.Log;

public class TextAppearanceConsts
{
    private static final String LOG_TAG = "TextAppearanceConsts";

    public static final int[] styleable_TextAppearance;
    public static final int styleable_TextAppearance_textColor;
    public static final int styleable_TextAppearance_textSize;
    public static final int styleable_TextAppearance_typeface;
    public static final int styleable_TextAppearance_fontFamily;
    public static final int styleable_TextAppearance_textStyle;
    
    static {
        // F*ing freaking code
        int ta[] = null, taTc = 0, taTs = 0, taTf = 0, taFf = 0, taTst = 0; 
        try{
            Class<?> clazz = Class.forName("com.android.internal.R$styleable", false, TextAppearanceConsts.class.getClassLoader());
            ta = (int[])clazz.getField("TextAppearance").get(null);
            taTc = getField(clazz, "TextAppearance_textColor");
            taTs = getField(clazz, "TextAppearance_textSize");
            taTf = getField(clazz, "TextAppearance_typeface");
            taFf = getField(clazz, "TextAppearance_fontFamily");
            taTst = getField(clazz, "TextAppearance_textStyle");
        }catch(Exception e){
            Log.e(LOG_TAG, "Failed to load styleable_TextAppearance", e);
        }
        styleable_TextAppearance = ta;
        styleable_TextAppearance_textColor = taTc;
        styleable_TextAppearance_textSize = taTs;
        styleable_TextAppearance_typeface = taTf;
        styleable_TextAppearance_fontFamily = taFf;
        styleable_TextAppearance_textStyle = taTst;
    }

    private static int getField(Class<?> clazz, String fieldName)
    throws IllegalAccessException
    {
        try{
            return clazz.getField(fieldName).getInt(null);
        }catch(NoSuchFieldException nsfe){
            Log.e(LOG_TAG, nsfe.toString());
            return -1;
        }
    }
    
}
