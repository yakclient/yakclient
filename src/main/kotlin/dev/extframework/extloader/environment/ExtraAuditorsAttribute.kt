package dev.extframework.extloader.environment

import dev.extframework.boot.audit.Auditors
import dev.extframework.tooling.api.environment.EnvironmentAttribute
import dev.extframework.tooling.api.environment.EnvironmentAttributeKey

public class ExtraAuditorsAttribute(
    public val auditors : Auditors = Auditors()
) : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*> = ExtraAuditorsAttribute

    public companion object : EnvironmentAttributeKey<ExtraAuditorsAttribute>
}