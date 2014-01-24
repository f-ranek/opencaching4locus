package org.bogus.domowygpx.activities;

import org.bogus.domowygpx.activities.ChooseCacheDifficultiesDialog.OnDifficultiesChosenListener;
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
public class CacheDifficultiesRenderer
{
    final Activity context;
    final RangeConfig taskConfig, terrainConfig;
    final TextView textView;
    
    ChooseCacheDifficultiesDialog dialog;
    
    /**
     * 
     * @param ctx Parent context
     * @param cfg Items config
     * @param tv  View
     */
    public CacheDifficultiesRenderer(Activity ctx, RangeConfig taskConfig, RangeConfig terrainConfig, TextView tv)
    {
        this.context = ctx;
        this.taskConfig = taskConfig;
        this.terrainConfig = terrainConfig;
        this.textView = tv;
        
        
        tv.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(final View v)
            {
                if (dialog == null){
                    dialog = new ChooseCacheDifficultiesDialog(context);
                    dialog.setOnDifficultiesChosenListener(new OnDifficultiesChosenListener(){
                        @Override
                        public void cacheDifficulties(RangeConfig taskConfig, RangeConfig terrainConfig)
                        {
                            applyToTextView();
                        }});
                }
                dialog.display(CacheDifficultiesRenderer.this.taskConfig, 
                    CacheDifficultiesRenderer.this.terrainConfig);
            }
        });        
    }
    
    /**
     * Apply config state to the view. Call this method manually after config changes.
     */
    public void applyToTextView()
    {
        if (taskConfig.isAllSet() && terrainConfig.isAllSet()){
            textView.setText(R.string.difficultiesAll);
        } else {
            Resources res = context.getResources();
            StringBuilder sb = new StringBuilder();
            if (!taskConfig.isAllSet()){
                int taskMin = taskConfig.getMin();
                int taskMax = taskConfig.getMax();
                if (taskMin == taskMax){
                    String str = res.getString(R.string.difficultiesTaskItem, taskMin);
                    sb.append(str);
                } else {
                    String str = res.getString(R.string.difficultiesTaskRange, taskMin, taskMax);
                    sb.append(str);
                }
            }
            if (!terrainConfig.isAllSet()){
                if (sb.length() > 0){
                    sb.append('\n');
                }
                int terrainMin = terrainConfig.getMin();
                int terrainMax = terrainConfig.getMax();
                if (terrainMin == terrainMax){
                    String str = res.getString(R.string.difficultiesTerrainItem, terrainMin);
                    sb.append(str);
                } else {
                    String str = res.getString(R.string.difficultiesTerrainRange, terrainMin, terrainMax);
                    sb.append(str);
                }
            }
            textView.setText(sb.toString());
        }
    }
}
