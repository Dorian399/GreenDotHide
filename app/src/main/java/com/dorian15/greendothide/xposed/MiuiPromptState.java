package com.dorian15.greendothide.xposed;

/** Mirrors one MIUI prompt's independent view/type fields until a real stop. */
final class MiuiPromptState<T> {
    private boolean allHidden;
    private boolean anyHidden;
    private boolean[] hiddenTypes = new boolean[4];
    private int[] types;
    private T views;
    private int viewStatus;
    private boolean viewsSuppressed;

    void setHiddenTypes(boolean[] hidden) {
        hiddenTypes = hidden.clone();
        allHidden = hidden[0] && hidden[1] && hidden[2] && hidden[3];
        anyHidden = hidden[0] || hidden[1] || hidden[2] || hidden[3];
    }

    boolean onViews(int status, T newViews, int[] newTypes) {
        views = newViews;
        viewStatus = status;
        if (newTypes != null)
            types = newTypes.clone();
        viewsSuppressed = shouldHide();
        return viewsSuppressed;
    }

    boolean onTypes(int[] newTypes) {
        types = newTypes.clone();
        boolean hidden = shouldHide();
        if (hidden)
            viewsSuppressed = true;
        return hidden;
    }

    T takeViewsToRestore() {
        if (!viewsSuppressed)
            return null;
        viewsSuppressed = false;
        return views;
    }

    int getViewStatus() {
        return viewStatus;
    }

    void clear() {
        types = null;
        views = null;
        viewStatus = 0;
        viewsSuppressed = false;
    }

    private boolean shouldHide() {
        if (!anyHidden)
            return false;
        if (types == null)
            return allHidden;
        for (int i = 0; i < types.length; i++)
            if (types[i] != 0 && !(i < hiddenTypes.length && hiddenTypes[i]))
                return false;
        return true;
    }
}
