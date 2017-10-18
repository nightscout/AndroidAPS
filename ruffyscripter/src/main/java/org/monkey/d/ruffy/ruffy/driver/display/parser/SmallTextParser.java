package org.monkey.d.ruffy.ruffy.driver.display.parser;

import org.monkey.d.ruffy.ruffy.driver.display.Symbol;
import org.monkey.d.ruffy.ruffy.driver.display.Token;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Created by fishermen21 on 20.05.17.
 */

public class SmallTextParser {

    private static String[][] numbers = {
            {
                    " ███ ",
                    "█   █",
                    "█  ██",
                    "█ █ █",
                    "██  █",
                    "█   █",
                    " ███ "
            },
            {
                    "  █  ",
                    " ██  ",
                    "  █  ",
                    "  █  ",
                    "  █  ",
                    "  █  ",
                    " ███ "
            },
            {
                    " ███ ",
                    "█   █",
                    "    █",
                    "   █ ",
                    "  █  ",
                    " █   ",
                    "█████"
            },
            {
                    "█████",
                    "   █ ",
                    "  █  ",
                    "   █ ",
                    "    █",
                    "█   █",
                    " ███ "
            },
            {
                    "   █ ",
                    "  ██ ",
                    " █ █ ",
                    "█  █ ",
                    "█████",
                    "   █ ",
                    "   █ "

            },
            {
                    "█████",
                    "█    ",
                    "████ ",
                    "    █",
                    "    █",
                    "█   █",
                    " ███ "
            },
            {
                    "  ██ ",
                    " █   ",
                    "█    ",
                    "████ ",
                    "█   █",
                    "█   █",
                    " ███ "
            },
            {
                    "█████",
                    "    █",
                    "   █ ",
                    "  █  ",
                    " █   ",
                    " █   ",
                    " █   ",
            },
            {
                    " ███ ",
                    "█   █",
                    "█   █",
                    " ███ ",
                    "█   █",
                    "█   █",
                    " ███ "
            },
            {
                    " ███ ",
                    "█   █",
                    "█   █",
                    " ████",
                    "    █",
                    "   █ ",
                    " ██  "
            }
    };

    private static Map<Character, String[]> letters = new HashMap<Character, String[]>();

    static {
        letters.put('A', new String[]{
                "  █  ",
                " █ █ ",
                "█   █",
                "█████",
                "█   █",
                "█   █",
                "█   █"
        });
        letters.put('a', new String[]{
                " ███ ",
                "    █",
                " ████",
                "█   █",
                " ████"
        });
        letters.put('Ä', new String[]{
                "█   █",
                " ███ ",
                "█   █",
                "█   █",
                "█████",
                "█   █",
                "█   █"
        });
        letters.put('ă', new String[]{
                " █ █ ",
                "  █  ",
                "  █  ",
                " █ █ ",
                "█   █",
                "█████",
                "█   █"
        });
        letters.put('Á', new String[]{
                "   █ ",
                "  █  ",
                " ███ ",
                "█   █",
                "█████",
                "█   █",
                "█   █"
        });
        letters.put('á', new String[]{
                "   █ ",
                "  █  ",
                "  █  ",
                " █ █ ",
                "█   █",
                "█████",
                "█   █"
        });
        letters.put('ã', new String[]{
                " █  █",
                "█ ██ ",
                "  █  ",
                " █ █ ",
                "█   █",
                "█████",
                "█   █"
        });

        letters.put('æ', new String[]{
                " ████",
                "█ █  ",
                "█ █  ",
                "████ ",
                "█ █  ",
                "█ █  ",
                "█ ███"
        });

        letters.put('B', new String[]{
                "████ ",
                "█   █",
                "█   █",
                "████ ",
                "█   █",
                "█   █",
                "████ "
        });
        letters.put('C', new String[]{
                " ███ ",
                "█   █",
                "█    ",
                "█    ",
                "█    ",
                "█   █",
                " ███ "
        });
        letters.put('ć', new String[]{
                "   █ ",
                "  █  ",
                " ████",
                "█    ",
                "█    ",
                "█    ",
                " ████"
        });
        letters.put('č', new String[]{
                " █ █ ",
                "  █  ",
                " ████",
                "█    ",
                "█    ",
                "█    ",
                " ████"
        });
        letters.put('Ç', new String[]{
                " ████",
                "█    ",
                "█    ",
                "█    ",
                " ████",
                "  █  ",
                " ██  "
        });

        letters.put('D', new String[]{
                "███  ",
                "█  █ ",
                "█   █",
                "█   █",
                "█   █",
                "█  █ ",
                "███  "
        });
        letters.put('E', new String[]{
                "█████",
                "█    ",
                "█    ",
                "████ ",
                "█    ",
                "█    ",
                "█████"
        });
        letters.put('É', new String[]{
                "   █ ",
                "  █  ",
                "█████",
                "█    ",
                "████ ",
                "█    ",
                "█████"
        });
        letters.put('Ê', new String[]{
                "  █  ",
                " █ █ ",
                "█████",
                "█    ",
                "████ ",
                "█    ",
                "█████"
        });
        letters.put('ę', new String[]{
                "█████",
                "█    ",
                "████ ",
                "█    ",
                "█████",
                "  █  ",
                "  ██ "
        });
        letters.put('F', new String[]{
                "█████",
                "█    ",
                "█    ",
                "████ ",
                "█    ",
                "█    ",
                "█    "
        });
        letters.put('G', new String[]{
                " ███ ",
                "█   █",
                "█    ",
                "█ ███",
                "█   █",
                "█   █",
                " ████"
        });
        letters.put('H', new String[]{
                "█   █",
                "█   █",
                "█   █",
                "█████",
                "█   █",
                "█   █",
                "█   █"
        });
        letters.put('I', new String[]{
                " ███ ",
                "  █  ",
                "  █  ",
                "  █  ",
                "  █  ",
                "  █  ",
                " ███ "
        });
        letters.put('i', new String[]{
                " █ ",
                "   ",
                "██ ",
                " █ ",
                " █ ",
                " █ ",
                "███"
        });
        letters.put('í', new String[]{
                "  █",
                " █ ",
                "███",
                " █ ",
                " █ ",
                " █ ",
                "███"
        });
        letters.put('İ', new String[]{
                " █ ",
                "   ",
                "███",
                " █ ",
                " █ ",
                " █ ",
                "███"
        });

        letters.put('J', new String[]{
                "  ███",
                "   █ ",
                "   █ ",
                "   █ ",
                "   █ ",
                "█  █ ",
                " ██  "
        });
        letters.put('K', new String[]{
                "█   █",
                "█  █ ",
                "█ █  ",
                "██   ",
                "█ █  ",
                "█  █ ",
                "█   █"
        });
        letters.put('L', new String[]{
                "█    ",
                "█    ",
                "█    ",
                "█    ",
                "█    ",
                "█    ",
                "█████"
        });
        letters.put('ł', new String[]{
                " █   ",
                " █   ",
                " █ █ ",
                " ██  ",
                "██   ",
                " █   ",
                " ████"
        });
        letters.put('M', new String[]{
                "█   █",
                "██ ██",
                "█ █ █",
                "█ █ █",
                "█   █",
                "█   █",
                "█   █"
        });
        letters.put('N', new String[]{
                "█   █",
                "█   █",
                "██  █",
                "█ █ █",
                "█  ██",
                "█   █",
                "█   █"
        });
        letters.put('Ñ', new String[]{
                " █  █",
                "█ ██ ",
                "█   █",
                "██  █",
                "█ █ █",
                "█  ██",
                "█   █"
        });
        letters.put('ň', new String[]{
                " █ █ ",
                "  █  ",
                "█   █",
                "██  █",
                "█ █ █",
                "█  ██",
                "█   █"
        });

        letters.put('O', new String[]{
                " ███ ",
                "█   █",
                "█   █",
                "█   █",
                "█   █",
                "█   █",
                " ███ "
        });
        letters.put('Ö', new String[]{
                "█   █",
                " ███ ",
                "█   █",
                "█   █",
                "█   █",
                "█   █",
                " ███ "
        });
        letters.put('ó', new String[]{
                "   █ ",
                "  █  ",
                " ███ ",
                "█   █",
                "█   █",
                "█   █",
                " ███ "
        });
        letters.put('ø', new String[]{
                "     █",
                "  ███ ",
                " █ █ █",
                " █ █ █",
                " █ █ █",
                "  ███ ",
                " █    "
        });
        letters.put('ő', new String[]{
                " █  █",
                "█  █ ",
                " ███ ",
                "█   █",
                "█   █",
                "█   █",
                " ███ "
        });

        letters.put('P', new String[]{
                "████ ",
                "█   █",
                "█   █",
                "████ ",
                "█    ",
                "█    ",
                "█    "
        });
        letters.put('Q', new String[]{
                " ███ ",
                "█   █",
                "█   █",
                "█   █",
                "█ █ █",
                "█  █ ",
                " ██ █"
        });
        letters.put('R', new String[]{
                "████ ",
                "█   █",
                "█   █",
                "████ ",
                "█ █  ",
                "█  █ ",
                "█   █"
        });
        letters.put('S', new String[]{
                " ████",
                "█    ",
                "█    ",
                " ███ ",
                "    █",
                "    █",
                "████ "
        });
        letters.put('ś', new String[]{
                "   █ ",
                "  █  ",
                " ████",
                "█    ",
                " ███ ",
                "    █",
                "████ "
        });
        letters.put('š', new String[]{
                " █ █ ",
                "  █  ",
                " ████",
                "█    ",
                " ███ ",
                "    █",
                "████ "
        });

        letters.put('T', new String[]{
                "█████",
                "  █  ",
                "  █  ",
                "  █  ",
                "  █  ",
                "  █  ",
                "  █  "
        });
        letters.put('U', new String[]{
                "█   █",
                "█   █",
                "█   █",
                "█   █",
                "█   █",
                "█   █",
                " ███ "
        });
        letters.put('u', new String[]{
                "█   █",
                "█   █",
                "█   █",
                "█  ██",
                " ██ █"
        });
        letters.put('Ü', new String[]{
                "█   █",
                "     ",
                "█   █",
                "█   █",
                "█   █",
                "█   █",
                " ███ "
        });
        letters.put('ú', new String[]{
                "   █ ",
                "  █  ",
                "█   █",
                "█   █",
                "█   █",
                "█   █",
                " ███ "
        });
        letters.put('ů', new String[]{
                "  █  ",
                " █ █ ",
                "█ █ █",
                "█   █",
                "█   █",
                "█   █",
                " ███ "
        });
        letters.put('V', new String[]{
                "█   █",
                "█   █",
                "█   █",
                "█   █",
                "█   █",
                " █ █ ",
                "  █  "
        });
        letters.put('W', new String[]{
                "█   █",
                "█   █",
                "█   █",
                "█ █ █",
                "█ █ █",
                "█ █ █",
                " █ █ "
        });
        letters.put('X', new String[]{
                "█   █",
                "█   █",
                " █ █ ",
                "  █  ",
                " █ █ ",
                "█   █",
                "█   █"
        });
        letters.put('Y', new String[]{
                "█   █",
                "█   █",
                "█   █",
                " █ █ ",
                "  █  ",
                "  █  ",
                "  █  "
        });
        letters.put('ý', new String[]{
                "   █ ",
                "█ █ █",
                "█   █",
                " █ █ ",
                "  █  ",
                "  █  ",
                "  █  "
        });
        letters.put('Z', new String[]{
                "█████",
                "    █",
                "   █ ",
                "  █  ",
                " █   ",
                "█    ",
                "█████"
        });
        letters.put('ź', new String[]{
                "  █  ",
                "█████",
                "    █",
                "  ██ ",
                " █   ",
                "█    ",
                "█████"
        });
        letters.put('ž', new String[]{
                " █ █ ",
                "  █  ",
                "█████",
                "   █ ",
                "  █  ",
                " █   ",
                "█████"
        });

        /// russian letters (not in alphabetical order):
        letters.put('б', new String[]{
                "█████",
                "█    ",
                "█    ",
                "████ ",
                "█   █",
                "█   █",
                "████ "
        });
        letters.put('ъ', new String[]{
                "██  ",
                " █  ",
                " █  ",
                " ██ ",
                " █ █",
                " █ █",
                " ██ "
        });
        letters.put('м', new String[]{
                "█   █",
                "██ ██",
                "█ █ █",
                "█   █",
                "█   █",
                "█   █",
                "█   █"
        });
        letters.put('л', new String[]{
                " ████",
                " █  █",
                " █  █",
                " █  █",
                " █  █",
                " █  █",
                "██  █"
        });
        letters.put('ю', new String[]{
                "█  █ ",
                "█ █ █",
                "█ █ █",
                "███ █",
                "█ █ █",
                "█ █ █",
                "█  █ "
        });
        letters.put('а', new String[]{
                "  █  ",
                " █ █ ",
                "█   █",
                "█   █",
                "█████",
                "█   █",
                "█   █"
        });
        letters.put('п', new String[]{
                "█████",
                "█   █",
                "█   █",
                "█   █",
                "█   █",
                "█   █",
                "█   █"
        });
        letters.put('я', new String[]{
                " ████",
                "█   █",
                "█   █",
                " ████",
                "  █ █",
                " █  █",
                "█   █"
        });
        letters.put('й', new String[]{
                " █ █ ",
                "  █  ",
                "█   █",
                "█  ██",
                "█ █ █",
                "██  █",
                "█   █"
        });
        letters.put('д', new String[]{
                "  ██ ",
                " █ █ ",
                " █ █ ",
                "█  █ ",
                "█  █ ",
                "█████",
                "█   █"
        });
        letters.put('ж', new String[]{
                "█ █ █",
                "█ █ █",
                " ███ ",
                " ███ ",
                "█ █ █",
                "█ █ █",
                "█ █ █"
        });
        letters.put('ы', new String[]{
                "█   █",
                "█   █",
                "█   █",
                "██  █",
                "█ █ █",
                "█ █ █",
                "██  █"
        });
        letters.put('у', new String[]{
                "█   █",
                "█   █",
                "█   █",
                " ███ ",
                "  █  ",
                " █   ",
                "█    "
        });
        letters.put('ч', new String[]{
                " █   █",
                " █   █",
                " █   █",
                " █  ██",
                "  ██ █",
                "     █",
                "     █"
        });
        letters.put('з', new String[]{
                "  ███ ",
                " █   █",
                "     █",
                "   ██ ",
                "     █",
                " █   █",
                "  ███ "
        });
        letters.put('ц', new String[]{
                "█  █ ",
                "█  █ ",
                "█  █ ",
                "█  █ ",
                "█  █ ",
                "█████",
                "    █"
        });
        letters.put('и', new String[]{
                "█   █",
                "█  ██",
                "█ █ █",
                "█ █ █",
                "█ █ █",
                "██  █",
                "█   █"
        });

        /// GREEK LETTERS (out of order)
        letters.put('Σ', new String[]{
                "█████",
                "█    ",
                " █   ",
                "  █  ",
                " █   ",
                "█    ",
                "█████"
        });
        letters.put('Δ', new String[]{
                "  █  ",
                "  █  ",
                " █ █ ",
                " █ █ ",
                "█   █",
                "█   █",
                "█████"
        });
        letters.put('Φ', new String[]{
                "  █  ",
                " ███ ",
                "█ █ █",
                "█ █ █",
                "█ █ █",
                " ███ ",
                "  █  "
        });
        letters.put('Λ', new String[]{
                "  █  ",
                " █ █ ",
                " █ █ ",
                "█   █",
                "█   █",
                "█   █",
                "█   █"
        });
        letters.put('Ω', new String[]{
                " ███ ",
                "█   █",
                "█   █",
                "█   █",
                "█   █",
                " █ █ ",
                "██ ██"
        });
        letters.put('υ', new String[]{
                "█   █",
                "█   █",
                "█   █",
                " ███ ",
                "  █  ",
                "  █  ",
                "  █  "
        });
        letters.put('Θ', new String[]{
                " ███ ",
                "█   █",
                "█   █",
                "█ █ █",
                "█   █",
                "█   █",
                " ███ "
        });


    }


    private static Map<Symbol, String[]> symbols = new HashMap<Symbol, String[]>();

    static {
        symbols.put(Symbol.CLOCK, new String[]{
                "  ███  ",
                " █ █ █ ",
                "█  █  █",
                "█  ██ █",
                "█     █",
                " █   █ ",
                "  ███  "
        });
        symbols.put(Symbol.UNITS_PER_HOUR, new String[]{
                "█  █    █ █   ",
                "█  █   █  █   ",
                "█  █   █  █ █ ",
                "█  █  █   ██ █",
                "█  █  █   █  █",
                "█  █ █    █  █",
                " ██  █    █  █"
        });
        symbols.put(Symbol.LOCK_CLOSED, new String[]{
                " ███ ",
                "█   █",
                "█   █",
                "█████",
                "██ ██",
                "██ ██",
                "█████"
        });
        symbols.put(Symbol.LOCK_OPENED, new String[]{
                " ███     ",
                "█   █    ",
                "█   █    ",
                "    █████",
                "    ██ ██",
                "    ██ ██",
                "    █████"
        });
        symbols.put(Symbol.CHECK, new String[]{
                "    █",
                "   ██",
                "█ ██ ",
                "███  ",
                " █   ",
                "     ",
                "     "
        });
        symbols.put(Symbol.DIVIDE, new String[]{
                "     ",
                "    █",
                "   █ ",
                "  █  ",
                " █   ",
                "█    ",
                "     "
        });
        symbols.put(Symbol.LOW_BAT, new String[]{
                "██████████ ",
                "█        █ ",
                "███      ██",
                "███       █",
                "███      ██",
                "█        █ ",
                "██████████ "

        });
        symbols.put(Symbol.LOW_INSULIN, new String[]{
                "█████████████    ",
                "█  █  █  █ ██ ███",
                "█  █  █  █ ████ █",
                "█          ████ █",
                "█          ████ █",
                "█          ██ ███",
                "█████████████    "
        });
        symbols.put(Symbol.NO_INSULIN, new String[]{
                "█████████████    ",
                "█  █  █  █  █ ███",
                "█  █  █  █  ███ █",
                "█             █ █",
                "█           ███ █",
                "█           █ ███",
                "█████████████    "
        });
        symbols.put(Symbol.CALENDAR, new String[]{
                "███████",
                "█     █",
                "███████",
                "█ █ █ █",
                "███████",
                "█ █ ███",
                "███████"
        });
        symbols.put(Symbol.DOT, new String[]{
                "     ",
                "     ",
                "     ",
                "     ",
                "     ",
                " ██  ",
                " ██  "
        });
        symbols.put(Symbol.SEPERATOR, new String[]{
                "     ",
                " ██  ",
                " ██  ",
                "     ",
                " ██  ",
                " ██  ",
                "     "
        });
        symbols.put(Symbol.ARROW, new String[]{
                "    █   ",
                "    ██  ",
                "███████ ",
                "████████",
                "███████ ",
                "    ██  ",
                "    █   "
        });
        symbols.put(Symbol.DOWN, new String[]{
                "  ███  ",
                "  ███  ",
                "  ███  ",
                "███████",
                " █████ ",
                "  ███  ",
                "   █   "
        });
        symbols.put(Symbol.UP, new String[]{
                "   █   ",
                "  ███  ",
                " █████ ",
                "███████",
                "  ███  ",
                "  ███  ",
                "  ███  "

        });
        symbols.put(Symbol.SUM, new String[]{
                "██████",
                "█    █",
                " █    ",
                "  █   ",
                " █    ",
                "█    █",
                "██████",
        });
        symbols.put(Symbol.BOLUS, new String[]{
                " ███   ",
                " █ █   ",
                " █ █   ",
                " █ █   ",
                " █ █   ",
                " █ █   ",
                "██ ████"
        });
        symbols.put(Symbol.MULTIWAVE, new String[]{
                "███     ",
                "█ █     ",
                "█ █     ",
                "█ ██████",
                "█      █",
                "█      █",
                "█      █"
        });
        symbols.put(Symbol.EXTENDED_BOLUS, new String[]{
                "███████ ",
                "█     █ ",
                "█     █ ",
                "█     █ ",
                "█     █ ",
                "█     █ ",
                "█     ██",
        });
        symbols.put(Symbol.SPEAKER, new String[]{
                "   ██ ",
                "  █ █ ",
                "██  █ ",
                "██  ██",
                "██  █ ",
                "  █ █ ",
                "   ██ "
        });
        symbols.put(Symbol.ERROR, new String[]{
                "  ███  ",
                " █████ ",
                "██ █ ██",
                "███ ███",
                "██ █ ██",
                " █████ ",
                "  ███  "
        });
        symbols.put(Symbol.WARNING, new String[]{
                "   █   ",
                "  ███  ",
                "  █ █  ",
                " █ █ █ ",
                " █   █ ",
                "█  █  █",
                "███████"
        });
        symbols.put(Symbol.BRACKET_LEFT, new String[]{
                "   █ ",
                "  █  ",
                " █   ",
                " █   ",
                " █   ",
                "  █  ",
                "   █ ",
                "     "
        });
        symbols.put(Symbol.BRACKET_RIGHT, new String[]{
                " █   ",
                "  █  ",
                "   █ ",
                "   █ ",
                "   █ ",
                "  █  ",
                " █   ",
                "     "
        });
        symbols.put(Symbol.PERCENT, new String[]{
                "██   ",
                "██  █",
                "   █ ",
                "  █  ",
                " █   ",
                "█  ██",
                "   ██"
        });
        symbols.put(Symbol.BASAL, new String[]{
                "  ████  ",
                "  █  ███",
                "███  █ █",
                "█ █  █ █",
                "█ █  █ █",
                "█ █  █ █",
                "█ █  █ █"
        });
        symbols.put(Symbol.MINUS, new String[]{
                "     ",
                "     ",
                "█████",
                "     "
        });
        symbols.put(Symbol.WARANTY, new String[]{
                " ███ █  ",
                "  ██  █ ",
                " █ █   █",
                "█      █",
                "█   █ █ ",
                " █  ██  ",
                "  █ ███ "
        });
    }

    private static LinkedList<Pattern> pattern = new LinkedList<>();
    static
    {
        for(Symbol s : symbols.keySet())
        {
            String[] patternString = symbols.get(s);
            Pattern p = new SymbolPattern(s,patternString,8);
            pattern.add(p);
        }
        for(int i = 0; i < 10;i++)
        {
            String[] patternString = numbers[i];
            Pattern p = new NumberPattern(i,patternString,8);
            pattern.add(p);
        }
        for(Character c : letters.keySet())
        {
            String[] patternString = letters.get(c);
            Pattern p = new CharacterPattern(c,patternString,8);
            pattern.add(p);
        }
    }
    public static Token findToken(byte[][] display, int which, int x) {
        for(Pattern p : pattern)
        {
            Token t = p.match(display,which,x);
            if(t!=null)
                return t;
        }
        return null;
    }
}