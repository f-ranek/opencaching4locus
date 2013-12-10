package org.bogus.domowygpx.activities;

import org.bogus.geocaching.egpx.BuildInfo;
import org.bogus.geocaching.egpx.R;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.widget.TextView;

public class InfoActivity extends Activity
{

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);
        
        final PackageInfo packageInfo; 
        try{
            packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            
        }catch(NameNotFoundException nnfe){
            throw new IllegalStateException(nnfe);
        }

        TextView info = (TextView) findViewById(R.id.buildInfoVersion);
        String versionInfoText = getResources().getString(R.string.versionInfo, 
            packageInfo.versionName,
            BuildInfo.TIMESTAMP,
            BuildInfo.GIT_VERSION
        );
        info.setText(versionInfoText);
    }

}
