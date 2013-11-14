package org.bogus.logging;

import org.bogus.geocaching.egpx.BuildConfig;

public class LogImpl implements org.apache.commons.logging.Log
{
    private final String tag;

    LogImpl(String tag)
    {
        this.tag = tag;
    }

    @Override
    public boolean isDebugEnabled()
    {
        return android.util.Log.isLoggable(tag, android.util.Log.DEBUG) || BuildConfig.DEBUG;
    }

    @Override
    public boolean isErrorEnabled()
    {
        return android.util.Log.isLoggable(tag, android.util.Log.ERROR);
    }

    @Override
    public boolean isFatalEnabled()
    {
        return true;
    }

    @Override
    public boolean isInfoEnabled()
    {
        return android.util.Log.isLoggable(tag, android.util.Log.INFO);
    }

    @Override
    public boolean isTraceEnabled()
    {
        return android.util.Log.isLoggable(tag, android.util.Log.VERBOSE) || BuildConfig.DEBUG;
    }

    @Override
    public boolean isWarnEnabled()
    {
        return android.util.Log.isLoggable(tag, android.util.Log.WARN);
    }

    @Override
    public void trace(Object message)
    {
        android.util.Log.v(tag, message == null ? "" : message.toString());
    }

    @Override
    public void trace(Object message, Throwable t)
    {
        android.util.Log.v(tag, message == null ? "" : message.toString(), t);
    }

    @Override
    public void debug(Object message)
    {
        android.util.Log.d(tag, message == null ? "" : message.toString());
    }

    @Override
    public void debug(Object message, Throwable t)
    {
        android.util.Log.d(tag, message == null ? "" : message.toString(), t);
    }

    @Override
    public void info(Object message)
    {
        android.util.Log.i(tag, message == null ? "" : message.toString());
    }

    @Override
    public void info(Object message, Throwable t)
    {
        android.util.Log.i(tag, message == null ? "" : message.toString(), t);
    }

    @Override
    public void warn(Object message)
    {
        android.util.Log.w(tag, message == null ? "" : message.toString());
    }

    @Override
    public void warn(Object message, Throwable t)
    {
        android.util.Log.w(tag, message == null ? "" : message.toString(), t);
    }

    @Override
    public void error(Object message)
    {
        android.util.Log.e(tag, message == null ? "" : message.toString());
    }

    @Override
    public void error(Object message, Throwable t)
    {
        android.util.Log.e(tag, message == null ? "" : message.toString(), t);
    }

    @Override
    public void fatal(Object message)
    {
        android.util.Log.wtf(tag, message == null ? "" : message.toString());
    }

    @Override
    public void fatal(Object message, Throwable t)
    {
        android.util.Log.wtf(tag, message == null ? "" : message.toString(), t);
    }
}
