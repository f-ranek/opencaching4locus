package org.bogus.domowygpx.activities;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bogus.domowygpx.utils.Pair;
import org.bogus.geocaching.egpx.R;

import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class ValidationUtils
{
    private View owner;

    private Map<String, Integer> errorFieldsMap = new HashMap<String, Integer>();
    private Map<String, Integer> errorFieldsFocusMap = new HashMap<String, Integer>();
    boolean focusedOnErrorField;

    private TaskConfiguration previousTaskConfiguration;
    
    public ValidationUtils(View owner)
    {
        this.owner = owner;
    }

    protected void resetViewError(int viewId)
    {
        TextView v = (TextView)owner.findViewById(viewId);
        v.setText(null);
        v.setVisibility(TextView.GONE);
        v.setTextAppearance(owner.getContext(), android.R.style.TextAppearance_Small);
    }
    
    public void resetViewErrors()
    {
        for (Integer id : errorFieldsMap.values()){
            resetViewError(id);
        }
        focusedOnErrorField = false;
    }    

    protected void markError(String errorText, TextView errorControl, boolean isWarning)
    {
        final boolean isShown = errorControl.getVisibility() == TextView.VISIBLE;
        if (isShown){
            errorControl.append("\n\r");
            errorControl.append(errorText);
        } else {
            errorControl.setText(errorText);
        }
        
        errorControl.setVisibility(TextView.VISIBLE);
        errorControl.setTextAppearance(owner.getContext(), 
            isWarning ? R.style.TextAppearance_Small_Warning 
                    : R.style.TextAppearance_Small_Error);
    }
    
    protected void processErrorsList(final List<Pair<String, String>> errors, boolean isWarning)
    {
        for (Pair<String, String> error : errors){
            final String fieldCode = error.first;
            final String errorText = error.second;
            
            int errorViewId;
            if (fieldCode != null && errorFieldsMap.containsKey(fieldCode)){
                errorViewId = errorFieldsMap.get(fieldCode);
            } else {
                errorViewId = R.id.errorOthers;
            }
            
            if (!focusedOnErrorField && fieldCode != null && errorFieldsFocusMap.containsKey(fieldCode)){
                // Use Next Focus.. properties
                int focusFieldId = errorFieldsFocusMap.get(fieldCode);
                owner.findViewById(focusFieldId).requestFocus();
                focusedOnErrorField = true;
            }
            
            View errorView = owner.findViewById(errorViewId);
            if (errorView instanceof TextView){
                TextView errorTextView = (TextView)errorView;
                markError(errorText, errorTextView, isWarning);
            }
        }
    }
    
    public void addErrorField(String key, int fieldId)
    {
        errorFieldsMap.put(key, fieldId);
    }

    public void addErrorFocusField(String key, int fieldId)
    {
        errorFieldsFocusMap.put(key, fieldId);
    }
    
    public boolean checkForErrors(
        TaskConfiguration taskConfiguration)
    {
        final List<Pair<String, String>> errors = taskConfiguration.getErrors();
        processErrorsList(errors, false);
        
        if (!errors.isEmpty()){
            previousTaskConfiguration = null;
            Toast.makeText(owner.getContext(), "Przed kontynuacją popraw zaznaczone błędy", Toast.LENGTH_LONG).show();
            return false;
        }

        final List<Pair<String, String>> warnings = taskConfiguration.getWarnings();
        processErrorsList(warnings, true);
        
        if (!warnings.isEmpty()){
            if (previousTaskConfiguration == null || !taskConfiguration.equals(previousTaskConfiguration)){
                previousTaskConfiguration = taskConfiguration;
                Toast.makeText(owner.getContext(), "Wystąpiły pewne problemy. Zweryfikuj dane, po czym ponownie kliknij 'Start'", Toast.LENGTH_LONG).show();
                return false;
            }
        }
        
        resetViewErrors();
        
        return true;
    }
}
