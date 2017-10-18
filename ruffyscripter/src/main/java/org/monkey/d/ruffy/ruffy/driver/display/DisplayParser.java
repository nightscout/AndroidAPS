package org.monkey.d.ruffy.ruffy.driver.display;

import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuFactory;
import org.monkey.d.ruffy.ruffy.driver.display.parser.LargeTextParser;
import org.monkey.d.ruffy.ruffy.driver.display.parser.SmallTextParser;

import java.util.LinkedList;

/**
 * Created by fishermen21 on 20.05.17.
 */

public class DisplayParser {
    private static boolean busy = false;

    public static void findMenu(final byte[][] pixels, final DisplayParserHandler handler) {
        if(busy)
        {
//            Log.v("Tokens","skipping frame, busy…");
            return;
        }
        busy = true;

        long t1 = System.currentTimeMillis();

        try {
            byte[][] display = new byte[4][96];
            for(int i = 0; i < 4;i++)
                for(int c = 0;c < 96;c++)
                    display[i][c]=pixels[i][95-c];

            LinkedList<Token>[] tokens = new LinkedList[]{
                    new LinkedList<Token>(),
                    new LinkedList<Token>(),
                    new LinkedList<Token>(),
                    new LinkedList<Token>()};
            int toks = 0;

            for(int i = 0; i< 4;i++)
            {

                for(int x = 0; x < 92;)//no token is supposed to be smaller then 5 columns
                {
                    Token t = null;

                    t = LargeTextParser.findToken(display,i,x);

                    if(t==null)
                    {
                        t = SmallTextParser.findToken(display, i, x);
                    }

                    if (t != null) {
                        tokens[i].add(t);
                        toks++;
                        x += t.getWidth()-1;
                    } else {
                        x++;
                    }
                }
            }

            long s = 0;
            for(int i = 0; i< 4;i++) {
                for (int x = 0; x < 96; x++) {
                    s+=display[i][x];
                }
            }
            if(s!=0)
            {
                print(display,"not empty");
            }

            Menu menu = MenuFactory.get(tokens);

            if(menu != null) {
//                Log.v("tokens", " needed " + ((((double) (System.currentTimeMillis() - t1)) / 1000d)) + " for parsing " + (menu != null ? menu.getType() : "no menu"));
                menu.setAttribute(MenuAttribute.DEBUG_TIMING, (((double) (System.currentTimeMillis() - t1)) / 1000d));
                handler.menuFound(menu);
            }
            else
                handler.noMenuFound();

            int nct = 0;
            for(int i=0;i<4;i++)nct+=tokens[i].size();
//            if(nct>0 && menu!= null)
//                Log.v("tokens",nct+" toks not consumed in "+menu.getType());
        }catch(Throwable e){e.printStackTrace();
//            Log.e("Tokens","error...",e);
 }
        finally {
            busy=false;
        }
    }

    private static String[] makeStrings(boolean[][][] pixels) {
        String[] display = new String[32];
        for (int w = 0; w < 4; w++) {
            for (int r = 0; r < 8; r++) {
                String line = "";
                for (int c = 0; c < 96; c++) {
                    line += pixels[w][r][c] ? "█" : " ";
                }
                display[(w*8)+r]=line;
            }
        }
        return display;
    }

  public static void print(byte[][] display, String text) {
//        Log.d("DisplayParser","////////////////////////////////////////////////////////////////////////////////////////////////");
//        Log.d("DisplayParser",text);

        for (int i = 0; i < 4; i++) {
            String[] lines = new String[]{"","","","","","","",""};
            for(int c = 0;c < 96;c++)
            {
                for(int r = 0; r < 8; r++) {
                    lines[r] += (display[i][c] & ((1 << r) & 0xFF)) != 0 ? "█" : " ";
                }
            }
//            for(int r = 0; r < 8; r++) {
//                Log.d("DisplayParser", lines[r]);
//            }
        }
//        Log.d("DisplayParser","////////////////////////////////////////////////////////////////////////////////////////////////");
    }
}

