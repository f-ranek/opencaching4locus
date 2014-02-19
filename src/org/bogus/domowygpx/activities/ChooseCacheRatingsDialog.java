package org.bogus.domowygpx.activities;

import org.bogus.geocaching.egpx.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.Selection;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

/**
 * A pick-up dialog for cache ratings and recomendations
 * @author Boguś
 *
 */
public class ChooseCacheRatingsDialog
{
    final Activity parent;
    AlertDialog dialog;
    OnRatingsChosenListener onRatingsChosenListener;
    CacheRatingsConfig ratings; 
    CacheRecommendationsConfig rcmds;

    /**
     * An interface to be notified when user closes the dialog
     * @author Boguś
     *
     */
    public interface OnRatingsChosenListener {
        /**
         * Dialog has been closed, and new config is to be applied
         */
        void cacheRatings(CacheRatingsConfig ratings, CacheRecommendationsConfig rcmds);
    }

    public ChooseCacheRatingsDialog(Activity parent)
    {
        this.parent = parent;
    }
    
    /**
     * Display dialog using given config. Make sure to {@link #setOnRatingsChosenListener(OnRatingsChosenListener)}
     * @param cacheTypes
     */
    public void display(CacheRatingsConfig ratings, CacheRecommendationsConfig rcmds)
    {
        this.ratings = ratings;
        this.rcmds = rcmds;
        if (dialog != null){
            dialog.setOnDismissListener(null);
            dialog.dismiss();
            dialog = null;
        }
        prepareDialog();
    }
    
    void uncheckRadios(CompoundButton[] buttons, CompoundButton self)
    {
        for (CompoundButton btn : buttons){
            if (btn != null && btn != self){
                btn.setChecked(false);
            }
        }
    }
    
    protected void prepareDialog()
    {
        
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(parent);
        dialogBuilder.setTitle(R.string.chooseCacheRatings); 
        LayoutInflater inflater = LayoutInflater.from(parent);
        View view = inflater.inflate(R.layout.dialog_ratings, null);
        dialogBuilder.setView(view);

        final RadioButton[] radios = new RadioButton[6];
        radios[0] = (RadioButton)view.findViewById(R.id.radioButtonRatingsAll);
        radios[3] = (RadioButton)view.findViewById(R.id.radioButtonRatings3);
        radios[4] = (RadioButton)view.findViewById(R.id.radioButtonRatings4);
        radios[5] = (RadioButton)view.findViewById(R.id.radioButtonRatings5);
        
        uncheckRadios(radios, null);
        
        final CheckBox checkBoxIncludeUnrated = (CheckBox)view.findViewById(R.id.checkBoxIncludeUnrated);
        checkBoxIncludeUnrated.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                ratings.setIncludeUnrated(isChecked);
            }
        });
        
        radios[0].setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                if (isChecked){
                    uncheckRadios(radios, buttonView);
                }
                checkBoxIncludeUnrated.setEnabled(!isChecked);
                ratings.setAll(isChecked);
            }
        });
        for (int i=3; i<=5; i++){
            final int idx = i;
            radios[idx].setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
            {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
                {
                    if (isChecked){
                        uncheckRadios(radios, buttonView);
                        ratings.setMinRating(idx);
                    }
                }
            });
        }
        
        checkBoxIncludeUnrated.setChecked(ratings.isIncludeUnrated());
        if (ratings.isAll()){
            radios[0].setChecked(true);
            checkBoxIncludeUnrated.setEnabled(false);
        } else {
            int val = ratings.getMinRating();
            if (radios[val] == null){
                val = 0;
            }
            radios[val].setChecked(true);
            checkBoxIncludeUnrated.setEnabled(val != 0);
        }
        
        final CheckBox checkBoxPercent = (CheckBox)view.findViewById(R.id.checkBoxRcmdsPercent);
        final EditText editRecomendations = (EditText)view.findViewById(R.id.editTextRcmds);
        final TextView textViewRdmdsInfo = (TextView)view.findViewById(R.id.textViewRdmdsInfo);
        
        checkBoxPercent.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                int val = rcmds.getValue();
                rcmds.setPercent(isChecked);
                if (val != rcmds.getValue()){
                    Editable text = editRecomendations.getText();
                    text.replace(0, text.length(), String.valueOf(rcmds.getValue()));
                }
                textViewRdmdsInfo.setText(isChecked 
                    ? R.string.cacheRcmdsInfoPercent 
                    : R.string.cacheRcmdsInfoNormal);
            }
        });
        
        checkBoxPercent.setChecked(rcmds.isPercent());
        if (rcmds.getValue() > 0 && !rcmds.isAll()){
            Editable text = editRecomendations.getText();
            text.replace(0, text.length(), String.valueOf(rcmds.getValue()));
            Selection.setSelection(text, text.length());
        }
        editRecomendations.addTextChangedListener(new TextWatcher(){

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
            }

            @Override
            public void afterTextChanged(Editable s)
            {
                try{
                    int newVal = s.length() == 0 ? 0 : Integer.parseInt(s.toString());
                    int oldVal = rcmds.getValue();
                    rcmds.setValue(newVal);
                    if (newVal != oldVal && s.length() > 0){
                        s.replace(0, s.length(), String.valueOf(rcmds.getValue()));
                    }
                }catch(NumberFormatException nfe){
                    // ignore
                }
                
            }});
        
        dialog = dialogBuilder.create();
        dialog.show();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener()
        {
            @Override
            public void onDismiss(DialogInterface dlg)
            {
                dialog = null;
                
                rcmds.setPercent(checkBoxPercent.isChecked());
                try{
                    final Editable s = editRecomendations.getText();
                    int newVal = s.length() == 0 ? 0 : Integer.parseInt(s.toString());
                    rcmds.setValue(newVal);
                    rcmds.setAll(newVal == 0);
                }catch(NumberFormatException nfe){
                    // ignore
                }
                
                if (onRatingsChosenListener != null){
                    onRatingsChosenListener.cacheRatings(ratings, rcmds);
                }
            }
        });
    }

    public OnRatingsChosenListener getOnRatingsChosenListener()
    {
        return onRatingsChosenListener;
    }

    public void setOnRatingsChosenListener(OnRatingsChosenListener onRatingsChosenListener)
    {
        this.onRatingsChosenListener = onRatingsChosenListener;
    }
}
