package org.bogus.domowygpx.activities;

import java.util.List;
import java.util.Map;

import org.bogus.android.FolderPreference;
import org.bogus.android.FolderPreferenceHelperActivity;
import org.bogus.geocaching.egpx.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends PreferenceActivity implements FolderPreferenceHelperActivity
{
    /**
     * Determines whether to always show the simplified settings UI, where
     * settings are presented in a single list. When false, settings are shown
     * as a master/detail two-pane view on tablets. When true, a single pane is
     * shown on tablets.
     */
    private static final boolean ALWAYS_SIMPLE_PREFS = false;

    private FolderPreferenceHelperActivityListener currActivityResultListener;
    
    
    @SuppressWarnings("deprecation")
    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);

        super.getPreferenceManager().setSharedPreferencesName("egpx");
        setupSimplePreferencesScreen();
    }

    
    /**
     * Shows the simplified settings UI if the device configuration dictates that a simplified, 
     * single-pane UI should be shown.
     */
    @SuppressWarnings("deprecation")
    private void setupSimplePreferencesScreen()
    {
        if (!isSimplePreferences(this)) {
            return;
        }

        addPreferencesFromResource(R.xml.pref_header);
        
        PreferenceCategory fakeHeader = new PreferenceCategory(this); // o-rzesz-ku
        fakeHeader.setTitle(R.string.pref_title_my_account);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_my_account);

        fakeHeader = new PreferenceCategory(this); // o-rzesz-ku
        fakeHeader.setTitle(R.string.pref_title_directories);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_directories);
        
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        final Map<String, ?> allConfigValues = getPreferenceManager().getSharedPreferences().getAll();
        bindPrefereneHierarchyX(preferenceScreen, allConfigValues);
        
        //startActivityForResult(Intent intent, int requestCode);
        
        //int childCount = preferenceScreen.getPreferenceCount()
        //getPreferenceManager().findPreference("x")
        
        //bindPreferenceSummaryToValue(findPreference("userName"));
        //bindPreferenceSummaryToValue(findPreference("foundStrategy"));
        
        // XXX load automatically!!!!
        /*
        // In the simplified UI, fragments are not used at all and we instead
        // use the older PreferenceActivity APIs.

        // Add 'general' preferences.
        addPreferencesFromResource(R.xml.pref_general);

        // Add 'notifications' preferences, and a corresponding header.
        PreferenceCategory fakeHeader = new PreferenceCategory(this);
        fakeHeader.setTitle(R.string.pref_header_notifications);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_notification);

        // Add 'data and sync' preferences, and a corresponding header.
        fakeHeader = new PreferenceCategory(this);
        fakeHeader.setTitle(R.string.pref_header_data_sync);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_data_sync);

        // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
        // their values. When their values change, their summaries are updated
        // to reflect the new value, per the Android Design guidelines.
        bindPreferenceSummaryToValue(findPreference("example_text"));
        bindPreferenceSummaryToValue(findPreference("example_list"));
        bindPreferenceSummaryToValue(findPreference("notifications_new_message_ringtone"));
        bindPreferenceSummaryToValue(findPreference("sync_frequency"));
        */
    }

    private void bindPrefereneHierarchyX(PreferenceGroup group, Map<String, ?> values)
    {
        int count = group.getPreferenceCount();
        for (int i=0; i<count; i++){
            Preference item = group.getPreference(i);
            if (item instanceof PreferenceGroup){
                PreferenceGroup group2 = ((PreferenceGroup)item);
                bindPrefereneHierarchyX(group2, values);
            } else {
                final String key = item.getKey(); 
                item.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
                Object value = values.get(key);
                sBindPreferenceSummaryToValueListener.onPreferenceChange(item, value);
                
                if (item instanceof FolderPreference){
                    ((FolderPreference)item).setFolderPreferenceHelperActivity(this);
                }
                /*
                if (item instanceof TwoStatePreference){
                    Boolean value = (Boolean)values.get(key);
                    if (value != null){
                        sBindPreferenceSummaryToValueListener.onPreferenceChange(item, value);
                    }
                } else 
                if (item instanceof EditTextPreference){
                    String value = (String)values.get(key);
                    if (value != null){
                        sBindPreferenceSummaryToValueListener.onPreferenceChange(item, value);
                    }
                } else 
                if (item instanceof ListPreference){
                    String value = (String)values.get(key);
                    if (value != null){
                        sBindPreferenceSummaryToValueListener.onPreferenceChange(item, value);
                    }
                } else 
                if (item instanceof IntListPreference){
                    Integer value = (Integer)values.get(key);
                    if (value != null){
                        sBindPreferenceSummaryToValueListener.onPreferenceChange(item, value);
                    }
                } else 
                if (item instanceof MultiSelectListPreference){
                    Set<String> value = (Set<String>)values.get(key);
                    if (value != null){
                        sBindPreferenceSummaryToValueListener.onPreferenceChange(item, value);
                    }
                } else {
                    // ups, unknown preference
                }*/
            }
        }
    }
    
    /** {@inheritDoc} */
    @Override
    public boolean onIsMultiPane()
    {
        return isXLargeTablet(this) && !isSimplePreferences(this);
    }

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private static boolean isXLargeTablet(Context context)
    {
        return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) 
                >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Determines whether the simplified settings UI should be shown. This is
     * true if this is forced via {@link #ALWAYS_SIMPLE_PREFS}, or the device
     * doesn't have newer APIs like {@link PreferenceFragment}, or the device
     * doesn't have an extra-large screen. In these cases, a single-pane
     * "simplified" settings UI should be shown.
     */
    private static boolean isSimplePreferences(Context context)
    {
        return ALWAYS_SIMPLE_PREFS || Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB || 
                !isXLargeTablet(context);
    }

    /** {@inheritDoc} */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target)
    {
        if (!isSimplePreferences(this)) {
            loadHeadersFromResource(R.xml.pref_headers, target);
        }
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    static private Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = 
            new Preference.OnPreferenceChangeListener()
    {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value)
        {
            String stringValue = null;
            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference)preference;
                String stringValue2 = value.toString();
                int index = listPreference.findIndexOfValue(stringValue2);

                // Set the summary to reflect the new value.
                if (index >= 0){
                    stringValue = listPreference.getEntries()[index].toString();
                }
            } else {
                if (value != null){
                    stringValue = value.toString();
                }
            }
            preference.setSummary(stringValue);
            /*else if (preference instanceof RingtonePreference) {
                // For ringtone preferences, look up the correct display value
                // using RingtoneManager.
                if (TextUtils.isEmpty(stringValue)) {
                    // Empty values correspond to 'silent' (no ringtone).
                    preference.setSummary(R.string.pref_ringtone_silent);

                } else {
                    Ringtone ringtone = RingtoneManager.getRingtone(preference.getContext(), Uri.parse(stringValue));

                    if (ringtone == null) {
                        // Clear the summary if there was a lookup error.
                        preference.setSummary(null);
                    } else {
                        // Set the summary to reflect the new ringtone display
                        // name.
                        String name = ringtone.getTitle(preference.getContext());
                        preference.setSummary(name);
                    }
                }

            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }*/
            // XXX return false and update manually
            return true;
        }
    };

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     * 
     * @see #sBindPreferenceSummaryToValueListener
     */
    static void bindPreferenceSummaryToValue(Preference preference)
    {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        // XXX co tu się kurwa dzieje?
        
        SharedPreferences preferences = preference.getPreferenceManager().getSharedPreferences();
        //Map<String, ?> values = preferences.getAll()
        
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, 
            preferences.getString(preference.getKey(), ""));
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class MyAccountPreferenceFragment extends PreferenceFragment
    {
        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_my_account);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            //bindPreferenceSummaryToValue(findPreference("userName"));
            bindPreferenceSummaryToValue(findPreference("foundStrategy"));
        }
    }
    
    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragment
    {
        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            bindPreferenceSummaryToValue(findPreference("example_text"));
            bindPreferenceSummaryToValue(findPreference("example_list"));
        }
    }

    /**
     * This fragment shows data and sync preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class DataSyncPreferenceFragment extends PreferenceFragment
    {
        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_data_sync);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            bindPreferenceSummaryToValue(findPreference("sync_frequency"));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        boolean preventDefault = false; 
        if (currActivityResultListener != null){
            preventDefault = currActivityResultListener.onActivityResult(requestCode, resultCode, data);
        } 
        if (!preventDefault){
            super.onActivityResult(requestCode, resultCode, data);
        }
    }


    @Override
    public void register(FolderPreferenceHelperActivityListener self)
    {
        currActivityResultListener = self;
    }


    @Override
    public void unregister(FolderPreferenceHelperActivityListener self)
    {
        if (currActivityResultListener == self){
            currActivityResultListener = null;
        }
    }
}
