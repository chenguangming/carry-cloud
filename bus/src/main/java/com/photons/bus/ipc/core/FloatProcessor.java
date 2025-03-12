package com.photons.bus.ipc.core;

import android.os.Bundle;

import com.photons.bus.ipc.consts.IpcConst;


/**
 * Created by liaohailiang on 2019/5/30.
 */
public class FloatProcessor implements Processor {

    @Override
    public boolean writeToBundle(Bundle bundle, Object value) {
        if (!(value instanceof Float)) {
            return false;
        }
        bundle.putFloat(IpcConst.KEY_VALUE, (float) value);
        return true;
    }

    @Override
    public Object createFromBundle(Bundle bundle) {
        return bundle.getFloat(IpcConst.KEY_VALUE);
    }
}
