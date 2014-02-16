package org.bogus.domowygpx.activities;

import org.bogus.android.AndroidUtils;
import org.bogus.geocaching.egpx.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

public class OnlyWithTrackablesFragment implements OnSharedPreferenceChangeListener
{
    ViewGroup tableRowWithTrackablesOnly;
    CheckBox checkBoxWithTrackablesOnly;
    
    public OnlyWithTrackablesFragment(final ViewGroup view, final Window window)
    {
        tableRowWithTrackablesOnly = (ViewGroup) view.findViewById(R.id.tableRowWithTrackablesOnly);
        checkBoxWithTrackablesOnly = (CheckBox) view.findViewById(R.id.checkBoxWithTrackablesOnly);
        checkBoxWithTrackablesOnly.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                AndroidUtils.hideSoftKeyboard(window);
            }
        });
        TextView textViewWithTrackablesOnly = (TextView) view.findViewById(R.id.textViewWithTrackablesOnly);
        textViewWithTrackablesOnly.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (checkBoxWithTrackablesOnly.isClickable()){
                    AndroidUtils.hideSoftKeyboard(window);
                    checkBoxWithTrackablesOnly.toggle();
                }
            }
        });
        
        SharedPreferences config = view.getContext().getSharedPreferences("egpx", Context.MODE_PRIVATE);
        config.registerOnSharedPreferenceChangeListener(this);
        
        if (!config.getBoolean("listTrackables", true)){
            tableRowWithTrackablesOnly.setVisibility(View.GONE);
        }
    }

    public boolean isOnlyWithTrackables()
    {
        return checkBoxWithTrackablesOnly.isChecked();
    }

    public void setOnlyWithTrackables(boolean onlyWithTrackables)
    {
        checkBoxWithTrackablesOnly.setChecked(onlyWithTrackables);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences config, String key)
    {
        if ("listTrackables".equals(key)){
            tableRowWithTrackablesOnly.setVisibility(
                config.getBoolean("listTrackables", true) ? View.VISIBLE : View.GONE);
        }
    }

}
