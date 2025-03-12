package com.photons.bus;

import androidx.annotation.NonNull;

import com.photons.bus.core.Config;
import com.photons.bus.core.LiveEvent;
import com.photons.bus.core.LiveEventBusCore;
import com.photons.bus.core.Observable;
import com.photons.bus.core.ObservableConfig;

/**
 * _     _           _____                _  ______
 * | |   (_)         |  ___|              | | | ___ \
 * | |    ___   _____| |____   _____ _ __ | |_| |_/ /_   _ ___
 * | |   | \ \ / / _ \  __\ \ / / _ \ '_ \| __| ___ \ | | / __|
 * | |___| |\ V /  __/ |___\ V /  __/ | | | |_| |_/ / |_| \__ \
 * \_____/_| \_/ \___\____/ \_/ \___|_| |_|\__\____/ \__,_|___/
 *
 *
 *
 * Created by liaohailiang on 2019/1/21.
 */

public final class LiveEventBus {

    /**
     * get observable by key with type
     *
     * @param key key
     * @param type type
     * @param <T> T
     * @return Observable
     */
    public static <T> Observable<T> get(@NonNull String key, @NonNull Class<T> type) {
        return LiveEventBusCore.get().with(key, type);
    }

    /**
     * get observable by key
     *
     * @param key String
     * @param <T> T
     * @return Observable
     */
    public static <T> Observable<T> get(@NonNull String key) {
        return (Observable<T>) get(key, Object.class);
    }

    /**
     * get observable from eventType
     *
     * @param eventType Class
     * @param <T> T
     * @return Observable
     */
    public static <T extends LiveEvent> Observable<T> get(@NonNull Class<T> eventType) {
        return get(eventType.getName(), eventType);
    }

    /**
     * use the inner class Config to set params
     * first of all, call config to get the Config instance
     * then, call the method of Config to config LiveEventBus
     * call this method in Application.onCreate
     * @return Config
     */
    public static Config config() {
        return LiveEventBusCore.get().config();
    }

    /**
     * use the inner class Config to set params
     * first of all, call config to get the Config instance
     * then, call the method of Config to config LiveEventBus
     * call this method in Application.onCreate
     * @param key String
     * @return Config
     */
    public static ObservableConfig config(@NonNull String key) {
        return LiveEventBusCore.get().config(key);
    }
}
