package com.pppopipupu.fxeasy.client;

import module java.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxAccountOpeningTest {

    private static final int STEPS = 3;
    private static final String NAME = "kurumi";
    private static final int AGE = 21;

    @Test
    void walksForwardToTheRegistration() {
        FxAccountOpening opening = new FxAccountOpening();
        opening.start();
        assertTrue(opening.active());
        assertEquals(0, opening.step());
        assertFalse(opening.apply(FxAccountOpening.Action.NEXT, STEPS, NAME, AGE));
        assertEquals(1, opening.step());
        assertFalse(opening.apply(FxAccountOpening.Action.NEXT, STEPS, NAME, AGE));
        assertEquals(2, opening.step());
        assertTrue(opening.active());
    }

    @Test
    void registeringEndsTheFlow() {
        FxAccountOpening opening = new FxAccountOpening();
        opening.start();
        opening.apply(FxAccountOpening.Action.NEXT, STEPS, NAME, AGE);
        opening.apply(FxAccountOpening.Action.NEXT, STEPS, NAME, AGE);
        assertTrue(opening.apply(FxAccountOpening.Action.OPEN, STEPS, NAME, AGE));
        assertFalse(opening.active());
    }

    @Test
    void aMinorCannotRegister() {
        FxAccountOpening opening = new FxAccountOpening();
        opening.start();
        opening.apply(FxAccountOpening.Action.NEXT, STEPS, NAME, AGE);
        opening.apply(FxAccountOpening.Action.NEXT, STEPS, NAME, AGE);
        assertFalse(opening.apply(FxAccountOpening.Action.OPEN, STEPS, NAME, FxAccountOpening.MIN_AGE - 1));
        assertTrue(opening.active());
        assertEquals("fxeasy.opening.register.underage", opening.message());
    }

    @Test
    void aNamelessOrAgelessRegistrationIsRefused() {
        FxAccountOpening opening = new FxAccountOpening();
        opening.start();
        opening.apply(FxAccountOpening.Action.NEXT, STEPS, NAME, AGE);
        opening.apply(FxAccountOpening.Action.NEXT, STEPS, NAME, AGE);
        assertFalse(opening.apply(FxAccountOpening.Action.OPEN, STEPS, "   ", AGE));
        assertEquals("fxeasy.opening.register.nameless", opening.message());
        assertFalse(opening.apply(FxAccountOpening.Action.OPEN, STEPS, NAME, 0));
        assertEquals("fxeasy.opening.register.ageless", opening.message());
        assertTrue(opening.active());
    }

    @Test
    void theAgeOfMajorityIsTheBoundary() {
        assertEquals("", FxAccountOpening.complaint(NAME, FxAccountOpening.MIN_AGE));
        assertEquals("", FxAccountOpening.complaint(NAME, 99));
        assertEquals("fxeasy.opening.register.underage", FxAccountOpening.complaint(NAME, FxAccountOpening.MIN_AGE - 1));
    }

    @Test
    void backsUpToTheFirstStepAndNoFurther() {
        FxAccountOpening opening = new FxAccountOpening();
        opening.start();
        opening.apply(FxAccountOpening.Action.NEXT, STEPS, NAME, AGE);
        assertFalse(opening.apply(FxAccountOpening.Action.BACK, STEPS, NAME, AGE));
        assertEquals(0, opening.step());
        assertFalse(opening.apply(FxAccountOpening.Action.BACK, STEPS, NAME, AGE));
        assertEquals(0, opening.step());
        assertTrue(opening.active());
    }

    @Test
    void nextNeverLeavesTheLastStep() {
        FxAccountOpening opening = new FxAccountOpening();
        opening.start();
        opening.apply(FxAccountOpening.Action.NEXT, STEPS, NAME, AGE);
        opening.apply(FxAccountOpening.Action.NEXT, STEPS, NAME, AGE);
        assertFalse(opening.apply(FxAccountOpening.Action.NEXT, STEPS, NAME, AGE));
        assertEquals(2, opening.step());
        assertTrue(opening.active());
    }

    @Test
    void ignoresFramesWithoutAClick() {
        FxAccountOpening opening = new FxAccountOpening();
        opening.start();
        assertFalse(opening.apply(FxAccountOpening.Action.NONE, STEPS, NAME, AGE));
        assertTrue(opening.active());
        assertEquals(0, opening.step());
    }
}
