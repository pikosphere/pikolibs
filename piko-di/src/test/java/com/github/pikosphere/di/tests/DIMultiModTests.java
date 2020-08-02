package com.github.pikosphere.di.tests;

import com.github.pikosphere.di.ItemKey;
import com.github.pikosphere.di.PikoDI;
import com.github.pikosphere.di.tests.multimods.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class DIMultiModTests {

    @Test
    void greetingsProviderMultiModTest() {
        PikoDI pikoDI = PikoDI.create(Module1.class, Module2.class);
        Svc1 svc1 = pikoDI.getInstanceOf(new ItemKey<>(Svc1.class));

        List<String> messages = Arrays.asList("Hello", "Dear", "My Dear", "Honourable", "Doctor");

        String message = svc1.getMessage();
        log.info("Generated greeting {}", message);
        assertTrue(messages.contains(message), String.format("The message '%s' is not in the expected list '%s'", message, messages));

    }

    @Test
    void greetingsProviderMultiModErrorNoProviderTest() {
        PikoDI.Exception exception = assertThrows(PikoDI.Exception.class, () -> {
            PikoDI pikoDI = PikoDI.create(Module1.class);
        });

        assertEquals("NO_PROVIDERS", exception.getErrorCode(), "Exception error codes do not match");

    }

    @Test
    void testSimpleCycles() {
        PikoDI.Exception exception = assertThrows(PikoDI.Exception.class, () -> {
            PikoDI pikoDI = PikoDI.create(Module3.class, Module4.class);
        });


        assertEquals("CYCLIC_DEPENDENCY_ITEMS", exception.getErrorCode(), "Exception error codes do not match");

        //Map<String,Object> errorData = exception.getData();

        //log.error("{}",errorData);

    }

    @Test
    void testDuplicateProviders() {
        PikoDI.Exception exception = assertThrows(PikoDI.Exception.class, () -> {
            PikoDI pikoDI = PikoDI.create(Module5.class, Module6.class);
        });

        //exception.printStackTrace();
        assertEquals("DUPLICATE_ITEMS", exception.getErrorCode(), "Exception error codes do not match");
    }

}
