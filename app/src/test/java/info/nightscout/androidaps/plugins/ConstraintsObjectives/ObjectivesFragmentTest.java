package info.nightscout.androidaps.plugins.ConstraintsObjectives;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ObjectivesFragmentTest {

    @Test
    public void testModifyVisibility() {

        ObjectivesFragment fragment = new ObjectivesFragment();

        int currentPosition = 1;
        long prevObjectiveAccomplishedTime = 0;
        long objectiveStartedTime = 0;
        int durationInDays = 0;
        long objectiveAccomplishedTime = 0;
        boolean requirementsMet = false;
        boolean enableFakeValue = false;

        // previous objective is not accomplished yet
        assertEquals(0, fragment.modifyVisibility(currentPosition, prevObjectiveAccomplishedTime,
                objectiveStartedTime, durationInDays, objectiveAccomplishedTime, requirementsMet, enableFakeValue));

        // not started yet
        prevObjectiveAccomplishedTime = 4711;
        assertEquals(1, fragment.modifyVisibility(currentPosition, prevObjectiveAccomplishedTime,
                objectiveStartedTime, durationInDays, objectiveAccomplishedTime, requirementsMet, enableFakeValue));

        // started
        // time calculation is true, requirements met is false
        objectiveStartedTime = Long.MAX_VALUE;
        durationInDays = 0;
        assertEquals(2, fragment.modifyVisibility(currentPosition, prevObjectiveAccomplishedTime,
                objectiveStartedTime, durationInDays, objectiveAccomplishedTime, requirementsMet, enableFakeValue));

        // started
        // time calculation is true, requirements met is true
        objectiveStartedTime = 10;
        durationInDays = 0;
        requirementsMet = true;
        assertEquals(3, fragment.modifyVisibility(currentPosition, prevObjectiveAccomplishedTime,
                objectiveStartedTime, durationInDays, objectiveAccomplishedTime, requirementsMet, enableFakeValue));

        // finished
        objectiveStartedTime = Long.MAX_VALUE;
        durationInDays = 0;
        requirementsMet = true;
        objectiveAccomplishedTime = Long.MAX_VALUE;
        assertEquals(4, fragment.modifyVisibility(currentPosition, prevObjectiveAccomplishedTime,
                objectiveStartedTime, durationInDays, objectiveAccomplishedTime, requirementsMet, enableFakeValue));


    }

}