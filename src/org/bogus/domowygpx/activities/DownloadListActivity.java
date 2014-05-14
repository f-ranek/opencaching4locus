package org.bogus.domowygpx.activities;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.bogus.domowygpx.activities.DownloadListContext.BaseListItem;
import org.bogus.domowygpx.activities.DownloadListContext.FileListItem;
import org.bogus.domowygpx.activities.DownloadListContext.GpxListItem;
import org.bogus.domowygpx.activities.DownloadListContext.OperationsListAdapter;
import org.bogus.domowygpx.application.Application;
import org.bogus.domowygpx.services.FilesDownloaderListener;
import org.bogus.domowygpx.services.FilesDownloaderService;
import org.bogus.domowygpx.services.FilesDownloaderService.FilesDownloadTask;
import org.bogus.domowygpx.services.GpxDownloaderListener;
import org.bogus.domowygpx.services.GpxDownloaderService;
import org.bogus.domowygpx.services.GpxDownloaderService.GpxTask;
import org.bogus.domowygpx.services.GpxDownloaderService.GpxTaskEvent;
import org.bogus.domowygpx.services.downloader.FileData;
import org.bogus.geocaching.egpx.R;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;

public class DownloadListActivity extends Activity implements GpxDownloaderListener, FilesDownloaderListener
{
    private final static String LOG_TAG = "DownloadListActivity";
    
    final DownloadListContext listItemContext = new DownloadListContext(){
        @Override
        void notifyDataSetChanged()
        {
            listViewAdapter.notifyDataSetChanged();
        }};
        
    private ServiceConnection gpxDownloaderServiceConnection = new ServiceConnection(){
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(LOG_TAG, "Established connection to " + className.getShortClassName());
            listItemContext.gpxDownloader = ((GpxDownloaderService.LocalBinder)service).getService();
            connectedToGpxDownloader();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            listItemContext.gpxDownloader = null;
            Log.w(LOG_TAG, "Lost connection to " + className.getShortClassName());
            bindDownloaderService();
        }
    };
    private ServiceConnection filesDownloaderServiceConnection = new ServiceConnection(){
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(LOG_TAG, "Established connection to " + className.getShortClassName());
            listItemContext.filesDownloader = ((FilesDownloaderService.LocalBinder)service).getService();
            connectedToFilesDownloader();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            listItemContext.filesDownloader = null;
            Log.w(LOG_TAG, "Lost connection to " + className.getShortClassName());
            bindDownloaderService();
        }
    };
    
    private int stableIdCounter;
    int filesDownloadRunningCounter;
    
    private ListView listViewOperations;
    OperationsListAdapter listViewAdapter;

    

    
    private GpxListItem getGpxListItem(GpxTask task, boolean bulkOperation)
    {
        final int taskId = task.taskId;
        for (BaseListItem listItem : listViewAdapter.listItems){
            if (taskId == listItem.taskId && listItem instanceof GpxListItem){
                return (GpxListItem)listItem;
            }
        }
        GpxListItem result = listItemContext.new GpxListItem();
        result.message = getString(R.string.infoWaiting);
        result.createdDateVal = task.createdDate;
        result.taskId = taskId;
        result.stableId = ++stableIdCounter;
        listViewAdapter.listItems.add(result);
        if (!bulkOperation){
            sortListItems();
        }
        return result;
    }

    private FileListItem getFilesListItem(FilesDownloadTask task, boolean bulkOperation)
    {
        final int taskId = task.taskId;
        for (BaseListItem listItem : listViewAdapter.listItems){
            if (taskId == listItem.taskId && listItem instanceof FileListItem){
                return (FileListItem)listItem;
            }
        }
        FileListItem result = listItemContext.new FileListItem();
        result.message = getString(R.string.infoWaiting);
        result.createdDateVal = task.createdDate;
        result.taskId = taskId;
        result.stableId = ++stableIdCounter;
        listViewAdapter.listItems.add(result); 
        if (!bulkOperation){
            sortListItems();
        }
        return result;
    }
    
    protected void connectedToGpxDownloader()
    {
        // events may get duplicated here
        final Iterator<BaseListItem> listItemIt = listViewAdapter.listItems.iterator();
        while(listItemIt.hasNext()){
            if (listItemIt.next() instanceof GpxListItem){
                listItemIt.remove();
            }
        }
        listItemContext.gpxDownloader.registerEventListener(this); 
        final List<GpxTask> tasks = listItemContext.gpxDownloader.getTasks(-1, false);
        boolean updateView = false;
        for (GpxTask task : tasks){
            GpxTaskEvent event = null; 
            if (task.events != null && task.events.size() > 0){
                event = task.events.get(task.events.size()-1);
            }
            updateView |= onGpxEventInternal(task, event, true);
        }
        if (updateView){
            sortListItems();
            listViewAdapter.notifyDataSetChanged();
        }
    }
    
    protected void connectedToFilesDownloader()
    {
        // events may get duplicated here
        final Iterator<BaseListItem> listItemIt = listViewAdapter.listItems.iterator();
        while(listItemIt.hasNext()){
            if (listItemIt.next() instanceof FileListItem){
                listItemIt.remove();
            }
        }
        listItemContext.filesDownloader.registerEventListener(this);
        List<FilesDownloadTask> tasks = listItemContext.filesDownloader.getTasks();
        for (FilesDownloadTask task : tasks){
            FileListItem listItem = getFilesListItem(task, true);
            listItem.initializeFileDownloadsInfo(task);
        }
        sortListItems();
        listViewAdapter.notifyDataSetChanged();
    }

    private void sortListItems()
    {
        Collections.sort(listViewAdapter.listItems, new Comparator<BaseListItem>(){

            @Override
            public int compare(BaseListItem lhs, BaseListItem rhs)
            {
                if (lhs.createdDateVal == rhs.createdDateVal){
                    return 0;
                } else 
                if (lhs.createdDateVal > rhs.createdDateVal){
                    return -1;
                } else {
                    return 1;
                }
            }});
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        Application.getInstance(this).showErrorDumpInfo(this);
        
        listItemContext.context = this;
        listItemContext.handler = new Handler(Looper.getMainLooper());

        setContentView(R.layout.activity_download_list);

        listViewOperations = (ListView)findViewById(R.id.listViewOperations);
        
        listViewAdapter = listItemContext.new OperationsListAdapter();
        listViewOperations.setAdapter(listViewAdapter);
        registerForContextMenu(listViewOperations);
        bindDownloaderService();
    }
    
    protected void bindDownloaderService()
    {
        if (listItemContext.gpxDownloader == null){
            bindService(new Intent(this, GpxDownloaderService.class), gpxDownloaderServiceConnection, Context.BIND_AUTO_CREATE);
        }
        if (listItemContext.filesDownloader == null){
            bindService(new Intent(this, FilesDownloaderService.class), filesDownloaderServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }
    
    protected boolean onGpxEventInternal(GpxTask task, final GpxTaskEvent event, boolean bulkOperation)
    {
        final GpxListItem listItem = getGpxListItem(task, bulkOperation);
        boolean updateView = listItem.onGpxEvent(task, event);
        return updateView;
    }
    
    @Override
    public void onTaskCreated(int taskId)
    {
        // don't bother
    }
    
    @Override
    public void onTaskEvent(final GpxTaskEvent event, final GpxTask task)
    {
        if (task == null){
            // simply ignore minor events
            return ;
        }
        boolean updateView = onGpxEventInternal(task, event, false);
        if (updateView){
            listViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onTaskRemoved(int taskId)
    {
        removeListItem(taskId, GpxListItem.class);
    }

    /**
     * Fired, then target file already exists
     * @param task
     * @param fileData
     */
    @Override
    public void onFileSkipped(FilesDownloadTask task, FileData fileData)
    {
        final FileListItem listItem = getFilesListItem(task, false);
        listItem.onFileSkipped(task, fileData);
        listViewAdapter.notifyDataSetChanged();
    }
    
    /**
     * Fired before the file begins to download
     * @param task
     * @param fileData
     */
    @Override
    public void onFileStarting(FilesDownloadTask task, FileData fileData)
    {
        final FileListItem listItem = getFilesListItem(task, false);
        listItem.onFileStarted(task, fileData, false);
        listViewAdapter.notifyDataSetChanged();
        if (filesDownloadRunningCounter <= 0){
            filesDownloadRunningCounter = 0;
            listItemContext.handler.postDelayed(new Runnable(){

                @Override
                public void run()
                {
                    if (filesDownloadRunningCounter > 0 && listItemContext.filesDownloader != null){
                        // TODO: see DownloadListContext.GpxListItem.onGpxEvent for more work explanation
                        Log.d(LOG_TAG, "Files periodic list refresh");
                        listViewAdapter.notifyDataSetChanged();
                        listItemContext.handler.postDelayed(this, 550);
                    }
                    
                }}, 550);
        }
        filesDownloadRunningCounter++;
    }
    
    
    
    
    /**
     * Fired, when the file begins to download, all headers are read, but content data is not
     * @param task
     * @param fileData
     */
    @Override
    public void onFileStarted(FilesDownloadTask task, FileData fileData)
    {
        final FileListItem listItem = getFilesListItem(task, false);
        listItem.onFileStarted(task, fileData, true);
        listViewAdapter.notifyDataSetChanged();
    }
    
    /**
     * Fired periodically during file download
     * @param task
     * @param fileData
     */
    @Override
    public void onFileProgress(FilesDownloadTask task, FileData fileData)
    {
        final FileListItem listItem = getFilesListItem(task, false);
        listItem.onFileProgress(task, fileData);
    }

    /**
     * Fired when the file has finished, either successfully, or with an error
     * @param task
     * @param fileData
     * @param exception
     */
    @Override
    public void onFileFinished(FilesDownloadTask task, FileData fileData, Exception exception)
    {
        final FileListItem listItem = getFilesListItem(task, false);
        listItem.onFileFinished(task, fileData, exception);
        listViewAdapter.notifyDataSetChanged();
        if (filesDownloadRunningCounter > 0){
            filesDownloadRunningCounter--;
        }
    }

    @Override
    public void onTaskFinished(FilesDownloadTask task)
    {
        final FileListItem listItem = getFilesListItem(task, false);
        listItem.onTaskFinished(task);
        listViewAdapter.notifyDataSetChanged();
    }
    
    @Override
    public void onTaskStateChanged(FilesDownloadTask task, int prevoiusState)
    {
        final FileListItem listItem = getFilesListItem(task, false);
        if (listItem.onTaskStateChanged(task)){
            listViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onTaskRemoved(FilesDownloadTask task)
    {
        final int taskId = task.taskId;
        removeListItem(taskId, FileListItem.class);
    }
    
    private void removeListItem(int taskId, Class<? extends BaseListItem> type)
    {
        Iterator<BaseListItem> liit = listViewAdapter.listItems.iterator();
        while (liit.hasNext()){
            BaseListItem listItem = liit.next();
            if (taskId == listItem.taskId && type.isInstance(listItem)){
                liit.remove();
                listViewAdapter.notifyDataSetChanged();
                break;
            }
        }
        
    }
    
    /*@Override
    protected void onStart()
    {
        super.onStart();
    }*/
    
    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        if (listItemContext.gpxDownloader != null) {
            listItemContext.gpxDownloader.unregisterEventListener(this);
            listItemContext.gpxDownloader = null;
        }
        if (listItemContext.filesDownloader != null) {
            listItemContext.filesDownloader.unregisterEventListener(this);
            listItemContext.filesDownloader = null;
        }
        // Detach our existing connection.
        unbindService(gpxDownloaderServiceConnection);
        unbindService(filesDownloaderServiceConnection);
    }
    
    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    /*@TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setupActionBar()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            //ActionBar actionBar = getActionBar();
            //getActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.download_list, menu);
        //return true;
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
            case android.R.id.home:
                // This ID represents the Home or Up button. In the case of this
                // activity, the Up button is shown. Use NavUtils to allow users
                // to navigate up one level in the application structure. For
                // more details, see the Navigation pattern on Android Design:
                //
                // http://developer.android.com/design/patterns/navigation.html#up-vs-back
                //
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    */

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        final AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_download_list_item_menu, menu);
        final BaseListItem listItem = listViewAdapter.getItem(info.position);
        listItem.setupContextMenu(menu);
    }    
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        final BaseListItem listItem = listViewAdapter.getItem(info.position);
        if (!listItem.onContextMenuItemSelected(item)){
            return super.onContextItemSelected(item);
        } else {
            return true;
        }
    }
}
