package org.bogus.domowygpx.application;

import java.io.IOException;

import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.SharedPreferencesBackupHelper;
import android.os.ParcelFileDescriptor;
import android.util.Log;


public class BackupAgent extends android.app.backup.BackupAgentHelper
{
    private final static String LOG_TAG = "BackupAgent";
    @Override
    public void onCreate() {
        super.onCreate();
        final SharedPreferencesBackupHelper helper = new SharedPreferencesBackupHelper(this, "egpx");
        addHelper("preferences_egpx", helper);
    }

    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState)
        throws IOException
    {
        Log.i(LOG_TAG, "onBackup");
        super.onBackup(oldState, data, newState);
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState) throws IOException
    {
        Log.i(LOG_TAG, "onRestore");
        super.onRestore(data, appVersionCode, newState);
    }

}
