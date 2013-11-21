package org.bogus.domowygpx.activities;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.bogus.android.swipe2dismiss.OnSwipeTouchListener;
import org.bogus.android.swipe2dismiss.SwipeDismissListViewTouchListener;
import org.bogus.domowygpx.services.FilesDownloaderApi;
import org.bogus.domowygpx.services.FilesDownloaderListener;
import org.bogus.domowygpx.services.FilesDownloaderService;
import org.bogus.domowygpx.services.FilesDownloaderService.FilesDownloadTask;
import org.bogus.domowygpx.services.GpxDownloaderApi;
import org.bogus.domowygpx.services.GpxDownloaderListener;
import org.bogus.domowygpx.services.GpxDownloaderService;
import org.bogus.domowygpx.services.GpxDownloaderService.GpxTask;
import org.bogus.domowygpx.services.GpxDownloaderService.GpxTaskEvent;
import org.bogus.domowygpx.services.downloader.FileData;
import org.bogus.geocaching.egpx.R;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TableRow;
import android.widget.TextView;


public class DownloadListActivity extends Activity implements GpxDownloaderListener, FilesDownloaderListener
{
    private final static String LOG_TAG = "DownloadListActivity";
    
    private final static boolean FORCE_OLD_SWIPE_TO_REMOVE = false;
    
    //final Handler handler = new Handler(Looper.getMainLooper());
    
    GpxDownloaderApi gpxDownloader;
    private ServiceConnection gpxDownloaderServiceConnection = new ServiceConnection(){
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(LOG_TAG, "Established connection to " + className.getShortClassName());
            gpxDownloader = ((GpxDownloaderService.LocalBinder)service).getService();
            connectedToGpxDownloader();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            gpxDownloader = null;
            Log.w(LOG_TAG, "Lost connection to " + className.getShortClassName());
            bindDownloaderService();
        }
    };
    FilesDownloaderApi filesDownloader;
    private ServiceConnection filesDownloaderServiceConnection = new ServiceConnection(){
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(LOG_TAG, "Established connection to " + className.getShortClassName());
            filesDownloader = ((FilesDownloaderService.LocalBinder)service).getService();
            connectedToFilesDownloader();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            filesDownloader = null;
            Log.w(LOG_TAG, "Lost connection to " + className.getShortClassName());
            bindDownloaderService();
        }
    };
    
    private int stableIdCounter;
    
    abstract class BaseListItem {
        // do not keep strong referenct to view data structures
        WeakReference<ListItemViewHolder> lastHolder;
        
        int stableId;
        int taskId;
        String message;
        String details;
        boolean isError;
        boolean isDone;
        String createdDate; 
        int progressCurrent = -1;
        int progressMax = -1;
        
        /**
         * Applies list item state to the view
         * @param holder
         * @param isFresh true, if the view was freshly inflated and initialized
         * @return false, if the previously used (recycled) view can not be reused, and a new one is required
         */
        boolean applyToView(ListItemViewHolder holder, boolean isFresh)
        {
            holder.textViewMainInfo.setText(message);
            holder.textViewDateInfo.setText(createdDate);
            if (isError){
                holder.textViewMainInfo.setTextColor(DownloadListActivity.this.getResources().getColor(R.color.colorError));
            } else
            if (isDone){
                holder.textViewMainInfo.setTextColor(DownloadListActivity.this.getResources().getColor(R.color.colorDoneOk));
            } else {
                if (!isFresh){
                    // request for a fresh view 
                    return false;
                }
            }
            if (details == null || details.length() == 0){
                holder.textViewDetailsInfo.setVisibility(View.GONE);
            } else {
                holder.textViewDetailsInfo.setVisibility(View.VISIBLE);
                holder.textViewDetailsInfo.setText(details);
            }
            if (progressCurrent >= 0){
                holder.progressBar1.setVisibility(View.VISIBLE);
                if (progressMax <= 0){
                    holder.progressBar1.setIndeterminate(true);
                } else {
                    holder.progressBar1.setIndeterminate(false);
                    holder.progressBar1.setMax(progressMax);
                    holder.progressBar1.setProgress(progressCurrent);
                }
            } else {
                holder.progressBar1.setVisibility(View.INVISIBLE);
            }
            return true;
        }

        /**
         * Tests, whether current item can be removed by user
         * @return
         */
        abstract boolean canBeRemoved();
        /**
         * Notifies of the user removal action
         */
        abstract void onItemRemoval();
        
        /**
         * Sets the visibility of a Row with progress bar 
         */
        void updateProgressRow(ListItemViewHolder holder)
        {
            TableRow view = holder.tableRowProgress; 
            final int childCount = view.getChildCount();
            if (childCount == 0){
                return ;
            }
            // iterate over view tree
            for (int i=0; i<childCount; i++){
                View child = view.getChildAt(i);
                boolean childRes = iterateViewHierarchy(child);
                if (childRes){
                    setViewVisible(true, view);
                    return ;
                }
            }

            setViewVisible(false, view);
        }
        
        private boolean iterateViewHierarchy(View view)
        {
            int visibility = view.getVisibility();
            if (visibility == View.GONE || visibility == View.INVISIBLE){
                return false;
            }
            if (view instanceof ViewGroup){
                ViewGroup vg = (ViewGroup)view;
                int childCount = vg.getChildCount();
                if (childCount == 0){
                    return false;
                }
                for (int i=0; i<childCount; i++){
                    View child = vg.getChildAt(i);
                    // START inlined for performance
                    int visibility2 = child.getVisibility();
                    if (visibility2 == View.GONE || visibility == View.INVISIBLE){
                        continue;
                    }
                    // inlined for performance END
                    boolean childRes = iterateViewHierarchy(child);
                    if (childRes){
                        return true;
                    }
                }
                return false;
            }
            return true;
        }
    }
    
    class GpxListItem extends BaseListItem {
        int filesTaskIdFromGpx;
        int totalSize;
        int stateCode;
        boolean cancelling;
        
        final View.OnClickListener onCancelListener = new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (gpxDownloader.cancelTask(taskId)){
                    cancelling = true;
                    listViewAdapter.notifyDataSetChanged();
                }
            }
        };
        
        @Override
        boolean applyToView(ListItemViewHolder holder, boolean isFresh)
        {
            if (!super.applyToView(holder, isFresh)){
                return false;
            }
            boolean requiresLayout = false;
            switch(stateCode){
                case GpxTask.STATE_RUNNING:
                    requiresLayout |= setViewVisible(true, holder.btnDownloadItemCancel);
                    holder.btnDownloadItemCancel.setOnClickListener(onCancelListener);
                    setImageButtonEnabled(!cancelling, holder.btnDownloadItemCancel);
                    break;
                case GpxTask.STATE_DONE:
                case GpxTask.STATE_CANCELED:
                case GpxTask.STATE_ERROR:
                    requiresLayout |= setViewVisible(false, holder.btnDownloadItemCancel);
                    holder.btnDownloadItemCancel.setOnClickListener(null);
                    break;
            }
            
            if (requiresLayout){
                holder.markLayoutNeeded();
            }
            
            return true;
        }
        
        @Override
        boolean canBeRemoved()
        {
            return (stateCode == GpxTask.STATE_DONE || stateCode == GpxTask.STATE_CANCELED || 
                    stateCode == GpxTask.STATE_ERROR);
        }
        
        @Override
        void onItemRemoval()
        {
            gpxDownloader.removeTask(taskId);
        }
        
        boolean onGpxEventInternal(GpxTask task)
        {
            boolean updateView = false;
            updateView |= stateCode != task.stateCode;
            stateCode = task.stateCode;
            
            if (createdDate == null){
                createdDate = formatDate(task.createdDate);
                updateView = true;
            }
            
            if (task.downloaderTaskId > 0){
                filesTaskIdFromGpx = task.downloaderTaskId; 
            }
            
            if (task.stateCode == GpxTask.STATE_ERROR  || 
                    task.stateCode == GpxTask.STATE_CANCELED)
            {
                updateView = true;
                isError = true;
                progressMax = -1;
                progressCurrent = -1;
                cancelling = false;
            }
            if (task.stateCode == GpxTaskEvent.EVENT_TYPE_FINISHED_OK){
                updateView = true;
                isDone = true;
                progressMax = -1;
                progressCurrent = -1;
                cancelling = false;
            }
            if (task.stateDescription != null){
                updateView = true;
                message = task.stateDescription;
            }
            if (task.currentCacheCode != null){
                updateView = true;
            }
            if (task.totalKB > totalSize){
                totalSize = task.totalKB;
                updateView = true;
                if (isError || isDone || task.expectedTotalKB <= 0){
                    progressMax = -1;
                    progressCurrent = -1;
                } else {
                    progressMax = task.expectedTotalKB;
                    progressCurrent = totalSize;
                }
            }
            
            if (updateView || cancelling){
                StringBuilder msg = new StringBuilder(64);
                if (cancelling){
                    msg.append("Przerywam");
                }
                if (task.currentCacheCode != null){
                    if (msg.length() > 0){
                        msg.append(", ");
                    }
                    msg.append(task.currentCacheCode);
                    msg.append(", ");
                }
                if (task.totalCacheCount > 0){
                    msg.append(DownloadListActivity.this.getResources().getQuantityString(R.plurals.cache, task.totalCacheCount, task.totalCacheCount));
                }
                if (totalSize > 0){
                    msg.append(", ");
                    msg.append(formatFileSize(totalSize));
                }
                details = msg.toString();
            }
            
            return updateView;
            
        }
    }
    
    class FileListItem extends BaseListItem {
        boolean isInStateTransition;
        private int oldTotalSize;
        boolean launching;
        
        FilesDownloadTask task;
        
        final View.OnClickListener onPlayListener = new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (filesDownloader.resumeTask(taskId)){
                    isInStateTransition = true;
                    launching = true;
                    FileListItem.this.message = "Uruchamiam";
                    progressCurrent = 0;
                    progressMax = -1; 
                    listViewAdapter.notifyDataSetChanged();
                }
            }
        };
        final View.OnClickListener onPauseListener = new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (filesDownloader.pauseTask(taskId)){
                    isInStateTransition = true;
                    listViewAdapter.notifyDataSetChanged();
                }
            }
        };
        final View.OnClickListener onReplayListener = new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
               if (filesDownloader.restartTask(taskId)){
                   isInStateTransition = true;
                   launching = true;
                   FileListItem.this.message = "Uruchamiam";
                   progressCurrent = 0;
                   progressMax = -1; 
                   listViewAdapter.notifyDataSetChanged();
               }
            }
        };
        final View.OnClickListener onCancelListener = new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (filesDownloader.cancelTask(taskId)){
                    isInStateTransition = true;
                    listViewAdapter.notifyDataSetChanged();
                }
            }
        };
        
        
        @Override
        boolean applyToView(ListItemViewHolder holder, boolean isFresh)
        {
            if (!super.applyToView(holder, isFresh)){
                return false;
            }
            boolean canPlay = false;
            boolean canPause = false;
            boolean canReplay = false;
            //boolean canStop = false;
            boolean canCancel = false;
            if (launching){
                // no icons to display
            } else {
                switch(task.state){
                    case FilesDownloadTask.STATE_RUNNING:
                        isInStateTransition = false;
                        canCancel = canPause = true;
                        break;
                    case FilesDownloadTask.STATE_FINISHED:
                    case FilesDownloadTask.STATE_CANCELLED:
                        isInStateTransition = false;
                        canReplay = true;
                        break;
                    case FilesDownloadTask.STATE_CANCELLING:
                        canCancel = true;
                        break;
                    case FilesDownloadTask.STATE_PAUSING:
                        canReplay = canCancel = true;
                        break;
                    case FilesDownloadTask.STATE_PAUSED:
                        isInStateTransition = false; 
                        // this should be actually unnecessary, since call to onStateTransitionFinished
                        // should reset flag
                        canPlay = canCancel = true;
                        break;
                }
            }            
            
            
            boolean requiresLayout = false;
            requiresLayout |= setViewVisible(canPlay, holder.btnDownloadItemPlay);
            requiresLayout |= setViewVisible(canPause, holder.btnDownloadItemPause);
            requiresLayout |= setViewVisible(canReplay, holder.btnDownloadItemReplay);
            requiresLayout |= setViewVisible(canCancel, holder.btnDownloadItemCancel);

            holder.btnDownloadItemPlay.setOnClickListener(onPlayListener);
            holder.btnDownloadItemPause.setOnClickListener(onPauseListener);
            holder.btnDownloadItemReplay.setOnClickListener(onReplayListener);
            holder.btnDownloadItemCancel.setOnClickListener(onCancelListener);
            
            // Note: we can always play ;)
            //setImageButtonEnabled(!isInStateTransition, holder.btnDownloadItemPlay);
            setImageButtonEnabled(!isInStateTransition, holder.btnDownloadItemPause);
            setImageButtonEnabled(!isInStateTransition, holder.btnDownloadItemReplay);
            setImageButtonEnabled(!isInStateTransition, holder.btnDownloadItemCancel);
            if (isInStateTransition){
                //holder.btnDownloadItemPlay.setClickable(false);
                holder.btnDownloadItemPause.setClickable(false);
                holder.btnDownloadItemReplay.setClickable(false);
                holder.btnDownloadItemCancel.setClickable(false);
            }
            
            if (requiresLayout){
                holder.markLayoutNeeded();
            }
            return true;
        }

        void generateDetails()
        {
            StringBuilder msg = new StringBuilder(128);
            msg.append(DownloadListActivity.this.getResources().getQuantityString(
                R.plurals.filesCountOverTotal, task.finishedFiles, task.finishedFiles, task.totalFiles));
            if (task.totalFilesSizeKB > 0){
                msg.append(", ");
                msg.append(formatFileSize(task.totalFilesSizeKB));
            }
            int errors = task.permanentErrorFiles + task.transientErrorFiles;
            if (errors > 0){
                msg.append(", ");
                msg.append(DownloadListActivity.this.getResources().getQuantityString(
                    R.plurals.error, errors, errors));
            }
            int skipped = task.skippedFiles;
            if (skipped > 0){
                msg.append(", ");
                msg.append(DownloadListActivity.this.getResources().getQuantityString(
                    R.plurals.skipped, skipped, skipped));
            }
        }
        
        boolean onFileProgress(FilesDownloadTask task, FileData fileData, int doneKB, int totalKB)
        {
            int totalSize = task.totalFilesSizeKB;
            if (totalSize >= 1024 && totalSize-oldTotalSize<10){
                return false;
            }
            oldTotalSize = totalSize;
            generateDetails();
            return true;
        }
        
        void onFileStarted(FilesDownloadTask task, FileData fileData, boolean started)
        {
            launching = false;
            FileListItem.this.message = "Pobieram pliki";
            progressMax = task.totalFiles;
            if (started){
                generateDetails();
            }
        }
        
        void onFileSkipped(FilesDownloadTask task, FileData fileData)
        {
            progressCurrent = task.finishedFiles + task.skippedFiles + task.permanentErrorFiles + task.transientErrorFiles; 
            progressMax = task.totalFiles;
            generateDetails();
        }
        

        void onFileFinished(FilesDownloadTask task, FileData fileData, Exception exception)
        {
            progressCurrent = task.finishedFiles + task.skippedFiles + task.permanentErrorFiles + task.transientErrorFiles; 
            progressMax = task.totalFiles;
            generateDetails();
        }
        
        void onTaskFinished(FilesDownloadTask task)
        {
            isError = task.isFailed();
            isDone = !isError;
            message = "Pobieranie zakończone";
            generateDetails();
        }
        
        void onStateTransitionFinished(FilesDownloadTask task)
        {
            isInStateTransition = false;
            if (task.state == FilesDownloadTask.STATE_CANCELLED){
                message = "Pobieranie zatrzymane";
            } else {
                message = "Pobieranie wstrzymane";
            }
        }
        void initializeFileDownloadsInfo(FilesDownloadTask task)
        {
            this.task = task;
            this.createdDate = formatDate(task.createdDate);
            switch(task.state){
                case FilesDownloadTask.STATE_CANCELLING:
                case FilesDownloadTask.STATE_PAUSING:
                case FilesDownloadTask.STATE_RUNNING:
                    message = "Pobieram";
                    break;
                case FilesDownloadTask.STATE_FINISHED:
                    message = "Pobieranie zakończone";
                    break;
                case FilesDownloadTask.STATE_CANCELLED:
                    message = "Pobieranie zatrzymane";
                    break;
                case FilesDownloadTask.STATE_PAUSED:
                    message = "Pobieranie wstrzymane";
                    break;
            }
            generateDetails();
        }
        
        @Override
        boolean canBeRemoved()
        {
            int state = task.state;
            return (state == FilesDownloadTask.STATE_FINISHED || state == FilesDownloadTask.STATE_PAUSED
                    || state == FilesDownloadTask.STATE_CANCELLED);
        }
        
        @Override
        void onItemRemoval()
        {
            filesDownloader.removeTask(taskId);
        }

        
    }
    
    static class ListItemViewHolder {
        private int flags;
        
        private final static int FLAG_UNUSABLE = 1; 
        private final static int FLAG_NEEDS_LAYOUT = 2;
        
        void markUnusable()
        {
            // XXX temporary flags |= FLAG_UNUSABLE;
        }
        
        boolean isUnusable()
        {
            return (flags&FLAG_UNUSABLE) != 0;
        }
        
        void markLayoutNeeded()
        {
            flags |= FLAG_NEEDS_LAYOUT;
        }
        
        boolean performLayoutIfNeeded()
        {
            boolean result = (flags&FLAG_NEEDS_LAYOUT) != 0;
            flags&=~FLAG_NEEDS_LAYOUT;
            if (result){
                viewRoot.requestLayout(); // XXX still does not work as expected :/
            }
            return result;
        }
        
        long stableId;
        
        View viewRoot;
        TextView textViewMainInfo;
        TextView textViewDetailsInfo;
        ProgressBar progressBar1;
        TextView textViewDateInfo;
        ImageButton btnDownloadItemPlay;
        ImageButton btnDownloadItemPause;
        ImageButton btnDownloadItemReplay;
        ImageButton btnDownloadItemStop;
        ImageButton btnDownloadItemCancel;
        TableRow tableRowProgress;
        
        //BaseListItem ownerListItem;
        
        /**
         * Makes any required fix-ups in the newly created view
         */
        void initialSetup()
        {
            progressBar1.setVisibility(View.GONE);
            btnDownloadItemPlay.setVisibility(View.GONE);
            btnDownloadItemPause.setVisibility(View.GONE);
            btnDownloadItemReplay.setVisibility(View.GONE);
            btnDownloadItemStop.setVisibility(View.GONE);
            btnDownloadItemCancel.setVisibility(View.GONE);
            markLayoutNeeded();
            // XXX viewRoot.requestLayout();
        }
    }

    final List<BaseListItem> listItems = new ArrayList<BaseListItem>();
    
    private ListView listViewOperations;
    BaseAdapter listViewAdapter;

    private GpxListItem getGpxListItem(int taskId, GpxTask task)
    {
        for (BaseListItem listItem : listItems){
            if (taskId == listItem.taskId && listItem instanceof GpxListItem){
                return (GpxListItem)listItem;
            }
        }
        GpxListItem result = new GpxListItem();
        result.taskId = taskId;
        result.stableId = ++stableIdCounter;

        // try to find files task associated with our gpx task
        int idx = 0;
        for (int i = 0; i<listItems.size(); i++){
            BaseListItem listItem = listItems.get(i); 
            if (listItem.taskId == task.downloaderTaskId && listItem instanceof FileListItem){
                idx = i;
                break;
            }
        }
        listItems.add(idx, result);
        
        return result;
    }

    private FileListItem getFilesListItem(int taskId, FilesDownloadTask task)
    {
        for (BaseListItem listItem : listItems){
            if (taskId == listItem.taskId && listItem instanceof FileListItem){
                return (FileListItem)listItem;
            }
        }
        FileListItem result = new FileListItem();
        result.taskId = taskId;
        result.stableId = ++stableIdCounter;
        // try to find gpx task associated with our files task
        int idx = -1;
        for (int i = 0; i<listItems.size(); i++){
            BaseListItem listItem = listItems.get(i); 
            if (listItem instanceof GpxListItem && ((GpxListItem)listItem).filesTaskIdFromGpx == taskId){
                idx = i;
                break;
            }
        }
        listItems.add(idx+1, result);
        return result;
    }
    
    protected void connectedToGpxDownloader()
    {
        // events may get duplicated here
        final Iterator<BaseListItem> listItemIt = listItems.iterator();
        while(listItemIt.hasNext()){
            if (listItemIt.next() instanceof GpxListItem){
                listItemIt.remove();
            }
        }
        gpxDownloader.registerEventListener(this); 
        final List<GpxTask> tasks = gpxDownloader.getTasks(-1, false);
        boolean updateView = false;
        for (GpxTask task : tasks){
            updateView |= onGpxEventInternal(task);
        }
        if (updateView){
            listViewAdapter.notifyDataSetChanged();
        }
    }
    
    protected void connectedToFilesDownloader()
    {
        // events may get duplicated here
        final Iterator<BaseListItem> listItemIt = listItems.iterator();
        while(listItemIt.hasNext()){
            if (listItemIt.next() instanceof FileListItem){
                listItemIt.remove();
            }
        }
        filesDownloader.registerEventListener(this);
        List<FilesDownloadTask> tasks = filesDownloader.getTasks();
        for (FilesDownloadTask task : tasks){
            FileListItem listItem = getFilesListItem(task.taskId, task);
            listItem.initializeFileDownloadsInfo(task);
        }
        listViewAdapter.notifyDataSetChanged();
    }

    /*
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void enableAcceleration()
    {
        if (android.os.Build.VERSION.SDK_INT >= 12){
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, 
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);        
        }
    }
    */
    
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        // enableAcceleration();
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_download_list);

        listViewOperations = (ListView)findViewById(R.id.listViewOperations);
        /*listViewOperations.setRecyclerListener(new RecyclerListener()
        {
            @Override
            public void onMovedToScrapHeap(View view)
            {
                ListItemViewHolder holder = (ListItemViewHolder) view.getTag();
                if (holder != null && holder.isUnusable()){
                    // release all the resources
                    if (view instanceof ViewGroup){
                        ViewGroup vg = (ViewGroup)view;
                        vg.removeAllViews();
                    }
                    view.setTag(null);
                }
                
            }
        });*/
        
        listViewAdapter = new BaseAdapter()
        {
            LayoutInflater layoutInflater = LayoutInflater.from(DownloadListActivity.this);
            private OnSwipeTouchListener onSwipeTouchListener;
            {
                // support for swipe-to-dismiss for older android devices
                // TODO: fuck that compatibility, implement context menu for the item
                if (android.os.Build.VERSION.SDK_INT < 12 || FORCE_OLD_SWIPE_TO_REMOVE){
                    onSwipeTouchListener = new OnSwipeTouchListener(DownloadListActivity.this) {
                        @Override
                        public void onSwipeRight()
                        {
                            // this view may get recycled, and refreshed without background
                            // but ignore the problem now
                            final ListItemViewHolder holder = (ListItemViewHolder) view.getTag();
                            final BaseListItem listItem = getItemById(holder.stableId);
                            if (listItem.canBeRemoved()){
                                final Drawable oldBackground = view.getBackground();
                                view.setBackgroundColor(getResources().getColor(R.color.colorListItemBackgroundRemovalIndicator));
                                view.postDelayed(new Runnable(){
                                    @SuppressWarnings("synthetic-access")
                                    @Override
                                    public void run()
                                    {
                                        holder.markUnusable();
                                        listItem.onItemRemoval();
                                        view.setBackground(oldBackground);
                                    }}, 250);
                            }
                        }
                    };
                }
            };
            
            @Override
            public boolean hasStableIds() {
                return true;
            }
            
            @Override
            public View getView(int position, View convertView, ViewGroup parent)
            {
                // XXX todo: implement our own pool of views, based on SoftReference and stableId (can be int, too)
                final BaseListItem listItem = (BaseListItem)getItem(position);
                ListItemViewHolder holder;
                boolean isFresh;
                do{
                    isFresh = convertView == null;
                    if (convertView == null) {
                        convertView = layoutInflater.inflate(R.layout.activity_download_list_item, null);
                        holder = new ListItemViewHolder();
                        holder.viewRoot = convertView;
                        holder.textViewMainInfo = (TextView)convertView.findViewById(R.id.textViewMainInfo);
                        holder.textViewDetailsInfo = (TextView)convertView.findViewById(R.id.textViewDetailsInfo);
                        holder.progressBar1 = (ProgressBar)convertView.findViewById(R.id.progressBar1);
                        holder.textViewDateInfo = (TextView)convertView.findViewById(R.id.textViewDateInfo);
                        holder.btnDownloadItemPlay = (ImageButton)convertView.findViewById(R.id.btnDownloadItemPlay);
                        holder.btnDownloadItemPause = (ImageButton)convertView.findViewById(R.id.btnDownloadItemPause);
                        holder.btnDownloadItemReplay = (ImageButton)convertView.findViewById(R.id.btnDownloadItemReplay);
                        holder.btnDownloadItemStop = (ImageButton)convertView.findViewById(R.id.btnDownloadItemStop);
                        holder.btnDownloadItemCancel = (ImageButton)convertView.findViewById(R.id.btnDownloadItemCancel);
                        holder.tableRowProgress = (TableRow)convertView.findViewById(R.id.tableRowProgress);
                        if (onSwipeTouchListener != null){
                            convertView.setOnTouchListener(onSwipeTouchListener);
                        }
                        holder.initialSetup();
                        //listItem.initialSetup(holder);
                        convertView.setTag(holder);
                    } else {
                        holder = (ListItemViewHolder) convertView.getTag();
                        //if (holder.ownerListItem != null && listItem.getClass() != holder.ownerListItem.getClass()){
                        //    convertView = null;
                        //    continue;
                        //}
                        
                    }
                    if (holder == null || holder.isUnusable()){
                        convertView = null;
                        continue;
                    }
                    holder.stableId = listItem.stableId;
    
                    if (listItem.applyToView(holder, isFresh) || isFresh){
                        break;
                    } else {
                        convertView = null;
                    }
                }while(true);
                listItem.updateProgressRow(holder);
                ListItemViewHolder lastHolder = null;
                if (listItem.lastHolder != null){
                    lastHolder = listItem.lastHolder.get(); 
                }
                if (lastHolder == null || lastHolder != holder){
                    listItem.lastHolder = new WeakReference<ListItemViewHolder>(holder);
                }
                holder.performLayoutIfNeeded();
                // holder.viewRoot.requestLayout();
                //if (!isFresh){
                //    convertView.setMinimumHeight(0);
                //    convertView.setBottom(convertView.getTop());
                //}
                return convertView;
            }
            
            @Override
            public long getItemId(int position)
            {
                return ((BaseListItem)getItem(position)).stableId;
            }
            
            @Override
            public Object getItem(final int position)
            {
                return listItems.get(position);
            }
            
            public BaseListItem getItemById(final long id)
            {
                for (BaseListItem li : listItems){
                    if (li.stableId == id){
                        return li;
                    }
                }
                return null;
            }
            
            @Override
            public int getCount()
            {
                return listItems.size();
            }
            
            @Override
            public int getItemViewType(int position) {
                BaseListItem item = (BaseListItem)getItem(position);
                // not so good design, but should work fast :-]
                if (item instanceof GpxListItem){
                    return 0;
                } else {
                    return 1;
                }
            }

            @Override
            public int getViewTypeCount() {
                return 2;
            }
        };
        if (!FORCE_OLD_SWIPE_TO_REMOVE && android.os.Build.VERSION.SDK_INT >= 12){
            SwipeDismissListViewTouchListener touchListener =
                    new SwipeDismissListViewTouchListener(
                        listViewOperations,
                            new SwipeDismissListViewTouchListener.DismissCallbacks() {
                                @Override
                                public void onDismiss(ListView listView, int[] reverseSortedPositions) {
                                    for (int position : reverseSortedPositions) {
                                        final BaseListItem listItem = (BaseListItem)listViewAdapter.getItem(position);
                                        listItem.onItemRemoval();
                                    }
                                    // listViewAdapter.notifyDataSetChanged();
                                }

                                @Override
                                public boolean canDismiss(int position)
                                {
                                    final BaseListItem listItem = (BaseListItem)listViewAdapter.getItem(position);
                                    return listItem.canBeRemoved();
                                }
                            });
            listViewOperations.setOnTouchListener(touchListener);
            listViewOperations.setOnScrollListener(touchListener.makeScrollListener());
            
        }
        
        listViewOperations.setAdapter(listViewAdapter);
        
        bindDownloaderService();
    }

    /**
     * Sets the specified image buttonto the given state, while modifying or
     * "graying-out" the icon as well
     * 
     * @param enabled The state of the menu item
     * @param item The image button to modify
     */
    static void setImageButtonEnabled(boolean enabled, ImageButton item) {
        if (item.isEnabled() == enabled){
            return ;
        }
        Drawable originalImg = (Drawable)item.getTag(R.id.imageButtonOriginalImage);
        if (originalImg == null){
            originalImg = item.getDrawable();
            item.setTag(R.id.imageButtonOriginalImage, originalImg);
        }
        if (enabled){
            item.setImageDrawable(originalImg);
        } else {
            Drawable grayed = (Drawable)item.getTag(R.id.imageButtonGrayedImage);
            if (grayed == null){
                grayed = getGrayscaled(originalImg);
                item.setTag(R.id.imageButtonGrayedImage, grayed);
            }
            item.setImageDrawable(grayed);
        }
        item.setEnabled(enabled);
    }

    static boolean setViewVisible(boolean visible, View view) {
        int vis = view.getVisibility();
        boolean currentlyVisible = vis == View.VISIBLE || vis == View.INVISIBLE;
        if (visible != currentlyVisible){
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
            return true;
        } else {
            return false;
        }
    }

    private static Drawable getGrayscaled(Drawable src) {
        Drawable res = src.mutate();
        res.setColorFilter(Color.GRAY, Mode.SRC_IN);
        return res;
    } 
    //private SparseArray<Drawable> grayscaledImages = new SparseArray<Drawable>();      

    
    protected void bindDownloaderService()
    {
        if (gpxDownloader == null){
            bindService(new Intent(this, GpxDownloaderService.class), gpxDownloaderServiceConnection, Context.BIND_AUTO_CREATE);
        }
        if (filesDownloader == null){
            bindService(new Intent(this, FilesDownloaderService.class), filesDownloaderServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }
    
    String formatFileSize(final int sizeKB)
    {
        if (sizeKB <= 1024){
            return sizeKB + " KB"; 
        } else {
            final NumberFormat nf = new DecimalFormat("####0.##"/*, new DecimalFormatSymbols(Locale.getDefault())*/);
            return nf.format(sizeKB/1024.0) + " MB";
        }
    }
    
    String formatDate(final long date)
    {
        final long now = System.currentTimeMillis();
        final long diffHours = (now-date) / (1000L*60L*60L);
        StringBuilder sb = new StringBuilder(24);
        final Date date2 = new Date(date);
        
        if (diffHours > 12 || date > now){
            final DateFormat df2 = android.text.format.DateFormat.getMediumDateFormat(this);
            sb.append(df2.format(date2));
            sb.append('\n');
        }
        final DateFormat df1 = android.text.format.DateFormat.getTimeFormat(this);
        sb.append(df1.format(date2));
        return sb.toString();
    }
    
    protected boolean onGpxEventInternal(GpxTask task)
    {
        final GpxListItem listItem = getGpxListItem(task.taskId, task);
        boolean updateView = listItem.onGpxEventInternal(task);
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
        boolean updateView = onGpxEventInternal(task);
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
        final FileListItem listItem = getFilesListItem(task.taskId, task);
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
        final FileListItem listItem = getFilesListItem(task.taskId, task);
        listItem.onFileStarted(task, fileData, false);
        listViewAdapter.notifyDataSetChanged();
    }
    
    /**
     * Fired, when the file begins to download, all headers are read, but content data is not
     * @param task
     * @param fileData
     */
    @Override
    public void onFileStarted(FilesDownloadTask task, FileData fileData)
    {
        final FileListItem listItem = getFilesListItem(task.taskId, task);
        listItem.onFileStarted(task, fileData, true);
        listViewAdapter.notifyDataSetChanged();
    }
    
    /**
     * Fired periodically during file download
     * @param task
     * @param fileData
     * @param doneKB
     * @param totalKB Total file size, or -1 if unknown
     */
    @Override
    public void onFileProgress(FilesDownloadTask task, FileData fileData, int doneKB, int totalKB)
    {
        final FileListItem listItem = getFilesListItem(task.taskId, task);
        if (listItem.onFileProgress(task, fileData, doneKB, totalKB)){
            listViewAdapter.notifyDataSetChanged();
        }

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
        final FileListItem listItem = getFilesListItem(task.taskId, task);
        listItem.onFileFinished(task, fileData, exception);
        listViewAdapter.notifyDataSetChanged();
    }

    @Override
    public void onTaskFinished(FilesDownloadTask task)
    {
        final FileListItem listItem = getFilesListItem(task.taskId, task);
        listItem.onTaskFinished(task);
        listViewAdapter.notifyDataSetChanged();
    }

    @Override
    public void onTaskPaused(FilesDownloadTask task)
    {
        final FileListItem listItem = getFilesListItem(task.taskId, task);
        listItem.onStateTransitionFinished(task);
        listViewAdapter.notifyDataSetChanged();
    }
    
    @Override
    public void onTaskCancelled(FilesDownloadTask task)
    {
        final FileListItem listItem = getFilesListItem(task.taskId, task);
        listItem.onStateTransitionFinished(task);
        listViewAdapter.notifyDataSetChanged();
    }
    
    @Override
    public void onTaskRemoved(FilesDownloadTask task)
    {
        final int taskId = task.taskId;
        removeListItem(taskId, FileListItem.class);
    }
    
    private void removeListItem(int taskId, Class<? extends BaseListItem> type)
    {
        Iterator<BaseListItem> liit = listItems.iterator();
        while (liit.hasNext()){
            BaseListItem listItem = liit.next();
            if (taskId == listItem.taskId && type.isInstance(listItem)){
                liit.remove();
                // XXX listViewOperations.requestLayout();
                //listViewOperations.setAdapter(listViewAdapter);
                // listViewOperations.smoothScrollToPosition(position)
                // listViewOperations.invalidateViews();
                
                //final int scrollX = listViewOperations.getScrollX();
                //final int scrollY = listViewOperations.getScrollY();

                //listViewOperations.measure(
                //    MeasureSpec.makeMeasureSpec(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED), 
                //    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                
                // seting the adapter will do the trick of entire list refresh...
                //listViewOperations.setAdapter(null);
                //listViewOperations.setAdapter(listViewAdapter);
                
                // mark the view item as unusable any more, so it will not get recycled
                ListItemViewHolder lastHolder = null;
                if (listItem.lastHolder != null){
                    lastHolder = listItem.lastHolder.get(); 
                }
                if (lastHolder != null){
                    lastHolder.markUnusable();
                }
                listViewAdapter.notifyDataSetChanged();
                //listViewOperations.scrollTo(scrollX, scrollY);
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
        if (gpxDownloader != null) {
            gpxDownloader.unregisterEventListener(this);
            gpxDownloader = null;
        }
        if (filesDownloader != null) {
            filesDownloader.unregisterEventListener(this);
            filesDownloader = null;
        }
        // Detach our existing connection.
        unbindService(gpxDownloaderServiceConnection);
        unbindService(filesDownloaderServiceConnection);
    }
    
    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    @SuppressWarnings("unused")
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setupActionBar()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
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

}
