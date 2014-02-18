package locus.api.android;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import locus.api.android.utils.LocusConst;
import locus.api.android.utils.LocusUtils;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

/**
 * Changes against @version 4e92fabf7cf9 
 */
public class ActionFiles {

	/**
	 * Generic call to system for applications that can import your file.
	 * @param context
	 * @param file
	 * @return
	 */
	public static boolean importFileSystem(Context context, File file) {
		if (file == null || !file.exists()) {
			return false;
		}
		
    	Intent sendIntent = new Intent(Intent.ACTION_VIEW);
    	Uri uri = Uri.fromFile(file);
    	sendIntent.setDataAndType(uri, getMimeType(file));
    	context.startActivity(sendIntent);
    	return true;
	}

	/**
	 * Import GPX/KML files directly into Locus application. 
	 * Return false if file don't exist or Locus is not installed
	 * @param context
	 * @param file
	 */
	public static void importFileLocus(Context context, File file) {
		importFileLocus(context, file, true, 0);
	}
	
	/**
	 * Import GPX/KML files directly into Locus application. 
	 * Return false if file don't exist or Locus is not installed
	 * @param context
	 * @param file
     * @param callImport
     * @param flags Intent flags
	 */
	public static void importFileLocus(Context context, File file, boolean callImport, int flags) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(flags);
        Uri uri = Uri.fromFile(file);
        intent.setDataAndType(uri, getMimeType(file));

        final PackageManager pm = context.getPackageManager();
        final List<ResolveInfo> activities = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        final List<String> locusPackageNames = Arrays.asList(LocusUtils.LOCUS_PACKAGE_NAMES);
		
		// get the most preffered one
		ResolveInfo resultActivity = null;
	outerLoop:
		for (String pkg : locusPackageNames){
		    for (ResolveInfo activity : activities){
	            if (pkg.equals(activity.activityInfo.packageName)){
	                resultActivity = activity;
	                break outerLoop;
	            }
	        }
		}
		if (resultActivity == null){
		    throw new IllegalStateException("Can not find suitable Locus activity to perform import");
		}
		
		String pkg = resultActivity.activityInfo.packageName;
        String clazz = resultActivity.activityInfo.name;
        if (clazz.charAt(0) == '.'){
            clazz = pkg + clazz;
        }
		
    	intent.setClassName(pkg, clazz);
    	intent.putExtra(LocusConst.INTENT_EXTRA_CALL_IMPORT, callImport);
    	context.startActivity(intent);
	}
	
	private static String getMimeType(File file) {
		String name = file.getName();
		int index = name.lastIndexOf(".");
		if (index == -1) {
			return "*/*";
		}
		return "application/" + name.substring(index + 1);
	}
}
