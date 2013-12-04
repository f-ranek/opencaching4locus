package org.bogus.android;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Queue;

import android.annotation.TargetApi;
import android.os.Build;

/**
 * A simple queue, use {@link #getInstance()} factory method to get the best implementation
 * @author Boguś
 *
 * @param <E>
 */
@SuppressWarnings("serial")
public class SimpleQueue<E> extends ArrayList<E> implements Queue<E>{
    
    @Override
    public boolean offer(E e)
    {
        add(e);
        return true;
    }

    @Override
    public E remove()
    {
        if (isEmpty()){
            throw new NoSuchElementException();
        } else {
            return poll();
        }
    }

    @Override
    public E poll()
    {
        if (isEmpty()){
            return null;
        } else {
            E result = get(0);
            remove(0);
            return result;
        }
    }

    @Override
    public E element()
    {
        if (isEmpty()){
            throw new NoSuchElementException();
        } else {
            return get(0);
        }
    }

    @Override
    public E peek()
    {
        if (isEmpty()){
            return null;
        } else {
            return get(0);
        }
    }
    
    /**
     * Instantinates queue compatible with all versions of Android
     * @return
     */
    @TargetApi(Build.VERSION_CODES.GINGERBREAD) 
    public static <E> Queue<E> getInstance()
    {
        Queue<E> queue;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD){
            queue = new ArrayDeque<E>();
        } else { 
            queue = new SimpleQueue<E>();
        }
        return queue;
    }
}