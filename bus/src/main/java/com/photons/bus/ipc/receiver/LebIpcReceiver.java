package com.photons.bus.ipc.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.photons.bus.LiveEventBus;
import com.photons.bus.ipc.consts.IpcConst;
import com.photons.bus.ipc.core.ProcessorManager;

/**
 * Created by liaohailiang on 2019/3/26.
 */
public class LebIpcReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (IpcConst.ACTION.equals(intent.getAction())) {
            try {
                String key = intent.getStringExtra(IpcConst.KEY);
                Object value = ProcessorManager.getManager().createFrom(intent);
                if (key != null && value != null) {
                    LiveEventBus.get(key).post(value);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
