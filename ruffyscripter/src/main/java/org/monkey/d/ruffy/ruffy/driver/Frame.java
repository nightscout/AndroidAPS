package org.monkey.d.ruffy.ruffy.driver;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fishermen21 on 16.05.17.
 */

public class Frame {
    public static List<Byte> frameEscape(List<Byte> out)
    {
        List<Byte> temp = new ArrayList();
        temp.add((byte)-52);
        for (int i = 0; i < out.size(); i++) {
            if (((Byte) out.get(i)).byteValue() == -52) {
                temp.add((byte)119);
                temp.add((byte)-35);
            } else if (((Byte) out.get(i)).byteValue() == (byte) 119) {
                temp.add((byte) 119);
                temp.add((byte) -18);
            } else {
                temp.add((Byte) out.get(i));
            }
        }
        temp.add((byte)-52);
        return temp;
    }

    private static List<Byte> packet = new ArrayList<Byte>();
    private static boolean start = false, stop = false, escaped = false;

    public static List<List<Byte>> frameDeEscaping(List<Byte> buffer)
    {
        List<List<Byte>> complete = new ArrayList<List<Byte>>();

        if(start)
        {

        }
        else
        {
            start = stop = escaped = false;
            packet.clear();
        }

        for(int i=0;i<buffer.size();i++)
        {
            if(escaped == true)
            {
                escaped = false;
                if(buffer.get(i) == -35)
                {
                    packet.add((byte)-52);
                }
                else if(buffer.get(i) == -18)
                {
                    packet.add((byte)119);
                }
            }
            else if(buffer.get(i) == 119)
            {
                if(i+1 >= buffer.size())
                {
                    escaped = true;				//If we are at the end of the buffer and find an escape character
                }
                else
                {
                    Byte next = buffer.get(i+1);
                    if(next == -35)
                    {
                        packet.add((byte)-52);
                        i++;								//Skip the next byte
                    }
                    else if(next == -18)
                    {
                        packet.add((byte)119);			//Skip the next byte
                        i++;
                    }
                }
            }
            else if(buffer.get(i) == -52)	//We need to cover the chance that there are multiple packets in the buffer
            {
                if(!start)
                {
                    start = true;
                }
                else
                {
                    stop = true;
                }

                if(start && stop)
                {
                    start = false;
                    stop = false;

                    if(packet.size() == 0)
                    {
                        start = true;
                        stop = false;
                    }
                    else if(i == 0)
                    {
                        start = true;
                        stop = false;
                    }
                    else
                    {
                        complete.add(packet);
                        packet = new ArrayList<Byte>();
                    }
                }
            }
            else
            {
                if(start)
                    packet.add(buffer.get(i));
            }
        }

        return complete;
    }
}
