package org.monkey.d.ruffy.ruffy.driver.display.parser;

import org.monkey.d.ruffy.ruffy.driver.display.Token;

/**
 * Created by fishermen21 on 21.05.17.
 */

public abstract class Pattern {
    private final byte[][] pattern;
    private final int shiftable;

    public Pattern(String[] patternString, int blocksize) {
        this.shiftable = blocksize - patternString.length;

        this.pattern = new byte[blocksize/8][patternString[0].length()];
        for(int row = 0; row < patternString.length;row+=8)
        {
            for(int c = 0; c < patternString[row].length();c++)
            {
                byte b = 0;
                for(int br = 0; br < 8; br++)
                {
                    if (row+br >= patternString.length)
                        break;
                    if (patternString[row+br].charAt(c) == '█')
                        b |= 1 << br;
                }
                pattern[row/8][c] = b;
            }
        }
    }

    public Token match(byte[][] display, int which, int x)
    {
        int s = 0;
        while(s<=shiftable)
        {
            boolean run = true;
            for(int r = 0;run && r < pattern.length;r++)
            {
                if(which+r>3){
                    run=false;
                    break;
                }

                for(int c = 0;run && c < pattern[0].length; c++)
                {
                    byte compare = ((byte)(pattern[r][c]<<s));
                    if(r>0)
                    {
                        short cs = pattern[r][c];
                        cs = ((short)(cs << 8));
                        cs |= pattern[r-1][c]&0xFF;
                        cs = ((short)(cs << s));
                        compare = (byte) (cs >> 8);
                    }
                    if(x+c >= 96 || display[which+r][x+c] != compare)
                        run = false;
                }
            }
            if(run)
            {
                for(int r = 0;run && r < pattern.length;r++) {
                    for (int c = 0; run && c < pattern[0].length; c++) {
                        display[which+r][x + c] = 0;
                    }
                }
                return new Token(this, which, x);//pattern found
            }
            s++;
        }
        return null;
    }

    public int getWidth() {
        return pattern[0].length;
    }
}
