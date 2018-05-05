package info.nightscout.androidaps.data;

/**
 * Created by mike on 05.06.2016.
 */
public class Iob {
    public double iobContrib = 0d;
    public double activityContrib = 0d;

    public Iob iobContrib(double iobContrib) {
        this.iobContrib = iobContrib;
        return this;
    }

    public Iob activityContrib(double activityContrib) {
        this.activityContrib = activityContrib;
        return this;
    }

    public Iob plus(Iob iob) {
        iobContrib += iob.iobContrib;
        activityContrib += iob.activityContrib;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Iob iob = (Iob) o;

        if (Double.compare(iob.iobContrib, iobContrib) != 0) return false;
        return Double.compare(iob.activityContrib, activityContrib) == 0;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(iobContrib);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(activityContrib);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}
