package com.github.pikosphere.di.tests.multimods;


public class Module2 {
    public static RandomMsgProvider provideMsgProvider() {
        return new RandomMessageProviderImpl();
    }
}
