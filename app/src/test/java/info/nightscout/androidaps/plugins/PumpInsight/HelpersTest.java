package info.nightscout.androidaps.plugins.PumpInsight;


import com.google.common.truth.Truth;

import org.junit.Test;

import static info.nightscout.androidaps.plugins.PumpInsight.utils.Helpers.roundDouble;

/**
 * Created by jamorham on 26.01.2018.
 */

public class HelpersTest {

    @Test
    public void checkRounding() throws Exception {

        // TODO more test cases including known precision breakdowns

        Truth.assertThat(roundDouble(Double.parseDouble("0.999999"),0))
                .isEqualTo(1d);

        Truth.assertThat(roundDouble(Double.parseDouble("0.123456"),0))
                .isEqualTo(0d);

    }



}