package org.bogus.domowygpx.services;

import java.io.File;
import java.io.IOException;
import java.util.List;

import android.content.Context;

public interface DumpableDatabase
{
    /**
     * Dumps database state to the temp storage, and returns database files content. This method can
     * be called from any thread.
     * @return
     */
    List<File> dumpDatabase(File rootDir)
    throws IOException;
    
    /**
     * This method is not called as part of the service lifecycle invocation.
     * This method is called on raw, uninitialized object, passing context as
     * a parameter
     * @param ctx
     * @return
     */
    List<File> getDatabaseFileNames(Context ctx);
}
