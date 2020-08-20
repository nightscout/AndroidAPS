package info.nightscout.androidaps.plugins.pump.omnipod.defs;

import java.util.Arrays;

public class NonceState {
    private final long[] table = new long[21];
    private int index;

    public NonceState(int lot, int tid) {
        initializeTable(lot, tid, (byte) 0x00);
    }

    public NonceState(int lot, int tid, byte seed) {
        initializeTable(lot, tid, seed);
    }

    private void initializeTable(int lot, int tid, byte seed) {
        table[0] = (long) (lot & 0xFFFF) + 0x55543DC3L + (((long) (lot) & 0xFFFFFFFFL) >> 16);
        table[0] = table[0] & 0xFFFFFFFFL;
        table[1] = (tid & 0xFFFF) + 0xAAAAE44EL + (((long) (tid) & 0xFFFFFFFFL) >> 16);
        table[1] = table[1] & 0xFFFFFFFFL;
        index = 0;
        table[0] += seed;
        for (int i = 0; i < 16; i++) {
            table[2 + i] = generateEntry();
        }
        index = (int) ((table[0] + table[1]) & 0X0F);
    }

    private int generateEntry() {
        table[0] = (((table[0] >> 16) + (table[0] & 0xFFFF) * 0x5D7FL) & 0xFFFFFFFFL);
        table[1] = (((table[1] >> 16) + (table[1] & 0xFFFF) * 0x8CA0L) & 0xFFFFFFFFL);
        return (int) ((table[1] + (table[0] << 16)) & 0xFFFFFFFFL);
    }

    public int getCurrentNonce() {
        return (int) table[(2 + index)];
    }

    public void advanceToNextNonce() {
        int nonce = getCurrentNonce();
        table[(2 + index)] = generateEntry();
        index = (nonce & 0x0F);
    }

    @Override
    public String toString() {
        return "NonceState{" +
                "table=" + Arrays.toString(table) +
                ", index=" + index +
                '}';
    }
}
