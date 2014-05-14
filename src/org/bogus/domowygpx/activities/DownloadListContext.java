package org.bogus.domowygpx.activities;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bogus.android.AndroidUtils;
import org.bogus.domowygpx.services.FilesDownloaderApi;
import org.bogus.domowygpx.services.FilesDownloaderService.FilesDownloadTask;
import org.bogus.domowygpx.services.GpxDownloaderApi;
import org.bogus.domowygpx.services.GpxDownloaderListener;
import org.bogus.domowygpx.services.GpxDownloaderService.GpxTask;
import org.bogus.domowygpx.services.GpxDownloaderService.GpxTaskEvent;
import org.bogus.domowygpx.services.downloader.FileData;
import org.bogus.geocaching.egpx.BuildConfig;
import org.bogus.geocaching.egpx.R;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

public abstract class DownloadListContext
{
    private final static String LOG_TAG = "DownloadListContext";
    Context context;
    GpxDownloaderApi gpxDownloader;
    FilesDownloaderApi filesDownloader;
    Handler handler;
    
    abstract void notifyDataSetChanged();
    
    class ListItemViewHolder {
        
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
            // visual view editor can not handle that
            textViewMainInfo.setTextAppearance(context, R.style.TextAppearance_Large);
        }
    }

    abstract class BaseListItem {
        
        String formatDate(final long date)
        {
            final long now = System.currentTimeMillis();
            final long diffHours = (now-date) / (1000L*60L*60L);
            StringBuilder sb = new StringBuilder(24);
            final Date date2 = new Date(date);
            
            if (diffHours > 12 || date > now){
                final DateFormat df2 = android.text.format.DateFormat.getMediumDateFormat(context);
                sb.append(df2.format(date2));
                sb.append('\n');
            }
            final DateFormat df1 = android.text.format.DateFormat.getTimeFormat(context);
            sb.append(df1.format(date2));
            return sb.toString();
        }

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
                holder.textViewMainInfo.setTextAppearance(context, R.style.TextAppearance_Large_Error);
            } else
            if (isDone){
                holder.textViewMainInfo.setTextAppearance(context, R.style.TextAppearance_Large_Ok);
            } else {
                holder.textViewMainInfo.setTextAppearance(context, R.style.TextAppearance_Large);
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

        /*public void close()
        {
            
        }*/
        
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
                    AndroidUtils.setViewVisible(true, view);
                    return ;
                }
            }

            AndroidUtils.setViewVisible(false, view);
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
                notifyDataSetChanged();
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
                    holder.btnDownloadItemCancel.setEnabled(!cancelling);
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
            }
            return super.onContextMenuItemSelected(item);
        }
        
        boolean onGpxEvent(GpxTask task, GpxTaskEvent event)
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
                            if (GpxListItem.this.onGpxEvent(task, event)){
                                notifyDataSetChanged();
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
                        boolean updated = false;
                        if (gpxDownloader != null){
                            updated = gpxDownloader.updateCurrentCacheStatus(taskId, statusUpdaterListener);
                        }
                        if (!updated || statusUpdater == null){
                            statusUpdater = null;
                        } else {
                            // TODO: keep only ONE queue with periodic refresh requests, and perform them
                            // only when last list update was before those 1000 ms
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
            if (task.stateCode == GpxTask.STATE_DONE){
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
                msg.append(context.getResources().getString(R.string.downloader_cancelling));
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
                msg.append(context.getResources().getQuantityString(
                    R.plurals.cache, task.totalCacheCount, task.totalCacheCount));
            }
            if (totalSize > 0){
                msg.append(", ");
                msg.append(AndroidUtils.formatFileSize(totalSize));
            }
            details = msg.toString();
        }
    }
    
    class FileListItem extends BaseListItem {

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
                notifyDataSetChanged();
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
            boolean showStop = false;
            boolean enableStop = false;
            boolean showPlay = false;
            boolean showCancel = false;
            boolean enableCancel = true;
            switch(task.state){
                case FilesDownloadTask.STATE_RUNNING:
                    showCancel = showStop = true;
                    enableStop = true;
                    break;
                case FilesDownloadTask.STATE_FINISHED:
                    break;
                case FilesDownloadTask.STATE_CANCELLING:
                    showCancel = true;
                    enableCancel = false;
                    break;
                case FilesDownloadTask.STATE_PAUSING:
                    showStop = showCancel = true;
                    break;
                case FilesDownloadTask.STATE_STOPPED:
                    showPlay = true;
                    break;
                default:
            }
            
            AndroidUtils.setViewVisible(showStop, holder.btnDownloadItemStop);
            AndroidUtils.setViewVisible(showPlay, holder.btnDownloadItemPlay);
            AndroidUtils.setViewVisible(showCancel, holder.btnDownloadItemCancel);

            holder.btnDownloadItemStop.setOnClickListener(onStopListener);
            holder.btnDownloadItemPlay.setOnClickListener(onPlayListener);
            holder.btnDownloadItemCancel.setOnClickListener(onCancelListener);

            holder.btnDownloadItemStop.setEnabled(enableStop);
            holder.btnDownloadItemCancel.setEnabled(enableCancel);
            
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
            }
            return super.onContextMenuItemSelected(item);
        }

        private void generateMainInfo()
        {
            int msgId = -1;
            switch(task.state){
                case FilesDownloadTask.STATE_CANCELLING:
                    msgId = R.string.downloader_cancelling2;
                    break;
                case FilesDownloadTask.STATE_PAUSING:
                    msgId = R.string.downloader_stopping;
                    break;
                case FilesDownloadTask.STATE_RUNNING:
                    msgId = R.string.downloader_downloading;
                    break;
                case FilesDownloadTask.STATE_FINISHED:
                    msgId = R.string.downloader_download_finished;
                    progressCurrent = progressMax = -1;
                    isError = task.isFailed();
                    isDone = !isError;
                    break;
                case FilesDownloadTask.STATE_STOPPED:
                    msgId = R.string.downloader_download_stopped;
                    progressCurrent = progressMax = -1;
                    break;
            }
            if (msgId > 0){
                message = context.getResources().getString(msgId);
            }
        }
        
        private void generateDetails()
        {
            StringBuilder msg = new StringBuilder(128);
            msg.append(context.getResources().getQuantityString(
                task.finishedFiles == task.totalFiles ? R.plurals.filesCount : R.plurals.filesCountOverTotal, 
                task.finishedFiles, task.finishedFiles, task.totalFiles));
            if (task.totalDownloadSize > 1024){
                msg.append(", ");
                msg.append(AndroidUtils.formatFileSize((int)(task.totalDownloadSize/1024L)));
            }
            int errors = task.permanentErrorFiles + task.transientErrorFiles;
            if (errors > 0){
                msg.append(", ");
                msg.append(context.getResources().getQuantityString(
                    R.plurals.error, errors, errors));
            }
            int skipped = task.skippedFiles;
            if (skipped > 0){
                msg.append(", ");
                msg.append(context.getResources().getQuantityString(
                    R.plurals.skipped, skipped, skipped));
            }
            if (msg.length() > 0){
                String newDetails = msg.toString();
                if (BuildConfig.DEBUG){
                    if (details == null || !newDetails.equals(details)){
                        Log.d(LOG_TAG, "Item details=" + newDetails);
                    }
                }
                details = newDetails;
            } else {
                if (BuildConfig.DEBUG){
                    if (details != null){
                        Log.d(LOG_TAG, "Item details=" + details);
                    }
                }
                details = null;
            }
        }
        
        void onFileProgress(FilesDownloadTask task, FileData fileData)
        {
            this.task = task;
            generateDetails();
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
    
    
    class OperationsListAdapter extends BaseAdapter
    {
        private LayoutInflater layoutInflater = LayoutInflater.from(context);
        final List<BaseListItem> listItems = new ArrayList<BaseListItem>();
        
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
}
