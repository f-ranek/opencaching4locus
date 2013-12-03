package org.bogus.android;

import java.io.File;

import org.bogus.android.FolderPreferenceHelperActivity.FolderPreferenceHelperActivityListener;
import org.bogus.geocaching.egpx.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

/**
 * A preference item for with a folder pick-up button. Requires {@link #setFolderPreferenceHelperActivity(FolderPreferenceHelperActivity) 
 * FolderPreferenceHelperActivity} to be registered.
 * @author Boguś
 *
 */
public class FolderPreference extends EditTextPreference 
    implements android.view.View.OnClickListener, FolderPreferenceHelperActivityListener
{
    private FolderPreferenceHelperActivity folderPreferenceHelperActivity;

    protected static final int REQUEST_CODE_PICK_DIRECTORY = 1;
    
    public FolderPreference(Context context)
    {
        super(context);
    }

    public FolderPreference(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public FolderPreference(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        if (folderPreferenceHelperActivity != null){
            ViewGroup container = (ViewGroup)view;
            if (container instanceof ScrollView){
                container = (ViewGroup)container.getChildAt(0);
            }
            LinearLayout layout = new LinearLayout(getContext());
            Button button = new Button(getContext());
            layout.addView(button);
            
            container.addView(layout);
            
            LinearLayout.LayoutParams btnLayoutParams = (LinearLayout.LayoutParams)(button.getLayoutParams());
            btnLayoutParams.width = 0;
            btnLayoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            btnLayoutParams.weight = 4;
            button.setText(R.string.pref_btn_choose_directory);
            button.setOnClickListener(this);
            
            ViewGroup.LayoutParams layoutLayoutParams = layout.getLayoutParams(); 
            layoutLayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            layoutLayoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            layout.setOrientation(LinearLayout.HORIZONTAL);
            layout.setGravity(Gravity.RIGHT);
            layout.setWeightSum(10);
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult)
    {
        if (positiveResult){
            boolean valid = validate();
            super.onDialogClosed(valid);
        } else {
            super.onDialogClosed(false);
        }
        if (folderPreferenceHelperActivity != null){
            folderPreferenceHelperActivity.unregister(this);
        }
    }

    @Override
    public void onClick(View v)
    {
        folderPreferenceHelperActivity.register(this);

        File dir = null;
        {
            CharSequence currentFolder = getEditText().getText();
            if (currentFolder != null && currentFolder.length() > 0){
                dir = new File(currentFolder.toString());
                //if (!dir.exists() || !dir.isDirectory()){
                //    dir = null;
                //}
            }
        }
        if (dir == null){
            dir = Environment.getExternalStorageDirectory();
        }
        final CharSequence title = getTitle();

        boolean intentSent = false;
        try {
            // Open-Intents
            Intent intent = new Intent("org.openintents.action.PICK_DIRECTORY");
            intent.setData(Uri.fromFile(dir));
            if (title != null){
                intent.putExtra("org.openintents.extra.TITLE", title.toString());
            }
            folderPreferenceHelperActivity.startActivityForResult(intent, REQUEST_CODE_PICK_DIRECTORY);
            intentSent = true;
        } catch (ActivityNotFoundException e) {
            //Toast.makeText(getContext(), "Ups, OI nie wystartował", Toast.LENGTH_SHORT).show();
        }
        if (!intentSent){
            // AndExplorer
            try{
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_PICK);
                Uri startDir = Uri.fromFile(dir);
                
                intent.setDataAndType(startDir, "vnd.android.cursor.dir/lysesoft.andexplorer.file");
                if (title != null){
                    intent.putExtra("explorer_title", title.toString());
                }

                // intent.putExtra("browser_list_layout", "2"); // ???
                folderPreferenceHelperActivity.startActivityForResult(intent, REQUEST_CODE_PICK_DIRECTORY);
                
                intentSent = true;
            } catch (ActivityNotFoundException e) {
                //Toast.makeText(getContext(), "Ups, AndExplorer nie wystartował", Toast.LENGTH_SHORT).show();
            }
        }
        
        if (!intentSent){
            try{
                // ES File Explorer
                Intent intent = new Intent("com.estrongs.action.PICK_DIRECTORY");
                if (title != null){
                    intent.putExtra("com.estrongs.intent.extra.TITLE", title.toString());
                }
                intent.setData(Uri.parse("file://" + dir));
                folderPreferenceHelperActivity.startActivityForResult(intent, REQUEST_CODE_PICK_DIRECTORY);

                intentSent = true;
            } catch (ActivityNotFoundException e) {
                //Toast.makeText(getContext(), "Ups, ES File Explorer nie wystartował", Toast.LENGTH_SHORT).show();
            }
        }
        
        /*
        if (!intentSent){
            try{
                // Samsung
                Intent intent = new Intent();
                intent.setAction("com.sec.android.app.myfiles.PICK_DATA");
                intent.putExtra("FOLDERPATH", dir.toString());
                intent.putExtra("CONTENT_TYPE", "folder/*");
                folderPreferenceHelperActivity.startActivityForResult(intent, REQUEST_CODE_PICK_DIRECTORY);
                intentSent = true;
            } catch (ActivityNotFoundException e) {
                // No compatible file manager was found.
                Toast.makeText(getContext(), "Ups, Samsung nie wystartował", 
                        Toast.LENGTH_SHORT).show();
            }
            
        }
        */
        if (!intentSent){
            folderPreferenceHelperActivity.unregister(this);
            Toast.makeText(getContext(), R.string.pref_btn_choose_directory_failed, 
                Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 
     * @param requestCode
     * @param resultCode
     * @param data
     * @return
     */
    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == REQUEST_CODE_PICK_DIRECTORY){
            folderPreferenceHelperActivity.unregister(this);
            if (resultCode == Activity.RESULT_OK && data != null) {
                // obtain the filename
                Uri fileUri = data.getData();
                if (fileUri != null) {
                    String filePath = fileUri.getPath();
                    if (filePath != null && "file".equals(fileUri.getScheme())) {
                        getEditText().setText(filePath);
                        validate();
                    }                   
                }
                return true;
            }
        }
        return false;
    }
    
    
    protected boolean validate()
    {
        File dir = null;
        String message = null;
        CharSequence currentFolder = getEditText().getText();
        if (currentFolder == null || currentFolder.length() == 0){
            message = "Wybierz katalog";
        } else {
            dir = new File(currentFolder.toString());
            if (dir.isFile()){
                message = "Wybierz katalog, nie plik";
            } else {
                dir.mkdirs();
                if (!dir.exists()){
                    message = "Ups, nie mogę stworzyć katalogu";
                } else {
                    if (!dir.canWrite()){
                        message = "Nie mogę zapisywać do wybranego przez Ciebie katalogu";
                    }
                }
            }
        }        
        if (message != null){
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setMessage(message);
            builder.setNegativeButton("OK", null);
            builder.show();
            return false;
        } else {
            if (!dir.isAbsolute()){
                dir = dir.getAbsoluteFile();
                getEditText().setText(dir.getPath());
            }
        }
        
        return true;
    }

    public FolderPreferenceHelperActivity getFolderPreferenceHelperActivity()
    {
        return folderPreferenceHelperActivity;
    }

    public void setFolderPreferenceHelperActivity(FolderPreferenceHelperActivity folderPreferenceHelperActivity)
    {
        this.folderPreferenceHelperActivity = folderPreferenceHelperActivity;
    }

    @Override
    protected void showDialog(Bundle state)
    {
        super.showDialog(state);
        final AlertDialog dialog = (AlertDialog)getDialog();
        final Button btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        btnPositive.setOnClickListener(new View.OnClickListener()
        {            
            @Override
            public void onClick(View v)
            {
                if (validate()){
                    // simulate dialog close
                    // NOTE: this will call #validate() again
                    FolderPreference.this.onClick(dialog, AlertDialog.BUTTON_POSITIVE);
                    dialog.dismiss();
                }
            }
        });
        dialog.getWindow().setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
}
