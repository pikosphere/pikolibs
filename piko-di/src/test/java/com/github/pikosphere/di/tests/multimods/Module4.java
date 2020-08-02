package com.github.pikosphere.di.tests.multimods;

public class Module4 {

    public static RandomMsgProvider provideMsgProvider(Svc2 anotherService) {
        return new RandomMessageProviderImpl();
    }

    public static Svc2 provideSvc2(Svc1 anotherService) {
        return new Svc2Impl();
    }

}
