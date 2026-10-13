package com.dorian15.greendothide.xposed;

import org.junit.Test;

import static org.junit.Assert.*;

public class HookTest {
    private static final boolean[] ALL = {true, true, true, true};

    @Test
    public void bothHalvesOfFullyHiddenPromptUseNormalClearPath() {
        assertTrue(Hook.shouldClearPrompt("action_update_prompt", null, true));
        int[] filtered = Hook.filterPromptTypes(new int[]{1, 1, 1, 1}, ALL);
        assertTrue(Hook.shouldClearPrompt("action_control_center_update_prompt", filtered, true));
    }

    @Test
    public void selectiveSettingsKeepVisibleTypesAndDoNotMutateSender() {
        int[] input = {1, 1, 1, 1};
        int[] filtered = Hook.filterPromptTypes(input, new boolean[]{true, false, true, false});
        assertArrayEquals(new int[]{0, 1, 0, 1}, filtered);
        assertArrayEquals(new int[]{1, 1, 1, 1}, input);
        assertFalse(Hook.shouldClearPrompt("action_control_center_update_prompt", filtered, false));
        assertFalse(Hook.shouldClearPrompt("action_update_prompt", null, false));
    }

    @Test
    public void lastVisibleItemDisappearingClearsOldState() {
        int[] filtered = Hook.filterPromptTypes(new int[]{1, 0, 0, 0},
                new boolean[]{true, false, false, false});
        assertTrue(Hook.shouldClearPrompt("action_control_center_update_prompt", filtered, false));
        assertTrue(Hook.shouldClearPrompt("action_control_center_update_prompt", new int[0], false));
    }

    @Test
    public void unknownTypesAndUnrelatedActionsArePreserved() {
        int[] filtered = Hook.filterPromptTypes(new int[]{1, 1, 1, 1, 2}, ALL);
        assertArrayEquals(new int[]{0, 0, 0, 0, 2}, filtered);
        assertFalse(Hook.shouldClearPrompt("action_update_prompt", filtered, true));
        assertFalse(Hook.shouldClearPrompt("unrelated_action", new int[4], true));
        assertNull(Hook.filterPromptTypes(null, ALL));
        assertArrayEquals(new int[]{0}, Hook.filterPromptTypes(new int[]{1}, ALL));
    }

    @Test
    public void allSwitchesOffPreserveEveryActiveType() {
        int[] input = {1, 1, 1, 1};
        int[] filtered = Hook.filterPromptTypes(input, new boolean[4]);
        assertArrayEquals(input, filtered);
        assertFalse(Hook.shouldClearPrompt("action_update_prompt", filtered, false));
    }
}
