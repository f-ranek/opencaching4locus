package org.bogus.domowygpx.activities;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings.Secure;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class DownloadListActivity extends Activity implements GpxDownloaderListener, FilesDownloaderListener
{
    private final static String LOG_TAG = "DownloadListActivity";
    
    private final static boolean FORCE_OLD_SWIPE_TO_REMOVE = false;
    
    Handler handler;
    
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
        int stableId;
        int taskId;
        String message;
        String details;
        boolean isError;
        boolean isDone;
        String createdDate; 
        long createdDateVal;
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
                holder.textViewMainInfo.setTextAppearance(DownloadListActivity.this, R.style.TextAppearance_Large_Error);
                //holder.textViewMainInfo.setTextColor(DownloadListActivity.this.getResources().getColor(R.color.colorError));
            } else
            if (isDone){
                holder.textViewMainInfo.setTextAppearance(DownloadListActivity.this, R.style.TextAppearance_Large_Ok);
                //holder.textViewMainInfo.setTextAppearance(DownloadListActivity.this, R.style.TextAppearance_Large_Ok);
                //holder.textViewMainInfo.setTextColor(DownloadListActivity.this.getResources().getColor(R.color.colorDoneOk));
            } else {
                holder.textViewMainInfo.setTextAppearance(DownloadListActivity.this, R.style.TextAppearance_Large);
                // holder.textViewMainInfo.setTextAppearance(DownloadListActivity.this, android.R.style.TextAppearance_Large);
                /*if (!isFresh){
                    // request for a fresh view 
                    return false;
                }*/
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
                // could also unwrap the progressBar from it's parent layout,
                // and set GONE when no action buttons are to be displayed,
                // and INVISIBLE when there are any
                // that way we won't need updateProgressRow any more
                // if we set it gone without parent wrapping layout, action buttons
                // (actualy the view holding them) will move to the left
                holder.progressBar1.setVisibility(View.GONE); 
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
         * Setup context menu for this list item 
         * @param menu
         */
        void setupContextMenu(ContextMenu menu)
        {
            final MenuItem mi = menu.findItem(R.id.actionDownloadItemRemove);
            if (mi != null){
                mi.setEnabled(canBeRemoved());
            }
        }
        /**
         * Invoked, when menu item is selected by user
         * @param item Selected menu item
         * @return true, if menu item has been processed in any way
         */
        boolean onContextMenuItemSelected(MenuItem item)
        {
            switch(item.getItemId()){
                case R.id.actionDownloadItemRemove:
                    onItemRemoval();
                    return true;
            }
            return false;
        }
        
        /**
         * Sets the visibility of a Row with progress bar 
         */
        void updateProgressRow(ListItemViewHolder holder)
        {
            ViewGroup view = holder.tableRowProgress; 
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
        int totalSize;
        int stateCode;
        boolean cancelling;
        
        Runnable statusUpdater;
        
        final View.OnClickListener onCancelListener = new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                GpxListItem.this.cancel();
            }
        };
        
        void cancel()
        {
            if (gpxDownloader.cancelTask(taskId)){
                cancelling = true;
                listViewAdapter.notifyDataSetChanged();
            }
        }
        
        @Override
        boolean applyToView(ListItemViewHolder holder, boolean isFresh)
        {
            if (!super.applyToView(holder, isFresh)){
                return false;
            }
            switch(stateCode){
                case GpxTask.STATE_RUNNING:
                    holder.btnDownloadItemCancel.setVisibility(View.VISIBLE);
                    holder.btnDownloadItemCancel.setOnClickListener(onCancelListener);
                    setImageButtonEnabled(!cancelling, holder.btnDownloadItemCancel);
                    break;
                case GpxTask.STATE_DONE:
                case GpxTask.STATE_CANCELED:
                case GpxTask.STATE_ERROR:
                    holder.btnDownloadItemCancel.setVisibility(View.GONE);
                    holder.btnDownloadItemCancel.setOnClickListener(null);
                    break;
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
        
        @Override
        void setupContextMenu(ContextMenu menu)
        {
            super.setupContextMenu(menu);
            final MenuItem mi = menu.findItem(R.id.actionDownloadItemCancel);
            mi.setEnabled(stateCode == GpxTask.STATE_RUNNING);
        }
        
        @Override
        boolean onContextMenuItemSelected(MenuItem item)
        {
            switch(item.getItemId()){
                case R.id.actionDownloadItemCancel:
                    cancel();
                    return true;
                case R.id.actionDownloadItemDevDetails:
                    String devDetails = gpxDownloader.taskToDeveloperDebugString(taskId);
                    showDeveloperDetailsInfo(devDetails);
                    return true;
            }
            return super.onContextMenuItemSelected(item);
        }
        
        boolean onGpxEventInternal(GpxTask task, GpxTaskEvent event)
        {
            boolean updateView = false;
            updateView |= stateCode != task.stateCode;
            stateCode = task.stateCode;
            
            if (createdDate == null){
                createdDateVal = task.createdDate;
                createdDate = formatDate(task.createdDate);
                updateView = true;
            }
            
            if (event != null && event.eventType == GpxTaskEvent.EVENT_TYPE_CACHE_CODE && 
                    task.stateCode == GpxTask.STATE_RUNNING && statusUpdater == null) 
            {
                statusUpdater = new Runnable()
                {
                    GpxDownloaderListener statusUpdaterListener = new GpxDownloaderListener()
                    {
                        @Override
                        public void onTaskRemoved(int taskId)
                        {
                        }
                        
                        @Override
                        public void onTaskEvent(GpxTaskEvent event, GpxTask task)
                        {
                            if (GpxListItem.this.onGpxEventInternal(task, event)){
                                DownloadListActivity.this.listViewAdapter.notifyDataSetChanged();
                            }
                        }
                        
                        @Override
                        public void onTaskCreated(int taskId)
                        {
                        }
                    };

                    @Override
                    public void run()
                    {
                        GpxDownloaderApi gpxDownloader = DownloadListActivity.this.gpxDownloader; 
                        boolean updated = false;
                        if (gpxDownloader != null){
                            updated = gpxDownloader.updateCurrentCacheStatus(taskId, statusUpdaterListener);
                        }
                        if (!updated || statusUpdater == null){
                            statusUpdater = null;
                        } else {
                            handler.postDelayed(statusUpdater, 1000);
                        }
                    }
                };
                handler.postDelayed(statusUpdater, 1000);
            }
            
            if (task.stateCode == GpxTask.STATE_ERROR  || 
                    task.stateCode == GpxTask.STATE_CANCELED)
            {
                updateView = true;
                isError = true;
                progressMax = -1;
                progressCurrent = -1;
                cancelling = false;
                statusUpdater = null;
            }
            if (task.stateCode == GpxTaskEvent.EVENT_TYPE_FINISHED_OK){
                updateView = true;
                isDone = true;
                progressMax = -1;
                progressCurrent = -1;
                cancelling = false;
                statusUpdater = null;
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
                generateDetails(task);
            }
            
            return updateView;
            
        }
        
        private void generateDetails(GpxTask task)
        {
            StringBuilder msg = new StringBuilder(64);
            if (cancelling){
                msg.append("Przerywam");
            }
            if (task.currentCacheCode != null){
                if (msg.length() > 0){
                    msg.append(", ");
                }
                msg.append(task.currentCacheCode);
                if (task.currentCacheName != null){
                    msg.append(" - ").append(task.currentCacheName);
                }
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
    }
    
    class FileListItem extends BaseListItem {
        private long oldTotalSize;
        FilesDownloadTask task;
        
        final View.OnClickListener onPlayListener = new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                replay(false);
            }
        };
        final View.OnClickListener onStopListener = new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                filesDownloader.stopTask(taskId);
            }
        };

        final View.OnClickListener onCancelListener = new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                filesDownloader.cancelTask(taskId);
            }
        };
        
        void replay(boolean restartFromScratch)
        {
            boolean result = filesDownloader.restartTask(taskId, restartFromScratch);
            if (result){
                progressCurrent = 0;
                progressMax = -1; 
                listViewAdapter.notifyDataSetChanged();
            }
        }
        
        @Override
        boolean applyToView(ListItemViewHolder holder, boolean isFresh)
        {
            if (this.createdDate == null){
                this.createdDateVal = task.createdDate;
                this.createdDate = formatDate(task.createdDate);
            }
            if (!super.applyToView(holder, isFresh)){
                return false;
            }
            boolean canStop = false;
            boolean enableStop = true;
            boolean canPlay = false;
            boolean canCancel = false;
            boolean enableCancel = true;
            switch(task.state){
                case FilesDownloadTask.STATE_RUNNING:
                    canCancel = canStop = true;
                    break;
                case FilesDownloadTask.STATE_FINISHED:
                    break;
                case FilesDownloadTask.STATE_CANCELLING:
                    canCancel = true;
                    enableCancel = false;
                    break;
                case FilesDownloadTask.STATE_PAUSING:
                    canStop = canCancel = true;
                    enableStop = false;
                    break;
                case FilesDownloadTask.STATE_STOPPED:
                    canPlay = true;
                    break;
            }
            
            setViewVisible(canStop, holder.btnDownloadItemStop);
            setViewVisible(canPlay, holder.btnDownloadItemPlay);
            setViewVisible(canCancel, holder.btnDownloadItemCancel);

            holder.btnDownloadItemStop.setOnClickListener(onStopListener);
            holder.btnDownloadItemPlay.setOnClickListener(onPlayListener);
            holder.btnDownloadItemCancel.setOnClickListener(onCancelListener);
            
            setImageButtonEnabled(enableStop, holder.btnDownloadItemStop);
            setImageButtonEnabled(enableCancel, holder.btnDownloadItemCancel);
            return true;
        }

        @Override
        void setupContextMenu(ContextMenu menu)
        {
            super.setupContextMenu(menu);
            
            boolean canStop = false;
            boolean enableStop = true;
            boolean canPlay = false;
            boolean canRestart = false;
            boolean canCancel = false;
            boolean enableCancel = true;
            switch(task.state){
                case FilesDownloadTask.STATE_RUNNING:
                    canCancel = canStop = true;
                    break;
                case FilesDownloadTask.STATE_FINISHED:
                    // this differs from #applyToView
                    canRestart = true;
                    break;
                case FilesDownloadTask.STATE_STOPPED:
                    canRestart = canPlay = true;
                    break;
                case FilesDownloadTask.STATE_CANCELLING:
                    canCancel = true;
                    enableCancel = false;
                    break;
                case FilesDownloadTask.STATE_PAUSING:
                    canStop = canCancel = true;
                    enableStop = false;
                    break;
            }

            menu.findItem(R.id.actionDownloadItemPlay).setVisible(canPlay);
            menu.findItem(R.id.actionDownloadItemRestart).setVisible(canRestart);
            MenuItem itemStop = menu.findItem(R.id.actionDownloadItemStop);
            itemStop.setVisible(canStop);
            itemStop.setEnabled(enableStop);
            MenuItem itemCancel = menu.findItem(R.id.actionDownloadItemCancel);
            itemCancel.setVisible(canCancel);
            itemCancel.setEnabled(enableCancel);
            
        }
        
        @Override
        boolean onContextMenuItemSelected(MenuItem item)
        {
            switch(item.getItemId()){
                case R.id.actionDownloadItemPlay:
                    replay(false);
                    return true;
                case R.id.actionDownloadItemRestart:
                    replay(true);
                    return true;
                case R.id.actionDownloadItemStop:
                    filesDownloader.stopTask(taskId);
                    return true;
                case R.id.actionDownloadItemCancel:
                    filesDownloader.cancelTask(taskId);
                    return true;
                case R.id.actionDownloadItemDevDetails:
                    String devDetails = filesDownloader.taskToDeveloperDebugString(taskId);
                    showDeveloperDetailsInfo(devDetails);
                    return true;
            }
            return super.onContextMenuItemSelected(item);
        }

        private void generateMainInfo()
        {
            switch(task.state){
                case FilesDownloadTask.STATE_CANCELLING:
                    message = "Trwa anulowanie";
                    break;
                case FilesDownloadTask.STATE_PAUSING:
                    message = "Zatrzymuję";
                    break;
                case FilesDownloadTask.STATE_RUNNING:
                    message = "Pobieram";
                    break;
                case FilesDownloadTask.STATE_FINISHED:
                    message = "Pobieranie zakończone";
                    progressCurrent = progressMax = -1;
                    isError = task.isFailed();
                    isDone = !isError;
                    break;
                case FilesDownloadTask.STATE_STOPPED:
                    message = "Pobieranie zatrzymane";
                    progressCurrent = progressMax = -1;
                    break;
            }
            
        }
        
        private void generateDetails()
        {
            StringBuilder msg = new StringBuilder(128);
            msg.append(DownloadListActivity.this.getResources().getQuantityString(
                task.finishedFiles == task.totalFiles ? R.plurals.filesCount : R.plurals.filesCountOverTotal, 
                task.finishedFiles, task.finishedFiles, task.totalFiles));
            if (task.totalDownloadSize > 1024){
                msg.append(", ");
                msg.append(formatFileSize((int)(task.totalDownloadSize/1024L)));
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
            if (msg.length() > 0){
                details = msg.toString();
            } else {
                details = null;
            }
        }
        
        boolean onFileProgress(FilesDownloadTask task, FileData fileData)
        {
            this.task = task;
            long totalSize = task.totalDownloadSize;
            if (totalSize >= 1048576 && totalSize-oldTotalSize<1024){
                return false;
            }
            oldTotalSize = totalSize;
            generateDetails();
            return true;
        }
        
        void onFileStarted(FilesDownloadTask task, FileData fileData, boolean started)
        {
            this.task = task;
            if (started){
                progressMax = task.totalFiles;
                progressCurrent = task.finishedFiles + task.skippedFiles + task.permanentErrorFiles + task.transientErrorFiles;
                generateMainInfo();
                generateDetails();
            } else {
                if (progressMax == -1){
                    // show we don't know ;)
                    progressMax = progressCurrent = 0;
                }
            }
        }
        
        void onFileSkipped(FilesDownloadTask task, FileData fileData)
        {
            this.task = task;
            progressCurrent = task.finishedFiles + task.skippedFiles + task.permanentErrorFiles + task.transientErrorFiles; 
            progressMax = task.totalFiles;
            generateDetails();
        }
        

        void onFileFinished(FilesDownloadTask task, FileData fileData, Exception exception)
        {
            this.task = task;
            progressCurrent = task.finishedFiles + task.skippedFiles + task.permanentErrorFiles + task.transientErrorFiles; 
            progressMax = task.totalFiles;
            generateDetails();
        }
        
        void onTaskFinished(FilesDownloadTask task)
        {
            this.task = task;
            generateMainInfo();
            generateDetails();
        }
        
        boolean onTaskStateChanged(FilesDownloadTask task)
        {
            this.task = task;
            generateMainInfo();
            generateDetails();
            return true;
        }
        void initializeFileDownloadsInfo(FilesDownloadTask task)
        {
            this.task = task;
            this.createdDateVal = task.createdDate;
            progressCurrent = task.finishedFiles + task.skippedFiles + task.permanentErrorFiles + task.transientErrorFiles; 
            progressMax = task.totalFiles;
            generateMainInfo();
            generateDetails();
        }
        
        @Override
        boolean canBeRemoved()
        {
            int state = task.state;
            return (state == FilesDownloadTask.STATE_FINISHED || state == FilesDownloadTask.STATE_STOPPED);
        }
        
        @Override
        void onItemRemoval()
        {
            filesDownloader.removeTask(taskId);
        }

        
    }
    
    static class ListItemViewHolder {
        /*
        private int flags;
        
        private final static int FLAG_UNUSABLE = 1; 
        private final static int FLAG_NEEDS_LAYOUT = 2;
        
        void markUnusable()
        {
            temporary flags |= FLAG_UNUSABLE;
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
                viewRoot.requestLayout(); 
            }
            return result;
        }*/
        
        // long stableId;
        
        View viewRoot;
        TextView textViewMainInfo;
        TextView textViewDetailsInfo;
        ProgressBar progressBar1;
        TextView textViewDateInfo;
        ImageButton btnDownloadItemStop;
        ImageButton btnDownloadItemPlay;
        ImageButton btnDownloadItemCancel;
        ViewGroup tableRowProgress;
        
        void initialSetup(View viewRoot)
        {
            this.viewRoot = viewRoot;
            textViewMainInfo = (TextView)viewRoot.findViewById(R.id.textViewMainInfo);
            textViewDetailsInfo = (TextView)viewRoot.findViewById(R.id.textViewDetailsInfo);
            progressBar1 = (ProgressBar)viewRoot.findViewById(R.id.progressBar1);
            textViewDateInfo = (TextView)viewRoot.findViewById(R.id.textViewDateInfo);
            btnDownloadItemStop = (ImageButton)viewRoot.findViewById(R.id.btnDownloadItemStop);
            btnDownloadItemPlay = (ImageButton)viewRoot.findViewById(R.id.btnDownloadItemPlay);
            btnDownloadItemCancel = (ImageButton)viewRoot.findViewById(R.id.btnDownloadItemCancel);
            tableRowProgress = (ViewGroup)viewRoot.findViewById(R.id.tableRowProgress);
            
        }
        
        /**
         * Makes any required fix-ups in the newly created view
         */
        void initialFixup()
        {
            progressBar1.setVisibility(View.GONE);
            btnDownloadItemStop.setVisibility(View.GONE);
            btnDownloadItemPlay.setVisibility(View.GONE);
            btnDownloadItemCancel.setVisibility(View.GONE);
        }
    }

    final List<BaseListItem> listItems = new ArrayList<BaseListItem>();
    
    private ListView listViewOperations;
    OperationsListAdapter listViewAdapter;

    class OperationsListAdapter extends BaseAdapter
    {
        private LayoutInflater layoutInflater = LayoutInflater.from(DownloadListActivity.this);
        
        @Override
        public boolean hasStableIds() {
            return true;
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            final BaseListItem listItem = getItem(position);
            ListItemViewHolder holder;
            boolean isFresh;
            do{
                isFresh = convertView == null;
                if (convertView == null) {
                    convertView = layoutInflater.inflate(R.layout.activity_download_list_item, null);
                    holder = new ListItemViewHolder();
                    holder.initialSetup(convertView);
                    holder.initialFixup();
                    convertView.setTag(holder);
                } else {
                    holder = (ListItemViewHolder) convertView.getTag();
                }
                /*
                if (holder == null || holder.isUnusable()){
                    if (convertView instanceof ViewGroup){
                        ViewGroup vg = (ViewGroup)convertView;
                        vg.removeAllViews();
                    }
                    convertView.setTag(null);
                    convertView = null;
                    continue;
                }
                */

                if (listItem.applyToView(holder, isFresh) || isFresh){
                    break;
                } else {
                    convertView = null;
                }
            }while(true);
            listItem.updateProgressRow(holder);
            // holder.performLayoutIfNeeded();
            return convertView;
        }
        
        @Override
        public long getItemId(int position)
        {
            return getItem(position).stableId;
        }
        
        @Override
        public BaseListItem getItem(final int position)
        {
            return listItems.get(position);
        }
        
        @Override
        public int getCount()
        {
            return listItems.size();
        }
        
        @Override
        public int getItemViewType(int position) {
            BaseListItem item = getItem(position);
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
    }
    
    private GpxListItem getGpxListItem(GpxTask task, boolean bulkOperation)
    {
        final int taskId = task.taskId;
        for (BaseListItem listItem : listItems){
            if (taskId == listItem.taskId && listItem instanceof GpxListItem){
                return (GpxListItem)listItem;
            }
        }
        GpxListItem result = new GpxListItem();
        result.createdDateVal = task.createdDate;
        result.taskId = taskId;
        result.stableId = ++stableIdCounter;
        listItems.add(result);
        if (!bulkOperation){
            sortListItems();
        }
        return result;
    }

    private FileListItem getFilesListItem(FilesDownloadTask task, boolean bulkOperation)
    {
        final int taskId = task.taskId;
        for (BaseListItem listItem : listItems){
            if (taskId == listItem.taskId && listItem instanceof FileListItem){
                return (FileListItem)listItem;
            }
        }
        FileListItem result = new FileListItem();
        result.createdDateVal = task.createdDate;
        result.taskId = taskId;
        result.stableId = ++stableIdCounter;
        listItems.add(result); 
        if (!bulkOperation){
            sortListItems();
        }
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
        final Iterator<BaseListItem> listItemIt = listItems.iterator();
        while(listItemIt.hasNext()){
            if (listItemIt.next() instanceof FileListItem){
                listItemIt.remove();
            }
        }
        filesDownloader.registerEventListener(this);
        List<FilesDownloadTask> tasks = filesDownloader.getTasks();
        for (FilesDownloadTask task : tasks){
            FileListItem listItem = getFilesListItem(task, true);
            listItem.initializeFileDownloadsInfo(task);
        }
        sortListItems();
        listViewAdapter.notifyDataSetChanged();
    }

    private void sortListItems()
    {
        Collections.sort(listItems, new Comparator<BaseListItem>(){

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
        
        handler = new Handler(Looper.myLooper());
        
        setContentView(R.layout.activity_download_list);

        listViewOperations = (ListView)findViewById(R.id.listViewOperations);
        
        listViewAdapter = new OperationsListAdapter();
        if (!FORCE_OLD_SWIPE_TO_REMOVE && android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1){
            SwipeDismissListViewTouchListener touchListener =
                    new SwipeDismissListViewTouchListener(
                        listViewOperations,
                            new SwipeDismissListViewTouchListener.DismissCallbacks() {
                                @Override
                                public void onDismiss(ListView listView, int[] reverseSortedPositions) {
                                    for (int position : reverseSortedPositions) {
                                        final BaseListItem listItem = listViewAdapter.getItem(position);
                                        listItem.onItemRemoval();
                                    }
                                }

                                @Override
                                public boolean canDismiss(int position)
                                {
                                    final BaseListItem listItem = listViewAdapter.getItem(position);
                                    return listItem.canBeRemoved();
                                }
                            });
            listViewOperations.setOnTouchListener(touchListener);
            listViewOperations.setOnScrollListener(touchListener.makeScrollListener());
            
        }
        
        listViewOperations.setAdapter(listViewAdapter);
        registerForContextMenu(listViewOperations);
        //setupActionBar();
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
        //item.setClickable(enabled);
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
    
    protected boolean onGpxEventInternal(GpxTask task, final GpxTaskEvent event, boolean bulkOperation)
    {
        final GpxListItem listItem = getGpxListItem(task, bulkOperation);
        boolean updateView = listItem.onGpxEventInternal(task, event);
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
        if (listItem.onFileProgress(task, fileData)){
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
        final FileListItem listItem = getFilesListItem(task, false);
        listItem.onFileFinished(task, fileData, exception);
        listViewAdapter.notifyDataSetChanged();
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
        Iterator<BaseListItem> liit = listItems.iterator();
        while (liit.hasNext()){
            BaseListItem listItem = liit.next();
            if (taskId == listItem.taskId && type.isInstance(listItem)){
                liit.remove();
                listViewAdapter.notifyDataSetChanged();
                break;
            }
        }
        
    }
    
    @SuppressLint("SimpleDateFormat")
    void showDeveloperDetailsInfo(String devDetails)
    {
        final boolean canShare;
        final String devDetails2;
        if (devDetails == null || devDetails.length() == 0){
            devDetails2 = "Brak dodatkowych informacji";
            canShare = false;
        } else {
            //devDetails2 = devDetails;
            
            StringBuilder sb = new StringBuilder(devDetails.length() + 128);
            sb.append(devDetails);
            sb.append("\n--------------------\nData: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append('\n');
            try{
                final String packageName = this.getPackageName();
                sb.append("Aplikacja: ");
                final PackageInfo packageInfo = this.getPackageManager().getPackageInfo(packageName, 0);
                sb.append(packageInfo.versionName).append(" (").append(packageInfo.versionCode).append(")");
            }catch(NameNotFoundException nnfe){
                // should not happen ;)
            }
            
            sb.append("\nSystem:");
            sb.append("\nOS Version: ").append(System.getProperty("os.version")).append(" (").append(android.os.Build.VERSION.INCREMENTAL).append(")");
            sb.append("\nOS API Level: ").append(android.os.Build.VERSION.SDK_INT);
            sb.append("\nDevice: ").append(android.os.Build.DEVICE);
            sb.append("\nModel (and Product): ").append(android.os.Build.MODEL).append(" (").append(android.os.Build.PRODUCT).append(")");
            
            sb.append("\n\nID urządzenia: ").append(Secure.getString(this.getContentResolver(), Secure.ANDROID_ID));
            
            devDetails2 = sb.toString();
            canShare = true;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.btnDownloadItemDevDetails);
        builder.setMessage(devDetails2);
        builder.setNegativeButton(R.string.lblDevDetailsClose, null);
        if (canShare){
            builder.setPositiveButton(R.string.lblDevDetailsSend, new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.addCategory(Intent.CATEGORY_DEFAULT);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TEXT, devDetails2);
                    startActivity(Intent.createChooser(intent, getResources().getText(R.string.lblDevDetailsSend)));
                }});
        }
        builder.show(); 
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
