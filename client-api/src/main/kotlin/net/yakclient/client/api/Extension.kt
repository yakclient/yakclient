package net.yakclient.client.api

public abstract class Extension {
    public abstract fun init(context: ExtensionContext)

    public abstract fun cleanup()
}