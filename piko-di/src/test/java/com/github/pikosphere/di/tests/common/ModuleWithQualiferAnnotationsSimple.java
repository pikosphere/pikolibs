package com.github.pikosphere.di.tests.common;

import javax.inject.Named;

public class ModuleWithQualiferAnnotationsSimple {


    @Named("simple.service1")
    public static Service1 provideService1Simple(@Named("simple.service2") Service2 simpleService2) {
        return new Service1();
    }

    @Named("simple.service2")
    public static Service2 provideService2Simple() {
        return new Service2();
    }

}
