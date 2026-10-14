package com.pppopipupu.fxeasy.client;

import module java.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxTutorialTest {

    private static final int STEPS = 3;

    @Test
    void walksForwardUntilTheStepAfterTheLast() {
        FxTutorial tutorial = new FxTutorial();
        tutorial.start();
        assertTrue(tutorial.active());
        assertEquals(0, tutorial.step());
        assertFalse(tutorial.apply(FxTutorial.Action.NEXT, STEPS));
        assertEquals(1, tutorial.step());
        assertFalse(tutorial.apply(FxTutorial.Action.NEXT, STEPS));
        assertEquals(2, tutorial.step());
        assertTrue(tutorial.apply(FxTutorial.Action.NEXT, STEPS));
        assertFalse(tutorial.active());
    }

    @Test
    void backsUpButNotBeforeTheFirstStep() {
        FxTutorial tutorial = new FxTutorial();
        tutorial.start();
        tutorial.apply(FxTutorial.Action.NEXT, STEPS);
        assertFalse(tutorial.apply(FxTutorial.Action.BACK, STEPS));
        assertEquals(0, tutorial.step());
        assertFalse(tutorial.apply(FxTutorial.Action.BACK, STEPS));
        assertEquals(0, tutorial.step());
        assertTrue(tutorial.active());
    }

    @Test
    void skipFinishesFromAnyStep() {
        FxTutorial tutorial = new FxTutorial();
        tutorial.start();
        tutorial.apply(FxTutorial.Action.NEXT, STEPS);
        assertTrue(tutorial.apply(FxTutorial.Action.FINISH, STEPS));
        assertFalse(tutorial.active());
    }

    @Test
    void ignoresFramesWithoutAClick() {
        FxTutorial tutorial = new FxTutorial();
        tutorial.start();
        assertFalse(tutorial.apply(FxTutorial.Action.NONE, STEPS));
        assertTrue(tutorial.active());
        assertEquals(0, tutorial.step());
    }

    @Test
    void startsOverFromTheFirstStep() {
        FxTutorial tutorial = new FxTutorial();
        tutorial.start();
        tutorial.apply(FxTutorial.Action.NEXT, STEPS);
        tutorial.start();
        assertTrue(tutorial.active());
        assertEquals(0, tutorial.step());
    }
}
