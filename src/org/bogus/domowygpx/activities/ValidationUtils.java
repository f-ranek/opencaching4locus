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
    private View ownerView;

    private Map<String, Integer> errorFieldsMap = new HashMap<String, Integer>();
    private Map<String, Integer> errorFieldsFocusMap = new HashMap<String, Integer>();
    boolean focusedOnErrorField;

    private TaskConfiguration previousTaskConfiguration;
    
    public ValidationUtils()
    {
        
    }
    public ValidationUtils(View ownerView)
    {
        this.ownerView = ownerView;
    }

    protected void resetViewError(int viewId)
    {
        TextView v = (TextView)ownerView.findViewById(viewId);
        v.setText(null);
        v.setVisibility(TextView.GONE);
        v.setTextAppearance(ownerView.getContext(), android.R.style.TextAppearance_Small);
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
            errorControl.append("\n");
            errorControl.append(errorText);
        } else {
            errorControl.setText(errorText);
        }
        
        errorControl.setVisibility(TextView.VISIBLE);
        errorControl.setTextAppearance(ownerView.getContext(), 
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
                ownerView.findViewById(focusFieldId).requestFocus();
                focusedOnErrorField = true;
            }
            
            View errorView = ownerView.findViewById(errorViewId);
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
    
    /**
     * Checks for errors, but does not show them on a view
     * @param taskConfiguration
     * @return true, in case of validation pass, false in case of errors
     */
    public boolean checkForErrorsSilent(
        TaskConfiguration taskConfiguration)
    {
        final List<Pair<String, String>> errors = taskConfiguration.getErrors();
        if (!errors.isEmpty()){
            return false;
        }
        final List<Pair<String, String>> warnings = taskConfiguration.getWarnings();
        if (!warnings.isEmpty()){
            return false;
        }
        return true;
    }
    
    /**
     * Checks for errors and shows them on a view
     * @param taskConfiguration
     * @return true, in case of validation pass, false in case of errors
     */
    public boolean checkForErrors(
        TaskConfiguration taskConfiguration)
    {
        final List<Pair<String, String>> errors = taskConfiguration.getErrors();
        processErrorsList(errors, false);
        
        if (!errors.isEmpty()){
            previousTaskConfiguration = null;
            Toast.makeText(ownerView.getContext(), "Przed kontynuacją popraw zaznaczone błędy", Toast.LENGTH_LONG).show();
            return false;
        }

        final List<Pair<String, String>> warnings = taskConfiguration.getWarnings();
        processErrorsList(warnings, true);
        
        if (!warnings.isEmpty()){
            if (previousTaskConfiguration == null || !taskConfiguration.equals(previousTaskConfiguration)){
                previousTaskConfiguration = taskConfiguration;
                Toast.makeText(ownerView.getContext(), "Wystąpiły pewne problemy. Zweryfikuj dane, po czym ponownie kliknij 'Start'", Toast.LENGTH_LONG).show();
                return false;
            }
        }
        
        resetViewErrors();
        
        return true;
    }
    public View getOwnerView()
    {
        return ownerView;
    }
    public void setOwnerView(View ownerView)
    {
        this.ownerView = ownerView;
    }
}
