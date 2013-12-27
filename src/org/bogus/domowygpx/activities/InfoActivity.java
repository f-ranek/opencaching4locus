package org.bogus.domowygpx.activities;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bogus.domowygpx.application.Application;
import org.bogus.geocaching.egpx.BuildInfo;
import org.bogus.geocaching.egpx.R;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.URLSpan;
import android.view.View;
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
        }
        {
            final TextView helpInfo = (TextView) findViewById(R.id.helpInfo);
            CharSequence helpInfoText = getResources().getText(R.string.infoHelp);
            final Pattern p = Pattern.compile("\\(([^\\)]+)=%([0-9])\\)", Pattern.CASE_INSENSITIVE);
            final String[] urls = new String[]{"https://github.com/f-ranek/opencaching4locus/wiki", 
                    "http://forum.opencaching.pl/viewtopic.php?f=6&t=7793"};
            
            final Matcher matcher = p.matcher(helpInfoText);
    
            int start = 0;
            if (matcher.find()) {
                final SpannableStringBuilder text = new SpannableStringBuilder();
                do {
                    int end = matcher.start();
                    if (end > start){
                        text.append(helpInfoText.subSequence(start, end));
                    }
                    
                    int len0 = text.length();
                    text.append(matcher.group(1));
                    int len1 = text.length();
                    int idx = Integer.parseInt(matcher.group(2));
                    URLSpan url = new URLSpan(urls[idx]);
                    text.setSpan(url, len0, len1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    
                    start = matcher.end();
                }while(matcher.find());
                if (helpInfoText.length() > start){
                    text.append(helpInfoText.subSequence(start, helpInfoText.length()));
                }
                
                final MovementMethod mm = helpInfo.getMovementMethod();
                if ((mm == null) || !(mm instanceof LinkMovementMethod)) {
                    helpInfo.setMovementMethod(LinkMovementMethod.getInstance());
                }
                helpInfoText = text;
            }
            helpInfo.setText(helpInfoText);
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
                    
                    TextView info = (TextView)findViewById(R.id.developerFileInfo); 
                    info.setVisibility(View.VISIBLE);
                    info.setText(getResources().getString(R.string.infoDeveloperInfoFile, sc.extractFileName(file)));
                    
                    sc.sendEmail(file);
                }
            }
        };
        task.execute();
    }
    
    
}
