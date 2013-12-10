package org.bogus.domowygpx.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.StringTokenizer;


public class LocationUtils
{
    /**
     * Constant used to specify formatting of a latitude or longitude
     * in the form "[+-]DDD.DDDDD where D indicates degrees.
     */
    public static final int FORMAT_DEGREES = 0;

    /**
     * Constant used to specify formatting of a latitude or longitude
     * in the form "[+-]DDD:MM.MMMMM" where D indicates degrees and
     * M indicates minutes of arc (1 minute = 1/60th of a degree).
     */
    public static final int FORMAT_MINUTES = 1;

    /**
     * Constant used to specify formatting of a latitude or longitude
     * in the form "DDD:MM:SS.SSSSS" where D indicates degrees, M
     * indicates minutes of arc, and S indicates seconds of arc (1
     * minute = 1/60th of a degree, 1 second = 1/3600th of a degree).
     */
    public static final int FORMAT_SECONDS = 2;
 
    /**
     * Constant used to specify formatting of a latitude
     * in the form "N|S DDD° MM.MMM'" where D indicates degrees and M
     * indicates minutes of arc.
     */
    public static final int FORMAT_WGS84_LAT = 3;
    /**
     * Constant used to specify formatting of a longitude
     * in the form "E|W DDD° MM.MMM'" where D indicates degrees and M
     * indicates minutes of arc.
     */
    public static final int FORMAT_WGS84_LON = 4;
    
    public static Pair<Integer, Double> parseLocation(CharSequence coordinate)
    {
        // IllegalArgumentException if bad syntax
        if (coordinate == null) {
            throw new NullPointerException("coordinate");
        }

        char startCh = coordinate.charAt(0);
        if (startCh == 'N' || startCh == 'S'){
            return parseWgs84Latitude(coordinate);
        }
        if (startCh == 'E' || startCh == 'W'){
            return parseWgs84Longitude(coordinate);
        }
        
        boolean negative = false;
        if (startCh == '-') {
            coordinate = coordinate.subSequence(1, coordinate.length());
            negative = true;
        }

        StringTokenizer st = new StringTokenizer(coordinate.toString(), ":");
        int tokens = st.countTokens();
        if (tokens < 1) {
            throw new IllegalArgumentException("coordinate=" + coordinate);
        }
        try {
            String degrees = st.nextToken();
            if (tokens == 1) {
                double val = Double.parseDouble(degrees);
                return Pair.makePair(FORMAT_DEGREES, negative ? -val : val);
            }

            String minutes = st.nextToken();
            int deg = Integer.parseInt(degrees);
            double min;
            double sec = 0.0;

            int format;
            if (st.hasMoreTokens()) {
                min = Integer.parseInt(minutes);
                String seconds = st.nextToken();
                sec = Double.parseDouble(seconds);
                format = FORMAT_SECONDS;
            } else {
                min = Double.parseDouble(minutes);
                format = FORMAT_MINUTES;
            }

            boolean isNegative180 = negative && (deg == 180) &&
                (min == 0) && (sec == 0);

            // deg must be in [0, 179] except for the case of -180 degrees
            if ((deg < 0.0) || (deg > 179 && !isNegative180)) {
                throw new IllegalArgumentException("coordinate=" + coordinate);
            }
            if (min < 0 || min > 59) {
                throw new IllegalArgumentException("coordinate=" +
                        coordinate);
            }
            if (sec < 0 || sec > 59) {
                throw new IllegalArgumentException("coordinate=" +
                        coordinate);
            }

            double val = deg + min/60.0 + sec/3600.0;
            return Pair.makePair(format, negative ? -val : val);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("coordinate=" + coordinate);
        }
    }

    private static Pair<Integer, Double> parseWgs84Latitude(CharSequence coordinate)
    {
        final char startCh = coordinate.charAt(0);
        final boolean isLat = startCh == 'N' || startCh == 'S';
        if (!isLat){
            throw new IllegalArgumentException("coordinate=" + coordinate);
        }
        final String coordinateStr = coordinate.toString();
        final double result = parseWgs84(coordinateStr);
        return Pair.makePair(FORMAT_WGS84_LAT, result);
    }

    private static Pair<Integer, Double> parseWgs84Longitude(CharSequence coordinate)
    {
        final char startCh = coordinate.charAt(0);
        final boolean isLon = startCh == 'E' || startCh == 'W';
        if (!isLon){
            throw new IllegalArgumentException("coordinate=" + coordinate);
        }
        return Pair.makePair(FORMAT_WGS84_LON, parseWgs84(coordinate.toString()));
    }

    private static double parseWgs84(String coordinate0)
    {
        try{
            coordinate0 = coordinate0.trim();
            char startCh = coordinate0.charAt(0);
            boolean negative = startCh == 'W' || startCh == 'S';
            String coordinate = coordinate0.substring(1).trim();
            int degIdx = coordinate.indexOf('°');
            if (degIdx == -1 || degIdx == coordinate.length()-1){
                if (degIdx == coordinate.length()-1){
                    coordinate = coordinate.substring(0, coordinate.length()-1);
                }
                int deg = Integer.parseInt(coordinate);
                boolean isNegative180 = negative && (deg == 180);
                if ((deg < 0) || (deg > 179 && !isNegative180)) {
                    throw new IllegalArgumentException("coordinate=" + coordinate0);
                }
                return negative ? -deg : deg;
            }
            int deg = Integer.parseInt(coordinate.substring(0, degIdx).trim());
            int minIdx = coordinate.indexOf('\'');
            if (minIdx == coordinate.length()-1){
                coordinate = coordinate.substring(degIdx+1, coordinate.length()-1);
            } else
            if (minIdx != -1){
                throw new IllegalArgumentException("coordinate=" + coordinate0);
            } else {
                coordinate = coordinate.substring(degIdx+1);
            }
            double min = Double.parseDouble(coordinate.trim());
            boolean isNegative180 = negative && (deg == 180) && (min == 0);
            // deg must be in [0, 179] except for the case of -180 degrees
            if ((deg < 0.0) || (deg > 179 && !isNegative180) || min < 0.0 || min >= 60.0) {
                throw new IllegalArgumentException("coordinate=" + coordinate0);
            }
            
            double val = deg*3600.0 + min*60.0;
            val /= 3600.0;
            return negative ? -val : val;
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("coordinate=" + coordinate0);
        }
    }
    
    /**
     * Converts a coordinate to a String representation. The outputType
     * may be one of FORMAT_DEGREES, FORMAT_MINUTES, or FORMAT_SECONDS.
     * The coordinate must be a valid double between -180.0 and 180.0.
     *
     * @throws IllegalArgumentException if coordinate is less than
     * -180.0, greater than 180.0, or is not a number.
     * @throws IllegalArgumentException if outputType is not one of
     * FORMAT_DEGREES, FORMAT_MINUTES, or FORMAT_SECONDS.
     */
    public static String format(double coordinate, int outputType) {
        if (coordinate < -180.0 || coordinate > 180.0 ||
            Double.isNaN(coordinate)) {
            throw new IllegalArgumentException("coordinate=" + coordinate);
        }
        
        if (outputType == FORMAT_WGS84_LAT || outputType == FORMAT_WGS84_LON){
            return formatWGS84(coordinate, outputType);
        } else 
        if ((outputType != FORMAT_DEGREES) &&
            (outputType != FORMAT_MINUTES) &&
            (outputType != FORMAT_SECONDS)) {
            throw new IllegalArgumentException("outputType=" + outputType);
        }

        StringBuilder sb = new StringBuilder();

        // Handle negative values
        if (coordinate < 0) {
            sb.append('-');
            coordinate = -coordinate;
        }
        
        if (outputType == FORMAT_MINUTES || outputType == FORMAT_SECONDS) {
            int degrees = (int) Math.floor(coordinate);
            sb.append(degrees);
            sb.append(':');
            coordinate -= degrees;
            coordinate *= 60.0;
            if (outputType == FORMAT_SECONDS) {
                int minutes = (int) Math.floor(coordinate);
                if (minutes < 10){
                    sb.append('0');
                }
                sb.append(minutes);
                sb.append(':');
                coordinate -= minutes;
                coordinate *= 60.0;
                if (Math.abs(coordinate) > 1e-2){ 
                    final DecimalFormat df = getDecimalFormat("#.##");
                    final String s = df.format(coordinate);
                    if (!"0".equals(s)){
                        sb.append(s);
                    }
                }
            } else {
                // MINUTES
                final DecimalFormat df = getDecimalFormat("00.###");
                final String s = df.format(coordinate);
                sb.append(s);
            }
        } else {
            // DEGREES
            final DecimalFormat df = getDecimalFormat("##0.#####");
            sb.append(df.format(coordinate));
        }

        return sb.toString();
    }

    protected static DecimalFormat getDecimalFormat(String format)
    {
        final DecimalFormat df = new DecimalFormat(format, new DecimalFormatSymbols(Locale.US));
        return df;
    }
 
    private static String formatWGS84(double coordinate, int type) {
        final DecimalFormat df = getDecimalFormat("00.###");
        
        StringBuilder sb = new StringBuilder(16);
        boolean isNegative = coordinate < 0;
        if (isNegative){
            coordinate = -coordinate;
            if (type == FORMAT_WGS84_LAT){
                sb.append('S');
            } else {
                sb.append('W');
            }
        } else {
            if (type == FORMAT_WGS84_LAT){
                sb.append('N');
            } else {
                sb.append('E');
            }
        }
        sb.append(' ');
        int degrees = (int) Math.floor(coordinate);
        sb.append(degrees);
        sb.append('°');
        coordinate -= degrees;
        if (Math.abs(coordinate) > 1e-7){ 
            coordinate *= 60.0;
            sb.append(' ');
            sb.append(df.format(coordinate));
            sb.append('\'');
        }
        
        return sb.toString();
    }    
}
