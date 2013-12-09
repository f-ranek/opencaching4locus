package org.bogus.domowygpx.activities;

import java.util.Map;

import org.bogus.android.AndroidUtils;
import org.bogus.android.ButtonPreference;
import org.bogus.android.FolderPreference;
import org.bogus.android.FolderPreferenceHelperActivity;
import org.bogus.android.IntListPreference;
import org.bogus.domowygpx.application.Application;
import org.bogus.geocaching.egpx.R;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.text.TextUtils;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. 
 * Settings are always presented as a single list. 
 */ 
public class SettingsActivity extends PreferenceActivity implements FolderPreferenceHelperActivity
{
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
        addPreferencesFromResource(R.xml.pref_header);
        
        PreferenceCategory fakeHeader = new PreferenceCategory(this); // o-rzesz-ku
        fakeHeader.setTitle(R.string.pref_title_my_account);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_my_account);

        fakeHeader = new PreferenceCategory(this);
        fakeHeader.setTitle(R.string.pref_title_downloads);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_downloads);

        fakeHeader = new PreferenceCategory(this); 
        fakeHeader.setTitle(R.string.pref_title_directories);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_directories);
        
        Preference downloadImagesStrategyPref = findPreference("downloadImagesStrategy");
        CompoundPreferenceChangeListener.add(downloadImagesStrategyPref, 
            new Preference.OnPreferenceChangeListener(){
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue)
            {
                Editor editor = preference.getSharedPreferences().edit();
                editor.putString("Locus.downloadImagesStrategy", AndroidUtils.toString(newValue));
                editor.commit();
                return true;
            }});
        
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        final Map<String, ?> allConfigValues = getPreferenceManager().getSharedPreferences().getAll();
        setupPrefereneHierarchy(preferenceScreen, allConfigValues);
        
        ButtonPreference reloadTargetDirsPref = (ButtonPreference)findPreference("reloadTargetDirs");
        reloadTargetDirsPref.setOnClickListener(new ButtonPreference.OnClickListener()
        {
            @Override
            public void onClick(ButtonPreference pref)
            {
                final Application app = (Application)getApplication();
                app.initSaveDirectories();
                int count = preferenceScreen.getPreferenceCount();
                final Map<String, ?> config = getPreferenceManager().getSharedPreferences().getAll();
                for (int i=0; i<count; i++){
                    Preference item = preferenceScreen.getPreference(i);
                    if (item instanceof FolderPreference){
                        final String key = item.getKey();
                        final Object value = config.get(key);
                        sBindPreferenceSummaryToValueListener.onPreferenceChange(item, value);
                    }
                }
            }
        });
        
        // XXX load UI automatically!!!!
    }

    private void setupPrefereneHierarchy(PreferenceGroup group, Map<String, ?> values)
    {
        int count = group.getPreferenceCount();
        for (int i=0; i<count; i++){
            Preference item = group.getPreference(i);
            if (item instanceof PreferenceGroup){
                PreferenceGroup group2 = ((PreferenceGroup)item);
                setupPrefereneHierarchy(group2, values);
            } else {
                final String key = item.getKey();
                if (TextUtils.isEmpty(item.getSummary())) {
                    CompoundPreferenceChangeListener.add(item, sBindPreferenceSummaryToValueListener);
                    final Object value = values.get(key);
                    sBindPreferenceSummaryToValueListener.onPreferenceChange(item, value);
                }
                if (item instanceof FolderPreference){
                    ((FolderPreference)item).setFolderPreferenceHelperActivity(this);
                }
            }
        }
    }
    
    @Override
    public boolean onIsMultiPane()
    {
        return false;
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = 
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
            } else 
            if (preference instanceof IntListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                IntListPreference listPreference = (IntListPreference)preference;
                Integer intValue2 = (Integer)value;
                int index = listPreference.findIndexOfValue(intValue2);

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
        CompoundPreferenceChangeListener.add(preference, sBindPreferenceSummaryToValueListener);
        SharedPreferences preferences = preference.getPreferenceManager().getSharedPreferences();
        
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, 
            preferences.getString(preference.getKey(), ""));
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
    
    static class CompoundPreferenceChangeListener implements Preference.OnPreferenceChangeListener
    {
        private Preference.OnPreferenceChangeListener[] listeners;

        public CompoundPreferenceChangeListener(Preference.OnPreferenceChangeListener... listeners)
        {
            this.listeners = listeners;
        }
        
        public static void add(Preference preference, 
            Preference.OnPreferenceChangeListener listener)
        {
            if (preference == null || listener == null){
                throw new NullPointerException();
            }
            
            final Preference.OnPreferenceChangeListener prev = preference.getOnPreferenceChangeListener();
            if (prev == null){
                preference.setOnPreferenceChangeListener(listener);
            } else 
            if (prev instanceof CompoundPreferenceChangeListener){
                CompoundPreferenceChangeListener cpl = (CompoundPreferenceChangeListener)prev;
                Preference.OnPreferenceChangeListener[] listeners = 
                        new Preference.OnPreferenceChangeListener[cpl.listeners.length+1];
                System.arraycopy(cpl.listeners, 0, listeners, 0, cpl.listeners.length);
                listeners[listeners.length-1] = listener;
                preference.setOnPreferenceChangeListener(new CompoundPreferenceChangeListener(listeners));
            } else {
                preference.setOnPreferenceChangeListener(new CompoundPreferenceChangeListener(prev, listener));
            }
        }
        
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue)
        {
            boolean result = true;
            for(Preference.OnPreferenceChangeListener listener: listeners){
                if (!listener.onPreferenceChange(preference, newValue)){
                    result = false;
                }
            }
            return result;
        }
        
    }
}
