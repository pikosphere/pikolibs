package com.github.pikosphere.di.tests.ins;

public class ComplexModule {


    public static Svc1 provideSvc1(RandomMsgProvider msgProvider) {
        return new Svc1Impl(msgProvider);
    }

    public static RandomMsgProvider provideMsgProvider() {
        return new RandomMessageProviderImpl();
    }

}
