package org.bogus.domowygpx.activities;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bogus.android.AndroidUtils;
import org.bogus.android.ButtonPreference;
import org.bogus.android.FolderPreference;
import org.bogus.android.FolderPreferenceHelperActivity;
import org.bogus.android.IntListPreference;
import org.bogus.domowygpx.application.Application;
import org.bogus.domowygpx.oauth.OAuth;
import org.bogus.domowygpx.oauth.OKAPI;
import org.bogus.geocaching.egpx.R;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.text.TextUtils;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. 
 * Settings are always presented as a single list. 
 */ 
public class SettingsActivity extends PreferenceActivity implements FolderPreferenceHelperActivity
{
    private FolderPreferenceHelperActivityListener currActivityResultListener;
    private final List<Object> preventGCStorage = new ArrayList<Object>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Application.getInstance(this).showErrorDumpInfo(this);
    }
    
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
        final OAuth oauth = OKAPI.getInstance(SettingsActivity.this).getOAuth();

        addPreferencesFromResource(R.xml.pref_header);
        {
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

            fakeHeader = new PreferenceCategory(this); 
            fakeHeader.setTitle(R.string.pref_title_advanced);
            getPreferenceScreen().addPreference(fakeHeader);
            addPreferencesFromResource(R.xml.pref_advanced);
        }        
        {
            final Preference downloadImagesStrategyPref = findPreference("downloadImagesStrategy");
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
        }
        final SharedPreferences config = getPreferenceManager().getSharedPreferences();
        final Map<String, ?> allConfigValues = config.getAll();
        setupPrefereneHierarchy(getPreferenceScreen(), allConfigValues);
        
        {
            final ButtonPreference reloadTargetDirsPref = (ButtonPreference)findPreference("reloadTargetDirs");
            reloadTargetDirsPref.setOnClickListener(new ButtonPreference.OnClickListener()
            {
                @Override
                public void onClick(ButtonPreference pref)
                {
                    final Application app = (Application)getApplication();
                    app.initSaveDirectories();
    
                    final PreferenceGroup preferenceScreen = getPreferenceScreen();
                    final int count = preferenceScreen.getPreferenceCount();
                    final Map<String, ?> allConfigValues = config.getAll();
                    for (int i=0; i<count; i++){
                        Preference item = preferenceScreen.getPreference(i);
                        if (item instanceof FolderPreference){
                            final String key = item.getKey();
                            final Object value = allConfigValues.get(key);
                            sBindPreferenceSummaryToValueListener.onPreferenceChange(item, value);
                        }
                    }
                }
            });
        }
        
        {
            final ButtonPreference resetLocusDontAskAgain = (ButtonPreference)findPreference("resetLocusDontAskAgain");
            OnSharedPreferenceChangeListener spcl = new OnSharedPreferenceChangeListener()
            {
                
                @Override
                public void onSharedPreferenceChanged(SharedPreferences config, String key)
                {
                    resetLocusDontAskAgain.setEnabled(
                        config.getBoolean("Locus.point_searchWithoutAsking",false) || 
                        config.getBoolean("Locus.search_searchWithoutAsking",false));
                }
            };
            config.registerOnSharedPreferenceChangeListener(spcl);
            spcl.onSharedPreferenceChanged(config, null);
            preventGCStorage.add(spcl); // we must keep a strong reference, since SharedPreferences keeps week refs
            
            resetLocusDontAskAgain.setOnClickListener(new ButtonPreference.OnClickListener(){
    
                @Override
                public void onClick(ButtonPreference pref)
                {
                    Editor editor = config.edit();
                    editor.remove("Locus.point_searchWithoutAsking");
                    editor.remove("Locus.search_searchWithoutAsking");
                    editor.commit();
                    pref.setEnabled(false);
                }});
        }

        {
            final Preference userName = findPreference("userName");
            
            CompoundPreferenceChangeListener.add(userName, 
                new Preference.OnPreferenceChangeListener(){
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    final boolean hasLogin = newValue != null && ((String)newValue).length() > 0;
                    // yes, I know about preference dependencies
                    findPreference("foundStrategy").setEnabled(hasLogin);
                    findPreference("excludeMyOwn").setEnabled(hasLogin);
                    return true;
                }});
            
            final ButtonPreference signToService = (ButtonPreference)findPreference("signToService");
            OnSharedPreferenceChangeListener spcl = new OnSharedPreferenceChangeListener()
            {
                
                @Override
                public void onSharedPreferenceChanged(SharedPreferences config, String key)
                {
                    final boolean signed = oauth.hasOAuth3();
                    if (signed){
                        signToService.setTitle(R.string.pref_my_account_sign_forget);
                        signToService.setSummary(R.string.pref_my_account_sign_forget_desc);
                        signToService.getButton().setText(R.string.pref_my_account_sign_forget_btn);
                    } else {
                        signToService.setTitle(R.string.pref_my_account_sign);
                        signToService.setSummary(R.string.pref_my_account_sign_desc);
                        signToService.getButton().setText(R.string.pref_my_account_sign_btn);
                    }
                    signToService.setShouldDisableDependents(!signed);
                    userName.setEnabled(!signed);
                    userName.getOnPreferenceChangeListener().onPreferenceChange(userName, config.getString("userName", ""));
                    
                }
            };
            config.registerOnSharedPreferenceChangeListener(spcl);
            spcl.onSharedPreferenceChanged(config, null);
            preventGCStorage.add(spcl); // we must keep a strong reference, since SharedPreferences keeps week refs
            userName.getOnPreferenceChangeListener().onPreferenceChange(userName, config.getString("userName", null));
            signToService.setOnClickListener(new ButtonPreference.OnClickListener(){
    
                @Override
                public void onClick(ButtonPreference pref)
                {
                    Intent intent = new Intent(Intent.ACTION_VIEW, null, SettingsActivity.this, OAuthSigningActivity.class);
                    SettingsActivity.this.startActivityForResult(intent, 0);
                }});
        }
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
