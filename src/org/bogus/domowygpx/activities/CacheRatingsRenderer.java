package org.bogus.domowygpx.activities;

import org.bogus.domowygpx.activities.ChooseCacheRatingsDialog.OnRatingsChosenListener;
import org.bogus.geocaching.egpx.R;

import android.app.Activity;
import android.content.res.Resources;
import android.view.View;
import android.widget.TextView;

/**
 * Responsible for updating view to reflect config state
 * @author Boguś
 *
 */
public class CacheRatingsRenderer
{
    
    final Activity context;
    final CacheRatingsConfig cacheRatingsConfig;
    final CacheRecommendationsConfig cacheRecommendationsConfig;
    final TextView textView;
    
    ChooseCacheRatingsDialog dialog;
    
    /**
     * 
     * @param ctx Parent context
     * @param ratings Ratings config
     * @param rcmds Recommendations config
     * @param tv  View
     */
    public CacheRatingsRenderer(Activity ctx, CacheRatingsConfig ratings, 
        CacheRecommendationsConfig rcmds, TextView tv)
    {
        this.context = ctx;
        this.cacheRatingsConfig = ratings;
        this.cacheRecommendationsConfig = rcmds;
        this.textView = tv;
        
        tv.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View v)
            {
                if (dialog == null){
                    dialog = new ChooseCacheRatingsDialog(context);
                    dialog.setOnRatingsChosenListener(new OnRatingsChosenListener()
                    {
                        
                        @Override
                        public void cacheRatings(CacheRatingsConfig ratings, CacheRecommendationsConfig rcmds)
                        {
                            applyToTextView();
                        }
                    });
                }
                dialog.display(cacheRatingsConfig, cacheRecommendationsConfig);
            }
        });        
    }
    
    /**
     * Apply config state to the view. Call this method manually after config changes.
     */
    public void applyToTextView()
    {
        if (cacheRatingsConfig.isAll() && cacheRecommendationsConfig.isAll()){
            textView.setText(R.string.cacheRatingsAll);
            textView.setTextAppearance(context, R.style.TextAppearance_Large);
        } else {
            Resources res = context.getResources();
            StringBuilder sb = new StringBuilder();
            if (!cacheRatingsConfig.isAll()){
                final int val = cacheRatingsConfig.getMinRating();
                final int resId;
                switch(val){
                    case 3: resId = R.string.cacheRatings3; break;
                    case 4: resId = R.string.cacheRatings4; break;
                    case 5: resId = R.string.cacheRatings5; break;
                    default: resId = -1;
                }
                if (resId > 0){
                    String str = res.getString(resId);
                    sb.append(str);
                } else {
                    sb.append(val);
                }
                if (cacheRatingsConfig.isIncludeUnrated()) {
                    String str2 = res.getString(R.string.cacheRatingsX);
                    sb.append(' ').append(str2);
                }
            }
            if (!cacheRecommendationsConfig.isAll()){
                if (sb.length() > 0){
                    sb.append('\n');
                }
                final int resId = cacheRecommendationsConfig.isPercent() 
                        ? R.plurals.cacheRcmdsTextPercent 
                        : R.plurals.cacheRcmdsText; 
                final int val = cacheRecommendationsConfig.getValue();
                String str = res.getQuantityString(resId, val, val);
                sb.append(str);
            }
            textView.setText(sb.toString());
            textView.setTextAppearance(context, R.style.TextAppearance_Medium);
        }
    }
}
