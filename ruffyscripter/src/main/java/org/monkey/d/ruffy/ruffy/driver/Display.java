package org.monkey.d.ruffy.ruffy.driver;

import java.nio.ByteBuffer;

public class Display {

    private final DisplayUpdater updater;
    private int index = -1;
    private CompleteDisplayHandler completeHandler;
    private boolean[] complete = {false,false,false,false};
    private byte[][] displayBytes = new byte[4][];

    public Display(DisplayUpdater updater)
    {
        this.updater = updater;
    }

    public void setCompletDisplayHandler(CompleteDisplayHandler completeHandler) {this.completeHandler = completeHandler;}

    private void update(byte[] rowBytes, boolean quarter[][], int which, int index)
    {
        updater.update(rowBytes,which);
        if(this.index==index)
        {
            complete[which]=true;

            this.displayBytes[which] = rowBytes;
            if(this.completeHandler != null && complete[0] && complete[1] && complete[2] && complete[3])
                completeHandler.handleCompleteFrame(this.displayBytes);

        }
        else
        {
            this.index = index;
            complete = new boolean[]{false,false,false,false};
            complete[which] = true;

            this.displayBytes= new byte[4][];
            this.displayBytes[which] = rowBytes;
        }
    }

    public void addDisplayFrame(ByteBuffer b)
    {
        //discard first 3
        b.getShort();
        b.get();
        int index = (int)(b.get() & 0xFF);
        byte row = b.get();

        byte[] displayBytes = new byte[96];		//New array
        b.get(displayBytes);						//Read in array from packet

        boolean[][] quarter = new boolean[8][96];

        int column = 96;
        for(byte d:displayBytes)
        {
            column--;

            quarter[0][column] = ((d & 0x01)!=0);
            quarter[1][column] = ((d & 0x02)!=0);
            quarter[2][column] = ((d & 0x04)!=0);
            quarter[3][column] = ((d & 0x08)!=0);
            quarter[4][column] = ((d & 0x10)!=0);
            quarter[5][column] = ((d & 0x20)!=0);
            quarter[6][column] = ((d & 0x40)!=0);
            quarter[7][column] = ((d & 0x80)!=0);
        }

        switch(row)
        {
            case 0x47:
                update(displayBytes,quarter,0,index);
                break;
            case 0x48:
                update(displayBytes,quarter,1,index);
                break;
            case (byte)0xB7:
                update(displayBytes,quarter,2,index);
                break;
            case (byte)0xB8:
                update(displayBytes,quarter,3,index);
                break;
        }
    }
}