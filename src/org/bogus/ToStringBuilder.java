package org.bogus;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

public class ToStringBuilder
{
    protected final StringBuilder sb = new StringBuilder(200);
    private String buff;
    /** 0 - utworzony, 1 - coś dodane, 2 - wygenerowany string */
    private int state;
    
    private boolean includeEmptyStrings;
    
    public boolean isIncludeEmptyStrings()
    {
        return includeEmptyStrings;
    }

    public void setIncludeEmptyStrings(boolean includeEmptyStrings)
    {
        this.includeEmptyStrings = includeEmptyStrings;
    }

    protected final void processState()
    {
        buff = null;
        
        switch(state){
        case 0: {
            state = 1;
            break;
        }
        case 1: {
            sb.append(", ");
            break;
        }
        case 2: {
            sb.setLength(sb.length() - 1);
            sb.append(", ");
            state = 1;
            break;
        }
        }
    }
    
    @Override
    public String toString()
    {
        switch(state){
        case 1: 
        case 0: {
            sb.append(']');
            state = 2;
            return buff = sb.toString();
        }
        case 2: {
            if (buff == null){
                 buff = sb.toString();
            }
            return buff;
        }
        }
        return null;
    }
    
    public ToStringBuilder(final Class<?> clazz)
    {
        sb.append(clazz.getSimpleName()).append('[');
    }
    
    public ToStringBuilder(final Object obj){
        this(obj.getClass());
    }
    
    
    public ToStringBuilder add(String name, Object value)
    {
        if (value != null){
            boolean append;
            if (value instanceof String){
                append = includeEmptyStrings || ((String)value).length() > 0;
            } else {
                append = true;
            }
            if (append){
                processState();
                if (name != null){
                    sb.append(name).append('=');
                }
                if (value instanceof Date){
                    appendDate((Date)value);
                } else 
                if (value instanceof Calendar){
                    appendCalendar((Calendar)value);
                } else
                if (value.getClass().isArray()){
                    appendArray(value);
                } else {
                    sb.append(value);
                }
            }
        }
        
        return this;
    }
    
    public ToStringBuilder add(String name, Date value)
    {
        if (value != null){
            processState();
            sb.append(name).append('=');
            appendDate(value);
        }
        return this;
    }
    
    public ToStringBuilder add(String name, boolean value)
    {
        processState();
        sb.append(name).append('=').append(value);
        return this;
    }

    public ToStringBuilder add(String name, byte value)
    {
        processState();
        sb.append(name).append('=').append(value);
        return this;
    }
    
    public ToStringBuilder add(String name, short value)
    {
        processState();
        sb.append(name).append('=').append(value);
        return this;
    }

    public ToStringBuilder add(String name, int value)
    {
        processState();
        sb.append(name).append('=').append(value);
        return this;
    }

    public ToStringBuilder add(String name, int value, int defValue)
    {
        if (value != defValue){
            processState();
            sb.append(name).append('=').append(value);
        }
        return this;
    }

    public ToStringBuilder add(String name, long value)
    {
        processState();
        sb.append(name).append('=').append(value);
        return this;
    }

    public ToStringBuilder add(String name, long value, long defValue)
    {
        if (value != defValue){
            processState();
            sb.append(name).append('=').append(value);
        }
        return this;
    }
    
    public ToStringBuilder add(String name, double value)
    {
        processState();
        sb.append(name).append('=').append(value);
        return this;
    }

    public ToStringBuilder add(String name, double value, double defValue)
    {
        boolean hasValue = ((Double.isNaN(defValue) ^ Double.isNaN(value)) || Math.abs(value - defValue) > 1e-10); 
        if (hasValue){
            processState();
            sb.append(name).append('=').append(value);
        }
        return this;
    }

    public ToStringBuilder add(String name, float value)
    {
        processState();
        sb.append(name).append('=').append(value);
        return this;
    }

    public ToStringBuilder add(String name, float value, float defValue)
    {
        boolean hasValue = ((Float.isNaN(defValue) ^ Float.isNaN(value)) || Math.abs(value - defValue) > 1e-10); 
        if (hasValue){
            processState();
            sb.append(name).append('=').append(value);
        }
        return this;
    }

    public ToStringBuilder add(String name, char value)
    {
        processState();
        sb.append(name).append('=').append(value);
        return this;
    }

    protected final StringBuilder appendTwo(int i)
    {
        if (i < 10){
            sb.append('0');
        }
        return sb.append(i);
    }
    
    @SuppressWarnings("deprecation")
    protected void appendDate(Date date)
    {
        sb.append(date.getYear() + 1900).append('-');
        appendTwo(date.getMonth() + 1).append('-');
        appendTwo(date.getDate());
        
        final int h = date.getHours();
        final int m = date.getMinutes();
        final int s = date.getSeconds();
        if (h != 0 || m != 0 || s != 0){
            sb.append(' ');
            appendTwo(h).append(':');
            appendTwo(m);
            if (s != 0){
                sb.append(':');
                appendTwo(s);
            }
        }
        
    }

    protected void appendArray(Object array)
    {
        final Class<?> componentType = array.getClass().getComponentType();
        if (componentType.equals(Boolean.TYPE)){
            sb.append(Arrays.toString((boolean[]) array));
        } else
        if (componentType.equals(Byte.TYPE)){
            sb.append(Arrays.toString((byte[]) array));
        } else
        if (componentType.equals(Short.TYPE)){
            sb.append(Arrays.toString((short[]) array));
        } else
        if (componentType.equals(Integer.TYPE)){
            sb.append(Arrays.toString((int[]) array));
        } else
        if (componentType.equals(Long.TYPE)){
            sb.append(Arrays.toString((long[]) array));
        } else
        if (componentType.equals(Float.TYPE)){
            sb.append(Arrays.toString((float[]) array));
        } else
        if (componentType.equals(Double.TYPE)){
            sb.append(Arrays.toString((double[]) array));
        } else
        if (componentType.equals(Character.TYPE)){
            sb.append(Arrays.toString((char[]) array));
        } else {
            sb.append(Arrays.toString((Object[]) array));
        }
        
    }
    
    protected void appendCalendar(Calendar cal)
    {
        sb.append(cal.get(Calendar.YEAR)).append('-');
        appendTwo(cal.get(Calendar.MONTH) + 1).append('-');
        appendTwo(cal.get(Calendar.DAY_OF_MONTH));
        
        final int h = cal.get(Calendar.HOUR_OF_DAY);
        final int m = cal.get(Calendar.MINUTE);
        final int s = cal.get(Calendar.SECOND);
        if (h != 0 || m != 0 || s != 0){
            sb.append(' ');
            appendTwo(h).append(':');
            appendTwo(m);
            if (s != 0){
                sb.append(':');
                appendTwo(s);
            }
        }
        
    }
}
