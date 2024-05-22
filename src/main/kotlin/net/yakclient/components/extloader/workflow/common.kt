package net.yakclient.components.extloader.workflow

import net.yakclient.boot.component.context.ContextNodeTree
import net.yakclient.boot.component.context.ContextNodeTypes
import net.yakclient.components.extloader.extension.artifact.ExtensionDescriptor
import net.yakclient.components.extloader.extension.artifact.ExtensionRepositorySettings


internal fun <T : Any> T?.check(name: () -> String): T {
    return checkNotNull(this) { "Error while trying to parse configuration for component: 'ext-loader'. Could not find property '${name()}'." }
}

internal fun ContextNodeTree.getCoerceCheckString(key: String): String {
    return get(key)?.coerceType(ContextNodeTypes.String).check { key }
}
internal fun ContextNodeTree.parseDescriptor(): ExtensionDescriptor = ExtensionDescriptor(
    getCoerceCheckString("groupId"),
    getCoerceCheckString("artifactId"),
    getCoerceCheckString("version"),
    null
)

internal fun ContextNodeTree.parseSettings(): ExtensionRepositorySettings {
    val repoType = getCoerceCheckString("type")
    val repo = getCoerceCheckString("location")

    return when (repoType.lowercase()) {
        "local" -> ExtensionRepositorySettings.local(repo)
        "default" -> ExtensionRepositorySettings.local(repo)
        else -> throw IllegalArgumentException("Unknown repository type: '$repoType' for repository : '$repo' ")
    }
}