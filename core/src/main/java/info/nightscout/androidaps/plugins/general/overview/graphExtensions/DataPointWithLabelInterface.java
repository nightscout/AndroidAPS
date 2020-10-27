package info.nightscout.androidaps.plugins.general.overview.graphExtensions;
/**
 * GraphView
 * Copyright (C) 2014  Jonas Gehring
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License,
 * with the "Linking Exception", which can be found at the license.txt
 * file in this program.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * with the "Linking Exception" along with this program; if not,
 * write to the author Jonas Gehring <g.jjoe64@gmail.com>.
 */

/**
 * Added by mike
 */

import com.jjoe64.graphview.series.DataPointInterface;

/**
 * interface of data points. Implement this in order
 * to use your class in {@link com.jjoe64.graphview.series.Series}.
 *
 * You can also use the default implementation {@link com.jjoe64.graphview.series.DataPoint} so
 * you do not have to implement it for yourself.
 *
 * @author jjoe64
 */
public interface DataPointWithLabelInterface extends DataPointInterface{
    /**
     * @return the x value
     */
    double getX();

    /**
     * @return the y value
     */
    double getY();
    void setY(double y);

    /**
     * @return the label value
     */
    String getLabel();

    long getDuration();
    PointsWithLabelGraphSeries.Shape getShape();
    float getSize();
    int getColor();
}
