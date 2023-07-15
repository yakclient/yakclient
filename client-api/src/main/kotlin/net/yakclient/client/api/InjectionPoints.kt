@file:JvmName("InjectionPoints")

package net.yakclient.client.api

/**
 * Injects into the very beginning of the method, before all other code
 * that was originally there.
 */
public const val AFTER_BEGIN: String = "after-begin"

/**
 * Injects at the very end of the method, right before the very last
 * return statement.
 */
public const val BEFORE_END: String = "before-end"

/**
 * Injects right before every method invocation statement.
 */
@Deprecated("This is an unnecessary injection point, will be replaced in future releases")
public const val BEFORE_INVOKE: String = "before-invoke"

/**
 * Injects right before every single return statement.
 */
public const val BEFORE_RETURN: String = "before-return"

/**
 * Will completely overwrite a method and replace its contents with
 * that of the injection. BE CAREFUL, this has alot of possibility
 * to collide with other injections! It should almost always take a
 * very low priority.
 */
public const val OVERWRITE: String = "overwrite"