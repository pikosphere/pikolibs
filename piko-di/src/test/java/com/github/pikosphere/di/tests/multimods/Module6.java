package com.github.pikosphere.di.tests.multimods;

public class Module6 {

    public static Svc1 provideSvc1(RandomMsgProvider msgProvider) {
        return new Svc1Impl(msgProvider);
    }

}
