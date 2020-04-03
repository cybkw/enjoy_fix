package com.enjoy.fix.patch;


/**
 * Created by hedex on 17/1/22.
 */

public interface PatchCallBack {

    /**
     * 在补丁应用后，回调此方法
     *
     * @param result 结果
     * @param patch  补丁
     */
    void onPatchResult(boolean result, String patch);


}
