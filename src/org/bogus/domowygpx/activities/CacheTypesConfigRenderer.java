package org.bogus.domowygpx.activities;

import org.bogus.domowygpx.activities.ChooseCacheTypesDialog.OnTypesChosenListener;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;

public class CacheTypesConfigRenderer
{
    static final int PADDING_DP = 1;
    
    final Activity context;
    final CacheTypesConfig config;
    final TextView textView;
    
    private int iconWidth, iconHeight;
    
    private int paddingPx;

    protected int drawableWidth, drawableHeight;
    
    public CacheTypesConfigRenderer(Activity ctx, CacheTypesConfig cfg, TextView tv)
    {
        this.context = ctx;
        this.config = cfg;
        this.textView = tv;
        drawableWidth = drawableHeight = -1;

        textView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener()
        {
            @Override
            public boolean onPreDraw()
            {
                int prevDrawableWidth = drawableWidth; 
                int prevDrawableHeight = drawableHeight;
                int width = textView.getWidth();
                width -= textView.getPaddingLeft();
                width -= textView.getPaddingRight();
                calculateDrawableSize(width);
                if (prevDrawableWidth != drawableWidth || prevDrawableHeight != drawableHeight){
                    Drawable drawable = generateDrawable();
                    textView.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
                    return false;
                }
                return true;
            }
        });
        
        // assume all icons are equal in size
        final int[][] androidConfig = config.getAndroidConfig();
        Drawable icon = context.getResources().getDrawable(androidConfig[1][0]);
        iconWidth = icon.getIntrinsicWidth();
        iconHeight = icon.getIntrinsicHeight();
        
        paddingPx = (int)(context.getResources().getDisplayMetrics().density * PADDING_DP); 
        
        tv.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View v)
            {
                final ChooseCacheTypesDialog cctd = new ChooseCacheTypesDialog(context);
                cctd.display(config);
                cctd.setOnTypesChosenListener(new OnTypesChosenListener(){

                    @Override
                    public void cacheTypes(CacheTypesConfig cacheTypes)
                    {
                        applyToTextView();
                    }});
            }
        });        
    }
    
    protected void calculateDrawableSize(int maxWidth)
    {
        drawableWidth = drawableHeight = -1;
        if (config.isAllSet()){
            return ;
        }
        int selectedCount = 0;
        for (int i=config.getCount()-1; i>=1; i--){
            if (config.get(i)){
                selectedCount++;
            }
        }
        if (selectedCount == 0){
            return ;
        }
        
        if (maxWidth < iconWidth){
            maxWidth = iconWidth;
        }
        
        int availColumns = (maxWidth + paddingPx) / (iconWidth + paddingPx); 
        if (availColumns > selectedCount){
            drawableWidth = selectedCount * (iconWidth + paddingPx) - paddingPx;
            drawableHeight = iconHeight;
        } else {
            drawableWidth = availColumns * (iconWidth + paddingPx) - paddingPx;
            int rows = (selectedCount + availColumns - 1) / availColumns;
            drawableHeight = rows * (iconHeight + paddingPx) - paddingPx;
        }
    }
    
    protected Drawable generateDrawable()
    {
        if (drawableWidth < 0 || drawableHeight < 0){
            return null;
        }
        
        Bitmap bmp = Bitmap.createBitmap(drawableWidth, drawableHeight, Bitmap.Config.ARGB_8888);
        bmp.setDensity(context.getResources().getDisplayMetrics().densityDpi);
        Canvas canvas = new Canvas(bmp);
        
        int x = 0, y = 0;
        final int[][] androidConfig = config.getAndroidConfig();
        for (int i=1; i<config.getCount(); i++){
            if (!config.get(i)){
                continue;
            }
            int iconResId = androidConfig[i][0];
            BitmapDrawable icon2 = (BitmapDrawable)(context.getResources().getDrawable(iconResId));
            canvas.drawBitmap(icon2.getBitmap(), x, y, null);
            x+=icon2.getIntrinsicWidth() + paddingPx;
            if (x >= drawableWidth){
                x = 0;
                y+=icon2.getIntrinsicHeight() + paddingPx;
            }
        }
        
        BitmapDrawable result = new BitmapDrawable(context.getResources(), bmp);
        return result;
    }
    
    public void applyToTextView()
    {
        if (config.isAllSet()){
            textView.setText(config.getAndroidConfig()[0][1]);
            textView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        } else {
            int width = textView.getWidth();  
            calculateDrawableSize(width);
            Drawable drawable = generateDrawable();
            textView.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
            textView.setText(null);
        }
    }
}
