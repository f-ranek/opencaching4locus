package org.bogus.domowygpx.activities;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import locus.api.android.utils.LocusConst;
import locus.api.android.utils.LocusUtils;

import org.bogus.android.AndroidUtils;
import org.bogus.android.DecimalKeyListener;
import org.bogus.android.LockableScrollView;
import org.bogus.domowygpx.application.Application;
import org.bogus.domowygpx.oauth.OKAPI;
import org.bogus.domowygpx.services.GpxDownloaderService;
import org.bogus.domowygpx.utils.LocationUtils;
import org.bogus.geocaching.egpx.R;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity
{
	private static final String LOG_TAG = "MainActivity";
	
	private boolean locationAlreadySet;
	
    private EditText editLat;
    private EditText editLon;
    private Button btnGetLocationFromGps;
	
	private EditText editMaxNumOfCaches;
	private EditText editMaxCacheDistance;
	private CacheTypesConfig cacheTypesConfig;
	private CacheTypesConfigRenderer cacheTypesConfigRenderer;

	private RangeConfig cacheTaskDifficultyConfig, cacheTerrainDifficultyConfig;
	private CacheDifficultiesRenderer cacheDifficultiesRenderer;

    private CacheRatingsConfig cacheRatingsConfig;
    private CacheRecommendationsConfig cacheRecommendationsConfig;
    private CacheRatingsRenderer cacheRatingsRenderer;

	
    private EditText editTargetFileName;

    private LocationManager locman;
    private LocationListener locationListener;

    ViewGroup tableRowAutoLocusImport;
    CheckBox checkBoxAutoLocusImport;
    private TextView textViewAutoLocusImport;
    
    private DownloadImagesFragment downloadImagesFragment;
    private OnlyWithTrackablesFragment onlyWithTrackablesFragment;
    
    private ValidationUtils validationUtils;
    
	/** Called when the activity is first created. */
    @Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		Application.getInstance(this).showErrorDumpInfo(this);
		
		final LayoutInflater inflater = LayoutInflater.from(this);
		inflater.setFactory(new LayoutInflater.Factory(){

            @Override
            public View onCreateView(String name, Context context, AttributeSet attrs)
            {
                // we don't use LockableScrollView in xml, because this breaks Eclipse editor
                // (it does not understand we are ScrollView, and truncates viewport)
                if ("ScrollView".equals(name)){
                    return new LockableScrollView(context, attrs);
                } else {
                    return null;
                }
            }});
		final ViewGroup view = (ViewGroup)inflater.inflate(R.layout.activity_main, null);
		setContentView(view);
		
		editLat = (EditText) findViewById(R.id.editLatitude);
		editLon = (EditText) findViewById(R.id.editLongitude);
		btnGetLocationFromGps = (Button) findViewById(R.id.btnGetLocationFromGps);
		btnGetLocationFromGps.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                AndroidUtils.hideSoftKeyboard(MainActivity.this);
                if (!isGpsPending()){
                    startGetLocationFromGps(false);
                } else {
                    stopGetLocationFromGps();
                }
            }
        });
		
		editTargetFileName = (EditText) findViewById(R.id.editTargetFileName);
		
		editMaxNumOfCaches = (EditText) findViewById(R.id.editMaxNumOfCaches);
		editMaxCacheDistance = (EditText) findViewById(R.id.editMaxCacheDistance);
		editMaxCacheDistance.setKeyListener(new DecimalKeyListener());
		
		final View.OnClickListener hideKeyboardClickListener = new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (v.isClickable()){
                    AndroidUtils.hideSoftKeyboard(MainActivity.this);
                }
            }
        }; 
		
		checkBoxAutoLocusImport = (CheckBox) findViewById(R.id.checkBoxAutoLocusImport);
		checkBoxAutoLocusImport.setOnClickListener(hideKeyboardClickListener);
		tableRowAutoLocusImport = (ViewGroup) findViewById(R.id.tableRowAutoLocusImport);
		textViewAutoLocusImport = (TextView) findViewById(R.id.textViewAutoLocusImport);
		textViewAutoLocusImport.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (checkBoxAutoLocusImport.isClickable()){
                    AndroidUtils.hideSoftKeyboard(MainActivity.this);
                    checkBoxAutoLocusImport.toggle();
                }
            }
        });
		
		onlyWithTrackablesFragment = new OnlyWithTrackablesFragment(view, getWindow());
		
		final TextView editCacheTypes = (TextView) findViewById(R.id.editCacheTypes);
		cacheTypesConfig = new CacheTypesConfig(OKAPI.getInstance(this)); // TODO: cache OKAPI in local variable ?
		cacheTypesConfigRenderer = new CacheTypesConfigRenderer(this, cacheTypesConfig, editCacheTypes);
		
		final TextView editCacheDifficulties = (TextView) findViewById(R.id.editCacheDifficulties);
		cacheTaskDifficultyConfig = new RangeConfig(); 
		cacheTerrainDifficultyConfig = new RangeConfig();
		cacheDifficultiesRenderer = new CacheDifficultiesRenderer(this, cacheTaskDifficultyConfig,
		    cacheTerrainDifficultyConfig, editCacheDifficulties);

        final TextView editCacheRatings = (TextView) findViewById(R.id.editCacheRatings);
        cacheRatingsConfig = new CacheRatingsConfig(); 
        cacheRecommendationsConfig = new CacheRecommendationsConfig();
        cacheRatingsRenderer = new CacheRatingsRenderer(this, cacheRatingsConfig,
            cacheRecommendationsConfig, editCacheRatings);
		
		if (hasActionBar()){
		    ViewGroup tableRowStart = (ViewGroup) findViewById(R.id.tableRowStart);
		    tableRowStart.setVisibility(View.GONE);
		} else {
		    Button btnStart = (Button) findViewById(R.id.btnStart);
		    btnStart.setOnClickListener(new View.OnClickListener()
        {
            
            @Override
            public void onClick(View v)
            {
                MainActivity.this.start();
            }
        });
		}
		validationUtils = new ValidationUtils(this.getWindow().getDecorView());
		
		validationUtils.addErrorField("LOCATION", R.id.errorLocation);
		validationUtils.addErrorField("CACHE_COUNT_LIMIT", R.id.errorMaxNumOfCaches);
		validationUtils.addErrorField("MAX_CACHE_DISTANCE", R.id.errorMaxCacheDistance);
		validationUtils.addErrorField("TARGET_FILE", R.id.errorTargetFileName);

		validationUtils.addErrorFocusField("LOCATION", R.id.editLatitude);
		validationUtils.addErrorFocusField("CACHE_COUNT_LIMIT", R.id.editMaxNumOfCaches);
		validationUtils.addErrorFocusField("MAX_CACHE_DISTANCE", R.id.editMaxCacheDistance);
		validationUtils.addErrorFocusField("TARGET_FILE", R.id.editTargetFileName);
        
        validationUtils.addErrorField("", R.id.errorOthers);
        
        validationUtils.resetViewErrors();
        
        locman = (LocationManager)getSystemService(LOCATION_SERVICE);

        downloadImagesFragment = new DownloadImagesFragment(view, getWindow());
        
        final boolean hasLocus = locus.api.android.utils.LocusUtils.isLocusAvailable(this, 200);
        tableRowAutoLocusImport.setVisibility(hasLocus ? View.VISIBLE : View.GONE);
        
        // invoked as Locus add-on?
        final Intent intent = getIntent();
        try{
            if (LocusUtils.isIntentMainFunction(intent)){
                locus.api.objects.extra.Location loc = 
                        LocusUtils.getLocationFromIntent(intent, LocusConst.INTENT_EXTRA_LOCATION_MAP_CENTER);
                if (loc != null){
                    Location loc2 = LocusUtils.convertToA(loc);
                    updateLocationInfo(loc2);
                    locationAlreadySet = true;
                }
            }
        }catch(Exception e){
            Log.e(LOG_TAG, "Locus communication error", e);
            Toast.makeText(this, R.string.locusCommunicationError, Toast.LENGTH_SHORT).show();
        }
	}
	
	@Override
    public void onDestroy()
	{
	    stopGetLocationFromGps();
	    super.onDestroy();
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

        Location lastKnown = null;
        {
            Location gpsLastKnown = locman.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location networkLastKnown = locman.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (gpsLastKnown != null && networkLastKnown != null){
                lastKnown = gpsLastKnown.getTime() > networkLastKnown.getTime() ? gpsLastKnown : networkLastKnown;
            } else 
            if (gpsLastKnown != null){
                lastKnown = gpsLastKnown;
            } else {
                lastKnown = networkLastKnown;
            }
            
        }
        if (lastKnown != null){
            updateLocationInfo(lastKnown);
            if (initialLoading){
                return ;
            }
	    }	    

        String provider;
	    if (!initialLoading && locman.isProviderEnabled(LocationManager.GPS_PROVIDER)){
	        provider = LocationManager.GPS_PROVIDER;
	    } else {
	        if (locman.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
	            provider = LocationManager.NETWORK_PROVIDER;
	            if (!initialLoading){
	                Toast.makeText(this, R.string.gpsDisabled, Toast.LENGTH_SHORT).show();
	            }
	        } else {
	            if (!initialLoading){
	                Toast.makeText(this, R.string.noLocationInfo, Toast.LENGTH_SHORT).show();
	            }
	            return ;
	        }
	    }
        final boolean fineLocation = !initialLoading && LocationManager.GPS_PROVIDER.equals(provider);
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
	            MainActivity.this.stopGetLocationFromGps();
	        }
	        
	        @Override
	        public void onLocationChanged(final Location location)
	        {
                MainActivity.this.updateLocationInfo(location);
                if (!fineLocation || location.getAccuracy() < 50){
                    MainActivity.this.stopGetLocationFromGps();
                }
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

    void start()
	{
        AndroidUtils.hideSoftKeyboard(this);
        validationUtils.resetViewErrors();
        
	    TaskConfiguration taskConfiguration = new TaskConfiguration();
        taskConfiguration.initFromConfig(this);

        taskConfiguration.setLatitude(editLat.getText());
	    taskConfiguration.setLongitude(editLon.getText());
	    taskConfiguration.setMaxNumOfCaches(editMaxNumOfCaches.getText());
	    taskConfiguration.setMaxCacheDistance(editMaxCacheDistance.getText());
	    taskConfiguration.setTargetFileName(editTargetFileName.getText());
	    taskConfiguration.setDownloadImagesStrategy(downloadImagesFragment.getCurrentDownloadImagesStrategy());
	    taskConfiguration.setOnlyWithTrackables(onlyWithTrackablesFragment.isOnlyWithTrackables());
	    taskConfiguration.setDoLocusImport(checkBoxAutoLocusImport.isChecked());
	    taskConfiguration.setCacheTypes(cacheTypesConfig.serializeToWebServiceString());
	    taskConfiguration.setCacheTaskDifficulty(cacheTaskDifficultyConfig.serializeToWebServiceString());
	    taskConfiguration.setCacheTerrainDifficulty(cacheTerrainDifficultyConfig.serializeToWebServiceString());
        taskConfiguration.setCacheRatings(cacheRatingsConfig.serializeToWebServiceString());
        taskConfiguration.setCacheRecommendations(cacheRecommendationsConfig.serializeToWebServiceString());
	    
	    taskConfiguration.parseAndValidate(this);
	    
	    for (String modifiedField : taskConfiguration.getModifiedFields()){
	        if ("CACHE_COUNT_LIMIT".equals(modifiedField)){
	            int maxNumOfCaches = taskConfiguration.getOutMaxNumOfCaches();
	            editMaxNumOfCaches.setText(maxNumOfCaches <= 0 ? null : String.valueOf(maxNumOfCaches));
	        } else 
            if ("MAX_CACHE_DISTANCE".equals(modifiedField)){
                if (taskConfiguration.getOutMaxCacheDistance() < 0){
                    editMaxCacheDistance.setText(null);
                } else {
                    DecimalFormat df = new DecimalFormat("###0.###");
                    String s = df.format(taskConfiguration.getOutMaxCacheDistance());
                    editMaxCacheDistance.setText(s);
                }
            } 
	    }
	    
	    if (!validationUtils.checkForErrors(taskConfiguration)){
	        return ;
	    }

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
        config.putString("downloadImagesStrategy", downloadImagesFragment.getCurrentDownloadImagesStrategy());
        config.putBoolean("onlyWithTrackables", onlyWithTrackablesFragment.isOnlyWithTrackables());
        config.putBoolean("autoLocusImport", checkBoxAutoLocusImport.isChecked());
        config.putString("cacheTypes", cacheTypesConfig.serializeToConfigString());
        config.putString("cacheTaskDifficulty", cacheTaskDifficultyConfig.serializeToConfigString());
        config.putString("cacheTerrainDifficulty", cacheTerrainDifficultyConfig.serializeToConfigString());
        config.putString("cacheRatings", cacheRatingsConfig.serializeToConfigString());
        config.putString("cacheRecommendations", cacheRecommendationsConfig.serializeToConfigString());
        AndroidUtils.applySharedPrefsEditor(config);
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
		    
		    if (!locationAlreadySet){
		        editLat.setText(config.getString("latitude", ""));
		        editLon.setText(config.getString("longitude", ""));
		    }
		    editTargetFileName.setText(config.getString("targetFileName", ""));
		    editMaxCacheDistance.setText(config.getString("maxCacheDistance", ""));
		    editMaxNumOfCaches.setText(config.getString("maxNumOfCaches", ""));
		    checkBoxAutoLocusImport.setChecked(config.getBoolean("autoLocusImport", true));
            
		    downloadImagesFragment.setCurrentDownloadImagesStrategy(
                config.getString("downloadImagesStrategy", TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ON_WIFI));
		    onlyWithTrackablesFragment.setOnlyWithTrackables(config.getBoolean("onlyWithTrackables", false));
		    
		    cacheTypesConfig.parseFromConfigString(config.getString("cacheTypes", cacheTypesConfig.getDefaultConfig()));
		    cacheTaskDifficultyConfig.parseFromConfigString(config.getString("cacheTaskDifficulty", null));
		    cacheTerrainDifficultyConfig.parseFromConfigString(config.getString("cacheTerrainDifficulty", null));
		    cacheRatingsConfig.parseFromConfigString(config.getString("cacheRatings", null));
		    cacheRecommendationsConfig.parseFromConfigString(config.getString("cacheRecommendations", null));
		    
            updateBtnGetLocationFromGps(isGpsPending());
		}catch(Exception e){
		    Log.e(LOG_TAG, "Failed to read config", e);
		    initNewConfig();
		}
		
		cacheTypesConfigRenderer.applyToTextView();
		cacheDifficultiesRenderer.applyToTextView();
		cacheRatingsRenderer.applyToTextView();
	}
	
	@SuppressLint("SimpleDateFormat")
    protected void initNewConfig()
	{
	    if (editLat.getText() == null || editLat.getText().length() == 0 ||
	            editLon.getText() == null || editLon.getText().length() == 0)
	    {
	        startGetLocationFromGps(true);
	    }

	    final boolean hasLocus = locus.api.android.utils.LocusUtils.isLocusAvailable(this, 200);
	    if (!hasLocus){
	        final String fileName = new SimpleDateFormat("yyyy-MM-dd_HH.mm'.gpx'").format(new Date());
	        editTargetFileName.setText(fileName);
	    }
	    downloadImagesFragment.setCurrentDownloadImagesStrategy(TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ON_WIFI);
	    cacheTypesConfig.parseFromConfigString(cacheTypesConfig.getDefaultConfig());
	    cacheTypesConfigRenderer.applyToTextView();
	    cacheDifficultiesRenderer.applyToTextView();
	    cacheRatingsRenderer.applyToTextView();
	}

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    /*@TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setupActionBar()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            ActionBar actionBar = getActionBar();
            if (actionBar != null){
                //actionBar.s
            }
            //getActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }*/
	
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    protected final boolean hasActionBar()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            ActionBar actionBar = getActionBar();
            return actionBar != null;
        } else {
            return false;
        }
    }
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_main_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        Intent intent = null;
        switch (item.getItemId()) {
            case R.id.actionGoToDownloads:
                intent = new Intent(this, DownloadListActivity.class);
                break;
            case R.id.actionSettings:
                intent = new Intent(this, org.bogus.domowygpx.activities.SettingsActivity.class);
                break;
            case R.id.actionInfo:
                intent = new Intent(this, org.bogus.domowygpx.activities.InfoActivity.class);
                break;
            case R.id.actionStart:
                start();
                break;
        }
        if (intent != null){
            intent.setAction(Intent.ACTION_VIEW);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }  
    
    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    @Override
    protected void onPause()
    {
        final SharedPreferences config = this.getSharedPreferences("egpx", MODE_PRIVATE);
        final Editor editor = config.edit();
        final String currentDownloadImagesStrategy = downloadImagesFragment.getCurrentDownloadImagesStrategy();
        if (!currentDownloadImagesStrategy.equals(config.getString("downloadImagesStrategy", null))){
            editor.putString("downloadImagesStrategy", currentDownloadImagesStrategy);
            AndroidUtils.applySharedPrefsEditor(editor);
        }
        super.onPause();
    }
}