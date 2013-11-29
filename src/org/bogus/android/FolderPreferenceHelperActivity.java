package org.bogus.android;


import android.content.Intent;

/**
 * Helper class to fire intents and get results back
 * @author Boguś
 *
 */
public interface FolderPreferenceHelperActivity {
    
    /**
     * Registers activity result listener
     * @param fphal
     */
    
    void register(FolderPreferenceHelperActivityListener fphal);
    /**
     * Invokes activity for result
     * @param intent
     * @param requestCode
     */
    void startActivityForResult(Intent intent, int requestCode);
    
    void unregister(FolderPreferenceHelperActivityListener fphal);

    public interface FolderPreferenceHelperActivityListener {
        /**
         * Receives activity's result 
         * @param requestCode
         * @param resultCode
         * @param data
         * @return true, if intent has been handled, false otherwise
         */
        boolean onActivityResult(int requestCode, int resultCode, Intent data);
    }
}