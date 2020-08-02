package com.github.pikosphere.di.tests.common;

public class ModuleWithCustomQualiferItems2 {

    @CustomQualifier
    public static Service1 provideService1CustomQualifier(@CustomQualifier Service2 service2) {
        return new Service1();
    }

    @CustomQualifier
    public static Service2 provideService2CustomQualifer() {
        return new Service2();
    }

}
