package com.photons.bus.ipc.core;

import android.os.Bundle;

import com.photons.bus.ipc.consts.IpcConst;


/**
 * Created by liaohailiang on 2019/5/30.
 */
public class LongProcessor implements Processor {

    @Override
    public boolean writeToBundle(Bundle bundle, Object value) {
        if (!(value instanceof Long)) {
            return false;
        }
        bundle.putLong(IpcConst.KEY_VALUE, (long) value);
        return true;
    }

    @Override
    public Object createFromBundle(Bundle bundle) {
        return bundle.getLong(IpcConst.KEY_VALUE);
    }
}
