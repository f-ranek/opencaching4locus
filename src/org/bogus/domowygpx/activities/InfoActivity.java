package org.bogus.domowygpx.activities;

import java.io.File;

import org.bogus.android.AndroidUtils;
import org.bogus.domowygpx.application.Application;
import org.bogus.domowygpx.application.StateCollector;
import org.bogus.geocaching.egpx.BuildInfo;
import org.bogus.geocaching.egpx.R;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.TextView;
import android.widget.Toast;

public class InfoActivity extends Activity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Application.getInstance(this).showErrorDumpInfo(this);
        
        setContentView(R.layout.activity_info);
        {
            final PackageInfo packageInfo; 
            try{
                packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                
            }catch(NameNotFoundException nnfe){
                throw new IllegalStateException(nnfe);
            }
    
            TextView buildInfo = (TextView) findViewById(R.id.buildInfoVersion);
            String versionInfoText = getResources().getString(R.string.versionInfo, 
                packageInfo.versionName,
                BuildInfo.TIMESTAMP,
                BuildInfo.GIT_VERSION
            );
            buildInfo.setText(versionInfoText);
            buildInfo.setOnLongClickListener(new OnLongClickListener()
            {
                @Override
                public boolean onLongClick(View v)
                {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.youtube.com/watch?v=3N4llxYLOmA"));
                    startActivity(intent);
                    return false;
                }
            });
        }
        {
            final TextView helpInfo = (TextView) findViewById(R.id.helpInfo);
            CharSequence helpInfoText = getResources().getText(R.string.infoHelp);
            final String[] urls = new String[]{"https://github.com/f-ranek/opencaching4locus/wiki", 
                    "http://forum.opencaching.pl/viewtopic.php?f=6&t=7793"};

            AndroidUtils.insertUrls(helpInfo, helpInfoText, urls);
        }
        findViewById(R.id.developerFileInfo).setVisibility(View.GONE);
        findViewById(R.id.btnGenerateDevInfo).setOnClickListener(new View.OnClickListener()
        {
            
            @Override
            public void onClick(View v)
            {
                dumpDataUI();
            }
        });
        
        // XXX add info about current okapi installation
    }

    
    void dumpDataUI()
    {
        AsyncTask<Void, Void, File> task = new AsyncTask<Void, Void, File>()
        {
            ProgressDialog progress;
            @Override
            protected void onPreExecute()
            {
                findViewById(R.id.developerFileInfo).setVisibility(View.GONE); 
                progress = ProgressDialog.show(InfoActivity.this, null,
                    getText(R.string.infoWorkInProgress), 
                    true, false);
            }
            
            @Override
            protected File doInBackground(Void... params)
            {
                StateCollector stateCollector = new StateCollector(InfoActivity.this);
                return stateCollector.dumpDataOnline();
            }
    
            @Override
            protected void onPostExecute(File file)
            {
                progress.dismiss();
                if (file == null){
                    Toast.makeText(InfoActivity.this, R.string.infoDeveloperInfoError, Toast.LENGTH_LONG).show();
                } else {
                    final StateCollector sc = new StateCollector(InfoActivity.this);
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file));
                    intent.setType("application/octet-stream");
                    sendBroadcast(intent);
                    
                    
                    TextView info = (TextView)findViewById(R.id.developerFileInfo); 
                    info.setVisibility(View.VISIBLE);
                    info.setText(getResources().getString(R.string.infoDeveloperInfoFile, sc.extractFileName(file)));
                    
                    sc.sendEmail(file);
                }
            }
        };
        AndroidUtils.executeAsyncTask(task);
    }
    
    
}
