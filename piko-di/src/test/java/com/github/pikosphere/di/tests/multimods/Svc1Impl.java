package com.github.pikosphere.di.tests.multimods;


class Svc1Impl implements Svc1 {

    private RandomMsgProvider msgProvider;

    Svc1Impl(RandomMsgProvider msgProvider) {
        this.msgProvider = msgProvider;
    }

    @Override
    public String getMessage() {
        return msgProvider.getMessage();
    }
}
