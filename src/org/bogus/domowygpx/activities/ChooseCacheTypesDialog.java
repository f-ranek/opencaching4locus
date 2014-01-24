package org.bogus.domowygpx.activities;

import org.bogus.geocaching.egpx.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;

/**
 * A pick-up dialog for cache types
 * @author Boguś
 *
 */
public class ChooseCacheTypesDialog
{
    final Activity parent;
    AlertDialog dialog;
    CacheTypesListAdapter listViewAdapter;
    OnTypesChosenListener onTypesChosenListener;

    /**
     * An interface to be notified when user closes the dialog
     * @author Boguś
     *
     */
    public interface OnTypesChosenListener {
        /**
         * Dialog has been closed, and new config is to be applied
         * @param config
         */
        void cacheTypes(CacheTypesConfig config);
    }
    
    static class CacheTypeItem {
        final int position;
        Drawable normalIcon;
        Drawable disabledIcon;
        String label;
        CompoundButton.OnCheckedChangeListener onCheckedListener;
        
        CacheTypeItem(int position)
        {
            this.position = position;
        }
    }
    
    CacheTypesConfig cacheTypesConfig;
    CacheTypeItem cacheTypeItems[];
    
    class CacheTypesListAdapter extends BaseAdapter
    {
        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            CheckBox cb;
            if (convertView == null){
                Context ctx = ChooseCacheTypesDialog.this.parent;
                cb = new CheckBox(ctx);
                cb.setTextAppearance(ctx, android.R.style.TextAppearance_Large);
                convertView = cb;
            } else {
                cb = (CheckBox)convertView;
            }
            final CacheTypeItem cit = getItem(position);
            applyToView(cit, cb);
            return cb;
        }
        
        @Override
        public long getItemId(int position)
        {
            return position;
        }
        
        @Override
        public CacheTypeItem getItem(final int position)
        {
            return cacheTypeItems[position];
        }
        
        @Override
        public int getCount()
        {
            return cacheTypeItems.length;
        }
        
        @Override
        public boolean hasStableIds()
        {
            return true;
        }
    }    
    
    void applyToView(final CacheTypeItem cacheTypeItem, CheckBox checkbox)
    {
        checkbox.setOnCheckedChangeListener(null);
        checkbox.setChecked(cacheTypesConfig.get(cacheTypeItem.position));
        checkbox.setOnCheckedChangeListener(cacheTypeItem.onCheckedListener);
        checkbox.setText(cacheTypeItem.label);
        
        Drawable icon;
        if (cacheTypeItem.position == 0){
            // all
            icon = cacheTypeItem.normalIcon;
            checkbox.setEnabled(true);
        } else {
            boolean enabled = !cacheTypesConfig.isAllSet();
            icon = enabled ? cacheTypeItem.normalIcon : cacheTypeItem.disabledIcon;
            checkbox.setEnabled(enabled);
        }
        
        //Drawable[] drawabes = checkbox.getCompoundDrawables();
        //if (drawabes == null || drawabes.length == 0 || drawabes[0] != icon){
            // this causes relayout, and almost infinite loop of layout measure followed by layout request
            // avoid by caching view on our side
            checkbox.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        //}
    }
    
    public ChooseCacheTypesDialog(Activity parent)
    {
        this.parent = parent;
    }
    
    /**
     * Display dialog using given config. Make sure to {@link #setOnTypesChosenListener(OnTypesChosenListener)}
     * @param cacheTypes
     */
    public void display(CacheTypesConfig cacheTypes)
    {
        this.cacheTypesConfig = cacheTypes;
        if (dialog != null){
            dialog.setOnDismissListener(null);
            dialog.dismiss();
            dialog = null;
        }
        prepareDialog();
    }
    
    /**
     * Parse the config and display dialog. Make sure to {@link #setOnTypesChosenListener(OnTypesChosenListener)}
     * @param cacheTypes
     */
    public void display(String cacheTypes)
    {
        CacheTypesConfig cacheTypesConfig = new CacheTypesConfig(); 
        cacheTypesConfig.parseFromConfigString(cacheTypes);
        display(cacheTypesConfig);
    }
    
    protected Drawable makeDisabled(Drawable icon0, float contrast, float brightness, int threshold)
    {
        final BitmapDrawable icon = (BitmapDrawable)(icon0.mutate());
        int width = icon.getIntrinsicWidth();
        int height = icon.getIntrinsicHeight();
        Paint paint = new Paint();

        Bitmap bmp0 = icon.getBitmap();
        
        Bitmap bmp1 = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        bmp1.setDensity(bmp0.getDensity());
        Canvas canvas1 = new Canvas(bmp1);
        paint.setColorFilter(new PorterDuffColorFilter(Color.GRAY, Mode.SRC_IN));
        canvas1.drawBitmap(bmp0, 0, 0, paint);
        
        BitmapDrawable icon2;
        
        for (int x=0; x<width; x++){
            for (int y=0; y<height; y++){
                int color = bmp1.getPixel(x, y);
                int a = Color.alpha(color);
                int c = (Color.red(color) + Color.green(color) + Color.blue(color))/3;

                c = (int)(c * contrast + brightness);
                c = Math.min(255, Math.max(c, 0));
                a = (int)(a * contrast + brightness);
                a = Math.min(255, Math.max(a, 0));
                
                int c2 = (a * c / 255 + 255-a);
                if (c2 >= threshold){
                    a = 0;
                } else {
                    c = (int)(c*0.8);
                }
                color = Color.argb(a, c, c, c);
                bmp1.setPixel(x, y, color);
            }
        }
        icon2 = new BitmapDrawable(null, bmp1);
        icon2.setTargetDensity(bmp0.getDensity());
        
        return icon2;
    }
    
    protected void prepareDialog()
    {
        final Resources resources = parent.getResources();
        final int[][] androidConfig = cacheTypesConfig.getAndroidConfig();
        cacheTypeItems = new CacheTypeItem[cacheTypesConfig.getCount()];
        {
            CacheTypeItem cti = new CacheTypeItem(0);
            cti.onCheckedListener = new CompoundButton.OnCheckedChangeListener()
            {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
                {
                    cacheTypesConfig.set(0, isChecked);
                    listViewAdapter.notifyDataSetChanged();
                    if (isChecked){
                        dialog.dismiss();
                    }
                }    
            };
            if (androidConfig[0][0] > 0){
                cti.label = " " + resources.getString(androidConfig[0][1]);
                cti.normalIcon = resources.getDrawable(androidConfig[0][0]);
            } else {
                cti.label = resources.getString(androidConfig[0][1]);
            }
            cacheTypeItems[0] = cti;
        }
        
        float contrast = 2f;
        float brightness = -400f;
        int threshold = 240;
        
        for (int i=1; i<cacheTypeItems.length; i++){
            CacheTypeItem cti = new CacheTypeItem(i);
            final int fi = i;
            cti.onCheckedListener = new CompoundButton.OnCheckedChangeListener()
            {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
                {
                    cacheTypesConfig.set(fi, isChecked);
                }    
            };
            
            cti.label = " " + resources.getString(androidConfig[i][1]);
            cti.normalIcon = resources.getDrawable(androidConfig[i][0]);
            cti.disabledIcon = makeDisabled(resources.getDrawable(androidConfig[i][0]), contrast, brightness, threshold);
            cacheTypeItems[i] = cti;
        }

        listViewAdapter = new CacheTypesListAdapter();
        
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(parent);
        dialogBuilder.setTitle(R.string.chooseCacheTypes);
        dialogBuilder.setAdapter(listViewAdapter, null);
        
        dialog = dialogBuilder.create();
        dialog.show();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener()
        {
            @Override
            public void onDismiss(DialogInterface dlg)
            {
                dialog = null;
                if (!cacheTypesConfig.isAnySet()){
                    cacheTypesConfig.set(0, true);
                }
                if (onTypesChosenListener != null){
                    onTypesChosenListener.cacheTypes(cacheTypesConfig);
                }
            }
        });
    }

    public OnTypesChosenListener getOnTypesChosenListener()
    {
        return onTypesChosenListener;
    }

    public void setOnTypesChosenListener(OnTypesChosenListener onTypesChosenListener)
    {
        this.onTypesChosenListener = onTypesChosenListener;
    }
}
