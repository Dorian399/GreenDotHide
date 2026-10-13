package com.dorian15.greendothide.xposed;

/** Tracks the two halves of one MIUI controller's current privacy prompt. */
final class MiuiPromptState<T> {
    private boolean allHidden;
    private boolean[] hiddenTypes = new boolean[4];
    private int[] types;
    private T views;
    private int viewStatus;
    private boolean viewsSuppressed;
    private boolean waitingForViews;

    MiuiPromptState(boolean allHidden) {
        this.allHidden = allHidden;
    }

    void setHiddenTypes(boolean[] hidden) {
        hiddenTypes = hidden.clone();
        allHidden = hidden[0] && hidden[1] && hidden[2] && hidden[3];
    }

    boolean onViews(int status, T newViews, int[] newTypes) {
        if (newTypes != null)
            types = newTypes.clone();
        boolean hidden = shouldHide();
        if (newTypes != null || waitingForViews) {
            // This pair is complete; never replay its view for the next prompt.
            views = null;
            viewsSuppressed = false;
            waitingForViews = false;
        } else {
            views = newViews;
            viewStatus = status;
            viewsSuppressed = hidden;
        }
        return hidden;
    }

    boolean onTypes(int[] newTypes) {
        types = newTypes.clone();
        boolean hidden = shouldHide();
        waitingForViews = views == null;
        if (hidden || !viewsSuppressed) {
            views = null;
            viewsSuppressed = false;
        }
        return hidden;
    }

    T takeViewsToRestore() {
        if (!viewsSuppressed)
            return null;
        viewsSuppressed = false;
        T pendingViews = views;
        views = null;
        return pendingViews;
    }

    int getViewStatus() {
        return viewStatus;
    }

    void clear() {
        types = null;
        views = null;
        viewStatus = 0;
        viewsSuppressed = false;
        waitingForViews = false;
    }

    private boolean shouldHide() {
        if (types == null)
            return allHidden;
        for (int i = 0; i < types.length; i++)
            if (types[i] != 0 && !(i < hiddenTypes.length && hiddenTypes[i]))
                return false;
        return true;
    }
}
