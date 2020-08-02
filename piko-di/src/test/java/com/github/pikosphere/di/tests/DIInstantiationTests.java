package com.github.pikosphere.di.tests;


import com.github.pikosphere.di.ItemKey;
import com.github.pikosphere.di.PikoDI;
import com.github.pikosphere.di.tests.common.Module3;
import com.github.pikosphere.di.tests.common.Service1;
import com.github.pikosphere.di.tests.ins.ComplexModule;
import com.github.pikosphere.di.tests.ins.Svc1;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
class DIInstantiationTests {

    @Test
    void testSimpleInstantiation() {
        PikoDI di = PikoDI.create(Module3.class);
        Service1 service1 = di.getInstanceOf(new ItemKey<>(Service1.class));
        assertNotNull(service1, "Service1 instantiation failed");
    }

    @Test
    void testSimpleMsgProviderModule() {
        PikoDI pikoDI = PikoDI.create(ComplexModule.class);
        Svc1 svc1 = pikoDI.getInstanceOf(new ItemKey<>(Svc1.class));

        List<String> messages = Arrays.asList("Hello", "Dear", "My Dear", "Honourable", "Doctor");

        String message = svc1.getMessage();
        log.info("Generated greeting {}", message);
        assertTrue(messages.contains(message), String.format("The message '%s' is not in the expected list '%s'", message, messages));
    }
}
