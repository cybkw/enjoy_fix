package com.enjoy.fix.plugin;

/**
 * Created by c_kunwu on 16/5/10.
 */
public interface Proxy {

    /**
     * @param methodName
     * @param args
     * @return
     */
    Object proxy(String methodName, Object[] args);


}
