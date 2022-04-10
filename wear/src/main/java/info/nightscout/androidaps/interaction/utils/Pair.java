package info.nightscout.androidaps.interaction.utils;

import java.util.Objects;

/**
 * Same as android Pair, but clean room java class - does not require Android SDK for tests
 */
public class Pair<F, S> {

    public final F first;
    public final S second;

    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }

    public static <F, S> Pair<F, S> create(F f, S s) {
        return new Pair<>(f, s);
    }

    @Override
    public boolean equals(java.lang.Object o) {
        if (o instanceof Pair) {
            return ((Pair) o).first.equals(first) && ((Pair) o).second.equals(second);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "First: \""+first.toString()+"\" Second: \""+second.toString()+"\"";
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }

}


