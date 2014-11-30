package org.bogus.android;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.URLSpan;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

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

    public static Drawable getGrayscaled(Drawable src) {
        Drawable res = src.mutate();
        res.setColorFilter(Color.GRAY, Mode.SRC_IN);
        return res;
    } 
    
    public static String formatFileSize(final int sizeKB)
        {
        if (sizeKB <= 1024){
            return sizeKB + " kB"; 
        } else {
            final NumberFormat nf = new DecimalFormat("####0.##");
            return nf.format(sizeKB/1024.0) + " MB";
        }
    }
    
    /**
     * Apply SharedPreferences' changes in an editor
     * @param editor
     */
    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public static void applySharedPrefsEditor(Editor editor)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD){
            editor.apply();
        } else {
            editor.commit();
        }
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static <Params, Progress, Result>  void executeAsyncTask(AsyncTask<Params, Progress, Result> task, Params ... params)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB){
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
        } else {
            task.execute(params);
        }
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void savePrefValueWithHistory(SharedPreferences config, Editor editor, String key, String value)
    {
        editor.putString(key, value);
        if (value != null){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB){
                final String key2 = key + ".history";
                final Set<String> history = config.getStringSet(key2, new HashSet<String>());
                history.add(value);
                final String prev = config.getString(key, null);
                if (prev != null){
                    history.add(prev);
                }
                editor.putStringSet(key2, history);
            }
        }
    }
    
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static boolean isOnWiFi(ConnectivityManager cm)
    {
        final NetworkInfo wifiInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        boolean hasWiFi = wifiInfo != null && wifiInfo.isConnected();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN){
            if (hasWiFi && cm.isActiveNetworkMetered()){
                hasWiFi = false;
            }
        }
        return hasWiFi;
        
    }

    private final static Pattern URL_TEXT_PATTERN = Pattern.compile("\\(([^\\)]+)=%([0-9])\\)", Pattern.CASE_INSENSITIVE);
    
    public static CharSequence insertUrls(CharSequence baseText, String[] urls)
    {
        final Matcher matcher = URL_TEXT_PATTERN.matcher(baseText);

        int start = 0;
        if (matcher.find()) {
            final SpannableStringBuilder newText = new SpannableStringBuilder();
            do {
                int end = matcher.start();
                if (end > start){
                    newText.append(baseText.subSequence(start, end));
                }
                
                int len0 = newText.length();
                newText.append(matcher.group(1));
                int len1 = newText.length();
                int idx = Integer.parseInt(matcher.group(2));
                URLSpan url = new URLSpan(urls[idx]);
                newText.setSpan(url, len0, len1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                
                start = matcher.end();
            }while(matcher.find());
            if (baseText.length() > start){
                newText.append(baseText.subSequence(start, baseText.length()));
            }
            
            return newText;
        }
        return baseText;
    }
    
    public static void insertUrls(TextView textView, CharSequence baseText, String[] urls)
    {
        CharSequence parsedText = insertUrls(baseText, urls);
        if (parsedText != baseText){
            final MovementMethod mm = textView.getMovementMethod();
            if (!(mm instanceof LinkMovementMethod)) {
                textView.setMovementMethod(LinkMovementMethod.getInstance());
            }
            textView.setText(parsedText);
        }
    }

}
