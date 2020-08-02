package com.github.pikosphere.di.tests;


import com.github.pikosphere.di.ItemKey;
import com.github.pikosphere.di.PikoDI;
import com.github.pikosphere.di.tests.common.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class SimpleDIBuildTests {

    @Test
    void createDI_no_modules() {
        PikoDI di = PikoDI.create();
        assertFalse(di.canProvide(new ItemKey<Object>(String.class, "test.value")));
    }

    @Test
    void createDI_one_empty_module() {
        PikoDI di = PikoDI.create(Module1.class);
        assertFalse(di.canProvide(new ItemKey<Object>(String.class, "test.value")));
    }

    @Test
    void createDI_module_with_composite_type() {
        PikoDI di = PikoDI.create(Module2.class);
        assertTrue(di.canProvide(new ItemKey<Object>(Service1.class, (Annotation) null)));
    }

    @Test
    void createDI_module_composite_with_deps_type() {
        PikoDI.Exception exception = assertThrows(PikoDI.Exception.class, () -> PikoDI.create(ModuleWithDeps.class));

        assertEquals("NO_PROVIDERS", exception.getErrorCode(), "Exception error codes do not match");
    }

    @Test
    void createDI_module_composite_dep_type() {
        PikoDI di = PikoDI.create(Module3.class);
        assertTrue(di.canProvide(new ItemKey<Object>(Service1.class, (Annotation) null)));
        assertTrue(di.canProvide(new ItemKey<Object>(Service2.class, (Annotation) null)));
        assertFalse(di.canProvide(new ItemKey<Object>(Integer.class, (Annotation) null)));

    }

    @Test
    void createDI_module_composite_dep_type_with_named_qualifier() {
        PikoDI di = PikoDI.create(ModuleWithQualiferAnnotationsSimple.class);
        //log.info("DI {}",di);
        ItemKey<Service1> key = new ItemKey<>(Service1.class, "simple.service1");
        boolean check = di.canProvide(key);
        assertTrue(check);
        //assertTrue(di.canProvide(new ItemKey<Object>(Service2.class,(Annotation)null)));
        //assertFalse(di.canProvide(new ItemKey<Object>(Integer.class,(Annotation)null)));

    }


    @Test
    void createDI_module_custom_qualifier_exception_noproviders() {
        PikoDI.Exception exception = assertThrows(PikoDI.Exception.class, () -> PikoDI.create(ModuleWithCustomQualiferItems.class));

        assertEquals("NO_PROVIDERS", exception.getErrorCode(), "Exception error codes do not match");

        /*PikoDI di = PikoDI.create(ModuleWithCustomQualiferItems.class);
        assertTrue(di.canProvide(new ItemKey(Service1.class,CustomQualifier.class)));*/
    }

    @Test
    void createDI_module_custom_qualifier() {
        PikoDI di = PikoDI.create(ModuleWithCustomQualiferItems2.class);
        assertTrue(di.canProvide(new ItemKey<>(Service1.class, CustomQualifier.class)));
    }

}
