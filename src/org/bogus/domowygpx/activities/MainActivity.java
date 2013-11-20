package org.bogus.domowygpx.activities;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bogus.domowygpx.services.GpxDownloaderService;
import org.bogus.domowygpx.utils.LocationUtils;
import org.bogus.domowygpx.utils.Pair;
import org.bogus.domowygpx.utils.TargetDirLocator;
import org.bogus.geocaching.egpx.R;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity
{
	private static final String LOG_TAG = "MainActivity";
	
    private EditText editLat;
    private EditText editLon;
    private Button btnGetLocationFromGps;
	
	private EditText editMaxNumOfCaches;
	private EditText editMaxCacheDistance;

	private EditText editUserName;
    private RadioGroup radioGroupFoundStrategy;

    private EditText editTargetFileName;

    private EditText editTargetDirName;
    private RadioGroup radioGroupDownloadImagesStrategy;

    private EditText editSourceCachesList;
    private LocationManager locman;
    private LocationListener locationListener;

    private Map<String, Integer> errorFieldsMap = new HashMap<String, Integer>();
    private Map<String, Integer> errorFieldsFocusMap = new HashMap<String, Integer>();
    boolean focusedOnErrorField;
    
    private TaskConfiguration previousTaskConfiguration;
    
	/** Called when the activity is first created. */
	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		editLat = (EditText) findViewById(R.id.editLatitude);
		editLon = (EditText) findViewById(R.id.editLongitude);
		btnGetLocationFromGps = (Button) findViewById(R.id.btnGetLocationFromGps);
		
		editTargetFileName = (EditText) findViewById(R.id.editTargetFileName);
		editTargetDirName = (EditText) findViewById(R.id.editTargetDirName);
		
		editMaxNumOfCaches = (EditText) findViewById(R.id.editMaxNumOfCaches);
		editMaxCacheDistance = (EditText) findViewById(R.id.editMaxCacheDistance);
		
		editUserName = (EditText) findViewById(R.id.editUserName);
		editUserName.addTextChangedListener(new TextWatcher()
        {
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
            }
            
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {
            }
            
            @SuppressWarnings("synthetic-access")
            @Override
            public void afterTextChanged(Editable s)
            {
                setRadioGroupEnabled(radioGroupFoundStrategy, s.length() != 0);
            }
        });

		radioGroupFoundStrategy = (RadioGroup) findViewById(R.id.radioFoundStrategy);
	    radioGroupDownloadImagesStrategy = (RadioGroup) findViewById(R.id.radioDownloadImagesStrategy);
		
	    editSourceCachesList = (EditText) findViewById(R.id.editSourceCachesList);

		
        //btnStart = (Button) findViewById(R.id.btnStart);
        //textViewErrorOthers = (TextView)findViewById(R.id.errorOthers);

        // editLat.requestFocus();
        
        errorFieldsMap.put("LOCATION", R.id.errorLocation);
        errorFieldsMap.put("LIMIT", R.id.errorMaxNumOfCaches);
        errorFieldsMap.put("MAX_CACHE_DISTANCE", R.id.errorMaxCacheDistance);
        errorFieldsMap.put("TARGET_FILE", R.id.errorTargetFileName);
        errorFieldsMap.put("TARGET_DIR", R.id.errorTargetDirName);
        errorFieldsMap.put("CACHE_LIST", R.id.errorSourceCachesList);

        errorFieldsFocusMap.put("LOCATION", R.id.editLatitude);
        errorFieldsFocusMap.put("LIMIT", R.id.editMaxNumOfCaches);
        errorFieldsFocusMap.put("MAX_CACHE_DISTANCE", R.id.editMaxCacheDistance);
        errorFieldsFocusMap.put("TARGET_FILE", R.id.editTargetFileName);
        errorFieldsFocusMap.put("TARGET_DIR", R.id.editTargetDirName);
        errorFieldsFocusMap.put("CACHE_LIST", R.id.editSourceCachesList);
        
        errorFieldsMap.put("", R.id.errorOthers);
        
        resetViewErrors();
        
        locman = (LocationManager)getSystemService(LOCATION_SERVICE);
	}
	
	protected void updateLocationInfo(Location location)
    {
        int latFormat = Location.FORMAT_MINUTES;
        try{
            latFormat = LocationUtils.parseLocation(editLat.getText()).first;
            if (latFormat == LocationUtils.FORMAT_WGS84_LON){
                latFormat = Location.FORMAT_MINUTES;
            }
        }catch(Exception e){
            // ignore
        }
        int lonFormat = Location.FORMAT_MINUTES;
        try{
            lonFormat = LocationUtils.parseLocation(editLon.getText()).first;
            if (lonFormat == LocationUtils.FORMAT_WGS84_LAT){
                lonFormat = Location.FORMAT_MINUTES;
            }
        }catch(Exception e){
            // ignore
        }
        
        editLat.setText(LocationUtils.format(location.getLatitude(), latFormat));
        editLon.setText(LocationUtils.format(location.getLongitude(), lonFormat));
    }

    public void onBtnGetLocationFromGpsClicked(final View btn)
	{
	    if (!isGpsPending()){
	        startGetLocationFromGps(false);
	    } else {
	        stopGetLocationFromGps();
	    }
	}
	
	protected final boolean isGpsPending()
	{
	    return locationListener != null;
	}
	
	protected void startGetLocationFromGps(boolean initialLoading)
	{
	    if (isGpsPending()){
	        return ;
	        // already registered for listening
	    }

	    if (initialLoading){
    	    Location lastKnown = locman.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnown == null){
                lastKnown = locman.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (lastKnown != null){
                updateLocationInfo(lastKnown);
                return ;
            }
	    }	    
	    String provider = initialLoading ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
	    boolean hasProvider = locman.isProviderEnabled(provider);
	    if (!hasProvider && !initialLoading){
	        provider = LocationManager.NETWORK_PROVIDER;
	        hasProvider = locman.isProviderEnabled(provider);
	    }
	    
	    if (!hasProvider){
	        if (!initialLoading){
	            Toast.makeText(this, "Nie można pobrać informacji o lokalizacji", Toast.LENGTH_LONG).show();
	        }
	        return ;
	    }
	    boolean hasGps = provider.equals(LocationManager.GPS_PROVIDER);
        if (!initialLoading && !hasGps){
            Toast.makeText(this, "GPS wyłączony, pobieram lokalizację z sieci", Toast.LENGTH_LONG).show();
        }
	    
        final boolean fineLocation = !initialLoading && hasGps;
	    locationListener = new LocationListener()
        {
	        @Override
	        public void onStatusChanged(final String provider, final int status, final Bundle extras)
	        {
	        }
	        
	        @Override
	        public void onProviderEnabled(final String provider)
	        {
	        }
	        
	        @Override
	        public void onProviderDisabled(final String provider)
	        {
	            onLocationChanged(null);
	        }
	        
	        @Override
	        public void onLocationChanged(final Location location)
	        {
	            MainActivity.this.runOnUiThread(
	                new Runnable(){
	                    @Override
	                    public void run(){
	                        if (location != null){
	                            MainActivity.this.updateLocationInfo(location);
	                        }
	                        if (location == null || !fineLocation || location.getAccuracy() < 50){
	                            MainActivity.this.stopGetLocationFromGps();
	                        }
	                    }
	                }
	                );
	            
	        }
	    };
	    
        locman.requestLocationUpdates(
            provider, 
            1000, 
            0, 
            locationListener);
        updateBtnGetLocationFromGps(true);
	}
	
    protected void stopGetLocationFromGps()
    {
        if (locationListener != null){
            locman.removeUpdates(locationListener);
            locationListener = null;
        }
        updateBtnGetLocationFromGps(false);
    }
    
    protected void updateBtnGetLocationFromGps(boolean isGpsPending)
    {
        btnGetLocationFromGps.setText(isGpsPending ? R.string.getLocationFromGpsCancel : R.string.getLocationFromGps);
    }

    protected void markError(String errorText, TextView errorControl, boolean isWarning)
    {
        final boolean isShown = errorControl.getVisibility() == TextView.VISIBLE;
        if (isShown){
            errorControl.append("\n\r");
            errorControl.append(errorText);
        } else {
            errorControl.setText(errorText);
        }
        
        errorControl.setVisibility(TextView.VISIBLE);
        errorControl.setTextColor(getResources().getColor(isWarning ? R.color.colorWarning : R.color.colorError));
    }
    
    protected void resetViewError(int viewId)
    {
        TextView v = (TextView)findViewById(viewId);
        v.setText("");
        v.setVisibility(TextView.GONE);
    }
    
    protected void resetViewErrors()
    {
        for (Integer id : errorFieldsMap.values()){
            resetViewError(id);
        }
        focusedOnErrorField = false;
    }
    
    public void onBtnStartClicked(final View btn)
	{
        resetViewErrors();
        //Toast.makeText(this, "Aaaaa, zaczynam robić", Toast.LENGTH_SHORT).show();
        
	    TaskConfiguration taskConfiguration = new TaskConfiguration();
	    taskConfiguration.setLatitude(editLat.getText().toString());
	    taskConfiguration.setLongitude(editLon.getText().toString());
	    taskConfiguration.setMaxNumOfCaches(editMaxNumOfCaches.getText().toString());
	    taskConfiguration.setMaxCacheDistance(editMaxCacheDistance.getText().toString());
	    taskConfiguration.setUserName(editUserName.getText().toString());
	    taskConfiguration.setFoundStrategy(getFoundStrategy());
	    taskConfiguration.setDownloadImagesStrategy(getDownloadImagesStrategy());
	    taskConfiguration.setTargetFileName(editTargetFileName.getText().toString());
	    taskConfiguration.setTargetDirName(editTargetDirName.getText().toString());
	    taskConfiguration.setSourceCachesList(editSourceCachesList.getText().toString());
	    
	    taskConfiguration.parseAndValidate(this);
	    
	    for (String modifiedField : taskConfiguration.getModifiedFields()){
	        if ("LIMIT".equals(modifiedField)){
	            int maxNumOfCaches = taskConfiguration.getOutMaxNumOfCaches();
	            editMaxNumOfCaches.setText(maxNumOfCaches <= 0 ? "" : String.valueOf(maxNumOfCaches));
	        } else 
            if ("TARGET_DIR".equals(modifiedField)){
                editTargetDirName.setText(taskConfiguration.getTargetDirName());
            } else 
            if ("TARGET_FILE".equals(modifiedField)){
                editTargetFileName.setText(taskConfiguration.getTargetFileName());
            } else
            if ("MAX_CACHE_DISTANCE".equals(modifiedField)){
                String s;
                if (taskConfiguration.getOutMaxCacheDistance() < 1){
                    s = Math.round(taskConfiguration.getOutMaxCacheDistance()*1000) + "m";
                } else {
                    s = taskConfiguration.getOutMaxCacheDistance() + "km";
                }
                editMaxCacheDistance.setText(s);
            } 
	    }
	    
	    final List<Pair<String, String>> errors = taskConfiguration.getErrors();
	    processErrorsList(errors, false);
	    
	    if (!errors.isEmpty()){
            previousTaskConfiguration = null;
	        Toast.makeText(this, "Przed kontynuacją popraw zaznaczone błędy", Toast.LENGTH_LONG).show();
	        return ;
	    }

	    final List<Pair<String, String>> warnings = taskConfiguration.getWarnings();
        processErrorsList(warnings, true);
	    
        
        if (!warnings.isEmpty()){
            if (previousTaskConfiguration == null || !taskConfiguration.equals(previousTaskConfiguration)){
                previousTaskConfiguration = taskConfiguration;
                Toast.makeText(this, "Zweryfikuj i ew. popraw napotkane ostrzeżenia, po czym ponownie kliknij 'Start'", Toast.LENGTH_LONG).show();
                return ;
            }
        }
        
        resetViewErrors();
        

        {
            final Intent intent = new Intent(this, DownloadListActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            startActivity(intent);
        }
        {
            Log.i(LOG_TAG, "Invoking service to start download");
            final Intent intent = new Intent(this, GpxDownloaderService.class);
            intent.setAction(GpxDownloaderService.INTENT_ACTION_START_DOWNLOAD);
            intent.putExtra(GpxDownloaderService.INTENT_EXTRA_TASK_CONFIGURATION, (Parcelable)taskConfiguration);
            startService(intent);
        }
	}

    protected void processErrorsList(final List<Pair<String, String>> errors, boolean isWarning)
    {
        for (Pair<String, String> error : errors){
	        final String fieldCode = error.first;
	        final String errorText = error.second;
	        
	        int errorViewId;
	        if (fieldCode != null && errorFieldsMap.containsKey(fieldCode)){
	            errorViewId = errorFieldsMap.get(fieldCode);
	        } else {
	            errorViewId = R.id.errorOthers;
	        }
	        
	        if (!focusedOnErrorField && fieldCode != null && errorFieldsFocusMap.containsKey(fieldCode)){
	            // Use Next Focus.. properties
	            int focusFieldId = errorFieldsFocusMap.get(fieldCode);
	            findViewById(focusFieldId).requestFocus();
	            focusedOnErrorField = true;
	        }
	        
	        View errorView = findViewById(errorViewId);
	        if (errorView instanceof TextView){
	            TextView errorTextView = (TextView)errorView;
	            markError(errorText, errorTextView, isWarning);
	        }
	    }
    }
	
	@Override
	protected void onStop()
	{
		super.onStop();

        Editor config = this.getSharedPreferences("egpx", MODE_PRIVATE).edit();
        config.putInt("configVersion", 1);
        
        config.putString("latitude", editLat.getText().toString());
        config.putString("longitude", editLon.getText().toString());
        config.putString("targetFileName", editTargetFileName.getText().toString());
        config.putString("maxCacheDistance", editMaxCacheDistance.getText().toString());
        config.putString("maxNumOfCaches", editMaxNumOfCaches.getText().toString());
        config.putString("userName", editUserName.getText().toString());
        
        String foundStrategy = getFoundStrategy();
        if (foundStrategy != null){
            config.putString("foundStrategy", foundStrategy);
        } else {
            config.remove("foundStrategy");
        }
        
        config.putString("targetDirName", editTargetDirName.getText().toString());
        String downloadImagesStrategy = getDownloadImagesStrategy();
        if (downloadImagesStrategy != null){
            config.putString("downloadImagesStrategy", downloadImagesStrategy);
        } else {
            config.remove("downloadImagesStrategy");
        }

        config.putString("sourceCachesList", editSourceCachesList.getText().toString());

        config.commit();
	}
	
	protected String getFoundStrategy()
	{
	    return getCheckboxTag(radioGroupFoundStrategy);
	}
	
    protected String getDownloadImagesStrategy()
    {
        return getCheckboxTag(radioGroupDownloadImagesStrategy);
    }

    protected String getCheckboxTag(RadioGroup radioGroup)
	{
        View radioDownloadImagesStrategy = radioGroup.findViewById(radioGroup.getCheckedRadioButtonId());
        if (radioDownloadImagesStrategy != null){
            return (String)radioDownloadImagesStrategy.getTag();
        }
        return null;
	}
	
	@Override
	protected void onStart()
	{
		super.onStart();
		
		final SharedPreferences config = this.getSharedPreferences("egpx", MODE_PRIVATE);
		try{
		    int version = config.getInt("configVersion", -1);
		    if (version > 1){
		        Log.w(LOG_TAG, "Newer preferences found: " + version);
		        initNewConfig();
		        return ;
		    }
		    if (version < 0){
                initNewConfig();
                return ;
		    }
		    
		    editLat.setText(config.getString("latitude", ""));
		    editLon.setText(config.getString("longitude", ""));
		    editTargetFileName.setText(config.getString("targetFileName", ""));
		    editMaxCacheDistance.setText(config.getString("maxCacheDistance", ""));
		    editMaxNumOfCaches.setText(config.getString("maxNumOfCaches", ""));
		    
            editUserName.setText(config.getString("userName", ""));
		    String foundStrategy = config.getString("foundStrategy", TaskConfiguration.FOUND_STRATEGY_MARK);
		    RadioButton radioFoundStrategy = (RadioButton)radioGroupFoundStrategy.findViewWithTag(foundStrategy);
		    if (radioFoundStrategy != null){
		        radioFoundStrategy.setChecked(true);
		    }
		    
		    editTargetDirName.setText(config.getString("targetDirName", ""));
            String downloadImagesStrategy = config.getString("downloadImagesStrategy", TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ON_WIFI);
            RadioButton radioDownloadImagesStrategy = (RadioButton)radioGroupDownloadImagesStrategy.findViewWithTag(downloadImagesStrategy);
            if (radioDownloadImagesStrategy != null){
                radioDownloadImagesStrategy.setChecked(true);
            }
		    
            editSourceCachesList.setText(config.getString("sourceCachesList", ""));
            
            updateBtnGetLocationFromGps(isGpsPending());
		}catch(Exception e){
		    Log.e(LOG_TAG, "Failed to read config", e);
		    initNewConfig();
		}

	}
	
	@SuppressLint("SimpleDateFormat")
    protected void initNewConfig()
	{
	    startGetLocationFromGps(true);

        final String fileName = new SimpleDateFormat("yyyy-MM-dd_HH.mm'.gpx'").format(new Date());
	    final TargetDirLocator tdl = new TargetDirLocator();
	    final List<File> locus = tdl.locateLocus();
	    File rootDir = new File("/");
	    if (!locus.isEmpty()){
	        rootDir = new File(locus.get(0), "mapItems");
	    } else {
	        final List<File> roots = tdl.locateSaveDirectories();
	        if (!roots.isEmpty()){
	            rootDir = roots.get(0);
	        }
	    }
        File file = new File(rootDir, fileName);
        editTargetFileName.setText(file.getAbsolutePath());
        editTargetDirName.setText("images");
        
        RadioButton radioFoundStrategy = (RadioButton)radioGroupFoundStrategy.findViewWithTag(TaskConfiguration.FOUND_STRATEGY_MARK);
        if (radioFoundStrategy != null){
            radioFoundStrategy.setChecked(true);
        }
        RadioButton radioDownloadImagesStrategy = (RadioButton)radioGroupDownloadImagesStrategy.findViewWithTag(TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ON_WIFI);
        if (radioDownloadImagesStrategy != null){
            radioDownloadImagesStrategy.setChecked(true);
        }
        
        setRadioGroupEnabled(radioGroupFoundStrategy, false);
	}

	protected void setRadioGroupEnabled(RadioGroup radioGroup, boolean enabled)
	{
	    radioGroup.setEnabled(enabled);
	    int childrenCount = radioGroup.getChildCount();
	    for (int i=0; i<childrenCount; i++){
	        View child = radioGroup.getChildAt(i);
	        child.setEnabled(enabled);
	    }
	}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
            case R.id.actionGoToDownloads:
                final Intent intent = new Intent(this, DownloadListActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }    
}