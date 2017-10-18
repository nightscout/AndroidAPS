package org.monkey.d.ruffy.ruffy.driver.display;

import org.monkey.d.ruffy.ruffy.driver.display.parser.Pattern;

/**
 * Created by fishermen21 on 21.05.17.
 */

public class Token {
    private final Pattern pattern;
    private final int block;
    private final int column;

    public Token(Pattern pattern, int block, int x) {
        this.pattern = pattern;
        this.block = block;
        this.column = x;
    }

    public int getWidth() {
        return pattern.getWidth();
    }

    @Override
    public String toString() {
        return pattern+" at ["+block+" / "+column+"]";
    }

    public Pattern getPattern() {
        return pattern;
    }

    public int getBlock() {
        return block;
    }

    public int getColumn() {
        return column;
    }
}
