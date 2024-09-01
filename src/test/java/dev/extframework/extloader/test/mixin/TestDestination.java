package dev.extframework.extloader.test.mixin;

public class TestDestination {

   int destMethod(String capture) {
        System.out.println("Hey");

        return 5;
    }

    static int staticDestMethod(long a, String capture) {
        System.out.println("Hey");

        return 5;
    }
}
