package com.github.pikosphere.di.tests.common;

public class ModuleWithCustomQualiferItems {

    @CustomQualifier
    public static Service1 provideService1CustomQualifier(Service2 service2) {
        return new Service1();
    }

    @CustomQualifier
    public static Service2 provideService2CustomQualifer() {
        return new Service2();
    }
}
