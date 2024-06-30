package dev.extframework.client.api;

public class InjectionContinuation {
    public Result returnEarly() {
        return new EarlyObjReturn(null);
    }

    public Result returnEarly(Object t) {
        return new EarlyObjReturn(t);
    }

    public Result returnEarly(boolean t) {
        return new EarlyZReturn(t);
    }
    public Result returnEarly(char t) {
        return new EarlyCReturn(t);
    }
    public Result returnEarly(short t) {
        return new EarlySReturn(t);
    }
    public Result returnEarly(int t) {
        return new EarlyIReturn(t);
    }

    public Result returnEarly(float t) {
        return new EarlyFReturn(t);
    }

    public Result returnEarly(long t) {
        return new EarlyLReturn(t);
    }

    public Result returnEarly(double t) {
        return new EarlyDReturn(t);
    }

    public Result resume() {
        return new Resume();
    }

    public sealed interface Result {
        int getOrdinance();
    }

    public record EarlyVoidReturn() implements  Result {
        @Override
        public int getOrdinance() {
            return 0;
        }
    }

    public record EarlyObjReturn(Object value) implements Result {
        @Override
        public int getOrdinance() {
            return 1;
        }
    }

    public record EarlyZReturn(boolean value) implements Result {
        @Override
        public int getOrdinance() {
            return 2;
        }
    }
    public record EarlyCReturn(char value) implements Result {
        @Override
        public int getOrdinance() {
            return 3;
        }
    }
    public record EarlyBReturn(byte value) implements Result {
        @Override
        public int getOrdinance() {
            return 4;
        }
    }
    public record EarlySReturn(short value) implements Result {
        @Override
        public int getOrdinance() {
            return 5;
        }
    }
    public record EarlyIReturn(int value) implements Result {
        @Override
        public int getOrdinance() {
            return 6;
        }
    }
    public record EarlyFReturn(float value) implements Result {
        @Override
        public int getOrdinance() {
            return 7;
        }
    }
    public record EarlyLReturn(long value) implements Result {
        @Override
        public int getOrdinance() {
            return 8;
        }
    }
    public record EarlyJReturn(long value) implements Result {
        @Override
        public int getOrdinance() {
            return 9;
        }
    }
    public record EarlyDReturn(double value) implements Result {
        @Override
        public int getOrdinance() {
            return 10;
        }
    }

    public static final class Resume implements Result {
        @Override
        public int getOrdinance() {
            return 11;
        }
    }
}

