package org.bogus.domowygpx.activities;

import locus.api.android.utils.LocusConst;
import locus.api.android.utils.LocusUtils;
import locus.api.android.utils.RequiredVersionMissingException;
import locus.api.objects.extra.Waypoint;

import org.bogus.android.AndroidUtils;
import org.bogus.domowygpx.activities.DownloadListContext.GpxListItem;
import org.bogus.domowygpx.activities.DownloadListContext.ListItemViewHolder;
import org.bogus.domowygpx.services.GpxDownloaderApi;
import org.bogus.domowygpx.services.GpxDownloaderListener;
import org.bogus.domowygpx.services.GpxDownloaderService;
import org.bogus.domowygpx.services.GpxDownloaderService.GpxTask;
import org.bogus.domowygpx.services.GpxDownloaderService.GpxTaskEvent;
import org.bogus.domowygpx.utils.LocationUtils;
import org.bogus.geocaching.egpx.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

public class LocusSearchForCachesActivity extends Activity implements GpxDownloaderListener
{
    private static final String LOG_TAG = "LocusSearch4CachesActivity";
    
    final DownloadListContext listItemContext = new DownloadListContext(){
        @Override
        void notifyDataSetChanged()
        {
        }};

    final DialogInterface.OnDismissListener onDismissListener = new DialogInterface.OnDismissListener()
    {
        @Override
        public void onDismiss(DialogInterface dlg)
        {
            dialog = null;
            Log.v(LOG_TAG, "OnDismissListener called");
            final SharedPreferences config = LocusSearchForCachesActivity.this.
                    getSharedPreferences("egpx", MODE_PRIVATE);
            final Editor editor = config.edit();
            editor.putString("Locus.maxCacheDistance", AndroidUtils.toString(editMaxCacheDistance.getText()));
            editor.putString("Locus.maxNumOfCaches", AndroidUtils.toString(editMaxNumOfCaches.getText()));
            editor.putString("Locus.downloadImagesStrategy", downloadImagesFragment.getCurrentDownloadImagesStrategy());
            
            if (quickSearch){
                editor.putBoolean("Locus.searchWithoutAsking", checkBoxDontAskAgain.isChecked());
            }
            
            editor.commit();
        }
    };
        
    boolean quickSearch; 
 
    EditText editMaxNumOfCaches;
    EditText editMaxCacheDistance;
    CheckBox checkBoxDontAskAgain;
    
    DownloadImagesFragment downloadImagesFragment;
    
    private ValidationUtils validationUtils;
    
    private locus.api.objects.extra.Location location;
    
    int gpxTaskId = -2;
    GpxListItem gpxListItem;
    ListItemViewHolder listItemViewHolder;
    
    String maxNumOfCachesText;
    String maxCacheDistanceText;
    String downloadImagesStrategy;
    AlertDialog dialog;
    
    @Override
    public void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        final Intent intent = this.getIntent();
        if (intent == null){
            Log.e(LOG_TAG, "null intent");
            finish(Activity.RESULT_CANCELED);
            return ;
        }
        
        boolean isPointTools = LocusUtils.isIntentPointTools(intent);
        boolean isSearchList = LocusUtils.isIntentSearchList(intent);
        if (!isPointTools && !isSearchList){
            Log.e(LOG_TAG, "Unknown action=" + intent.getAction());
            finish(Activity.RESULT_CANCELED);
            return ;
        }
        
        quickSearch = isPointTools;
        if (isSearchList){
            location = LocusUtils.getLocationFromIntent(intent, LocusConst.INTENT_EXTRA_LOCATION_MAP_CENTER);
            if (location == null){
                Log.e(LOG_TAG, "null location for action=" + intent.getAction());
                finish(Activity.RESULT_CANCELED);
                return ;
            }
        } else
        if (isPointTools){
            try{
                final Waypoint wpt = LocusUtils.handleIntentPointTools(this, intent);
                if (wpt != null){
                    location = wpt.getLocation();
                }
                if (location == null){
                    Log.e(LOG_TAG, "null location for action=" + intent.getAction());
                    finish(Activity.RESULT_CANCELED);
                    return ;
                }
            }catch(RequiredVersionMissingException rvme){
                Log.e(LOG_TAG, "Hmm, should not happen", rvme);
                finish(Activity.RESULT_CANCELED);
                return ;
            }
        }

        final SharedPreferences config = this.getSharedPreferences("egpx", MODE_PRIVATE);
        maxCacheDistanceText = config.getString("Locus.maxCacheDistance", 
            config.getString("maxCacheDistance", ""));
        maxNumOfCachesText = config.getString("Locus.maxNumOfCaches", 
            config.getString("maxNumOfCaches", ""));
        downloadImagesStrategy = 
            config.getString("Locus.downloadImagesStrategy",
                config.getString("downloadImagesStrategy", 
                    TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ON_WIFI));
        if (quickSearch && config.getBoolean("Locus.searchWithoutAsking",false)){
            startDownload();
        } else {
            showParamsDialog();
        }
     
        listItemContext.context = this;
        listItemContext.handler = new Handler(Looper.getMainLooper());
        
        bindDownloaderService();
    }
    
    protected void showProgressDialog()
    {
        if(dialog != null){
            dialog.dismiss();
            dialog = null;
        } 

        final LayoutInflater inflater = LayoutInflater.from(this);
        final View view = inflater.inflate(R.layout.activity_download_list_item, null);
        listItemViewHolder = listItemContext.new ListItemViewHolder();
        listItemViewHolder.initialSetup(view);
        listItemViewHolder.initialFixup();
        listItemViewHolder.textViewDateInfo.setVisibility(View.GONE);
        gpxListItem = listItemContext.new GpxListItem();
        gpxListItem.message = getResources().getString(R.string.titleLocusSearchInProgress); 
        gpxListItem.applyToView(listItemViewHolder, true);
        
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setView(view);
        dialog = dialogBuilder.create();
        dialog.setOnDismissListener(onDismissListener);
        dialog.show();
        updateProgressDialogWidth();
    }
    
    protected void showParamsDialog()
    {
        if (dialog != null){
            return ;
        }
        
        final LayoutInflater inflater = LayoutInflater.from(this);
        final View view = inflater.inflate(R.layout.activity_locus_search_for_caches, null);
        
        {
            TextView textViewGeoPosition = (TextView) view.findViewById(R.id.textViewGeoPosition);
            String locText = LocationUtils.format(location.latitude, LocationUtils.FORMAT_WGS84_LAT)
                + " "
                + LocationUtils.format(location.longitude, LocationUtils.FORMAT_WGS84_LON);
            textViewGeoPosition.setText(locText);
        }
        editMaxNumOfCaches = (EditText) view.findViewById(R.id.editMaxNumOfCaches);
        editMaxCacheDistance = (EditText) view.findViewById(R.id.editMaxCacheDistance);
        
        checkBoxDontAskAgain = (CheckBox) view.findViewById(R.id.checkBoxDontAskAgain);
        checkBoxDontAskAgain.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    if (v.isClickable()){
                        AndroidUtils.hideSoftKeyboard(dialog);
                    }
                }
            });
        final TextView textViewDontAskAgain = (TextView) view.findViewById(R.id.textViewDontAskAgain);
        textViewDontAskAgain.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (checkBoxDontAskAgain.isClickable()){
                    AndroidUtils.hideSoftKeyboard(dialog);
                    checkBoxDontAskAgain.toggle();
                }
            }
        });        
        
        if (!quickSearch){
            View dontAskAgain = view.findViewById(R.id.tableRowDontAskAgain);
            dontAskAgain.setVisibility(View.GONE);
        }

        validationUtils = new ValidationUtils(view);
        
        validationUtils.addErrorField("CACHE_COUNT_LIMIT", R.id.errorMaxNumOfCaches);
        validationUtils.addErrorField("MAX_CACHE_DISTANCE", R.id.errorMaxCacheDistance);

        validationUtils.addErrorFocusField("CACHE_COUNT_LIMIT", R.id.editMaxNumOfCaches);
        validationUtils.addErrorFocusField("MAX_CACHE_DISTANCE", R.id.editMaxCacheDistance);
        
        validationUtils.addErrorField("", R.id.errorOthers);
        
        validationUtils.resetViewErrors();
        
        downloadImagesFragment = new DownloadImagesFragment();
        downloadImagesFragment.onCreate(view);
        
        editMaxCacheDistance.setText(maxCacheDistanceText);
        editMaxNumOfCaches.setText(maxNumOfCachesText);
        downloadImagesFragment.setCurrentDownloadImagesStrategy(downloadImagesStrategy);
        
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle(R.string.titleLocusSearch);
        dialogBuilder.setNegativeButton(R.string.btnClose, null);
        dialogBuilder.setPositiveButton(R.string.start, null);
        dialogBuilder.setOnCancelListener(new DialogInterface.OnCancelListener(){
            @Override
            public void onCancel(DialogInterface dialog)
            {
                finish(Activity.RESULT_CANCELED);
            }});
        dialogBuilder.setView(view);
        
        dialog = dialogBuilder.create();
        downloadImagesFragment.setWindow(dialog.getWindow());
        dialog.setOnDismissListener(onDismissListener);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener()
        {            
            @Override
            public void onClick(View v)
            {
                LocusSearchForCachesActivity.this.startDownloadFromUI();
            }
        });   
    }
    
    protected void startDownloadFromUI()
    {
        AndroidUtils.hideSoftKeyboard(dialog);
        maxNumOfCachesText = AndroidUtils.toString(editMaxNumOfCaches.getText());
        maxCacheDistanceText = AndroidUtils.toString(editMaxCacheDistance.getText());
        downloadImagesStrategy = downloadImagesFragment.getCurrentDownloadImagesStrategy();
        validationUtils.resetViewErrors();
        startDownload();
    }
    
    protected void startDownload(){
        
        TaskConfiguration taskConfiguration = new TaskConfiguration();
        taskConfiguration.initFromConfig(this);

        taskConfiguration.setOutLatitude(location.latitude);
        taskConfiguration.setOutLongitude(location.longitude);
        taskConfiguration.setMaxNumOfCaches(maxNumOfCachesText);
        taskConfiguration.setMaxCacheDistance(maxCacheDistanceText);
        taskConfiguration.setDownloadImagesStrategy(downloadImagesStrategy);
        taskConfiguration.setDoLocusImport(true);
        
        taskConfiguration.parseAndValidate(this);
        
        if (!taskConfiguration.getModifiedFields().isEmpty()){
            showParamsDialog();
        }
        
        for (String modifiedField : taskConfiguration.getModifiedFields()){
            if ("CACHE_COUNT_LIMIT".equals(modifiedField)){
                int maxNumOfCaches = taskConfiguration.getOutMaxNumOfCaches();
                editMaxNumOfCaches.setText(maxNumOfCaches <= 0 ? "" : String.valueOf(maxNumOfCaches));
            } else 
            if ("MAX_CACHE_DISTANCE".equals(modifiedField)){
                String s;
                if (taskConfiguration.getOutMaxCacheDistance() < 1){
                    s = Math.round(taskConfiguration.getOutMaxCacheDistance()*1000) + " m";
                } else {
                    s = taskConfiguration.getOutMaxCacheDistance() + " km";
                }
                editMaxCacheDistance.setText(s);
            } 
        }
        
        if (!validationUtils.checkForErrors(taskConfiguration)){
            // in case of any errors
            showParamsDialog();
            return ;
        }

        showProgressDialog();
        
        {
            Log.i(LOG_TAG, "Invoking service to start download");
            final Intent intent = new Intent(this, GpxDownloaderService.class);
            intent.setAction(GpxDownloaderApi.INTENT_ACTION_START_DOWNLOAD);
            intent.putExtra(GpxDownloaderApi.INTENT_EXTRA_TASK_CONFIGURATION, (Parcelable)taskConfiguration);
            intent.putExtra(GpxDownloaderApi.INTENT_EXTRA_MESSENGER, 
                new Messenger(new Handler(Looper.getMainLooper(), new Handler.Callback()
                {
                    @Override
                    public boolean handleMessage(Message msg)
                    {
                        gpxTaskId = msg.arg1;
                        Log.i(LOG_TAG, "GPX taskId=" + msg.arg1);
                        return true;
                    }
                }
            )));
            
            startService(intent);
        }
    }
    
    @Override
    protected void onDestroy()
    {
        if (dialog != null){
            dialog.dismiss();
            dialog = null;
        }
        if (listItemContext.gpxDownloader != null){
            listItemContext.gpxDownloader.unregisterEventListener(this);
        }
        unbindService(gpxDownloaderServiceConnection);
        super.onDestroy();
    }
    
    public void finish(int resultCode){
        if (super.isFinishing()){
            Log.v(LOG_TAG, "Activity.finish() called several times");
            return ;
        }
        Log.v(LOG_TAG, "Activity.finish()");
        super.setResult(resultCode);
        super.finish();
    }

    private final ServiceConnection gpxDownloaderServiceConnection = new ServiceConnection(){
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            listItemContext.gpxDownloader = ((GpxDownloaderService.LocalBinder)service).getService();
            listItemContext.gpxDownloader.registerEventListener(LocusSearchForCachesActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            listItemContext.gpxDownloader = null;
            Log.w(LOG_TAG, "Lost connection to " + className.getShortClassName());
            bindDownloaderService();
        }
    };
    protected void bindDownloaderService()
    {
        if (listItemContext.gpxDownloader == null){
            bindService(new Intent(this, GpxDownloaderService.class), gpxDownloaderServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }
    @Override
    public void onTaskCreated(int taskId)
    {
    }
    
    @Override
    public void onTaskEvent(GpxTaskEvent event, GpxTask task)
    {
        if (task.taskId == gpxTaskId){
            gpxListItem.onGpxEvent(task, event);
            gpxListItem.applyToView(listItemViewHolder, true);
            if (gpxListItem.isDone || gpxListItem.isError){
                if (!isFinishing()){
                    listItemContext.handler.postDelayed(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            if (dialog != null){
                                dialog.dismiss();
                                dialog = null;
                            }
                            finish(Activity.RESULT_OK);
                        }
                    }, 125); // TODO: make a fade-out animation
                }
            }
        }
    }
    
    @Override
    public void onTaskRemoved(int taskId)
    {
        if (taskId == gpxTaskId){
            finish(Activity.RESULT_CANCELED);
        }
    }
    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        updateProgressDialogWidth();
    }
    
    void updateProgressDialogWidth()
    {
        if (listItemViewHolder == null){
            return ;
        }
        final WindowManager wm = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
        final Display display = wm.getDefaultDisplay();
        @SuppressWarnings("deprecation")
        int width = display.getWidth(); 
        listItemViewHolder.viewRoot.getLayoutParams().width = (int)(0.95 * width);
    }
}
