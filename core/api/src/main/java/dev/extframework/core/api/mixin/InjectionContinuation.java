package dev.extframework.core.api.mixin;

public class InjectionContinuation {
    public Result returnEarly() {
        return new EarlyVoidReturn();
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

    public Result returnEarly(byte t) {
        return new EarlyBReturn(t);
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
        return new EarlyJReturn(t);
    }

    public Result returnEarly(double t) {
        return new EarlyDReturn(t);
    }

    public Result resume() {
        return new Resume();
    }

    public interface Result {
        int getOrdinance();
    }

    public static class EarlyVoidReturn implements Result {
        @Override
        public int getOrdinance() {
            return 0;
        }
    }

    public static class EarlyObjReturn implements Result {
        private final Object value;

        public EarlyObjReturn(Object value) {
            this.value = value;
        }

        public Object getValue() {
            return value;
        }

        @Override
        public int getOrdinance() {
            return 1;
        }
    }

    public static class EarlyZReturn implements Result {
        private final boolean value;

        public EarlyZReturn(boolean value) {
            this.value = value;
        }

        public boolean getValue() {
            return value;
        }

        @Override
        public int getOrdinance() {
            return 2;
        }
    }

    public static class EarlyCReturn implements Result {
        private final char value;

        public EarlyCReturn(char value) {
            this.value = value;
        }

        public char getValue() {
            return value;
        }

        @Override
        public int getOrdinance() {
            return 3;
        }
    }

    public static class EarlyBReturn implements Result {
        private final byte value;

        public EarlyBReturn(byte value) {
            this.value = value;
        }

        public byte getValue() {
            return value;
        }

        @Override
        public int getOrdinance() {
            return 4;
        }
    }

    public static class EarlySReturn implements Result {
        private final short value;

        public EarlySReturn(short value) {
            this.value = value;
        }

        public short getValue() {
            return value;
        }

        @Override
        public int getOrdinance() {
            return 5;
        }
    }

    public static class EarlyIReturn implements Result {
        private final int value;

        public EarlyIReturn(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        @Override
        public int getOrdinance() {
            return 6;
        }
    }

    public static class EarlyFReturn implements Result {
        private final float value;

        public EarlyFReturn(float value) {
            this.value = value;
        }

        public float getValue() {
            return value;
        }

        @Override
        public int getOrdinance() {
            return 7;
        }
    }

    public static class EarlyJReturn implements Result {
        private final long value;

        public EarlyJReturn(long value) {
            this.value = value;
        }

        public long getValue() {
            return value;
        }

        @Override
        public int getOrdinance() {
            return 9;
        }
    }

    public static class EarlyDReturn implements Result {
        private final double value;

        public EarlyDReturn(double value) {
            this.value = value;
        }

        public double getValue() {
            return value;
        }

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

