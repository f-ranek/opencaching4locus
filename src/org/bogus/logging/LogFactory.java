package org.bogus.logging;

public class LogFactory
{
    public static org.apache.commons.logging.Log getLog(Class<?> clazz)
    {
        String name = clazz.getName();
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx > 0){
            name = name.substring(dotIdx+1);
        }
        if (name.length() > 23){
            name = name.substring(0, 23);
        }
        return new LogImpl(name);
    }
}
