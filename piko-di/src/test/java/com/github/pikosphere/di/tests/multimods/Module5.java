package com.github.pikosphere.di.tests.multimods;

public class Module5 {

    public static Svc1 provideSvc1(RandomMsgProvider msgProvider) {
        return new Svc1Impl(msgProvider);
    }

    public static RandomMsgProvider provideMsgProvider() {
        return new RandomMessageProviderImpl();
    }

}
