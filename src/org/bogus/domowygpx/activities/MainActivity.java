package org.bogus.domowygpx.activities;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bogus.domowygpx.services.GpxDownloaderService;
import org.bogus.domowygpx.utils.LocationUtils;
import org.bogus.domowygpx.utils.Pair;
import org.bogus.geocaching.egpx.R;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
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

    private EditText editTargetFileName;

    private ConnectivityManager conectivityManager;
    private LocationManager locman;
    private LocationListener locationListener;

    ViewGroup tableRowAutoLocusImport;
    CheckBox checkBoxAutoLocusImport;
    private TextView textViewAutoLocusImport;
    
    String currentDownloadImagesStrategy;
    CheckBox checkBoxDownloadImages;
    private TextView textViewDownloadImages;

    private Map<String, Integer> errorFieldsMap = new HashMap<String, Integer>();
    private Map<String, Integer> errorFieldsFocusMap = new HashMap<String, Integer>();
    boolean focusedOnErrorField;
    
    private TaskConfiguration previousTaskConfiguration;
    
    void hideSoftKeyboard()
    {
        final InputMethodManager inputManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        final View currentlyFocused = getCurrentFocus();
        if (currentlyFocused != null){
            inputManager.hideSoftInputFromWindow(currentlyFocused.getWindowToken(),
                InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }
    
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
		
		editMaxNumOfCaches = (EditText) findViewById(R.id.editMaxNumOfCaches);
		editMaxCacheDistance = (EditText) findViewById(R.id.editMaxCacheDistance);
		
		checkBoxAutoLocusImport = (CheckBox) findViewById(R.id.checkBoxAutoLocusImport);
		checkBoxAutoLocusImport.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    if (v.isClickable()){
                        hideSoftKeyboard();
                    }
                }
            });
		tableRowAutoLocusImport = (ViewGroup) findViewById(R.id.tableRowAutoLocusImport);
		textViewAutoLocusImport = (TextView) findViewById(R.id.textViewAutoLocusImport);
		textViewAutoLocusImport.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (checkBoxAutoLocusImport.isClickable()){
                    checkBoxAutoLocusImport.toggle();
                    hideSoftKeyboard();
                }
            }
        });
		
		checkBoxDownloadImages = (CheckBox) findViewById(R.id.checkBoxDownloadImages);
		checkBoxDownloadImages.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                if (isChecked){
                    currentDownloadImagesStrategy = TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ALWAYS;
                } else {
                    currentDownloadImagesStrategy = TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_NEVER;
                }
                updateDownloadImagesState();
                hideSoftKeyboard();
            }
        });
		textViewDownloadImages = (TextView) findViewById(R.id.textViewDownloadImages);
		textViewDownloadImages.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ON_WIFI.equals(currentDownloadImagesStrategy)){
                    currentDownloadImagesStrategy = TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ALWAYS;
                } else
                if (TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_NEVER.equals(currentDownloadImagesStrategy)){
                    currentDownloadImagesStrategy = TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ON_WIFI;
                } else 
                if (TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ALWAYS.equals(currentDownloadImagesStrategy)){
                    currentDownloadImagesStrategy = TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_NEVER;
                } else {
                    throw new IllegalStateException();
                }
                updateDownloadImagesState();
                hideSoftKeyboard();
            }
        });
        
        errorFieldsMap.put("LOCATION", R.id.errorLocation);
        errorFieldsMap.put("CACHE_COUNT_LIMIT", R.id.errorMaxNumOfCaches);
        errorFieldsMap.put("MAX_CACHE_DISTANCE", R.id.errorMaxCacheDistance);
        errorFieldsMap.put("TARGET_FILE", R.id.errorTargetFileName);

        errorFieldsFocusMap.put("LOCATION", R.id.editLatitude);
        errorFieldsFocusMap.put("CACHE_COUNT_LIMIT", R.id.editMaxNumOfCaches);
        errorFieldsFocusMap.put("MAX_CACHE_DISTANCE", R.id.editMaxCacheDistance);
        errorFieldsFocusMap.put("TARGET_FILE", R.id.editTargetFileName);
        
        errorFieldsMap.put("", R.id.errorOthers);
        
        resetViewErrors();
        
        locman = (LocationManager)getSystemService(LOCATION_SERVICE);
        conectivityManager = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
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
        hideSoftKeyboard();
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
        errorControl.setTextAppearance(this, 
            isWarning ? R.style.TextAppearance_Small_Warning 
                    : R.style.TextAppearance_Small_Error);
    }
    
    protected void resetViewError(int viewId)
    {
        TextView v = (TextView)findViewById(viewId);
        v.setText(null);
        v.setVisibility(TextView.GONE);
        v.setTextAppearance(this, android.R.style.TextAppearance_Small);
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
        hideSoftKeyboard();
        resetViewErrors();
        
        //SharedPreferences config = getSharedPreferences("egpx", MODE_PRIVATE);
        
	    TaskConfiguration taskConfiguration = new TaskConfiguration();
        taskConfiguration.initFromConfig(this);

        taskConfiguration.setLatitude(editLat.getText());
	    taskConfiguration.setLongitude(editLon.getText());
	    taskConfiguration.setMaxNumOfCaches(editMaxNumOfCaches.getText());
	    taskConfiguration.setMaxCacheDistance(editMaxCacheDistance.getText());
	    taskConfiguration.setTargetFileName(editTargetFileName.getText());
	    taskConfiguration.setDownloadImagesStrategy(currentDownloadImagesStrategy);
	    taskConfiguration.setDoLocusImport(checkBoxAutoLocusImport.isChecked());
	    
	    taskConfiguration.parseAndValidate(this);
	    
	    for (String modifiedField : taskConfiguration.getModifiedFields()){
	        if ("CACHE_COUNT_LIMIT".equals(modifiedField)){
	            int maxNumOfCaches = taskConfiguration.getOutMaxNumOfCaches();
	            editMaxNumOfCaches.setText(maxNumOfCaches <= 0 ? "" : String.valueOf(maxNumOfCaches));
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
                Toast.makeText(this, "Wystąpiły pewne problemy. Zweryfikuj dane, po czym ponownie kliknij 'Start'", Toast.LENGTH_LONG).show();
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
        config.putInt("MainActivity.configVersion", 1);
        
        config.putString("latitude", editLat.getText().toString());
        config.putString("longitude", editLon.getText().toString());
        config.putString("targetFileName", editTargetFileName.getText().toString());
        config.putString("maxCacheDistance", editMaxCacheDistance.getText().toString());
        config.putString("maxNumOfCaches", editMaxNumOfCaches.getText().toString());
        config.putString("downloadImagesStrategy", currentDownloadImagesStrategy);
        config.putBoolean("autoLocusImport", checkBoxAutoLocusImport.isChecked());

        config.commit();
	}
	
	@Override
	protected void onStart()
	{
		super.onStart();
		
		final SharedPreferences config = this.getSharedPreferences("egpx", MODE_PRIVATE);
		try{
		    int version = config.getInt("MainActivity.configVersion", -1);
		    // XXX todo: migracja
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
		    checkBoxAutoLocusImport.setChecked(config.getBoolean("autoLocusImport", true));
            
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

	    final boolean hasLocus = locus.api.android.utils.LocusUtils.isLocusAvailable(this, 200);
	    if (!hasLocus){
	        final String fileName = new SimpleDateFormat("yyyy-MM-dd_HH.mm'.gpx'").format(new Date());
	        editTargetFileName.setText(fileName);
	    }
        currentDownloadImagesStrategy = TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ON_WIFI;
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
        final Intent intent;
        switch (item.getItemId()) {
            case R.id.actionGoToDownloads:
                intent = new Intent(this, DownloadListActivity.class);
                break;
            case R.id.actionSettings:
                intent = new Intent(this, org.bogus.domowygpx.activities.SettingsActivity.class);
                break;
            default:
                intent = null;
        }
        if (intent != null){
            intent.setAction(Intent.ACTION_VIEW);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }  
    
    void updateDownloadImagesState()
    {
        int lblResId;
        boolean state;
        if (TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ON_WIFI.equals(currentDownloadImagesStrategy)){
            NetworkInfo wifiInfo = conectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            state = wifiInfo.isConnected();        
            lblResId = R.string.downloadImages_wifi;
        } else
        if (TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_NEVER.equals(currentDownloadImagesStrategy)){
            state = false;
            lblResId = R.string.downloadImages_never;
        } else 
        if (TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ALWAYS.equals(currentDownloadImagesStrategy)){
            state = true;
            lblResId = R.string.downloadImages_always;
        } else {
            throw new IllegalStateException();
        }
        
        textViewDownloadImages.setText(lblResId);
        checkBoxDownloadImages.setChecked(state);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        // this may change in preferences, as well as wifi state may change
        final SharedPreferences config = this.getSharedPreferences("egpx", MODE_PRIVATE);
        currentDownloadImagesStrategy = config.getString("downloadImagesStrategy", TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ON_WIFI);
        updateDownloadImagesState();

        final boolean hasLocus = locus.api.android.utils.LocusUtils.isLocusAvailable(this, 200);
        tableRowAutoLocusImport.setVisibility(hasLocus ? View.VISIBLE : View.GONE);
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    @Override
    protected void onPause()
    {
        final SharedPreferences config = this.getSharedPreferences("egpx", MODE_PRIVATE);
        final Editor editor = config.edit();
        if (!currentDownloadImagesStrategy.equals(config.getString("downloadImagesStrategy", null))){
            editor.putString("downloadImagesStrategy", currentDownloadImagesStrategy);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD){
                editor.apply();
            } else {
                editor.commit();
            }
        }
        super.onPause();
    }
}