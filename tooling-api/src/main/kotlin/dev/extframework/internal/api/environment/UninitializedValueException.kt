package dev.extframework.internal.api.environment

public class UninitializedValueException(value: String) : Exception("The value: '$value' has not been initialized.") {
}