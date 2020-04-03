package com.enjoy.enjoyfix;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {

    static class A {
        public static String a = "2";
    }

    static class B extends A {
        public static String a;
    }

    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
        System.out.println(B.a);
    }
}