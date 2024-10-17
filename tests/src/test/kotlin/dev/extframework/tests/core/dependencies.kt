package dev.extframework.tests.core

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.map
import dev.extframework.boot.archive.*
import dev.extframework.boot.audit.Auditors
import dev.extframework.boot.audit.chain
import dev.extframework.boot.constraint.Constrained
import dev.extframework.boot.constraint.ConstraintArchiveAuditor
import dev.extframework.boot.constraint.ConstraintNegotiator
import dev.extframework.boot.dependency.BasicDependencyNode
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.boot.maven.MavenConstraintNegotiator
import dev.extframework.boot.maven.MavenDependencyResolver
import dev.extframework.boot.maven.MavenResolverProvider
import dev.extframework.boot.monad.removeIf
import dev.extframework.common.util.readInputStream
import dev.extframework.internal.api.extension.artifact.ExtensionDescriptor
import java.nio.file.Path

private class THIS

fun setupBoot(path: Path): Pair<ArchiveGraph, DependencyTypeContainer> {
    val dependencies = THIS::class.java.getResource("/dependencies.txt")?.openStream()?.use {
        val fileStr = String(it.readInputStream())
        fileStr.split("\n").toSet()
    }?.filterNot { it.isBlank() }?.mapTo(HashSet()) { SimpleMavenDescriptor.parseDescription(it)!! }
        ?: throw IllegalStateException("Cant load dependencies?")

    val archiveGraph = DefaultArchiveGraph(
        path,
        dependencies.associateByTo(HashMap()) {
            BasicDependencyNode(it, null,
                object : ArchiveAccessTree {
                    override val descriptor: ArtifactMetadata.Descriptor = it
                    override val targets: List<ArchiveTarget> = listOf()
                }
            )
        } as MutableMap<ArtifactMetadata.Descriptor, ArchiveNode<*>>
    )

    val negotiator = MavenConstraintNegotiator()

    val alreadyLoaded = dependencies.map {
        negotiator.classify(it)
    }

    val maven = object : MavenDependencyResolver(
        parentClassLoader = THIS::class.java.classLoader,
    ) {
        override val auditors: Auditors
            get() = super.auditors.replace(
                ConstraintArchiveAuditor(
                    listOf(
                        MavenConstraintNegotiator()
                    ),
                ).chain(object : ArchiveTreeAuditor {
                    override fun audit(event: ArchiveTreeAuditContext): Job<ArchiveTreeAuditContext> = job {
                        event.copy(tree = event.tree.removeIf {
                            alreadyLoaded.contains(negotiator.classify(it.value.descriptor as SimpleMavenDescriptor))
                        }!!)
                    }
                })
            )
    }
    archiveGraph.registerResolver(maven)

    return archiveGraph to DependencyTypeContainer(archiveGraph).apply {
        register("simple-maven", MavenResolverProvider(resolver = maven))
    }
}