package com.github.pikosphere.di.tests.ins;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class RandomMessageProviderImpl implements RandomMsgProvider {

    private List<String> messages = Arrays.asList("Hello", "Dear", "My Dear", "Honourable", "Doctor");
    private Random random = new Random();

    @Override
    public String getMessage() {
        int size = messages.size();
        int i = random.nextInt(size);
        return messages.get(i);
    }


}
