package com.dorian15.greendothide.xposed;

import org.junit.Test;

import static org.junit.Assert.*;

public class HookTest {
    private static final boolean[] ALL = {true, true, true, true};

    @Test
    public void liveSettingsReevaluateCachedRawTypes() {
        MiuiPromptState<String> state = new MiuiPromptState<>(false);
        state.setHiddenTypes(ALL);
        assertTrue(state.onTypes(new int[]{1, 0, 0, 0}));
        assertTrue(state.onViews(1, "hidden-camera", null));
        state.setHiddenTypes(new boolean[]{false, true, true, true});
        assertFalse(state.onViews(1, "visible-camera", null));
        state.setHiddenTypes(ALL);
        assertTrue(state.onViews(1, "hidden-again", null));
    }

    @Test
    public void liveSettingsDoNotHideUnknownTypesOrAliasFlags() {
        MiuiPromptState<String> state = new MiuiPromptState<>(false);
        boolean[] hidden = ALL.clone();
        state.setHiddenTypes(hidden);
        hidden[0] = false;
        assertTrue(state.onTypes(new int[]{1, 0, 0, 0}));
        assertFalse(state.onTypes(new int[]{0, 0, 0, 0, 1}));
        state.setHiddenTypes(new boolean[4]);
        assertFalse(state.onViews(1, "unknown", null));
    }

    @Test
    public void selectiveSettingsKeepVisibleTypesAndDoNotMutateSender() {
        int[] input = {1, 1, 1, 1};
        int[] filtered = Hook.filterPromptTypes(input, new boolean[]{true, false, true, false});
        assertArrayEquals(new int[]{0, 1, 0, 1}, filtered);
        assertArrayEquals(new int[]{1, 1, 1, 1}, input);
    }

    @Test
    public void lastVisibleItemDisappearingClearsOldState() {
        int[] filtered = Hook.filterPromptTypes(new int[]{1, 0, 0, 0},
                new boolean[]{true, false, false, false});
        MiuiPromptState<String> state = new MiuiPromptState<>(false);
        assertTrue(state.onTypes(filtered));
        assertTrue(state.onTypes(new int[0]));
    }

    @Test
    public void unknownNullAndShortTypesArePreserved() {
        int[] filtered = Hook.filterPromptTypes(new int[]{1, 1, 1, 1, 2}, ALL);
        assertArrayEquals(new int[]{0, 0, 0, 0, 2}, filtered);
        assertFalse(new MiuiPromptState<String>(true).onTypes(filtered));
        assertNull(Hook.filterPromptTypes(null, ALL));
        assertArrayEquals(new int[]{0}, Hook.filterPromptTypes(new int[]{1}, ALL));
    }

    @Test
    public void allSwitchesOffPreserveEveryActiveType() {
        int[] input = {1, 1, 1, 1};
        int[] filtered = Hook.filterPromptTypes(input, new boolean[4]);
        assertArrayEquals(input, filtered);
        assertFalse(new MiuiPromptState<String>(false).onTypes(filtered));
    }

    @Test
    public void hiddenPromptStaysClearedInEitherOrder() {
        for (boolean viewsFirst : new boolean[]{true, false}) {
            Controller controller = new Controller(true);
            controller.pair("camera", new int[4], viewsFirst);
            assertNull(controller.visibleViews);
            assertNull(controller.visibleTypes);
            controller.views("refreshed-camera", null);
            assertNull(controller.visibleViews);
        }
    }

    @Test
    public void selectivelyVisiblePromptSurvivesEitherOrder() {
        for (boolean viewsFirst : new boolean[]{true, false}) {
            Controller controller = new Controller(false);
            int[] types = {0, 1, 0, 0};
            controller.pair("microphone", types, viewsFirst);
            assertEquals("microphone", controller.visibleViews);
            assertArrayEquals(types, controller.visibleTypes);
        }
    }

    @Test
    public void unknownTypeRestoresSuppressedViewsInEitherOrder() {
        for (boolean viewsFirst : new boolean[]{true, false}) {
            Controller controller = new Controller(true);
            int[] types = {0, 0, 0, 0, 1};
            controller.pair("unknown", types, viewsFirst);
            assertEquals("unknown", controller.visibleViews);
            assertArrayEquals(types, controller.visibleTypes);
            assertEquals(viewsFirst ? 1 : 0, controller.replays);
            controller.types(types);
            assertEquals(viewsFirst ? 1 : 0, controller.replays);
        }
    }

    @Test
    public void latestSuppressedViewIsRestoredOnVisibleTransition() {
        Controller controller = new Controller(false);
        controller.pair("camera", new int[4], true);
        controller.views("microphone", null);
        assertNull(controller.visibleViews);
        controller.types(new int[]{0, 1, 0, 0});
        assertEquals("microphone", controller.visibleViews);
        assertEquals(1, controller.replays);
    }

    @Test
    public void completedHiddenPairCannotReplayIntoTheNextPrompt() {
        for (boolean viewsFirst : new boolean[]{true, false}) {
            Controller controller = new Controller(true);
            controller.pair("old-hidden-camera", new int[4], viewsFirst);
            controller.types(new int[]{0, 0, 0, 0, 1});
            assertNull(controller.visibleViews);
            assertEquals(0, controller.replays);
            controller.views("new-unknown-prompt", null);
            assertEquals("new-unknown-prompt", controller.visibleViews);
        }
    }

    @Test
    public void realStopBetweenHalvesClearsCachedViewsAndTypes() {
        Controller controller = new Controller(true);
        controller.views("stale", null);
        controller.stop();
        controller.types(new int[]{0, 0, 0, 0, 1});
        assertNull(controller.visibleViews);
        assertEquals(0, controller.replays);

        Controller selective = new Controller(false);
        selective.types(new int[4]);
        selective.stop();
        selective.views("new-prompt", null);
        assertEquals("new-prompt", selective.visibleViews);
    }

    @Test
    public void combinedUpdateReplacesPreviousTypeDecision() {
        Controller controller = new Controller(true);
        controller.views("hidden", new int[4]);
        assertNull(controller.visibleViews);
        controller.views("unknown", new int[]{0, 0, 0, 0, 1});
        assertEquals("unknown", controller.visibleViews);
        assertEquals(0, controller.replays);
    }

    @Test
    public void cachedDecisionDoesNotAliasTheInputArray() {
        MiuiPromptState<String> state = new MiuiPromptState<>(true);
        int[] types = {0, 1, 0, 0};
        assertFalse(state.onTypes(types));
        types[1] = 0;
        assertFalse(state.onViews(2, "visible", null));
        assertNull(state.takeViewsToRestore());

        MiuiPromptState<String> pending = new MiuiPromptState<>(true);
        assertTrue(pending.onViews(2, "pending", null));
        assertFalse(pending.onTypes(new int[]{0, 0, 0, 0, 1}));
        assertEquals("pending", pending.takeViewsToRestore());
        assertEquals(2, pending.getViewStatus());
    }

    // Model the observed MIUI contract: status=0 clears the whole prompt;
    // view/type messages update separate fields. Queued rendering sees these fields.
    private static final class Controller {
        final MiuiPromptState<String> state;
        String visibleViews;
        int[] visibleTypes;
        int replays;

        Controller(boolean allHidden) {
            state = new MiuiPromptState<>(allHidden);
        }

        void pair(String views, int[] types, boolean viewsFirst) {
            if (viewsFirst) {
                views(views, null);
                types(types);
            } else {
                types(types);
                views(views, null);
            }
        }

        void views(String views, int[] types) {
            if (state.onViews(1, views, types)) {
                clearOriginal();
            } else {
                visibleViews = views;
                if (types != null)
                    visibleTypes = types.clone();
            }
        }

        void types(int[] types) {
            if (state.onTypes(types)) {
                clearOriginal();
            } else {
                String replay = state.takeViewsToRestore();
                if (replay != null) {
                    visibleViews = replay;
                    replays++;
                }
                visibleTypes = types.clone();
            }
        }

        void stop() {
            state.clear();
            clearOriginal();
        }

        void clearOriginal() {
            visibleViews = null;
            visibleTypes = null;
        }
    }
}
