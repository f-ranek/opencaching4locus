package org.bogus.domowygpx.activities;
/*
import java.io.File;
import menion.android.locus.addon.publiclib.LocusUtils;
*/
import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class LocusImportActivity extends Activity
{
	private static final String LOG_TAG = "LocusImportActivity";
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		Intent intent = this.getIntent();
		
		Bundle bundle = intent.getExtras();
		if (bundle == null){
		    Toast.makeText(this, "Brak danych, " + intent, Toast.LENGTH_LONG).show();
		    finish();
		    return ;
		}
		
		StringBuilder sb = new StringBuilder(128);
		
		sb.append(intent).append("\n\n");
		
		Set<String> keys = bundle.keySet();
		for (String key : keys){
		    Object value = bundle.get(key);
		    sb.append(key).append(": ").append(value).append('\n');
		}
		if (intent.getData() != null){
		    sb.append("intent.data: ").append(intent.getData()).append('\n');
		}
		
		Log.i(LOG_TAG, sb.toString());
		
		AlertDialog.Builder b = new AlertDialog.Builder(this);
		b.setMessage(sb.toString());
		b.setTitle("Przekazane dane");
		b.setNeutralButton("Zamknij", new OnClickListener()
        {
            
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                LocusImportActivity.this.finish();
                
            }
        });
		b.create().show();
	}
}