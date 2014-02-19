package org.bogus.android;

import java.text.DecimalFormatSymbols;

import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.NumberKeyListener;

public class DecimalKeyListener extends NumberKeyListener
{
    private final char[] acceptedCharacters;
    private final char decimalChar;

    private final CharSequence decimalCharSequence;
    
    public DecimalKeyListener()
    {
        decimalChar = new DecimalFormatSymbols().getDecimalSeparator();
        if (decimalChar == '.'){
            acceptedCharacters = new char[11];
            decimalCharSequence = null;
        } else {
            acceptedCharacters = new char[12];
            acceptedCharacters[11] = decimalChar;
            decimalCharSequence = String.valueOf(decimalChar);
        }
        for (int i=0; i<10; i++){
            acceptedCharacters[i] = (char)('0' + i);
        }
        acceptedCharacters[10] = '.';
    }
    
    @Override
    protected char[] getAcceptedChars()
    {
        return acceptedCharacters;
    }

    @Override
    public int getInputType()
    {
        return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
    }

    @Override
    public CharSequence filter(CharSequence source, int start, int end,
                               Spanned dest, int dstart, int dend) {
        CharSequence out = super.filter(source, start, end, dest, dstart, dend);

        if (decimalChar == '.'){
            // no more filtering needed
            return out;
        }
        
        if (out != null) {
            source = out;
            start = 0;
            end = out.length();
        }

        // find any dots, and replace them with decimalChar
        SpannableStringBuilder stripped = null;
        for (int i = end - 1; i >= start; i--) {
            char c = source.charAt(i);
            if (c == '.'){
                if (stripped == null) {
                    stripped = new SpannableStringBuilder(source, start, end);
                }
    
                stripped.replace(i - start, i - start + 1, decimalCharSequence);
            }
        }

        if (stripped != null) {
            return stripped;
        } else if (out != null) {
            return out;
        } else {
            return null;
        }
    }    
}
