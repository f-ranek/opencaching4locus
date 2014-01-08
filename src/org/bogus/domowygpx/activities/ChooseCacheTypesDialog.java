package org.bogus.domowygpx.activities;

import org.bogus.android.AndroidUtils;
import org.bogus.geocaching.egpx.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;

public class ChooseCacheTypesDialog
{
    final Activity parent;
    AlertDialog dialog;
    ListView listViewCacheTypes;
    CacheTypesListAdapter listViewAdapter;
    OnTypesChosenListener onTypesChosenListener;

    public interface OnTypesChosenListener {
        void cacheTypes(String cacheTypes);
    }

    class CacheTypeItemViewHolder {
        
        View viewRoot;
        CheckBox cacheTypeItemCheckBox;
        TextView cacheTypeItemText;
        
        void initialSetup(View viewRoot)
        {
            this.viewRoot = viewRoot;
            cacheTypeItemCheckBox = (CheckBox)viewRoot.findViewById(R.id.cacheTypeItemCheckBox);
            cacheTypeItemText = (TextView)viewRoot.findViewById(R.id.cacheTypeItemText);
            cacheTypeItemText.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    cacheTypeItemCheckBox.toggle();
                }
            });
        }
        
    }    
    
    CacheTypesConfig cacheTypesConfig = new CacheTypesConfig();
    
    class CacheTypesListAdapter extends BaseAdapter
    {
        private LayoutInflater layoutInflater = LayoutInflater.from(parent);
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            CacheTypeItemViewHolder holder;
            if (convertView == null) {
                convertView = layoutInflater.inflate(R.layout.cache_type_list_item, null);
                holder = new CacheTypeItemViewHolder();
                holder.initialSetup(convertView);
                convertView.setTag(holder);
            } else {
                holder = (CacheTypeItemViewHolder) convertView.getTag();
            }
            applyToView(position, holder);
            return convertView;
        }
        
        @Override
        public long getItemId(int position)
        {
            return position;
        }
        
        @Override
        public Void getItem(final int position)
        {
            return null; // cacheTypesConfig[position]; // XXX ???
        }
        
        @Override
        public int getCount()
        {
            return cacheTypesConfig.getCount();
        }
    }    
    
    private CompoundButton.OnCheckedChangeListener allClickListener = new CompoundButton.OnCheckedChangeListener()
    {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
            listViewAdapter.notifyDataSetChanged();
            if (isChecked){
                dialog.dismiss();
            }
        }    
    };

    void applyToView(final int position, CacheTypeItemViewHolder holder)
    {
        holder.cacheTypeItemCheckBox.setOnCheckedChangeListener(null);
        holder.cacheTypeItemCheckBox.setChecked(cacheTypesConfig.get(position));
        final int[][] androidConfig = cacheTypesConfig.getAndroidConfig();
        if (position == 0){
            // all
            holder.cacheTypeItemCheckBox.setOnCheckedChangeListener(allClickListener);
            holder.cacheTypeItemText.setCompoundDrawables(null, null, null, null);
            holder.cacheTypeItemText.setEnabled(true);
            holder.cacheTypeItemCheckBox.setEnabled(true);
        } else {
            holder.cacheTypeItemCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
            {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
                {
                    cacheTypesConfig.set(position, isChecked);
                }    
            });
            boolean enabled = !cacheTypesConfig.isAllSet();
            Drawable icon = parent.getResources().getDrawable(androidConfig[position][0]);
            if (!enabled){
                icon = AndroidUtils.getGrayscaled(icon);
            }
            holder.cacheTypeItemText.setCompoundDrawables(icon, null, null, null);
            holder.cacheTypeItemText.setEnabled(enabled);
            holder.cacheTypeItemCheckBox.setEnabled(enabled);
        }
        holder.cacheTypeItemText.setText(androidConfig[position][1]);
    }
    
    public ChooseCacheTypesDialog(Activity parent)
    {
        this.parent = parent;
    }
    
    public void display(String cacheTypes)
    {
        if (dialog != null){
            dialog.setOnDismissListener(null);
            dialog.dismiss();
            dialog = null;
        }
        cacheTypesConfig.parseFromString(cacheTypes);
        prepareDialog();
    }
    
    private void prepareDialog()
    {
        final LayoutInflater inflater = LayoutInflater.from(parent);
        final ViewGroup view = (ViewGroup)inflater.inflate(R.layout.activity_choose_cache_type, null);
        
        listViewCacheTypes = (ListView)view.findViewById(R.id.listViewCacheTypes);
        listViewAdapter = new CacheTypesListAdapter();
        listViewCacheTypes.setAdapter(listViewAdapter);
        
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(parent);
        dialogBuilder.setTitle(R.string.title_activity_oauth_signing);
        dialogBuilder.setView(view);
        
        dialog = dialogBuilder.create();
        dialog.show();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener()
        {
            @Override
            public void onDismiss(DialogInterface dialog)
            {
                if (onTypesChosenListener != null){
                    final String result = cacheTypesConfig.serializeToString();
                    onTypesChosenListener.cacheTypes(result);
                }
            }
        });
        
    }

    public OnTypesChosenListener getOnTypesChosenListener()
    {
        return onTypesChosenListener;
    }

    public void setOnTypesChosenListener(OnTypesChosenListener onTypesChosenListener)
    {
        this.onTypesChosenListener = onTypesChosenListener;
    }
}
