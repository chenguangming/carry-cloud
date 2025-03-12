package com.photons.bus.ipc.annotation;

import com.photons.bus.ipc.core.Processor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by liaohailiang on 2019/5/30.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface IpcConfig {

    Class<? extends Processor> processor();
}
