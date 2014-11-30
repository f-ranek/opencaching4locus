package android.content;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Context
{
    public Map<String, Object> preferences = new HashMap<String, Object>();
    
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return new SharedPreferences(){

            @Override
            public Map<String, ?> getAll()
            {
                return Collections.unmodifiableMap(preferences);
            }

            @Override
            public String getString(String key, String defValue)
            {
                return preferences.containsKey(key) ? (String)preferences.get(key) : defValue;
            }

            @SuppressWarnings("unchecked")
            @Override
            public Set<String> getStringSet(String key, Set<String> defValues)
            {
                return preferences.containsKey(key) ? (Set<String>)preferences.get(key) : defValues;
            }

            @Override
            public int getInt(String key, int defValue)
            {
                return preferences.containsKey(key) ? (Integer)preferences.get(key) : defValue;
            }

            @Override
            public long getLong(String key, long defValue)
            {
                return preferences.containsKey(key) ? (Long)preferences.get(key) : defValue;
            }

            @Override
            public float getFloat(String key, float defValue)
            {
                return preferences.containsKey(key) ? (Float)preferences.get(key) : defValue;
            }

            @Override
            public boolean getBoolean(String key, boolean defValue)
            {
                return preferences.containsKey(key) ? (Boolean)preferences.get(key) : defValue;
            }

            @Override
            public boolean contains(String key)
            {
                return preferences.containsKey(key);
            }

            @Override
            public Editor edit()
            {
                return new Editor()
                {
                    Set<String> toRemove = new HashSet<String>();
                    Map<String, Object> toApply = new HashMap<String, Object>();
                    
                    @Override
                    public Editor putString(String key, String value)
                    {
                        toApply.put(key, value);
                        return this;
                    }

                    @Override
                    public Editor putStringSet(String key, Set<String> values)
                    {
                        toApply.put(key, values);
                        return this;
                    }

                    @Override
                    public Editor putInt(String key, int value)
                    {
                        toApply.put(key, value);
                        return this;
                    }

                    @Override
                    public Editor putLong(String key, long value)
                    {
                        toApply.put(key, value);
                        return this;
                    }

                    @Override
                    public Editor putFloat(String key, float value)
                    {
                        toApply.put(key, value);
                        return this;
                    }

                    @Override
                    public Editor putBoolean(String key, boolean value)
                    {
                        toApply.put(key, value);
                        return this;
                    }

                    @Override
                    public Editor remove(String key)
                    {
                        toRemove.add(key);
                        return this;
                    }

                    @Override
                    public Editor clear()
                    {
                        toRemove.addAll(preferences.keySet());
                        return this;
                    }

                    @Override
                    public boolean commit()
                    {
                        apply();
                        return true;
                    }

                    @Override
                    public void apply()
                    {
                        preferences.entrySet().removeAll(toRemove);
                        preferences.putAll(toApply);
                    }
                    
                };
            }

            @Override
            public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener)
            {
                // TODO Auto-generated method stub
                
            }

            @Override
            public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener)
            {
                // TODO Auto-generated method stub
                
            }
            
        };
    }
}
