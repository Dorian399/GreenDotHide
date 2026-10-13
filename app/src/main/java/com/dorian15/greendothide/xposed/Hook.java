package com.dorian15.greendothide.xposed;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.dorian15.greendothide.Prefs;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Modern libxposed entry point. Declared in {@code META-INF/xposed/java_init.list}; the framework
 * instantiates it and calls {@code attachFramework} before any lifecycle callback, so nothing may
 * be touched from a constructor.
 */
public class Hook extends XposedModule {

    private static final String TAG = "GreenDotHide";

    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String MIUI_PRIVACY_CONTROLLER =
            "com.android.systemui.statusbar.privacy.MiuiPrivacyControllerImpl";

    /**
     * AOSP moved the privacy classes out of {@code statusbar} and has shuffled them around more
     * than once, so hooks are attempted against every known package and the ones this SystemUI
     * actually ships win.
     */
    private static final String[] AOSP_PRIVACY_PACKAGES = {
            "com.android.systemui.privacy.",                // Android 13+
            "com.android.systemui.statusbar.privacy.",       // Android 12
            "com.android.systemui.statusbar.phone.privacy.", // Android 11 and older
    };

    private volatile boolean hideLocation = true;
    private volatile boolean hideMic = true;
    private volatile boolean hideCamera = true;
    private volatile boolean hideMedia = true;
    private static final String UPDATE_PROMPT = "action_update_prompt";
    private static final String UPDATE_TYPES = "action_control_center_update_prompt";
    private final Map<Object, MiuiPromptState<Bundle>> promptStates = new WeakHashMap<>();

    static int[] filterPromptTypes(int[] types, boolean[] hidden) {
        if (types == null)
            return null;
        int[] filtered = types.clone();
        for (int i = 0; i < Math.min(filtered.length, hidden.length); i++)
            if (hidden[i])
                filtered[i] = 0;
        return filtered;
    }

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "Loaded in " + param.getProcessName() + " by " + getFrameworkName()
                + " " + getFrameworkVersion() + " (Xposed API " + getApiVersion() + ")");
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!SYSTEM_UI.equals(param.getPackageName()))
            return;

        bindPreferences();

        ClassLoader classLoader = param.getClassLoader();

        log(Log.INFO, TAG, "Hooking SystemUI (location=" + hideLocation + ", mic=" + hideMic
                + ", camera=" + hideCamera + ", media=" + hideMedia + ")");

        // MIUI/HyperOS: entry point of privacy item updates; hand MIUI a filtered
        // copy, including empty updates so previous indicators are cleared.
        hookMethod(classLoader, MIUI_PRIVACY_CONTROLLER, "onPrivacyItemsChanged",
                chain -> {
                    Object incoming = chain.getArg(0);
                    if (!(incoming instanceof List))
                        return chain.proceed();
                    List<Object> filtered = filterPrivacyList((List<?>) incoming);
                    return chain.proceed(new Object[]{filtered});
                },
                List.class);

        // MIUI sends views and types separately on some releases, combined on others.
        hookMethod(classLoader, MIUI_PRIVACY_CONTROLLER, "setStatus",
                this::handleMiuiStatus,
                int.class, String.class, Bundle.class);

        // Android 14 may update this cache before the types half arrives.
        hookMethod(classLoader,
                "com.android.systemui.statusbar.privacy.MiuiPrivacyFlaresDotController",
                "updateLastMiuiPromptInfo", chain -> chain.getArg(1) == null
                        ? chain.proceed(new Object[]{chain.getArg(0), new int[4]}) : chain.proceed(),
                RemoteViews.class, int[].class);

        // AOSP based SystemUI: kill indicators at the config level. These read the
        // privacy DeviceConfig flags and seed AppOpsPrivacyItemMonitor, which then
        // drops every matching privacy item before it ever reaches the chip.
        hookMethod(classLoader, aospClasses("PrivacyConfig"), "isLocationEnabled",
                chain -> hideLocation ? Boolean.FALSE : chain.proceed());
        hookMethod(classLoader, aospClasses("PrivacyConfig"), "isMicCameraEnabled",
                chain -> hideMic && hideCamera ? Boolean.FALSE : chain.proceed());
        hookMethod(classLoader, aospClasses("PrivacyConfig"), "isMediaProjectionEnabled",
                chain -> hideMedia ? Boolean.FALSE : chain.proceed());

        // Android 14+: the one choke point every privacy list passes through, right
        // before it is stored, so the callbacks also propagate the filtered list.
        hookMethod(classLoader, aospClasses("PrivacyItemController"), "processNewList",
                chain -> {
                    Object result = chain.proceed();
                    return result instanceof List ? filterPrivacyList((List<?>) result) : result;
                },
                List.class);

        // Android 12-13 and any build without processNewList: swap the cached list
        // once the controller has rebuilt it.
        hookMethod(classLoader, aospClasses("PrivacyItemController"), "updatePrivacyList",
                chain -> {
                    Object result = chain.proceed();
                    filterControllerList(chain.getThisObject());
                    return result;
                });

        // Older Kotlin-mangled accessor, kept for builds that still expose it.
        hookMethod(classLoader, aospClasses("PrivacyItemController"),
                "getPrivacyList$frameworks__base__packages__SystemUI__android_common__SystemUI_core",
                chain -> {
                    Object result = chain.proceed();
                    return result instanceof List ? filterPrivacyList((List<?>) result) : result;
                });
    }

    private Object handleMiuiStatus(XposedInterface.Chain chain) throws Throwable {
        String action = (String) chain.getArg(1);
        if (!UPDATE_PROMPT.equals(action) && !UPDATE_TYPES.equals(action))
            return chain.proceed();
        int status = (Integer) chain.getArg(0);
        Object controller = chain.getThisObject();
        synchronized (promptStates) {
            if (status == 0) {
                promptStates.remove(controller);
                return chain.proceed();
            }
            if (!(chain.getArg(2) instanceof Bundle))
                return chain.proceed();
            boolean[] hide = {hideCamera, hideMic, hideLocation, hideMedia};
            Bundle bundle = (Bundle) chain.getArg(2);
            boolean viewUpdate = UPDATE_PROMPT.equals(action);
            if (viewUpdate) {
                int priority = bundle.getInt("key_status_bar_priority", -1);
                if (priority < 0 || priority > 3
                        || !(bundle.getParcelable("key_status_bar_mini_state") instanceof RemoteViews)
                        || !(bundle.getParcelable("key_status_bar_home_state") instanceof RemoteViews))
                    return chain.proceed();
            }
            int[] types = bundle.getIntArray("key_prompt_type");
            if (!viewUpdate && types == null)
                return chain.proceed();
            Bundle copy = new Bundle(bundle);
            if (types != null)
                copy.putIntArray("key_prompt_type", filterPromptTypes(types, hide));
            MiuiPromptState<Bundle> state = promptStates.get(controller);
            if (state == null) {
                state = new MiuiPromptState<>();
                promptStates.put(controller, state);
            }
            // Cache raw types; API 102 preferences can change without a restart.
            state.setHiddenTypes(hide);
            boolean hidden = viewUpdate ? state.onViews(status, copy, types) : state.onTypes(types);
            if (hidden)
                return chain.proceed(new Object[]{0, action, copy});
            if (!viewUpdate) {
                Bundle views = state.takeViewsToRestore();
                if (views != null) {
                    // A combined update may have cached older filtered types.
                    // Restore its views with this update's current type decision.
                    Bundle restored = new Bundle(views);
                    restored.putIntArray("key_prompt_type", filterPromptTypes(types, hide));
                    getInvoker((Method) chain.getExecutable()).setType(XposedInterface.Invoker.Type.ORIGIN)
                            .invoke(controller, state.getViewStatus(), UPDATE_PROMPT, restored);
                }
            }
            return chain.proceed(new Object[]{status, action, copy});
        }
    }

    /**
     * Subscribes to the remote preferences the module app writes through the Xposed service, so
     * toggles take effect without restarting SystemUI.
     */
    private void bindPreferences() {
        SharedPreferences prefs;
        try {
            prefs = getRemotePreferences(Prefs.GROUP);
        } catch (Throwable t) {
            // Frameworks without PROP_CAP_REMOTE reject this outright; defaults still apply.
            log(Log.WARN, TAG, "Remote preferences unavailable, staying on defaults", t);
            return;
        }

        readPreferences(prefs);
        // Only four flags, so a full re-read is cheaper than tracking the changed key.
        prefs.registerOnSharedPreferenceChangeListener((changed, key) -> {
            readPreferences(changed);
            log(Log.INFO, TAG, "Preferences changed (" + key + "): location=" + hideLocation
                    + ", mic=" + hideMic + ", camera=" + hideCamera + ", media=" + hideMedia);
        });
    }

    private void readPreferences(SharedPreferences prefs) {
        hideLocation = prefs.getBoolean(Prefs.KEY_LOCATION, true);
        hideMic = prefs.getBoolean(Prefs.KEY_MIC, true);
        hideCamera = prefs.getBoolean(Prefs.KEY_CAMERA, true);
        hideMedia = prefs.getBoolean(Prefs.KEY_MEDIA, true);
    }

    private boolean shouldHide(String privacyType) {
        return (privacyType.equals("TYPE_MICROPHONE") && hideMic)
                || (privacyType.equals("TYPE_CAMERA") && hideCamera)
                || (privacyType.equals("TYPE_LOCATION") && hideLocation)
                || (privacyType.equals("TYPE_MEDIA_PROJECTION") && hideMedia);
    }

    /**
     * Builds a NEW list without hidden items. The input list is never mutated:
     * SystemUI components keep references to the same instance, and in-place
     * removal corrupts their state (root cause of SystemUI crashes).
     */
    private List<Object> filterPrivacyList(List<?> list) {
        List<Object> filtered = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item == null)
                continue;
            try {
                Object type = Reflect.getField(item, "privacyType");
                if (type != null && shouldHide(type.toString()))
                    continue;
            } catch (Throwable t) {
                log(Log.WARN, TAG, "Unreadable privacyType on " + item.getClass().getName(), t);
            }
            filtered.add(item);
        }
        return filtered;
    }

    /** Swaps the controller's cached privacyList for a filtered copy; never mutates the original. */
    private void filterControllerList(Object controller) {
        if (controller == null)
            return;
        try {
            List<?> current = (List<?>) Reflect.getField(controller, "privacyList");
            if (current == null)
                return;
            List<Object> filtered = filterPrivacyList(current);
            if (filtered.size() != current.size())
                Reflect.setField(controller, "privacyList", filtered);
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Unable to filter the controller's privacyList", t);
        }
    }

    private static String[] aospClasses(String simpleName) {
        String[] names = new String[AOSP_PRIVACY_PACKAGES.length];
        for (int i = 0; i < names.length; i++)
            names[i] = AOSP_PRIVACY_PACKAGES[i] + simpleName;
        return names;
    }

    private void hookMethod(ClassLoader classLoader, String className, String methodName,
                            XposedInterface.Hooker hooker, Class<?>... parameterTypes) {
        hookMethod(classLoader, new String[]{className}, methodName, hooker, parameterTypes);
    }

    /**
     * Installs the hook on the first class variant this SystemUI actually has. A missing class or
     * method is normal across Android versions, so it is a debug line rather than a warning.
     */
    private void hookMethod(ClassLoader classLoader, String[] classNames, String methodName,
                            XposedInterface.Hooker hooker, Class<?>... parameterTypes) {
        for (String className : classNames) {
            Method method;
            try {
                method = Reflect.findMethod(Reflect.findClass(className, classLoader),
                        methodName, parameterTypes);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                continue;
            } catch (Throwable t) {
                log(Log.WARN, TAG, "Could not resolve " + className + "." + methodName, t);
                return;
            }

            try {
                // PROTECTIVE keeps a bug in this module from taking SystemUI down with it.
                hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(hooker);
                log(Log.INFO, TAG, "Hooked into: " + className + "." + methodName);
            } catch (Throwable t) {
                log(Log.WARN, TAG, "Ignoring hook: " + className + "." + methodName, t);
            }
            return;
        }
        log(Log.INFO, TAG, "Not present on this SystemUI: " + methodName);
    }
}
