package org.monkey.d.ruffy.ruffy.driver.display.menu;

import org.monkey.d.ruffy.ruffy.driver.display.Menu;
import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.monkey.d.ruffy.ruffy.driver.display.Symbol;
import org.monkey.d.ruffy.ruffy.driver.display.Token;
import org.monkey.d.ruffy.ruffy.driver.display.parser.CharacterPattern;
import org.monkey.d.ruffy.ruffy.driver.display.parser.NumberPattern;
import org.monkey.d.ruffy.ruffy.driver.display.parser.Pattern;
import org.monkey.d.ruffy.ruffy.driver.display.parser.SymbolPattern;

import java.util.LinkedList;

/**
 * Created by fishermen21 on 22.05.17.
 */

public class MenuFactory {
    public static Menu get(LinkedList<Token>[] tokens) {
        if(tokens[0].size()>0)
        {
            Pattern p = tokens[0].getFirst().getPattern();
            if(isSymbol(p,Symbol.CLOCK))
            {
                if(tokens[1].size()==1)
                {
                    if(isSymbol(tokens[1].get(0).getPattern(),Symbol.LARGE_STOP))
                        return makeStopMenu(tokens);
                }
                else
                {
                    for(Token t:tokens[0])
                        if(isSymbol(t.getPattern(),Symbol.MINUS))
                            return makeBasalSet(tokens);

                }
                return makeMainMenu(tokens);
            }
        }



        if(tokens[2].size()==1)
        {
            String s0 = parseString(tokens[0],true);
            Pattern p = tokens[2].get(0).getPattern();

            if(p instanceof NumberPattern && isSymbol(tokens[1].get(0).getPattern(),Symbol.LARGE_BASAL_SET))
            {
                return makeBasalTotal(tokens);
            }

            String s1 = parseString(tokens[1],true);

            if(isSymbol(p,Symbol.LARGE_STOP))
            {
                tokens[2].removeFirst();
                return new Menu(MenuType.STOP_MENU);
            }

            if(isSymbol(p,Symbol.LARGE_BOLUS))
            {
                tokens[2].removeFirst();
                return new Menu(MenuType.BOLUS_MENU);
            }

            if(isSymbol(p,Symbol.LARGE_EXTENDED_BOLUS))
            {
                tokens[2].removeFirst();
                return new Menu(MenuType.EXTENDED_BOLUS_MENU);
            }

            if(isSymbol(p,Symbol.LARGE_MULTIWAVE))
            {
                tokens[2].removeFirst();
                return new Menu(MenuType.MULTIWAVE_BOLUS_MENU);
            }

            if(isSymbol(p,Symbol.LARGE_TBR))
            {
                tokens[2].removeFirst();
                return new Menu(MenuType.TBR_MENU);
            }

            if(isSymbol(p,Symbol.LARGE_MY_DATA))
            {
                tokens[2].removeFirst();
                return new Menu(MenuType.MY_DATA_MENU);
            }

            if(isSymbol(p,Symbol.LARGE_BASAL))
            {
                tokens[2].removeFirst();
                return new Menu(MenuType.BASAL_MENU);
            }

            if(isSymbol(p,Symbol.LARGE_ALARM_SETTINGS))
            {
                tokens[2].removeFirst();
                return new Menu(MenuType.ALARM_MENU);
            }

            if(isSymbol(p,Symbol.LARGE_CALENDAR))
            {
                tokens[2].removeFirst();
                return new Menu(MenuType.DATE_AND_TIME_MENU);
            }

            if(isSymbol(p,Symbol.LARGE_PUMP_SETTINGS))
            {
                tokens[2].removeFirst();
                return new Menu(MenuType.PUMP_MENU);
            }

            if(isSymbol(p,Symbol.LARGE_THERAPIE_SETTINGS))
            {
                tokens[2].removeFirst();
                return new Menu(MenuType.THERAPY_MENU);
            }

            if(isSymbol(p,Symbol.LARGE_BLUETOOTH_SETTINGS))
            {
                tokens[2].removeFirst();
                return new Menu(MenuType.BLUETOOTH_MENU);
            }

            if(isSymbol(p,Symbol.LARGE_MENU_SETTINGS))
            {
                tokens[2].removeFirst();
                return new Menu(MenuType.MENU_SETTINGS_MENU);
            }

            if(isSymbol(p,Symbol.LARGE_CHECK))
            {
                tokens[2].removeFirst();
                return new Menu(MenuType.START_MENU);
            }
        }
        else if(tokens[2].size()==2)
        {
            Pattern p1 = tokens[2].removeFirst().getPattern();
            Pattern p2 = tokens[2].removeFirst().getPattern();

            if(isSymbol(p1,Symbol.LARGE_BASAL))
            {
                if(p2 instanceof NumberPattern)
                {
                    int num = ((NumberPattern)p2).getNumber();
                    parseString(tokens[0],true);
                    parseString(tokens[1],true);
                    switch(num)
                    {
                        case 1:
                            return new Menu(MenuType.BASAL_1_MENU);
                        case 2:
                            return new Menu(MenuType.BASAL_2_MENU);
                        case 3:
                            return new Menu(MenuType.BASAL_3_MENU);
                        case 4:
                            return new Menu(MenuType.BASAL_4_MENU);
                        case 5:
                            return new Menu(MenuType.BASAL_5_MENU);
                    }
                }
            }
            return null;
        }
        else if(tokens[0].size()>1)
        {
            String title = parseString(tokens[0],false);

            Title t = TitleResolver.resolve(title);
            if(t!=null) {
                //resolved so we can consume
                parseString(tokens[0],true);
                switch (t) {
                    case BOLUS_AMOUNT:
                        return makeBolusEnter(tokens);
                    case BOLUS_DURATION:
                        return makeBolusDuration(tokens);
                    case IMMEDIATE_BOLUS:
                        return makeImmediateBolus(tokens);
                    case QUICK_INFO:
                        return makeQuickInfo(tokens);
                    case BOLUS_DATA:
                        return makeBolusData(tokens);
                    case DAILY_TOTALS:
                        return makeDailyData(tokens);
                    case ERROR_DATA:
                        return makeErrorData(tokens);
                    case TBR_DATA:
                        return makeTBRData(tokens);
                    case TBR_SET:
                        return makeTBRSet(tokens);
                    case TBR_DURATION:
                        return makeTBRDuration(tokens);
                }
                Pattern p = tokens[1].get(0).getPattern();
            }
        }
        if(tokens[0].size()>0 && tokens[3].size()>0) {
            String m = parseString(tokens[0], false);
            Token t30 = tokens[3].getFirst();

            if(isSymbol(t30.getPattern(),Symbol.CHECK) && m.length()>0)
                return makeWarning(tokens);
        }
        return null;
    }

    private static Menu makeWarning(LinkedList<Token>[] tokens) {
        Menu m = new Menu(MenuType.WARNING_OR_ERROR);
        String message = parseString(tokens[0],true);
        m.setAttribute(MenuAttribute.MESSAGE,message);
        int stage = 0;
        int warning = 0;
        int type = 0;
        while(tokens[1].size()>0) {
            Pattern p = tokens[1].removeFirst().getPattern();
            switch (stage) {
                case 0:
                    if(isSymbol(p,Symbol.LARGE_WARNING))
                    {
                        type = 1;
                        stage++;
                    }
                    else if(isSymbol(p,Symbol.LARGE_ERROR))
                    {
                        type = 2;
                        stage++;
                    }
                    else
                        return null;
                    break;
                case 1:
                    if(p instanceof CharacterPattern)
                    {
                        char w = ((CharacterPattern)p).getCharacter();
                        if(type == 1 && w == 'W')
                        {
                            stage++;
                        }
                        else if(type == 2 && w == 'E')
                        {
                            stage++;
                        }
                        else
                            return null;
                    }
                    else
                        return null;
                    break;
                case 2:
                    if(p instanceof NumberPattern)
                    {
                        warning = ((NumberPattern)p).getNumber();
                        stage++;
                    }
                    else return null;
                    break;
                case 3:
                    if(p instanceof NumberPattern) {
                        warning *= 10;
                        warning += ((NumberPattern) p).getNumber();
                        stage++;
                    }
                    else if(isSymbol(p,Symbol.LARGE_STOP))
                        stage+=2;
                    else
                        return null;
                    break;
                case 4:
                    if(isSymbol(p,Symbol.LARGE_STOP))
                        stage++;
                    else
                        return null;
                    break;
                default:
                    return null;
            }
        }
        if(type == 1) {
            m.setAttribute(MenuAttribute.WARNING, warning);
        }
        else if(type == 2) {
            m.setAttribute(MenuAttribute.ERROR, warning);
        } else {
            m.setAttribute(MenuAttribute.ERROR_OR_WARNING, warning);
        }

        if(isSymbol(tokens[3].getFirst().getPattern(),Symbol.CHECK))
        {
            tokens[3].removeFirst();
            parseString(tokens[3],true);//ignore result
        }
        return m;
    }

    private static Menu makeBasalSet(LinkedList<Token>[] tokens) {
        Menu m = new Menu(MenuType.BASAL_SET);
        LinkedList<Pattern> from = new LinkedList<>();
        LinkedList<Pattern> to = new LinkedList<>();
        int stage = 0;
        while(tokens[0].size()>0) {
            Pattern p = tokens[0].removeFirst().getPattern();
            switch (stage) {
                case 0:
                    if(isSymbol(p,Symbol.CLOCK))
                        stage++;
                    else
                        return null;
                    break;
                case 1:
                    if(isSymbol(p,Symbol.MINUS))
                        stage++;
                    else if(isSymbol(p,Symbol.SEPERATOR))
                        from.add(p);
                    else if(p instanceof CharacterPattern)
                        from.add(p);
                    else if(p instanceof NumberPattern)
                        from.add(p);
                    else
                        return null;
                    break;
                case 2:
                    if(p instanceof CharacterPattern)
                        to.add(p);
                    else if(p instanceof NumberPattern)
                        to.add(p);
                    else if(isSymbol(p,Symbol.SEPERATOR))
                        to.add(p);
                    else
                        return null;
                    break;

                default:
                    return null;
            }
        }
        if(from.size()>0 && to.size()>0)
        {
            try {
                int f10 = ((NumberPattern) from.removeFirst()).getNumber();
                int f1 = ((NumberPattern) from.removeFirst()).getNumber();
                int a = 0;
                if (from.size() > 0) {
                    if(from.getFirst() instanceof CharacterPattern) {
                        char c0 = ((CharacterPattern) from.removeFirst()).getCharacter();
                        char c1 = ((CharacterPattern) from.removeFirst()).getCharacter();
                        if (c0 == 'P' && c1 == 'M' && !(f10 == 1 && f1 == 2))
                            a += 12;
                        else if (c0 == 'A' && c1 == 'M' && f10 == 1 && f1 == 2)
                            a -= 12;
                    } else if(isSymbol(from.getFirst(),Symbol.SEPERATOR))
                    {}//ignore
                    else
                        return null;

                }
                m.setAttribute(MenuAttribute.BASAL_START, new MenuTime((f10 * 10) + f1 + a, 0));

                int t10 = ((NumberPattern) to.removeFirst()).getNumber();
                int t1 = ((NumberPattern) to.removeFirst()).getNumber();
                a = 0;
                if (to.size() > 0) {
                    if(to.getFirst() instanceof CharacterPattern) {
                        char c0 = ((CharacterPattern) to.removeFirst()).getCharacter();
                        char c1 = ((CharacterPattern) to.removeFirst()).getCharacter();
                        if (c0 == 'P' && c1 == 'M' && !(t10==1 && t1 == 2))
                            a += 12;
                        else if (c0 == 'A' && c1 == 'M' && t10 == 1 && t1 == 2)
                            a -= 12;
                    } else if(isSymbol(to.getFirst(),Symbol.SEPERATOR))
                    {}//ignore
                    else
                        return null;
                }
                m.setAttribute(MenuAttribute.BASAL_END, new MenuTime((t10 * 10) + t1 + a, 0));
            }catch(Exception e)
            {
                e.printStackTrace();
                return null;
            }
        }
        else
            return null;

        stage = 0;
        LinkedList<Pattern> basal = new LinkedList<>();
        while(tokens[1].size()>0) {
            Pattern p = tokens[1].removeFirst().getPattern();
            switch (stage) {
                case 0:
                    if(isSymbol(p,Symbol.LARGE_BASAL))
                        stage++;
                    else
                        return null;
                    break;
                case 1:
                    if(isSymbol(p,Symbol.LARGE_UNITS_PER_HOUR))
                        stage++;
                    else if(isSymbol(p,Symbol.LARGE_DOT))
                        basal.add(p);
                    else if(p instanceof NumberPattern)
                        basal.add(p);
                    else
                        return null;
                    break;
                default:
                    return null;
            }
        }
        if(basal.size()>0)
        {
            try
            {
                String n = "";
                for(Pattern p: basal)
                {
                    if(p instanceof NumberPattern)
                        n+=((NumberPattern)p).getNumber();
                    else if(isSymbol(p,Symbol.LARGE_DOT))
                        n+=".";
                    else
                        return null;
                }
                double d = Double.parseDouble(n);
                m.setAttribute(MenuAttribute.BASAL_RATE,d);
            }catch(Exception e)
            {
                e.printStackTrace();
                return null;
            }
        }
        else
        {
            m.setAttribute(MenuAttribute.BASAL_RATE,new MenuBlink());
        }
        if(tokens[2].size()==1 && tokens[2].get(0).getPattern() instanceof NumberPattern)
        {
            m.setAttribute(MenuAttribute.BASAL_SELECTED,((NumberPattern)tokens[2].removeFirst().getPattern()).getNumber());
        }
        else
            return null;
        return m;
    }

    private static Menu makeBasalTotal(LinkedList<Token>[] tokens) {
        Menu m = new Menu(MenuType.BASAL_TOTAL);
        LinkedList<Pattern> basal = new LinkedList<>();

        int stage = 0;
        while(tokens[1].size()>0) {
            Pattern p = tokens[1].removeFirst().getPattern();
            switch (stage) {
                case 0:
                    if(isSymbol(p,Symbol.LARGE_BASAL_SET))
                        stage++;
                    else
                        return null;
                    break;
                case 1:
                    if(p instanceof NumberPattern)
                        basal.add(p);
                    else if (isSymbol(p,Symbol.LARGE_DOT))
                        basal.add(p);
                    else if(p instanceof CharacterPattern && ((CharacterPattern)p).getCharacter()=='u')
                        stage++;
                    else
                        return null;
                    break;
                default:
                    return null;
            }
        }
        if(basal.size()>0)
        {
            try {
                String n = "";
                for (Pattern p : basal)
                    if (p instanceof NumberPattern)
                        n += ((NumberPattern) p).getNumber();
                    else if (isSymbol(p, Symbol.LARGE_DOT))
                        n += ".";
                    else
                        return null;
                double d = Double.parseDouble(n);
                m.setAttribute(MenuAttribute.BASAL_TOTAL,d);
            }catch(Exception e)
            {
                e.printStackTrace();
                return null;
            }
        }
        else
            return null;

        if(tokens[2].size()==1 && tokens[2].get(0).getPattern() instanceof NumberPattern)
        {
            m.setAttribute(MenuAttribute.BASAL_SELECTED,((NumberPattern)tokens[2].removeFirst().getPattern()).getNumber());
        }
        else
        {
            return null;
        }

        stage = 0;
        while(tokens[3].size()>0)
        {
            Pattern p = tokens[3].removeFirst().getPattern();
            switch(stage)
            {
                case 0:
                    if(isSymbol(p,Symbol.CHECK))
                    {
                        String s = parseString(tokens[3],true);
                        if(s!=null)
                            stage++;
                        else
                            return null;
                    }
                    else
                        return null;
                    break;
                default:
                    return null;
            }
        }
        return m;
    }

    private static Menu makeStopMenu(LinkedList<Token>[] tokens) {
        Menu m = new Menu(MenuType.STOP);
        if(!isSymbol(tokens[1].removeFirst().getPattern(),Symbol.LARGE_STOP))
            return null;
        int stage = 0;
        LinkedList<Pattern> clock = new LinkedList<>();
        LinkedList<Pattern> date = new LinkedList<>();
        while(tokens[0].size()>0) {
            Pattern p = tokens[0].removeFirst().getPattern();
            switch (stage) {
                case 0:
                    if (isSymbol(p, Symbol.CLOCK))
                        stage++;
                    else
                        return null;
                    break;
                case 1:
                    if(p instanceof NumberPattern)
                        clock.add(p);
                    else if(p instanceof CharacterPattern)
                        clock.add(p);
                    else if (isSymbol(p, Symbol.SEPERATOR))
                    {}
                    else if(isSymbol(p,Symbol.CALENDAR))
                        stage++;
                    else
                        return null;
                    break;
                case 2:
                    if(p instanceof NumberPattern)
                        date.add(p);
                    else if (isSymbol(p, Symbol.DIVIDE))
                        date .add(p);
                    else if (isSymbol(p, Symbol.DOT))
                        date .add(p);
                    else
                        return null;
                    break;
                case 3:
                    return null;
            }
        }
        if(clock.size()>=4) {
            try {
                int hour10 = ((NumberPattern) clock.removeFirst()).getNumber();
                int hour1 = ((NumberPattern) clock.removeFirst()).getNumber();
                int minute10 = ((NumberPattern) clock.removeFirst()).getNumber();
                int minute1 = ((NumberPattern) clock.removeFirst()).getNumber();
                int timeadd = 0;
                if (clock.size() == 2) {
                    CharacterPattern p0 = (CharacterPattern) clock.removeFirst();
                    CharacterPattern p1 = (CharacterPattern) clock.removeFirst();
                    if(p0.getCharacter()=='A' && p1.getCharacter()=='M' && hour10==1 && hour1 == 2)
                        timeadd-=12;
                    else if(p0.getCharacter()=='P' && p1.getCharacter()=='M')
                        timeadd+=12;
                }
                m.setAttribute(MenuAttribute.TIME,new MenuTime((hour10*10)+hour1+timeadd,(minute10*10)+minute1));
            }catch(Exception e)
            {
                e.printStackTrace();
                return null;
            }

        }
        else
            return null;
        if(date.size()==5) {
            try {
                int f10 = ((NumberPattern) date.removeFirst()).getNumber();
                int f1 = ((NumberPattern) date.removeFirst()).getNumber();
                boolean divide = isSymbol(date.removeFirst(),Symbol.DIVIDE);
                int s10 = ((NumberPattern) date.removeFirst()).getNumber();
                int s1 = ((NumberPattern) date.removeFirst()).getNumber();
                if(divide)
                    m.setAttribute(MenuAttribute.DATE, new MenuDate((s10*10)+s1,(f10*10)+f1));
                else
                    m.setAttribute(MenuAttribute.DATE, new MenuDate((f10*10)+f1,(s10*10)+s1));

            }catch(Exception e)
            {
                e.printStackTrace();
                return null;
            }
        }
        else
            return null;

        stage = 0;
        int lowInsulin = 0;
        int lowBattery= 0;
        boolean waranty= true;
        int lockState = 0;
        while(tokens[3].size()>0) {
            Token t = tokens[3].removeFirst();
            Pattern p = t.getPattern();
            switch (stage) {
                case 0:
                    if (isSymbol(p, Symbol.LOW_BAT)) {
                        lowBattery= 1;
                    } else if (isSymbol(p, Symbol.NO_BAT)) {
                        lowBattery= 2;
                    } else if (isSymbol(p, Symbol.LOW_INSULIN)) {
                        lowInsulin= 1;
                    } else if (isSymbol(p, Symbol.NO_INSULIN)) {
                        lowInsulin= 2;
                    } else if (isSymbol(p, Symbol.LOCK_CLOSED)) {
                        lockState=2;
                    } else if (isSymbol(p, Symbol.LOCK_OPENED)) {
                        lockState=2;
                    } else if (isSymbol(p, Symbol.WARANTY)) {
                        waranty = false;
                    } else {
                        return null;
                    }
                    break;
                case 2:
                    return null;
            }
        }

        m.setAttribute(MenuAttribute.BATTERY_STATE,lowBattery);

        m.setAttribute(MenuAttribute.INSULIN_STATE,lowInsulin);

        m.setAttribute(MenuAttribute.WARANTY,new Boolean(waranty));

        m.setAttribute(MenuAttribute.LOCK_STATE,new Integer(lockState));

        return m;
    }

    private static Menu makeTBRSet(LinkedList<Token>[] tokens) {
        Menu m = new Menu(MenuType.TBR_SET);
        int stage = 0;
        LinkedList<NumberPattern> number = new LinkedList<>();
        while(tokens[1].size()>0)
        {
            Pattern p = tokens[1].removeFirst().getPattern();
            switch (stage)
            {
                case 0:
                    if(isSymbol(p,Symbol.LARGE_BASAL))
                        stage++;
                    else
                        return null;
                    break;
                case 1:
                    if(p instanceof NumberPattern)
                    {
                        number.add((NumberPattern)p);
                    }
                    else if (isSymbol(p,Symbol.LARGE_PERCENT))
                    {
                        stage++;
                    }
                    break;
                case 2:
                    return null;
            }
        }
        if(number.size()>0)
        {
            String n = "";
            while(number.size()>0)
            {
                n += number.removeFirst().getNumber();
            }
            try{
                double d = Double.parseDouble(n);
                m.setAttribute(MenuAttribute.BASAL_RATE,d);
            }catch(Exception e){e.printStackTrace();return null;}
        } else if(number.size()==0)
            m.setAttribute(MenuAttribute.BASAL_RATE, new MenuBlink());
        else
            return null;

        if(tokens[3].size()>0) {
            stage = 0;
            number.clear();
            while (tokens[3].size() > 0) {
                Pattern p = tokens[3].removeFirst().getPattern();
                switch (stage) {
                    case 0:
                        if (isSymbol(p, Symbol.ARROW))
                            stage++;
                        else
                            return null;
                        break;
                    case 1:
                        if (p instanceof NumberPattern)
                            number.add((NumberPattern) p);
                        else if (isSymbol(p, Symbol.SEPERATOR))
                        {}
                        else return null;
                        break;
                    case 2:
                        return null;
                }
            }
            if (number.size() == 4) {
                int hour10 = number.removeFirst().getNumber();
                int hour1 = number.removeFirst().getNumber();
                int minute10 = number.removeFirst().getNumber();
                int minute1 = number.removeFirst().getNumber();
                m.setAttribute(MenuAttribute.RUNTIME, new MenuTime((hour10 * 10) + hour1, (minute10 * 10) + minute1));
            } else return null;
        }
        else m.setAttribute(MenuAttribute.RUNTIME,new MenuTime(0,0));
        return m;
    }

    private static Menu makeTBRDuration(LinkedList<Token>[] tokens) {
        Menu m = new Menu(MenuType.TBR_DURATION);
        int stage = 0;
        LinkedList<NumberPattern> number = new LinkedList<>();
        while(tokens[1].size()>0)
        {
            Pattern p = tokens[1].removeFirst().getPattern();
            switch (stage)
            {
                case 0:
                    if(isSymbol(p,Symbol.LARGE_ARROW))
                        stage++;
                    else
                        return null;
                    break;
                case 1:
                    if(p instanceof NumberPattern)
                    {
                        number.add((NumberPattern)p);
                    }
                    else if (isSymbol(p,Symbol.LARGE_PERCENT))
                    {
                        stage++;
                    }
                    else if (isSymbol(p,Symbol.LARGE_SEPERATOR))
                    {
                    }
                    else return null;
                    break;
                case 2:
                    return null;
            }
        }
        if(number.size()==4)
        {
            int hour10 = number.removeFirst().getNumber();
            int hour1 = number.removeFirst().getNumber();
            int minute10 = number.removeFirst().getNumber();
            int minute1 = number.removeFirst().getNumber();
            m.setAttribute(MenuAttribute.RUNTIME,new MenuTime((hour10*10)+hour1,(minute10*10)+minute1));
        } else if(number.size()==0) m.setAttribute(MenuAttribute.RUNTIME,new MenuBlink());
        else return null;

        stage = 0;
        number.clear();
        while(tokens[3].size()>0)
        {
            Pattern p = tokens[3].removeFirst().getPattern();
            switch (stage)
            {
                case 0:
                    if(isSymbol(p,Symbol.BASAL))
                        stage++;
                    else return null;
                    break;
                case 1:
                    if(p instanceof  NumberPattern)
                        number.add((NumberPattern)p);
                    else if(isSymbol(p,Symbol.PERCENT))
                        stage++;
                    else return null;
                    break;
                case 3:
                    return null;
            }
        }
        if(number.size()>0)
        {
            String n = "";
            while(number.size()>0)
            {
                n += number.removeFirst().getNumber();
            }
            try{
                double d = Double.parseDouble(n);
                m.setAttribute(MenuAttribute.BASAL_RATE,d);
            }catch(Exception e){e.printStackTrace();return null;}
        }
        return m;
    }

    private static Menu makeTBRData(LinkedList<Token>[] tokens) {
        Menu m = new Menu(MenuType.TBR_DATA);
        int stage = 0;
        LinkedList<NumberPattern> percent = new LinkedList<>();
        LinkedList<NumberPattern> cr = new LinkedList<>();
        LinkedList<NumberPattern> tr = new LinkedList<>();

        while (tokens[1].size()>0) {
            Token t = tokens[1].removeFirst();
            Pattern p = t.getPattern();
            switch (stage) {
                case 0:
                    if (isSymbol(p, Symbol.UP))
                        stage++;
                    else if (isSymbol(p, Symbol.DOWN))
                        stage++;
                    else
                        return null;
                    break;
                case 1:
                    if(p instanceof NumberPattern)
                        percent.add((NumberPattern) p);
                    else if(isSymbol(p,Symbol.PERCENT))
                        stage++;
                    else
                        return null;
                    break;
                case 2:
                    if(p instanceof NumberPattern)
                        cr.add((NumberPattern) p);
                    else if(isSymbol(p,Symbol.DIVIDE))
                        stage++;
                    else
                        return null;
                    break;
                case 3:
                    if(p instanceof NumberPattern)
                        tr.add((NumberPattern) p);
                    else
                        return null;
                    break;
                default:
                    return null;
            }
        }
        if(percent.size()>0)
        {
            try
            {
                String n = "";
                for(NumberPattern p : percent)
                {
                    n+=p.getNumber();
                }
                double d = Double.parseDouble(n);
                m.setAttribute(MenuAttribute.TBR,d);
            }catch(Exception e)
            {
                e.printStackTrace();
                return null;
            }
        }
        if(cr.size()==2 && tr.size() == 2)
        {
            int c = cr.removeFirst().getNumber()*10;
            c+=cr.removeFirst().getNumber();
            m.setAttribute(MenuAttribute.CURRENT_RECORD,new Integer(c));
            int t = tr.removeFirst().getNumber()*10;
            t+=tr.removeFirst().getNumber();
            m.setAttribute(MenuAttribute.TOTAL_RECORD,new Integer(t));
        }
        else
            return null;

        LinkedList<Pattern> clock = new LinkedList<>();
        stage = 0;
        while(tokens[2].size()>0) {
            Pattern p = tokens[2].removeFirst().getPattern();
            switch (stage) {
                case 0:
                    if (isSymbol(p, Symbol.ARROW))
                        stage++;
                    else
                        return null;
                    break;
                case 1:
                    if (p instanceof NumberPattern)
                        clock.add(p);
                    else if (isSymbol(p, Symbol.SEPERATOR)) {
                    } else
                        return null;
                    break;
                default:
                    return null;
            }
        }
        if(clock.size()==4) {
            try {
                int hour10 = ((NumberPattern) clock.removeFirst()).getNumber();
                int hour1 = ((NumberPattern) clock.removeFirst()).getNumber();
                int minute10 = ((NumberPattern) clock.removeFirst()).getNumber();
                int minute1 = ((NumberPattern) clock.removeFirst()).getNumber();
                m.setAttribute(MenuAttribute.RUNTIME,new MenuTime((hour10*10)+hour1,(minute10*10)+minute1));
            }catch(Exception e)
            {
                e.printStackTrace();
                return null;
            }

        }
        else
            return null;

        clock.clear();
        LinkedList<Pattern> date = new LinkedList<>();
        stage = 0;
        while(tokens[3].size()>0) {
            Pattern p = tokens[3].removeFirst().getPattern();
            switch (stage) {
                case 0:
                    if (isSymbol(p, Symbol.CLOCK))
                        stage++;
                    else
                        return null;
                    break;
                case 1:
                    if(p instanceof NumberPattern)
                        clock.add(p);
                    else if(p instanceof CharacterPattern)
                        clock.add(p);
                    else if (isSymbol(p, Symbol.SEPERATOR))
                    {}
                    else if(isSymbol(p,Symbol.CALENDAR))
                        stage++;
                    else
                        return null;
                    break;
                case 2:
                    if(p instanceof NumberPattern)
                        date.add(p);
                    else if (isSymbol(p, Symbol.DIVIDE))
                        date .add(p);
                    else if (isSymbol(p, Symbol.DOT))
                        date .add(p);
                    else
                        return null;
                    break;
                case 3:
                    return null;
            }
        }
        if(clock.size()>=4) {
            try {
                int hour10 = ((NumberPattern) clock.removeFirst()).getNumber();
                int hour1 = ((NumberPattern) clock.removeFirst()).getNumber();
                int minute10 = ((NumberPattern) clock.removeFirst()).getNumber();
                int minute1 = ((NumberPattern) clock.removeFirst()).getNumber();
                int timeadd = 0;
                if (clock.size() == 2) {
                    CharacterPattern p0 = (CharacterPattern) clock.removeFirst();
                    CharacterPattern p1 = (CharacterPattern) clock.removeFirst();
                    if(p0.getCharacter()=='A' && p1.getCharacter()=='M' && hour10==1 && hour1 == 2)
                        timeadd-=12;
                    else if(p0.getCharacter()=='P' && p1.getCharacter()=='M')
                        timeadd+=12;
                }
                m.setAttribute(MenuAttribute.TIME,new MenuTime((hour10*10)+hour1+timeadd,(minute10*10)+minute1));
            }catch(Exception e)
            {
                e.printStackTrace();
                return null;
            }

        }
        else
            return null;
        if(date.size()==5) {
            try {
                int f10 = ((NumberPattern) date.removeFirst()).getNumber();
                int f1 = ((NumberPattern) date.removeFirst()).getNumber();
                boolean divide = isSymbol(date.removeFirst(),Symbol.DIVIDE);
                int s10 = ((NumberPattern) date.removeFirst()).getNumber();
                int s1 = ((NumberPattern) date.removeFirst()).getNumber();
                if(divide)
                    m.setAttribute(MenuAttribute.DATE, new MenuDate((s10*10)+s1,(f10*10)+f1));
                else
                    m.setAttribute(MenuAttribute.DATE, new MenuDate((f10*10)+f1,(s10*10)+s1));

            }catch(Exception e)
            {
                e.printStackTrace();
                return null;
            }
        }
        else
            return null;
        return m;
    }

    private static Menu makeErrorData(LinkedList<Token>[] tokens) {
        Menu m = new Menu(MenuType.ERROR_DATA);
        boolean error = false;
        boolean warning = false;
        int code = 0;
        LinkedList<NumberPattern> cr = new LinkedList<>();
        LinkedList<NumberPattern> tr = new LinkedList<>();

        int stage = 0;
        while (tokens[1].size()>0) {
            Token t = tokens[1].removeFirst();
            Pattern p = t.getPattern();
            switch (stage) {
                case 0:
                    if (isSymbol(p, Symbol.WARNING)) {
                        warning=true;
                        stage++;
                    } else if (isSymbol(p, Symbol.ERROR)) {
                        error=true;
                        stage++;
                    } else
                        return null;
                    break;
                case 1:
                    if(p instanceof CharacterPattern && ((((CharacterPattern)p).getCharacter()=='W' && warning) ||
                            (((CharacterPattern)p).getCharacter()=='E' && error)))
                        stage++;
                    else
                        return null;
                    break;
                case 2:
                    if(p instanceof NumberPattern)
                    {
                        code = ((NumberPattern) p).getNumber();
                        stage++;
                    }
                    else
                        return null;
                    break;
                case 3:
                    if(p instanceof NumberPattern)
                        cr.add((NumberPattern) p);
                    else if(isSymbol(p,Symbol.DIVIDE))
                        stage++;
                    else
                        return null;
                    break;
                case 4:
                    if(p instanceof NumberPattern)
                        tr.add((NumberPattern) p);
                    else
                        return null;
                    break;
                case 5:
                    return null;
            }
        }
        if(error)
            m.setAttribute(MenuAttribute.ERROR,new Integer(code));
        else if(warning)
            m.setAttribute(MenuAttribute.WARNING,new Integer(code));
        else
            return null;

        if(cr.size()==2 && tr.size() == 2)
        {
            int c = cr.removeFirst().getNumber()*10;
            c+=cr.removeFirst().getNumber();
            m.setAttribute(MenuAttribute.CURRENT_RECORD,new Integer(c));
            int t = tr.removeFirst().getNumber()*10;
            t+=tr.removeFirst().getNumber();
            m.setAttribute(MenuAttribute.TOTAL_RECORD,new Integer(t));
        }
        else
            return null;

        String message = parseString(tokens[2],true);
        if(message!=null)
            m.setAttribute(MenuAttribute.MESSAGE,message);
        else
            return null;

        LinkedList<Pattern> clock = new LinkedList<>();
        LinkedList<Pattern> date = new LinkedList<>();
        stage = 0;
        while(tokens[3].size()>0) {
            Pattern p = tokens[3].removeFirst().getPattern();
            switch (stage) {
                case 0:
                    if (isSymbol(p, Symbol.CLOCK))
                        stage++;
                    else
                        return null;
                    break;
                case 1:
                    if(p instanceof NumberPattern)
                        clock.add(p);
                    else if(p instanceof CharacterPattern)
                        clock.add(p);
                    else if (isSymbol(p, Symbol.SEPERATOR))
                    {}
                    else if(isSymbol(p,Symbol.CALENDAR))
                        stage++;
                    else
                        return null;
                    break;
                case 2:
                    if(p instanceof NumberPattern)
                        date.add(p);
                    else if (isSymbol(p, Symbol.DIVIDE))
                        date .add(p);
                    else if (isSymbol(p, Symbol.DOT))
                        date .add(p);
                    else
                        return null;
                    break;
                case 3:
                    return null;
            }
        }
        if(clock.size()>=4) {
            try {
                int hour10 = ((NumberPattern) clock.removeFirst()).getNumber();
                int hour1 = ((NumberPattern) clock.removeFirst()).getNumber();
                int minute10 = ((NumberPattern) clock.removeFirst()).getNumber();
                int minute1 = ((NumberPattern) clock.removeFirst()).getNumber();
                int timeadd = 0;
                if (clock.size() == 2) {
                    CharacterPattern p0 = (CharacterPattern) clock.removeFirst();
                    CharacterPattern p1 = (CharacterPattern) clock.removeFirst();
                    if(p0.getCharacter()=='A' && p1.getCharacter()=='M' && hour10==1 && hour1 == 2)
                        timeadd-=12;
                    else if(p0.getCharacter()=='P' && p1.getCharacter()=='M')
                        timeadd+=12;
                }
                m.setAttribute(MenuAttribute.TIME,new MenuTime((hour10*10)+hour1+timeadd,(minute10*10)+minute1));
            }catch(Exception e)
            {
                e.printStackTrace();
                return null;
            }

        }
        else
            return null;
        if(date.size()==5) {
            try {
                int f10 = ((NumberPattern) date.removeFirst()).getNumber();
                int f1 = ((NumberPattern) date.removeFirst()).getNumber();
                boolean divide = isSymbol(date.removeFirst(),Symbol.DIVIDE);
                int s10 = ((NumberPattern) date.removeFirst()).getNumber();
                int s1 = ((NumberPattern) date.removeFirst()).getNumber();
                if(divide)
                    m.setAttribute(MenuAttribute.DATE, new MenuDate((s10*10)+s1,(f10*10)+f1));
                else
                    m.setAttribute(MenuAttribute.DATE, new MenuDate((f10*10)+f1,(s10*10)+s1));

            }catch(Exception e)
            {
                e.printStackTrace();
                return null;
            }
        }
        else
            return null;
        return m;
    }

    private static Menu makeDailyData(LinkedList<Token>[] tokens) {
        Menu m = new Menu(MenuType.DAILY_DATA);

        LinkedList<NumberPattern> cr = new LinkedList<>();
        LinkedList<NumberPattern> tr = new LinkedList<>();

        int stage = 0;
        while (tokens[1].size()>0) {
            Token t = tokens[1].removeFirst();
            Pattern p = t.getPattern();
            switch (stage) {
                case 0:
                    if(p instanceof NumberPattern)
                        cr.add((NumberPattern) p);
                    else if(isSymbol(p,Symbol.DIVIDE))
                        stage++;
                    else
                        return null;
                    break;
                case 1:
                    if(p instanceof NumberPattern)
                        tr.add((NumberPattern) p);
                    else
                        return null;
                    break;
                case 2:
                    return null;
            }
        }
        if(cr.size()==2 && tr.size() == 2)
        {
            int c = cr.removeFirst().getNumber()*10;
            c+=cr.removeFirst().getNumber();
            m.setAttribute(MenuAttribute.CURRENT_RECORD,new Integer(c));
            int t = tr.removeFirst().getNumber()*10;
            t+=tr.removeFirst().getNumber();
            m.setAttribute(MenuAttribute.TOTAL_RECORD,new Integer(t));
        }
        else
            return null;

        LinkedList<Pattern> sum = new LinkedList<>();
        stage = 0;
        while (tokens[2].size()>0) {
            Token t = tokens[2].removeFirst();
            Pattern p = t.getPattern();
            switch (stage) {
                case 0:
                    if(isSymbol(p,Symbol.SUM))
                        stage++;
                    else
                        return null;
                    break;
                case 1:
                    if(p instanceof NumberPattern)
                        sum.add((NumberPattern) p);
                    else if(isSymbol(p,Symbol.DOT))
                        sum.add(p);
                    else if(p instanceof CharacterPattern && ((CharacterPattern)p).getCharacter()=='U')
                        stage++;
                    else
                        return null;
                    break;
                case 2:
                    return null;
            }
        }

        if(sum.size()>0)
        {
            try
            {
                String n = "";
                for(Pattern p : sum)
                {
                    if(p instanceof NumberPattern)
                        n+=((NumberPattern)p).getNumber();
                    else if(isSymbol(p,Symbol.DOT))
                        n+=".";
                    else
                        return null;
                }
                double d = Double.parseDouble(n);
                m.setAttribute(MenuAttribute.DAILY_TOTAL,d);
            }catch(Exception e)
            {
                e.printStackTrace();
                return null;
            }
        }
        LinkedList<Pattern> date = new LinkedList<>();
        stage = 0;
        while(tokens[3].size()>0) {
            Pattern p = tokens[3].removeFirst().getPattern();
            switch (stage) {
                case 0:
                    if(isSymbol(p,Symbol.CALENDAR))
                        stage++;
                    else
                        return null;
                    break;
                case 1:
                    if(p instanceof NumberPattern)
                        date.add(p);
                    else if (isSymbol(p, Symbol.DIVIDE))
                        date .add(p);
                    else if (isSymbol(p, Symbol.DOT))
                        date .add(p);
                    else
                        return null;
                    break;
                case 2:
                    return null;
            }
        }
        if(date.size()==5) {
            try {
                int f10 = ((NumberPattern) date.removeFirst()).getNumber();
                int f1 = ((NumberPattern) date.removeFirst()).getNumber();
                boolean divide = isSymbol(date.removeFirst(),Symbol.DIVIDE);
                int s10 = ((NumberPattern) date.removeFirst()).getNumber();
                int s1 = ((NumberPattern) date.removeFirst()).getNumber();
                if(divide)
                    m.setAttribute(MenuAttribute.DATE, new MenuDate((s10*10)+s1,(f10*10)+f1));
                else
                    m.setAttribute(MenuAttribute.DATE, new MenuDate((f10*10)+f1,(s10*10)+s1));

            }catch(Exception e)
            {
                e.printStackTrace();
                return null;
            }
        }
        else
            return null;
        return m;
    }

    private static Menu makeBolusData(LinkedList<Token>[] tokens) {
        Menu m = new Menu(MenuType.BOLUS_DATA);
        LinkedList<Pattern> bolus = new LinkedList<>();
        LinkedList<NumberPattern> cr = new LinkedList<>();
        LinkedList<NumberPattern> tr = new LinkedList<>();
        BolusType bt = null;

        int stage = 0;
        while (tokens[1].size()>0) {
            Token t = tokens[1].removeFirst();
            Pattern p = t.getPattern();
            switch (stage) {
                case 0:
                    if (isSymbol(p, Symbol.BOLUS)) {
                        bt = BolusType.NORMAL;
                        stage++;
                    } else if (isSymbol(p, Symbol.EXTENDED_BOLUS)) {
                        bt = BolusType.EXTENDED;
                        stage++;
                    } else if (isSymbol(p, Symbol.MULTIWAVE)) {
                        bt = BolusType.MULTIWAVE;
                        stage++;
                    } else
                        return null;
                    break;
                case 1:
                    if(isSymbol(p,Symbol.DOT))
                        bolus.add(p);
                    else if(p instanceof NumberPattern)
                        bolus.add(p);
                    else if(p instanceof CharacterPattern && ((CharacterPattern)p).getCharacter()=='U')
                        stage++;
                    else
                        return null;
                    break;
                case 2:
                    if(p instanceof NumberPattern)
                        cr.add((NumberPattern) p);
                    else if(isSymbol(p,Symbol.DIVIDE))
                        stage++;
                    else
                        return null;
                    break;
                case 3:
                    if(p instanceof NumberPattern)
                        tr.add((NumberPattern) p);
                    else if(isSymbol(p,Symbol.DIVIDE))
                        stage++;
                    else
                        return null;
                    break;
                case 4:
                    return null;
            }
        }
        if(bt!=null)
        {
            m.setAttribute(MenuAttribute.BOLUS_TYPE,bt);

            try
            {
                String n = "";
                for(Pattern p: bolus)
                {
                    if(p instanceof NumberPattern)
                        n+=((NumberPattern)p).getNumber();
                    else if(isSymbol(p,Symbol.DOT))
                        n+=".";
                    else
                        return null;
                }
                double d = Double.parseDouble(n);
                m.setAttribute(MenuAttribute.BOLUS,d);
            }catch(Exception e)
            {
                e.printStackTrace();
                return null;
            }
        }
        else
            return null;
        if(cr.size()==2 && tr.size() == 2)
        {
            int c = cr.removeFirst().getNumber()*10;
            c+=cr.removeFirst().getNumber();
            m.setAttribute(MenuAttribute.CURRENT_RECORD,new Integer(c));
            int t = tr.removeFirst().getNumber()*10;
            t+=tr.removeFirst().getNumber();
            m.setAttribute(MenuAttribute.TOTAL_RECORD,new Integer(t));
        }
        else
            return null;

        LinkedList<Pattern> clock = new LinkedList<>();
        stage = 0;
        while(tokens[2].size()>0) {
            Pattern p = tokens[2].removeFirst().getPattern();
            switch (stage) {
                case 0:
                    if (isSymbol(p, Symbol.ARROW))
                        stage++;
                    else
                        return null;
                    break;
                case 1:
                    if(p instanceof NumberPattern)
                        clock.add(p);
                    else if(p instanceof CharacterPattern)
                        clock.add(p);
                    else if (isSymbol(p, Symbol.SEPERATOR))
                    {}
                    else
                        return null;
                    break;
                case 2:
                    return null;
            }
        }
        if(clock.size()>=4) {
            try {
                int hour10 = ((NumberPattern) clock.removeFirst()).getNumber();
                int hour1 = ((NumberPattern) clock.removeFirst()).getNumber();
                int minute10 = ((NumberPattern) clock.removeFirst()).getNumber();
                int minute1 = ((NumberPattern) clock.removeFirst()).getNumber();
                int timeadd = 0;
                if (clock.size() == 2) {
                    CharacterPattern p0 = (CharacterPattern) clock.removeFirst();
                    CharacterPattern p1 = (CharacterPattern) clock.removeFirst();
                    if(p0.getCharacter()=='A' && p1.getCharacter()=='M' && hour10==1 && hour1 == 2)
                        timeadd-=12;
                    else if(p0.getCharacter()=='P' && p1.getCharacter()=='M')
                        timeadd+=12;
                }
                m.setAttribute(MenuAttribute.RUNTIME,new MenuTime((hour10*10)+hour1+timeadd,(minute10*10)+minute1));
            }catch(Exception e)
            {
                e.printStackTrace();
                return null;
            }

        }
        else if(bt != BolusType.NORMAL)//we need a runtime if not normal/quick bolus
            return null;

        clock.clear();
        LinkedList<Pattern> date = new LinkedList<>();
        stage = 0;
        while(tokens[3].size()>0) {
            Pattern p = tokens[3].removeFirst().getPattern();
            switch (stage) {
                case 0:
                    if (isSymbol(p, Symbol.CLOCK))
                        stage++;
                    else
                        return null;
                    break;
                case 1:
                    if(p instanceof NumberPattern)
                        clock.add(p);
                    else if(p instanceof CharacterPattern)
                        clock.add(p);
                    else if (isSymbol(p, Symbol.SEPERATOR))
                    {}
                    else if(isSymbol(p,Symbol.CALENDAR))
                        stage++;
                    else
                        return null;
                    break;
                case 2:
                    if(p instanceof NumberPattern)
                        date.add(p);
                    else if (isSymbol(p, Symbol.DIVIDE))
                        date .add(p);
                    else if (isSymbol(p, Symbol.DOT))
                        date .add(p);
                    else
                        return null;
                    break;
                case 3:
                    return null;
            }
        }
        if(clock.size()>=4) {
            try {
                int hour10 = ((NumberPattern) clock.removeFirst()).getNumber();
                int hour1 = ((NumberPattern) clock.removeFirst()).getNumber();
                int minute10 = ((NumberPattern) clock.removeFirst()).getNumber();
                int minute1 = ((NumberPattern) clock.removeFirst()).getNumber();
                int timeadd = 0;
                if (clock.size() == 2) {
                    CharacterPattern p0 = (CharacterPattern) clock.removeFirst();
                    CharacterPattern p1 = (CharacterPattern) clock.removeFirst();
                    if(p0.getCharacter()=='A' && p1.getCharacter()=='M' && hour10==1 && hour1 == 2)
                        timeadd-=12;
                    else if(p0.getCharacter()=='P' && p1.getCharacter()=='M')
                        timeadd+=12;
                }
                m.setAttribute(MenuAttribute.TIME,new MenuTime((hour10*10)+hour1+timeadd,(minute10*10)+minute1));
            }catch(Exception e)
            {
                e.printStackTrace();
                return null;
            }

        }
        else
            return null;
        if(date.size()==5) {
            try {
                int f10 = ((NumberPattern) date.removeFirst()).getNumber();
                int f1 = ((NumberPattern) date.removeFirst()).getNumber();
                boolean divide = isSymbol(date.removeFirst(),Symbol.DIVIDE);
                int s10 = ((NumberPattern) date.removeFirst()).getNumber();
                int s1 = ((NumberPattern) date.removeFirst()).getNumber();
                if(divide)
                    m.setAttribute(MenuAttribute.DATE, new MenuDate((s10*10)+s1,(f10*10)+f1));
                else
                    m.setAttribute(MenuAttribute.DATE, new MenuDate((f10*10)+f1,(s10*10)+s1));

            }catch(Exception e)
            {
                e.printStackTrace();
                return null;
            }
        }
        else
            return null;

        return m;
    }

    private static Menu makeQuickInfo(LinkedList<Token>[] tokens) {
        Menu m = new Menu(MenuType.QUICK_INFO);
        LinkedList<Pattern> number = new LinkedList<>();

        int stage = 0;
        while (tokens[1].size()>0) {
            Token t = tokens[1].removeFirst();
            Pattern p = t.getPattern();
            switch (stage) {
                case 0:
                    if (isSymbol(p, Symbol.LARGE_AMPULE_FULL)) {
                        stage++;
                    } else
                        return null;
                    break;
                case 1:
                    if(p instanceof NumberPattern || isSymbol(p,Symbol.LARGE_DOT))
                    {
                        number.add(p);
                    }
                    else if(p instanceof CharacterPattern && ((CharacterPattern)p).getCharacter()=='u')
                    {
                        stage++;
                    }
                    else
                        return null;
                    break;
                case 3:
                    return null;
            }
        }
        double doubleNumber = 0d;
        String d = "";
        for(Pattern p : number)
        {
            if(p instanceof NumberPattern)
            {
                d+=""+((NumberPattern)p).getNumber();
            } else if(isSymbol(p,Symbol.LARGE_DOT)) {
                d += ".";
            } else {
                return null;//violation!
            }
        }
        try { doubleNumber = Double.parseDouble(d);}
        catch (Exception e){return null;}//violation, there must something parseable

        m.setAttribute(MenuAttribute.REMAINING_INSULIN,new Double(doubleNumber));

        //FIXME 4th line
        tokens[3].clear();

        return m;
    }

    private static String parseString(LinkedList<Token> tokens, boolean consume) {
        String s = "";
        Token last =null;
        for(Token t : new LinkedList<>(tokens))
        {
            Pattern p = t.getPattern();

            if(consume)
                tokens.removeFirst();

            if(last!=null)
            {
                int x = last.getColumn()+last.getWidth()+1+3;
                if(x < t.getColumn())
                {
                    s+=" ";
                }
            }
            if(p instanceof CharacterPattern)
            {
                s += ((CharacterPattern)p).getCharacter();
            }
            else if(isSymbol(p,Symbol.DOT))
            {
                s+=".";
            }
            else if(isSymbol(p,Symbol.SEPERATOR))
            {
                s+=":";
            }
            else if(isSymbol(p,Symbol.DIVIDE))
            {
                s+="/";
            }
            else if(isSymbol(p,Symbol.BRACKET_LEFT))
            {
                s+="(";
            }
            else if(isSymbol(p,Symbol.BRACKET_RIGHT))
            {
                s+=")";
            }
            else if(isSymbol(p,Symbol.MINUS))
            {
                s+="-";
            }
            else
            {
                return null;
            }
            last = t;
        }
        return s;
    }

    private static Menu makeBolusDuration(LinkedList<Token>[] tokens) {
        Menu m = new Menu(MenuType.BOLUS_DURATION);
        LinkedList<Integer> time = new LinkedList<>();

        int stage = 0;
        while (tokens[1].size()>0) {
            Token t = tokens[1].removeFirst();
            Pattern p = t.getPattern();
            switch (stage) {
                case 0:
                    if (isSymbol(p, Symbol.LARGE_ARROW)) {
                        stage++;
                    } else
                        return null;
                    break;
                case 1:
                case 2:
                case 4:
                case 5:
                    if (p instanceof NumberPattern) {
                        time.add(((NumberPattern) p).getNumber());
                        stage++;
                    } else
                        return null;
                    break;
                case 3:
                    if (isSymbol(p, Symbol.LARGE_SEPERATOR))
                        stage++;
                    else
                        return null;
                    break;
            }
        }
        if(time.size()==4)
        {
            int minute1 = time.removeLast();
            int minute10 = time.removeLast();
            int hour1 = time.removeLast();
            int hour10 = time.removeLast();
            m.setAttribute(MenuAttribute.RUNTIME,new MenuTime((hour10*10)+hour1,(minute10*10)+minute1));
        }
        else if(time.size()==0)
        {
            m.setAttribute(MenuAttribute.RUNTIME,new MenuBlink());
        }
        else
            return null;

        LinkedList<Pattern> number = new LinkedList<>();
        LinkedList<Pattern> number2 = new LinkedList<>();
        Symbol sym1 = null;
        Symbol sym2 = null;
        stage = 0;
        while (tokens[3].size()>0) {
            Token t = tokens[3].removeFirst();
            Pattern p = t.getPattern();
            switch (stage) {
                case 0:
                    if (isSymbol(p, Symbol.EXTENDED_BOLUS)) {
                        sym1 = Symbol.EXTENDED_BOLUS;
                        stage++;
                    } else if (isSymbol(p, Symbol.MULTIWAVE)) {
                        sym1 = Symbol.MULTIWAVE;
                        stage++;
                    } else
                        return null;
                    break;
                case 1:
                    if (p instanceof NumberPattern || isSymbol(p, Symbol.DOT)) {
                        number.add(p);
                    } else if (p instanceof CharacterPattern && ((CharacterPattern) p).getCharacter() == 'U') {
                        stage++;
                    } else
                        return null;
                    break;
                case 2:
                    if (isSymbol(p, Symbol.BOLUS)) {
                        sym2 = Symbol.BOLUS;
                        stage++;
                    } else
                        return null;
                    break;
                case 3:
                    if (p instanceof NumberPattern || isSymbol(p,Symbol.DOT)) {
                        number2.add(p);
                    } else if(p instanceof CharacterPattern && ((CharacterPattern)p).getCharacter()=='U') {
                        stage++;
                    } else
                        return null;
                    break;
            }
        }
        double doubleNumber = 0d;
        String d = "";
        for(Pattern p : number)
        {
            if(p instanceof NumberPattern)
            {
                d+=""+((NumberPattern)p).getNumber();
            } else if(isSymbol(p,Symbol.DOT)) {
                d += ".";
            } else {
                return null;//violation!
            }
        }
        try { doubleNumber = Double.parseDouble(d);}
        catch (Exception e){return null;}//violation, there must something parseable

        if(sym1 == Symbol.EXTENDED_BOLUS)
            m.setAttribute(MenuAttribute.BOLUS,new Double(doubleNumber));
        else if(sym1 == Symbol.MULTIWAVE) {
            m.setAttribute(MenuAttribute.BOLUS, new Double(doubleNumber));
            doubleNumber = 0d;
            d = "";
            for (Pattern p : number2) {
                if (p instanceof NumberPattern) {
                    d += "" + ((NumberPattern) p).getNumber();
                } else if (isSymbol(p, Symbol.DOT)) {
                    d += ".";
                } else {
                    return null;//violation!
                }
            }
            try {
                doubleNumber = Double.parseDouble(d);
            } catch (Exception e) {
                return null;
            }//violation, there must something parseable

            m.setAttribute(MenuAttribute.MULTIWAVE_BOLUS, new Double(doubleNumber));
        }
        return m;
    }

    private static Menu makeImmediateBolus(LinkedList<Token>[] tokens) {
        Menu m = new Menu(MenuType.IMMEDIATE_BOLUS);
        LinkedList<Pattern> number = new LinkedList<>();

        int stage = 0;
        while (tokens[1].size()>0) {
            Token t = tokens[1].removeFirst();
            Pattern p = t.getPattern();
            switch (stage) {
                case 0:
                    if (isSymbol(p, Symbol.LARGE_MULTIWAVE_BOLUS)) {
                        stage++;
                    } else
                        return null;
                    break;
                case 1:
                    if (p instanceof NumberPattern) {
                        number.add(p);
                    } else if(isSymbol(p,Symbol.LARGE_DOT)) {
                        number.add(p);
                    } else if(p instanceof CharacterPattern && ((CharacterPattern)p).getCharacter()=='u') {
                        stage++;
                    } else
                        return null;
                    break;
                case 3:
                    return null;
            }
        }

        double doubleNumber = 0d;
        String d = "";

        if(number.size()==0)
        {
            m.setAttribute(MenuAttribute.MULTIWAVE_BOLUS,new MenuBlink());
        }
        else {
            for (Pattern p : number) {
                if (p instanceof NumberPattern) {
                    d += "" + ((NumberPattern) p).getNumber();
                } else if (isSymbol(p, Symbol.LARGE_DOT)) {
                    d += ".";
                } else {
                    return null;//violation!
                }
            }
            try {
                doubleNumber = Double.parseDouble(d);
            } catch (Exception e) {
                return null;
            }//violation, there must something parseable

            m.setAttribute(MenuAttribute.MULTIWAVE_BOLUS, new Double(doubleNumber));
        }

        LinkedList<Integer> time = new LinkedList<>();
        number.clear();
        stage = 0;
        while (tokens[3].size() > 0) {
            Token t = tokens[3].removeFirst();
            Pattern p = t.getPattern();
            switch (stage) {
                case 0:
                    if (isSymbol(p, Symbol.ARROW)) {
                        stage++;
                    } else
                        return null;
                    break;
                case 1:
                case 2:
                case 4:
                case 5:
                    if (p instanceof NumberPattern) {
                        time.add(((NumberPattern) p).getNumber());
                        stage++;
                    } else
                        return null;
                    break;
                case 3:
                    if (isSymbol(p, Symbol.SEPERATOR))
                        stage++;
                    else
                        return null;
                    break;
                case 6:
                    if (isSymbol(p, Symbol.MULTIWAVE)) {
                        stage++;
                    } else
                        return null;
                    break;
                case 7:
                    if (p instanceof NumberPattern || isSymbol(p,Symbol.DOT)) {
                        number.add(p);
                    } else if(p instanceof CharacterPattern && ((CharacterPattern)p).getCharacter()=='U') {
                        stage++;
                    } else
                        return null;
                    break;
                case 8:
                    return null;
            }
        }
        if (time.size() == 4) {
            int minute1 = time.removeLast();
            int minute10 = time.removeLast();
            int hour1 = time.removeLast();
            int hour10 = time.removeLast();
            m.setAttribute(MenuAttribute.RUNTIME, new MenuTime((hour10 * 10) + hour1, (minute10 * 10) + minute1));
        }
        else
            return null;

        if(number.size()>0)
        {
            d="";
            for (Pattern p : number) {
                if (p instanceof NumberPattern) {
                    d += "" + ((NumberPattern) p).getNumber();
                } else if (isSymbol(p, Symbol.DOT)) {
                    d += ".";
                } else {
                    return null;//violation!
                }
            }
            try {
                doubleNumber = Double.parseDouble(d);
            } catch (Exception e) {
                return null;
            }//violation, there must something parseable

            m.setAttribute(MenuAttribute.BOLUS, new Double(doubleNumber));
        }
        else
            return null;
        return m;
    }

    private static Menu makeBolusEnter(LinkedList<Token>[] tokens) {
        Menu m = new Menu(MenuType.BOLUS_ENTER);
        LinkedList<Pattern> number = new LinkedList<>();

        int stage = 0;

        BolusType bt = null;
        //main part
        while (tokens[1].size()>0) {
            Token t = tokens[1].removeFirst();
            Pattern p = t.getPattern();
            switch (stage) {
                case 0:
                    if (isSymbol(p, Symbol.LARGE_BOLUS)) {
                        bt = BolusType.NORMAL;
                        stage++;
                    } else if (isSymbol(p, Symbol.LARGE_MULTIWAVE)) {
                        bt = BolusType.MULTIWAVE;
                        stage++;
                    } else if (isSymbol(p, Symbol.LARGE_EXTENDED_BOLUS)) {
                        bt = BolusType.EXTENDED;
                        stage++;
                    } else if(p instanceof NumberPattern) {
                        number.add(p);
                        stage++;
                    } else if(p instanceof CharacterPattern && ((CharacterPattern)p).getCharacter()=='u') {
                        stage=2;
                    }else
                        return null;
                    break;
                case 1:
                    if (p instanceof NumberPattern) {
                        number.add(p);
                    } else if(isSymbol(p,Symbol.LARGE_DOT)) {
                        number.add(p);
                    } else if(p instanceof CharacterPattern && ((CharacterPattern)p).getCharacter()=='u') {
                        stage++;
                    } else
                        return null;
                    break;
                case 2:
                    return null;
            }
        }

        if(bt!=null)
            m.setAttribute(MenuAttribute.BOLUS_TYPE,bt);
        else
            m.setAttribute(MenuAttribute.BOLUS_TYPE,new MenuBlink());

        double doubleNumber = 0d;
        String d = "";
        if(number.size()>0) {
            for (Pattern p : number) {
                if (p instanceof NumberPattern) {
                    d += "" + ((NumberPattern) p).getNumber();
                } else if (isSymbol(p, Symbol.LARGE_DOT)) {
                    d += ".";
                } else {
                    return null;//violation!
                }
            }
            try {
                doubleNumber = Double.parseDouble(d);
            } catch (Exception e) {
                return null;
            }//violation, there must something parseable

            m.setAttribute(MenuAttribute.BOLUS, new Double(doubleNumber));
        } else
            m.setAttribute(MenuAttribute.BOLUS,new MenuBlink());

        //4th line
        LinkedList<Integer> time = new LinkedList<>();
        number.clear();
        stage = 0;
        while (tokens[3].size() > 0) {
            Token t = tokens[3].removeFirst();
            Pattern p = t.getPattern();
            switch (stage) {
                case 0:
                    if (isSymbol(p, Symbol.ARROW)) {
                        stage++;
                    } else
                        return null;
                    break;
                case 1:
                case 2:
                case 4:
                case 5:
                    if (p instanceof NumberPattern) {
                        time.add(((NumberPattern) p).getNumber());
                        stage++;
                    } else
                        return null;
                    break;
                case 3:
                    if (isSymbol(p, Symbol.SEPERATOR))
                        stage++;
                    else
                        return null;
                    break;
                case 6:
                    if (isSymbol(p, Symbol.BOLUS)) {
                        stage++;
                    } else
                        return null;
                    break;
                case 7:
                    if (p instanceof NumberPattern) {
                        number.add(p);
                    } else if(isSymbol(p,Symbol.DOT)) {
                        number.add(p);
                    } else if(p instanceof CharacterPattern && ((CharacterPattern)p).getCharacter()=='U') {
                        stage++;
                    } else
                        return null;
                    break;
                case 8:
                    return null;
            }
        }
        if(time.size()>0)
        {
            int minute1 = time.removeLast();
            int minute10 = time.removeLast();
            int hour1 = time.removeLast();
            int hour10 = time.removeLast();
            m.setAttribute(MenuAttribute.RUNTIME, new MenuTime((hour10 * 10) + hour1, (minute10 * 10) + minute1));

            if(number.size() > 0)
            {
                doubleNumber = 0d;
                d = "";
                for(Pattern p : number)
                {
                    if(p instanceof NumberPattern)
                    {
                        d+=""+((NumberPattern)p).getNumber();
                    } else if(isSymbol(p,Symbol.DOT)) {
                        d += ".";
                    } else if(p instanceof CharacterPattern && ((CharacterPattern)p).getCharacter()=='U'){
                        //irgnore
                    } else {
                        return null;//violation!
                    }
                }
                try { doubleNumber = Double.parseDouble(d);}
                catch (Exception e){return null;}//violation, there must something parseable

                m.setAttribute(MenuAttribute.MULTIWAVE_BOLUS, new Double(doubleNumber));
            }
        }
        else
        {
            m.setAttribute(MenuAttribute.RUNTIME, new MenuTime(0,0));
            m.setAttribute(MenuAttribute.MULTIWAVE_BOLUS, new Double(0));
        }
        return m;
    }

    private static Menu makeMainMenu(LinkedList<Token>[] tokens) {
        Menu m = new Menu(MenuType.MAIN_MENU);
        LinkedList<Integer> time = new LinkedList<>();
        LinkedList<Integer> runtime = new LinkedList<>();
        LinkedList<Character> timeC = new LinkedList<>();
        boolean hasRunning=false;

        int stage = 0;
        while(tokens[0].size()>0)
        {
            Token t = tokens[0].removeFirst();
            Pattern p = t.getPattern();
            switch(stage)
            {
                case 0://clock
                    if(!isSymbol(p,Symbol.CLOCK))
                        return null;//wrong
                    stage++;
                    break;
                case 1://hour10
                case 2://hour1
                case 4://minute10
                case 5://minute1
                    if (p instanceof NumberPattern) {
                        time.add(((NumberPattern) p).getNumber());
                    } else
                        return null;//Wrong
                    stage++;
                    break;
                case 3://: or number (: blinks)
                    if(isSymbol(p,Symbol.SEPERATOR))
                    {
                        stage++;
                    }
                    else if (p instanceof NumberPattern) {
                        time.add(((NumberPattern) p).getNumber());
                        stage += 2;
                    }
                    else
                        return null;//wr
                    break;
                case 6://P(m), A(M), or running
                    if(p instanceof CharacterPattern) {
                        timeC.add(((CharacterPattern) p).getCharacter());
                        stage++;
                    } else if(isSymbol(p,Symbol.ARROW)) {
                        hasRunning = true;
                        stage = 9;
                    } else
                        return null;//wrong
                    break;
                case 7://it should be an M
                    if(p instanceof CharacterPattern) {
                        timeC.add(((CharacterPattern) p).getCharacter());
                        stage++;
                    } else
                        return null;//nothing else matters
                    break;
                case 8://can onbly be running arrow
                    if(isSymbol(p,Symbol.ARROW)) {
                        hasRunning = true;
                        stage++;
                    } else
                        return null;
                    break;
                case 9://h10
                case 10://h1
                case 12://m10
                case 13://m1
                    if (p instanceof NumberPattern) {
                        runtime.add(((NumberPattern) p).getNumber());
                    } else
                        return null;//Wrong
                    stage++;
                    break;
                case 11://: or number (: blinks)
                    if(isSymbol(p,Symbol.SEPERATOR))
                    {
                        stage++;
                    }
                    else
                        return null;//wr
                    break;
                default:
                    return null;//the impossible girl
            }
        }
        //set time
        int minute1 = time.removeLast();
        int minute10 = time.removeLast();
        int hour1 = time.removeLast();
        int hour10 = 0;
        if(time.size()>0)
            hour10 = time.removeLast();

        int tadd = 0;
        if(timeC.size()>0)
        {
            if(timeC.get(0)=='P' && timeC.get(1)=='M' && !(hour10==1 && hour1 == 2))
            {
                tadd += 12;
            }
            else if(timeC.get(0)=='A' && timeC.get(1)=='M' && hour10 == 1 && hour1 == 2)
            {
                tadd -= 12;
            }
        }
        m.setAttribute(MenuAttribute.TIME,new MenuTime((hour10*10)+tadd+hour1,(minute10*10)+minute1));

        if(hasRunning) {
            minute1 = runtime.removeLast();
            minute10 = runtime.removeLast();
            hour1 = runtime.removeLast();
            hour10 = runtime.removeLast();
            m.setAttribute(MenuAttribute.RUNTIME, new MenuTime((hour10 * 10) + hour1, (minute10 * 10) + minute1));
        }

        stage = 0;
        BolusType bt = null;
        int tbr = 0;
        LinkedList<Pattern> number = new LinkedList<>();

        while(tokens[1].size()>0) {
            Token t = tokens[1].removeFirst();
            Pattern p = t.getPattern();
            switch (stage) {
                case 0:
                    if (isSymbol(p, Symbol.SEPERATOR.LARGE_EXTENDED_BOLUS)) {
                        bt = BolusType.EXTENDED;
                        stage++;
                    } else if (isSymbol(p, Symbol.SEPERATOR.LARGE_MULTIWAVE)) {
                        bt = BolusType.MULTIWAVE_EXTENDED;
                        stage++;
                    }
                    else if(isSymbol(p,Symbol.SEPERATOR.LARGE_MULTIWAVE_BOLUS))
                    {
                        bt = BolusType.MULTIWAVE_BOLUS;
                        stage++;
                    }
                    else if(isSymbol(p,Symbol.SEPERATOR.LARGE_BOLUS))
                    {
                        bt = BolusType.NORMAL;
                        stage++;
                    }
                    else if (isSymbol(p, Symbol.SEPERATOR.LARGE_BASAL)) {
                        bt = null;
                        stage++;
                    } else {
                        return null;
                    }
                    break;
                case 1:
                    if (isSymbol(p, Symbol.UP)) {
                        tbr = 1;
                        stage++;
                    } else if (isSymbol(p, Symbol.DOWN)) {
                        tbr = 2;
                        stage++;
                    } else if (p instanceof NumberPattern) {
                        number.add(p);
                        stage += 2;
                    } else
                        return null;//
                    break;
                case 2:
                    if (p instanceof NumberPattern) {
                        number.add(p);
                        stage++;
                    } else
                        return null;//
                    break;
                case 3:
                    if (p instanceof NumberPattern) {
                        number.add(p);
                    } else if (p instanceof CharacterPattern && ((CharacterPattern)p).getCharacter()=='u') {
                        number.add(p);
                    } else if (isSymbol(p, Symbol.LARGE_DOT) || isSymbol(p, Symbol.LARGE_PERCENT) || isSymbol(p,Symbol.LARGE_UNITS_PER_HOUR)) {
                        number.add(p);
                    } else
                        return null;//
                    break;
            }
        }
        double doubleNUmber = 0d;
        String d = "";
        for(Pattern p : number)
        {
            if(p instanceof NumberPattern)
            {
                d+=""+((NumberPattern)p).getNumber();
            } else if(isSymbol(p,Symbol.LARGE_DOT)) {
                d += ".";
            } else if(isSymbol(p,Symbol.LARGE_PERCENT) ||
                    isSymbol(p,Symbol.LARGE_UNITS_PER_HOUR) ||
                    (p instanceof CharacterPattern && ((CharacterPattern)p).getCharacter()=='u')){
                //irgnore
            } else {
                return null;//violation!
            }
        }
        try { doubleNUmber = Double.parseDouble(d);}
        catch (Exception e){return null;}//violation, there must something parseable

        if(bt != null)
        {
            //running bolus
            m.setAttribute(MenuAttribute.BOLUS_TYPE,bt);
            m.setAttribute(MenuAttribute.BOLUS_REMAINING,doubleNUmber);
        }
        else
        {
            switch(tbr)
            {
                case 0:
                    m.setAttribute(MenuAttribute.TBR,new Double(100));
                    m.setAttribute(MenuAttribute.BASAL_RATE,doubleNUmber);
                    break;
                case 1:
                case 2:
                    m.setAttribute(MenuAttribute.TBR,new Double(doubleNUmber));
                    break;
            }
        }

        if(tokens[2].size()==1 && tokens[2].get(0).getPattern() instanceof NumberPattern)
            m.setAttribute(MenuAttribute.BASAL_SELECTED,new Integer(((NumberPattern)tokens[2].removeFirst().getPattern()).getNumber()));
        else
            return null;

        stage = 0;
        number.clear();
        int lowInsulin = 0;
        int lowBattery= 0;
        boolean waranty = true;

        int lockState = 0;
        while(tokens[3].size()>0) {
            Token t = tokens[3].removeFirst();
            Pattern p = t.getPattern();
            switch (stage) {
                case 0:
                    if (p instanceof NumberPattern) {
                        number.add(p);
                    } else if (isSymbol(p, Symbol.DOT)) {
                        number.add(p);
                    } else if (isSymbol(p, Symbol.UNITS_PER_HOUR)) {
                        number.add(p);
                        stage++;
                    } else if (isSymbol(p, Symbol.LOW_BAT)) {
                        lowBattery = 1;
                    } else if (isSymbol(p, Symbol.NO_BAT)) {
                        lowBattery = 2;
                    } else if (isSymbol(p, Symbol.LOW_INSULIN)) {
                        lowInsulin= 1;
                    } else if (isSymbol(p, Symbol.NO_INSULIN)) {
                        lowInsulin= 2;
                    } else if (isSymbol(p, Symbol.LOCK_CLOSED)) {
                        lockState=2;
                    } else if (isSymbol(p, Symbol.LOCK_OPENED)) {
                        lockState=2;
                    } else if (isSymbol(p, Symbol.WARANTY)) {
                        waranty = false;
                    } else {
                        return null;
                    }
                    break;
                case 1:
                    if (isSymbol(p, Symbol.LOW_BAT)) {
                        lowBattery = 1;
                    } else if (isSymbol(p, Symbol.NO_BAT)) {
                        lowBattery = 2;
                    } else if (isSymbol(p, Symbol.LOW_INSULIN)) {
                        lowInsulin = 1;
                    } else if (isSymbol(p, Symbol.NO_INSULIN)) {
                        lowInsulin= 2;
                    } else if (isSymbol(p, Symbol.LOCK_CLOSED)) {
                        lockState=2;
                    } else if (isSymbol(p, Symbol.LOCK_OPENED)) {
                        lockState=2;
                    } else if (isSymbol(p, Symbol.WARANTY)) {
                        waranty = false;
                    } else {
                        return null;
                    }
                    break;
            }
        }

        m.setAttribute(MenuAttribute.BATTERY_STATE,lowBattery);
        m.setAttribute(MenuAttribute.INSULIN_STATE,lowInsulin);
        m.setAttribute(MenuAttribute.WARANTY,new Boolean(waranty));

        m.setAttribute(MenuAttribute.LOCK_STATE,new Integer(lockState));

        if(number.size()>0) {
            doubleNUmber = 0d;
            d = "";
            for (Pattern p : number) {
                if (p instanceof NumberPattern) {
                    d += "" + ((NumberPattern) p).getNumber();
                } else if (isSymbol(p, Symbol.DOT)) {
                    d += ".";
                } else if (isSymbol(p, Symbol.UNITS_PER_HOUR)) {
                    //irgnore
                } else {
                    return null;//violation!
                }
            }
            try {
                doubleNUmber = Double.parseDouble(d);
            } catch (Exception e) {
                return null;
            }//violation, there must something parseable
            m.setAttribute(MenuAttribute.BASAL_RATE, doubleNUmber);
        }

        return m;
    }


    private static boolean isSymbol(Pattern p, Symbol symbol) {
        return (p instanceof SymbolPattern) && ((SymbolPattern) p).getSymbol() == symbol;
    }
}
