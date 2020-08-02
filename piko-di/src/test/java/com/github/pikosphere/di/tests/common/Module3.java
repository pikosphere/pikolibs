package com.github.pikosphere.di.tests.common;

public class Module3 {

    public static Service1 provideService1(Service2 service2) {
        return new Service1();
    }

    public static Service2 provideService2() {
        return new Service2();
    }
}
