package com.enjoy.fix.plugin.utils;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;

/**
 * @author Lance
 * @date 2019/4/4
 */
public class AnnotationUtils {


    public static List<CtClass> readAnnotation(List<CtClass> allClass) {
        List<CtClass> modifies = new ArrayList<>();
        for (CtClass ctclass : allClass) {
            if (ctclass.hasAnnotation("com.enjoy.fix.patch.annotation.Modify")) {
                modifies.add(ctclass);
            }
        }
        return modifies;

    }


}
