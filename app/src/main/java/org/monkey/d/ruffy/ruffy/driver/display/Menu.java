package org.monkey.d.ruffy.ruffy.driver.display;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import org.monkey.d.ruffy.ruffy.driver.display.menu.BolusType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuBlink;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuDate;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuTime;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by fishermen21 on 20.05.17.
 */

public class Menu implements Parcelable{
    private MenuType type;
    private Map<MenuAttribute,Object> attributes = new HashMap<>();

    public Menu(MenuType type)
    {
        this.type = type;
    }

    public Menu(Parcel in) {
        this.type = MenuType.valueOf(in.readString());
        while(in.dataAvail()>0) {
            try {
                String attr = in.readString();
                String clas = in.readString();
                String value = in.readString();

                if(attr!=null && clas!=null && value!=null) {
                    MenuAttribute a = MenuAttribute.valueOf(attr);
                    Object o = null;
                    if (Integer.class.toString().equals(clas)) {
                        o = new Integer(value);
                    } else if (Double.class.toString().equals(clas)) {
                        o = new Double(value);
                    } else if (Boolean.class.toString().equals(clas)) {
                        o = new Boolean(value);
                    } else if (MenuDate.class.toString().equals(clas)) {
                        o = new MenuDate(value);
                    } else if (MenuTime.class.toString().equals(clas)) {
                        o = new MenuTime(value);
                    } else if (MenuBlink.class.toString().equals(clas)) {
                        o = new MenuBlink();
                    } else if (BolusType.class.toString().equals(clas)) {
                        o = BolusType.valueOf(value);
                    } else if (String.class.toString().equals(clas)) {
                        o = new String(value);
                    }

                    if (o != null) {
                        attributes.put(a, o);
                    } else {
                        Log.e("MenuIn", "failed to parse: " + attr + " / " + clas + " / " + value);
                    }
                }
            }catch(Exception e)
            {
                Log.e("MenuIn","Exception in read",e);
            }

        }
    }

    public void setAttribute(MenuAttribute key, Object value)
    {
        attributes.put(key,value);
    }

    public List<MenuAttribute> attributes()
    {
        return new LinkedList<MenuAttribute>(attributes.keySet());
    }

    public Object getAttribute(MenuAttribute key)
    {
        return attributes.get(key);
    }

    public MenuType getType() {
        return type;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(type.toString());
        for(MenuAttribute a : attributes.keySet())
        {
            try
            {
                dest.writeString(a.toString());
                Object o = attributes.get(a);

                dest.writeString(o.getClass().toString());
                dest.writeString(o.toString());
            }catch(Exception e)
            {
                Log.v("MenuOut","error in write",e);
            }
        }
    }
    public static final Creator<Menu> CREATOR = new
            Creator<Menu>() {
                public Menu createFromParcel(Parcel in) {
                    return new Menu(in);
                }

                public Menu[] newArray(int size) {
                    return new Menu[size];
                }
            };

    @Override
    public String toString() {
        String to = "Menu: "+getType()+" atr:";
        for(MenuAttribute atr : attributes())
            to+=atr.toString()+"="+getAttribute(atr)+";";
        return to;
    }
}
