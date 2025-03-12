package com.photons.bus.ipc.core;

import android.os.Bundle;

import com.photons.bus.ipc.consts.IpcConst;

/**
 * Created by liaohailiang on 2019/5/30.
 */
public class StringProcessor implements Processor {

    @Override
    public boolean writeToBundle(Bundle bundle, Object value) {
        if (!(value instanceof String)) {
            return false;
        }
        bundle.putString(IpcConst.KEY_VALUE, (String) value);
        return true;
    }

    @Override
    public Object createFromBundle(Bundle bundle) {
        return bundle.getString(IpcConst.KEY_VALUE);
    }
}
