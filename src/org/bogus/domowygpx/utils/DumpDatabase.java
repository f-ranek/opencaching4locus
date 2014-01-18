package org.bogus.domowygpx.utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;
import android.util.Log;

public class DumpDatabase
{
    private final static String LOG_TAG = "DumpDatabase";
    
    /** Value returned by {@link #getType(int)} if the specified column is null */
    static final int FIELD_TYPE_NULL = 0;

    /** Value returned by {@link #getType(int)} if the specified  column type is integer */
    static final int FIELD_TYPE_INTEGER = 1;

    /** Value returned by {@link #getType(int)} if the specified column type is float */
    static final int FIELD_TYPE_FLOAT = 2;

    /** Value returned by {@link #getType(int)} if the specified column type is string */
    static final int FIELD_TYPE_STRING = 3;

    /** Value returned by {@link #getType(int)} if the specified column type is blob */
    static final int FIELD_TYPE_BLOB = 4;

    protected String escapeAttr(String str)
    {
        return str.replace("\"", "&quot;");
    }
    protected String escapeData(String str)
    {
        return str.replace("<", "&lt;").replace(">", "&gt;");
    }
    
    protected void dumpTableData(SQLiteDatabase database, String tableName, PrintWriter pw)
    throws IOException
    {
        String sql = null;
        int count = 0;
        try{
            pw.print("<table name=\"");
            pw.print(escapeAttr(tableName));
            pw.println("\">");
    
            if (!database.isReadOnly()){
                database.beginTransaction();
            }
            Cursor meta = null;
            Cursor data = null;
            try{
                count = 0;
                sql = "PRAGMA table_info(" + tableName + ")";
                meta = database.rawQuery(sql, null);
                //
                final int colCount = meta.getCount();
                final int[] colTypes = new int[colCount]; 
                final StringBuilder pks = new StringBuilder();
                final StringBuilder columns = new StringBuilder();
                final String[] colNames = new String[colCount];
                {
                    int nameIdx = meta.getColumnIndexOrThrow("name");
                    int typeIdx = meta.getColumnIndexOrThrow("type");
                    int pkIdx = meta.getColumnIndexOrThrow("pk");
                    while (meta.moveToNext()){
                        final String colName = meta.getString(nameIdx);
                        final String colType = meta.getString(typeIdx);
                        final int pk = meta.getInt(pkIdx);
                        if (pk > 0){
                            if (pks.length() != 0){
                                pks.append(',');
                            }
                            pks.append(pk);
                        }
                        pw.print("<meta name=\"");
                        pw.print(escapeAttr(colName));
                        pw.print("\" type=\"");
                        pw.print(escapeAttr(colType));
                        pw.println("\">");
                        if (columns.length() > 0){
                            columns.append(',');
                        }
                        columns.append(colName);
                        int res = 0;
                        if ("INTEGER".equals(colType) || "LONG".equals(colType)){
                            res = FIELD_TYPE_INTEGER; 
                        } else 
                        if ("FLOAT".equals(colType)){
                            res = FIELD_TYPE_FLOAT; 
                        } else 
                        if ("BLOB".equals(colType)){
                            res = FIELD_TYPE_BLOB; 
                        } else 
                        if ("STRING".equals(colType) || "TEXT".equals(colType)){
                            res = FIELD_TYPE_STRING; 
                        }
                        colTypes[count] = res;
                        colNames[count] = colName;
                        count++;
                    }
                }
                
                if (pks.length() == 0){
                    for (int i=1; i<=colCount; i++){
                        if (pks.length() != 0){
                            pks.append(',');
                        }
                        pks.append(i);
                    }
                }
                
                count = 0;
                do{
                    sql = "select " + columns + " from " + tableName + " order by " + pks
                            + " limit 15 offset " + count;
                    data = database.rawQuery(sql, null);
                    if (data.moveToFirst()){
                        do{
                            count++;
                            pw.println("<row>");
                            for (int i = 0; i<colCount; i++){
                                pw.print("\t<column name=\"");
                                pw.print(escapeAttr(colNames[i]));
                                if (!data.isNull(i)){
                                    pw.print("\">");
                                    final int type = colTypes[i];
                                    switch(type){
                                        case FIELD_TYPE_BLOB:
                                            byte[] blob = data.getBlob(i);
                                            pw.print(Base64.encodeToString(blob, Base64.DEFAULT));
                                            break; 
                                        case FIELD_TYPE_FLOAT:
                                            pw.print(data.getDouble(i));
                                            break; 
                                        case FIELD_TYPE_INTEGER: 
                                            pw.print(data.getLong(i));
                                            break; 
                                        case FIELD_TYPE_STRING: 
                                            String item = data.getString(i);
                                            pw.print(escapeData(item));
                                            break; 
                                    }
                                    pw.println("</column>");
                                } else {
                                    pw.println(" />");
                                }
                            }
                            pw.println("</row>");
                        }while(data.moveToNext());
                    } else {
                        break;
                    }
                    data.close();
                }while(true);
                if (!database.isReadOnly()){
                    database.setTransactionSuccessful();
                }
            }finally{
                if (data != null){
                    data.close();
                }
                if (meta != null){
                    meta.close();
                }
                if (!database.isReadOnly()){
                    database.endTransaction();
                }
            }
            pw.println("</table>");
            if (pw.checkError()){
                throw new IOException();
            }
        }catch(RuntimeException re){
            Log.e(LOG_TAG, "Failure while processing '" + sql + "' row=" + count, re);
            throw re;
        }
    }
    
    public boolean dumpDatabase(SQLiteDatabase database, File target)
    throws IOException
    {
        Cursor tables = database.rawQuery("select name from sqlite_master where type = 'table' order by name", null);
        if (tables.moveToFirst()){
            OutputStream os = null;
            try{
                os = new FileOutputStream(target);
                os = new BufferedOutputStream(os, 8192);
                final PrintWriter pw = new PrintWriter(new OutputStreamWriter(os, "UTF-8"), false); 
                pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                pw.print("<database version=\"");
                pw.print(database.getVersion());
                pw.println("\">");
                do{
                    String tableName = tables.getString(0);
                    dumpTableData(database, tableName, pw);
                }while(tables.moveToNext());
                pw.println("</database>");
                pw.flush();
                if (pw.checkError()){
                    throw new IOException();
                }
                os.flush();
            }finally{
                IOUtils.closeQuietly(os);
                tables.close();
            }
            return true;
        } else {
            return false;
        }
    }
    
    public List<File> getOfflineDatabaseFiles(File database)
    {
        List<File> result = new ArrayList<File>(4);
        if (database.exists()){
            result.add(database);
        }
        File f = new File(database.getPath() + "-journal");
        if (f.exists()){
            result.add(f);
        }
        f = new File(database.getPath() + "-wal");
        if (f.exists()){
            result.add(f);
        }
        f = new File(database.getPath() + "-shm");
        if (f.exists()){
            result.add(f);
        }
        return result;
    }
    
    public boolean isFileNameSuffixed(File file)
    {
        String name = file.getName();
        return name.endsWith("-journal") || name.endsWith("-wal") || name.endsWith("-shm"); 
    }
}
