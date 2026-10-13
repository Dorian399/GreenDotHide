package com.dorian15.greendothide;

import android.app.Application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * Owns the connection to the Xposed framework. The framework only hands out its service once per
 * process, and {@link XposedServiceHelper#registerListener} must be called exactly once, so the
 * registration lives here instead of in an Activity that can be recreated.
 */
public class ModuleApplication extends Application {

    /** Notified with the framework's service, or {@code null} while none is connected. */
    public interface Listener {
        void onXposedService(XposedService service);
    }

    private static final Object LOCK = new Object();
    private static final Set<XposedService> SERVICES = new LinkedHashSet<>();
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static boolean registered;

    @Override
    public void onCreate() {
        super.onCreate();
        synchronized (LOCK) {
            if (registered)
                return;
            registered = true;
            XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
                @Override
                public void onServiceBind(XposedService service) {
                    synchronized (LOCK) {
                        SERVICES.add(service);
                    }
                    publish();
                }

                @Override
                public void onServiceDied(XposedService service) {
                    synchronized (LOCK) {
                        SERVICES.remove(service);
                    }
                    publish();
                }
            });
        }
    }

    public static void addListener(Listener listener) {
        XposedService current;
        synchronized (LOCK) {
            LISTENERS.add(listener);
            current = firstService();
        }
        // May already be bound if the Activity was recreated.
        if (current != null)
            listener.onXposedService(current);
    }

    public static void removeListener(Listener listener) {
        synchronized (LOCK) {
            LISTENERS.remove(listener);
        }
    }

    private static XposedService firstService() {
        for (XposedService service : SERVICES)
            return service;
        return null;
    }

    private static void publish() {
        XposedService current;
        List<Listener> snapshot;
        synchronized (LOCK) {
            current = firstService();
            snapshot = new ArrayList<>(LISTENERS);
        }
        for (Listener listener : snapshot)
            listener.onXposedService(current);
    }
}
