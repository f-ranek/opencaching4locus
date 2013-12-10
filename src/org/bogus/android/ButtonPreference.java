package org.bogus.android;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;

/**
 * A preference to execute shot-action. Button title can be set from xml config, using 
 * <code>android:text</code> attribute. Other properties can be set programatically,
 * by {@link #getButton() getting button} and modifing it's properties. Remember to
 * {@link #setOnClickListener(OnClickListener) setOnClickListener}.
 * 
 * @author Boguś
 */
public class ButtonPreference extends Preference
{
    private final static String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    OnClickListener onClickListener;
    private Button button;
    private String buttonText;
    
    public interface OnClickListener
    {
        /**
         * Called, when button has been clicked
         * @param pref
         */
        void onClick(ButtonPreference pref);
    }
    
    public ButtonPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        int resId = attrs.getAttributeResourceValue(ANDROID_NS, "text", 0);
        buttonText = context.getResources().getString(resId);
    }

    public ButtonPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.preferenceStyle);
    }

    public ButtonPreference(Context context) {
        this(context, null);
    }
    
    @Override
    public boolean isPersistent() {
        return false;
    }
    
    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        final ViewGroup viewGroup = (ViewGroup)view.findViewById(android.R.id.widget_frame);
        final Button button = getButton();
        button.setText(buttonText);
        ViewParent buttonParent = button.getParent();  
        if (buttonParent != viewGroup){
            if (buttonParent != null){
                ((ViewGroup)buttonParent).removeView(button);
            }
            viewGroup.addView(button);
        }
        viewGroup.setVisibility(View.VISIBLE);
        viewGroup.setClickable(true);
    }
    
    public void setOnClickListener(final OnClickListener ocl)
    {
        this.onClickListener = ocl;
    }

    @Override
    public void setEnabled(boolean enabled)
    {
        super.setEnabled(enabled);
        if (button != null){
            button.setEnabled(enabled);
        }
    }
    
    @Override
    public void onDependencyChanged(Preference dependency, boolean disableDependent) {
        super.onDependencyChanged(dependency, disableDependent);
        if (button != null){
            button.setEnabled(isEnabled());
        }
    }
    
    public Button getButton()
    {
        if (button == null){
            button = new Button(getContext());
            button.setEnabled(isEnabled());
            button.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    if (onClickListener != null){
                        onClickListener.onClick(ButtonPreference.this);
                    }
                }
            });
        }
        return button;
    }
}
