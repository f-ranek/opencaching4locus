package org.bogus.android;

import org.bogus.geocaching.egpx.R;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;

public class AndroidUtils
{
    public static void hideSoftKeyboard(Window window)
    {
        if (window == null){
            return ;
        }
        final InputMethodManager inputManager = (InputMethodManager)
                window.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        final View currentlyFocused = window.getCurrentFocus();
        if (currentlyFocused != null){
            inputManager.hideSoftInputFromWindow(currentlyFocused.getWindowToken(),
                InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }
    
    public static void hideSoftKeyboard(Activity activity)
    {
        if (activity != null && activity.getWindow() != null){
            hideSoftKeyboard(activity.getWindow());
        }
    }

    public static void hideSoftKeyboard(Dialog dialog)
    {
        if (dialog != null && dialog.getWindow() != null){
            hideSoftKeyboard(dialog.getWindow());
        }
    }

    public static String toString(Object obj)
    {
        if (obj == null){
            return null;
        } else {
            return obj.toString();
        }
    }
    
    @SuppressLint("SimpleDateFormat")
    /*
    public static void showDeveloperDetailsInfo(final Context context, String devDetails)
    {
        final boolean canShare;
        final String devDetails2;
        if (devDetails == null || devDetails.length() == 0){
            devDetails2 = "Brak dodatkowych informacji";
            canShare = false;
        } else {
            //devDetails2 = devDetails;
            
            StringBuilder sb = new StringBuilder(devDetails.length() + 128);
            sb.append(devDetails);
            sb.append("\n--------------------\nData: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append('\n');
            try{
                final String packageName = context.getPackageName();
                sb.append("Aplikacja: ");
                final PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
                sb.append(packageInfo.versionName).append(" (").append(org.bogus.geocaching.egpx.BuildInfo.GIT_VERSION).append(")");
            }catch(NameNotFoundException nnfe){
                // should not happen ;)
            }
            
            sb.append("\nSystem:");
            sb.append("\nOS Version: ").append(System.getProperty("os.version")).append(" (").append(android.os.Build.VERSION.INCREMENTAL).append(")");
            sb.append("\nOS API Level: ").append(android.os.Build.VERSION.SDK_INT);
            sb.append("\nDevice: ").append(android.os.Build.DEVICE);
            sb.append("\nModel (and Product): ").append(android.os.Build.MODEL).append(" (").append(android.os.Build.PRODUCT).append(")");
            
            sb.append("\n\nID urządzenia: ").append(Secure.getString(context.getContentResolver(), Secure.ANDROID_ID));
            
            devDetails2 = sb.toString();
            canShare = true;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.btnDownloadItemDevDetails);
        builder.setMessage(devDetails2);
        builder.setNegativeButton(R.string.lblDevDetailsClose, null);
        if (canShare){
            builder.setPositiveButton(R.string.lblDevDetailsSend, new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.addCategory(Intent.CATEGORY_DEFAULT);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TEXT, devDetails2);
                    context.startActivity(Intent.createChooser(intent, context.getResources().getText(R.string.lblDevDetailsSend)));
                }});
        }
        builder.show(); 
    }
    */
    
    /**
     * Sets the specified image buttonto the given state, while modifying or
     * "graying-out" the icon as well
     * 
     * @param enabled The state of the menu item
     * @param item The image button to modify
     */
    public static void setImageButtonEnabled(boolean enabled, ImageButton item) {
        if (item.isEnabled() == enabled){
            return ;
        }
        Drawable originalImg = (Drawable)item.getTag(R.id.imageButtonOriginalImage);
        if (originalImg == null){
            originalImg = item.getDrawable();
            item.setTag(R.id.imageButtonOriginalImage, originalImg);
        }
        if (enabled){
            item.setImageDrawable(originalImg);
        } else {
            Drawable grayed = (Drawable)item.getTag(R.id.imageButtonGrayedImage);
            if (grayed == null){
                grayed = getGrayscaled(originalImg);
                item.setTag(R.id.imageButtonGrayedImage, grayed);
            }
            item.setImageDrawable(grayed);
        }
        item.setEnabled(enabled);
        //item.setClickable(enabled);
    }

    public static boolean setViewVisible(boolean visible, View view) {
        int vis = view.getVisibility();
        boolean currentlyVisible = vis == View.VISIBLE || vis == View.INVISIBLE;
        if (visible != currentlyVisible){
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
            return true;
        } else {
            return false;
        }
    }

    private static Drawable getGrayscaled(Drawable src) {
        Drawable res = src.mutate();
        res.setColorFilter(Color.GRAY, Mode.SRC_IN);
        return res;
    } 
}
