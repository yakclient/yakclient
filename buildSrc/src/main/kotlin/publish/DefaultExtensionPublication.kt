package dev.extframework.gradle.publish

import org.gradle.api.Action
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.provider.Provider
import org.gradle.api.publish.internal.PublicationArtifactSet
import org.gradle.api.publish.internal.PublicationInternal
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal
import org.gradle.internal.DisplayName

internal class DefaultExtensionPublication(
    private val _name: String
) : ExtensionPublication, PublicationInternal<Nothing> {
    override fun getName(): String {
        return _name
    }

    override fun withoutBuildIdentifier() {
    }

    override fun withBuildIdentifier() {
    }

    override fun getDisplayName(): DisplayName {
        return object : DisplayName {
            override fun getDisplayName(): String {
                return _name
            }

            override fun getCapitalizedDisplayName(): String {
                return _name
            }
        }
    }

    override fun getCoordinates(): ModuleVersionIdentifier {
        TODO("Not yet implemented")
    }

    override fun <T : Any?> getCoordinates(type: Class<T>): T? {
        TODO("Not yet implemented")
    }

    override fun getComponent(): Provider<SoftwareComponentInternal> {
        TODO("Not yet implemented")
    }

    override fun isAlias(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isLegacy(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getAttributes(): ImmutableAttributes {
        TODO("Not yet implemented")
    }

    override fun setAlias(alias: Boolean) {
        TODO("Not yet implemented")
    }

    override fun getPublishableArtifacts(): PublicationArtifactSet<Nothing> {
        TODO("Not yet implemented")
    }

    override fun getPublishedFile(source: PublishArtifact?): PublicationInternal.PublishedFile {
        TODO("Not yet implemented")
    }

    override fun getVersionMappingStrategy(): VersionMappingStrategyInternal {
        TODO("Not yet implemented")
    }

    override fun isPublishBuildId(): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeDerivedArtifact(artifact: Nothing?) {
        TODO("Not yet implemented")
    }

    override fun addDerivedArtifact(originalArtifact: Nothing?, file: PublicationInternal.DerivedArtifact?): Nothing {
        TODO("Not yet implemented")
    }

    override fun whenPublishableArtifactRemoved(action: Action<in Nothing>?) {
        TODO("Not yet implemented")
    }

    override fun allPublishableArtifacts(action: Action<in Nothing>?) {
        TODO("Not yet implemented")
    }
}