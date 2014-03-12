package org.bogus.domowygpx.activities;

import org.bogus.android.RangeBar;
import org.bogus.geocaching.egpx.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;


/**
 * A pick-up dialog for cache difficulties (task and terrain)
 * @author Boguś
 *
 */
public class ChooseCacheDifficultiesDialog
{
    final Activity parent;
    AlertDialog dialog;
    OnDifficultiesChosenListener onDifficultiesChosenListener;
    RangeConfig taskConfig, terrainConfig;

    /**
     * An interface to be notified when user closes the dialog 
     * @author Boguś
     *
     */
    public interface OnDifficultiesChosenListener {
        /**
         * Dialog has been closed, and new config is to be applied
         */
        void cacheDifficulties(RangeConfig taskConfig, RangeConfig terrainConfig);
    }

    public ChooseCacheDifficultiesDialog(Activity parent)
    {
        this.parent = parent;
    }
    
    /**
     * Display dialog using given config. Make sure to {@link #setOnDifficultiesChosenListener(OnDifficultiesChosenListener)}
     */
    public void display(RangeConfig taskConfig, RangeConfig terrainConfig)
    {
        if (dialog != null){
            dialog.setOnDismissListener(null);
            dialog.dismiss();
            dialog = null;
        }
        this.taskConfig = taskConfig;
        this.terrainConfig = terrainConfig;
        prepareDialog();
    }
    
    protected void setRanges(RangeBar rangeBar, RangeConfig config)
    {
        int min = config.getMin();
        int max = config.getMax();
        if (min == -1){
            min = 0;
        } else {
            min = min-1;
        }
        if (max == -1){
            max = 4;
        } else {
            max = max-1;
        }
        rangeBar.setThumbIndices(min, max);
    }
    protected void setConfig(RangeConfig config, RangeBar rangeBar)
    {
        int min = rangeBar.getLeftIndex()+1;
        int max = rangeBar.getRightIndex()+1;
        
        config.setMin(min);
        config.setMax(max);
        config.validate();
    }
    
    protected void prepareDialog()
    {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(parent);
        dialogBuilder.setTitle(R.string.chooseDifficulties);
        LayoutInflater inflater = LayoutInflater.from(parent);
        View view = inflater.inflate(R.layout.dialog_difficulty, null);
        dialogBuilder.setView(view);

        final RangeBar rangeTask = (RangeBar)view.findViewById(R.id.rangeDifficultyTask);
        final RangeBar rangeTerrain = (RangeBar)view.findViewById(R.id.rangeDifficultyTerrain);
        
        setRanges(rangeTask, taskConfig);
        setRanges(rangeTerrain, terrainConfig);
        
        dialog = dialogBuilder.create();
        dialog.show();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener()
        {
            @Override
            public void onDismiss(DialogInterface dlg)
            {
                setConfig(taskConfig, rangeTask);
                setConfig(terrainConfig, rangeTerrain);

                dialog = null;
                if (onDifficultiesChosenListener != null){
                    onDifficultiesChosenListener.cacheDifficulties(taskConfig, terrainConfig);
                }
            }
        });
        
        dialog.getButton(Dialog.BUTTON_NEUTRAL).setTextAppearance(parent, R.style.TextAppearance_Large);
    }

    public OnDifficultiesChosenListener getOnDifficultiesChosenListener()
    {
        return onDifficultiesChosenListener;
    }

    public void setOnDifficultiesChosenListener(OnDifficultiesChosenListener onDifficultiesChosenListener)
    {
        this.onDifficultiesChosenListener = onDifficultiesChosenListener;
    }
}
