package org.bogus.domowygpx.activities;
/*
import java.io.File;
import menion.android.locus.addon.publiclib.LocusUtils;
*/
import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

public class LocusImportActivity extends Activity
{
	//private static final String LOG_TAG = "egpx";
	private Double latitude;
	private Double longitude;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		if ((!this.getIntent().hasExtra("latitude")) || (!(this.getIntent().hasExtra("longitude"))))
		{
			this.runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					Toast.makeText(LocusImportActivity.this,
							"This plugin must be called with proper 'latitude' and 'longitude' arguments!",
							Toast.LENGTH_LONG).show();
				}
			});
		}
		this.latitude = this.getIntent().getDoubleExtra("latitude", 0.0);
		this.longitude = this.getIntent().getDoubleExtra("longitude", 0.0);
		
		Toast.makeText(this, "Niezaimplementowane: lat=" + latitude + ", lon=" + longitude, Toast.LENGTH_LONG).show();
		
		/*
		LocusImportActivity.this.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				final File outputFile = LocusImportActivity.this.getFileStreamPath("results-temp.gpx");
				Runnable onSuccess = new Runnable()
				{
					@Override
					public void run()
					{
						LocusImportActivity.this.runOnUiThread(new Runnable()
						{
							@Override
							public void run()
							{
								LocusUtils.importFileLocus(LocusImportActivity.this, outputFile);
								LocusImportActivity.this.finish();
							}
						});
					}
				};
				Runnable onFailure = new Runnable()
				{
					@Override
					public void run()
					{
						LocusImportActivity.this.finish();
					}
				};
				new DownloadTask(LocusImportActivity.this, outputFile, LocusImportActivity.this.latitude
						.toString(), LocusImportActivity.this.longitude.toString(), "30", MainActivity
						.getPref(LocusImportActivity.this, "user", ""), MainActivity.getPref(
						LocusImportActivity.this, "when_found", "mark"), "http://opencaching.pl/okapi/",
						onSuccess, onFailure).execute((Void) null);
			}
		});
		*/
	}
}