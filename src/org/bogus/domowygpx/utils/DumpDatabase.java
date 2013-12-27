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

public class DumpDatabase
{
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
        pw.print("<table name=\"");
        pw.print(escapeAttr(tableName));
        pw.println("\">");

        
        Cursor meta = database.rawQuery("PRAGMA table_info(" + tableName + ")", null);
        Cursor data = database.rawQuery("select * from " + tableName, null);
        try{
            final int colCount = data.getColumnCount();
            final String[] colNames = data.getColumnNames();
            final int[] colTypes = new int[colCount]; 

            {
                int nameIdx = meta.getColumnIndexOrThrow("name");
                int typeIdx = meta.getColumnIndexOrThrow("type");
                while (meta.moveToNext()){
                    final String colName = meta.getString(nameIdx);
                    final String colType = meta.getString(typeIdx);
                    pw.print("<meta name=\"");
                    pw.print(escapeAttr(colName));
                    pw.print("\" type=\"");
                    pw.print(escapeAttr(colType));
                    pw.println("\">");
                    for (int i=0; i<colCount; i++){
                        if (colNames[i].equals(colName)){
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
                            colTypes[i] = res;
                            break;
                        }
                    }
                }
            }

            if (data.moveToFirst()){
                do{
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
            }
        }finally{
            data.close();
            meta.close();
        }
        pw.println("</table>");
    }
    
    public boolean dumpDatabase(SQLiteDatabase database, File target)
    throws IOException
    {
        Cursor tables = database.rawQuery("select name from sqlite_master where type = 'table'", null);
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
}
