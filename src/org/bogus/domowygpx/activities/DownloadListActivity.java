package org.bogus.domowygpx.activities;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.bogus.domowygpx.services.GpxDownloaderApi;
import org.bogus.domowygpx.services.GpxDownloaderListener;
import org.bogus.domowygpx.services.GpxDownloaderService;
import org.bogus.domowygpx.services.GpxDownloaderService.GpxTask;
import org.bogus.domowygpx.services.GpxDownloaderService.GpxTaskEvent;
import org.bogus.geocaching.egpx.R;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class DownloadListActivity extends Activity implements GpxDownloaderListener, ServiceConnection
{
    private final static String LOG_TAG = "DownloadListActivity";
    
    private GpxDownloaderApi gpxDownloader;
    // private final Handler eventHandler = new Handler();
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
        Log.i(LOG_TAG, "Established connection to " + className.getShortClassName());
        
        gpxDownloader = ((GpxDownloaderService.LocalBinder)service).getService();

        // events may get duplicated here
        listItems.clear();
        gpxDownloader.registerEventListener(DownloadListActivity.this); 
        final List<GpxTask> tasks = gpxDownloader.getTasks(-1, false);
        processTasks(tasks);
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
        // This is called when the connection with the service has been unexpectedly disconnected - 
        // process crashed.
        
        Log.w(LOG_TAG, "Lost connection to " + className.getShortClassName());
        
        if (className.getClassName().equals(GpxDownloaderService.class.getName())){
            gpxDownloader = null;
            bindGpxDownloaderService();
        }
    }
    
    private int stableIdCounter;
    
    static class ListItem {
        int stableId;
        boolean isDownloadTask;
        int taskId;
        String message;
        String details;
        boolean isError;
        boolean isDone;
        int totalSize;
        String createdDate; // TODO
    }
    
    static class ListItemViewHolder {
        TextView textViewMainInfo;
        TextView textViewDetailsInfo;
        TextView textViewDateInfo;
    }

    final List<ListItem> listItems = new ArrayList<ListItem>();
    
    private ListView listViewOperations;
    private BaseAdapter listViewAdapter;

    private ListItem getGpxListItem(int taskId, GpxTask task)
    {
        for (ListItem listItem : listItems){
            if (taskId == listItem.taskId){
                return listItem;
            }
        }
        ListItem result = new ListItem();
        result.taskId = taskId;
        result.stableId = ++stableIdCounter;
        //result.createdDate = /*task == null ? System.currentTimeMillis() :*/ task.createdDate;
        listItems.add(0, result); 
        return result;
    }
    
    
    
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_list);

        listViewOperations = (ListView)findViewById(R.id.listViewOperations);
        listViewAdapter = new BaseAdapter()
        {
            private LayoutInflater layoutInflater = LayoutInflater.from(DownloadListActivity.this);
            
            @Override
            public boolean hasStableIds() {
                return true;
            }
            
            @Override
            public View getView(int position, View convertView, ViewGroup parent)
            {
                ListItemViewHolder holder;
                if (convertView == null) {
                    convertView = layoutInflater.inflate(R.layout.activity_download_list_item, null);
                    holder = new ListItemViewHolder();
                    holder.textViewMainInfo = (TextView) convertView.findViewById(R.id.textViewMainInfo);
                    holder.textViewDetailsInfo = (TextView) convertView.findViewById(R.id.textViewDetailsInfo);
                    holder.textViewDateInfo = (TextView) convertView.findViewById(R.id.textViewDateInfo);
                    convertView.setTag(holder);
                } else {
                    holder = (ListItemViewHolder) convertView.getTag();
                }
         
                final ListItem listItem = getItem(position);
                holder.textViewMainInfo.setText(listItem.message);
                holder.textViewDateInfo.setText(listItem.createdDate);
                if (listItem.isError){
                    holder.textViewMainInfo.setTextColor(getResources().getColor(R.color.colorError));
                } else
                if (listItem.isDone){
                    holder.textViewMainInfo.setTextColor(getResources().getColor(R.color.colorDoneOk));
                } else {
                    // oh, fuck, bad idea, but temporarly works
                    holder.textViewMainInfo.setTextColor(holder.textViewDetailsInfo.getTextColors());
                }
                
                if (listItem.details == null){
                    holder.textViewDetailsInfo.setVisibility(TextView.GONE);
                } else {
                    holder.textViewDetailsInfo.setVisibility(TextView.VISIBLE);
                    holder.textViewDetailsInfo.setText(listItem.details);
                }
         
                return convertView;
            }
            
            @Override
            public long getItemId(int position)
            {
                return getItem(position).stableId;
            }
            
            @Override
            public ListItem getItem(final int position)
            {
                return listItems.get(position);
            }
            
            @Override
            public int getCount()
            {
                return listItems.size();
            }
        };
        listViewOperations.setAdapter(listViewAdapter);
        
        bindGpxDownloaderService();
    }

    protected void bindGpxDownloaderService()
    {
        bindService(new Intent(this, GpxDownloaderService.class), this, Context.BIND_AUTO_CREATE);
    }
    
    static String formatFileSize(final int sizeKB)
    {
        if (sizeKB <= 1024){
            return sizeKB + " KB"; 
        } else {
            final NumberFormat nf = new DecimalFormat("####0.##");
            return nf.format(sizeKB/1024.0) + " MB";
        }
    }
    
    protected boolean onGpxEventInternal(GpxTask task)
    {
        boolean updateView = false;
        final ListItem listItem = getGpxListItem(task.taskId, task);
        if (listItem.createdDate == null){
            final long now = System.currentTimeMillis();
            final long diffHours = (now-task.createdDate) / (1000L*60L*60L);
            StringBuilder sb = new StringBuilder(24);
            final Date date = new Date(task.createdDate);
            
            if (diffHours > 12 || (task.createdDate > now)){
                final DateFormat df2 = android.text.format.DateFormat.getMediumDateFormat(getApplicationContext());
                sb.append(df2.format(date));
                sb.append('\n');
            }
            final DateFormat df1 = android.text.format.DateFormat.getTimeFormat(getApplicationContext());
            sb.append(df1.format(date));
            listItem.createdDate = sb.toString();
            updateView = true;
        }
        if (task.stateCode == GpxTask.STATE_ERROR  || 
                task.stateCode == GpxTask.STATE_CANCELED)
        {
            updateView = true;
            listItem.isError = true;
        }
        if (task.stateCode == GpxTaskEvent.EVENT_TYPE_FINISHED_OK){
            updateView = true;
            listItem.isDone = true;
        }
        if (task.stateDescription != null){
            updateView = true;
            listItem.message = task.stateDescription;
        }
        if (task.currentCacheCode != null){
            updateView = true;
        }
        if (task.totalKB > listItem.totalSize){
            listItem.totalSize = task.totalKB;
            updateView = true;
        }
        
        if (updateView){
            StringBuilder msg = new StringBuilder(64);
            if (task.currentCacheCode != null){
                msg.append(task.currentCacheCode);
                msg.append(", ");
            }
            if (task.totalCacheCount > 0){
                msg.append(task.totalCacheCount);
                if (task.totalCacheCount == 1){
                    msg.append(" kesz"); // XXX Framework for such formatting
                } else 
                if (task.totalCacheCount >= 2 && task.totalCacheCount <= 4){
                    msg.append(" kesze");
                } else {
                    msg.append(" keszy");
                }
            }
            if (listItem.totalSize > 0){
                msg.append(", ");
                msg.append(formatFileSize(listItem.totalSize));
            }
            listItem.details = msg.toString();
        }
        
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

    protected void processTasks(List<GpxTask> tasks)
    {
        boolean updateView = false;
        for (GpxTask task : tasks){
            updateView |= onGpxEventInternal(task);
        }
        if (updateView){
            listViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onTaskRemoved(int taskId)
    {
        Iterator<ListItem> liit = listItems.iterator();
        while (liit.hasNext()){
            ListItem listItem = liit.next();
            if (taskId == listItem.taskId){
                listViewAdapter.notifyDataSetChanged();
                liit.remove();
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
        // Detach our existing connection.
        unbindService(this);
    }
    
    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     *//*
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
        getMenuInflater().inflate(R.menu.download_list, menu);
        return true;
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
    }*/

}
