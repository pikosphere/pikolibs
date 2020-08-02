package com.github.pikosphere.di.tests.common;

public class ModuleWithDeps {

    public static Service1 provideService1(Service2 service2) {
        return new Service1();
    }

}
