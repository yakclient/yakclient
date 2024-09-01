package dev.extframework.extloader.test.extension

import BootLoggerFactory
import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.launch
import dev.extframework.archives.ArchiveHandle
import dev.extframework.boot.archive.*
import dev.extframework.boot.audit.Auditors
import dev.extframework.boot.audit.chain
import dev.extframework.boot.constraint.ConstraintArchiveAuditor
import dev.extframework.boot.dependency.BasicDependencyNode
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.boot.maven.MavenConstraintNegotiator
import dev.extframework.boot.maven.MavenDependencyResolver
import dev.extframework.boot.maven.MavenResolverProvider
import dev.extframework.boot.monad.removeIf
import dev.extframework.common.util.readInputStream
import dev.extframework.extloader.work
import dev.extframework.extloader.workflow.DevWorkflow
import dev.extframework.extloader.workflow.DevWorkflowContext
import dev.extframework.internal.api.extension.artifact.ExtensionDescriptor
import dev.extframework.internal.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.internal.api.target.ApplicationDescriptor
import dev.extframework.internal.api.target.ApplicationTarget
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class TweakerTest {
    @Test
    fun `Load extension`() {
        val path = Files.createTempDirectory("temp")

        val (g, d) = setupBoot(path)

        launch(BootLoggerFactory()) {
            work(
                path,
                g, d,
                DevWorkflowContext(
                    ExtensionDescriptor.parseDescriptor("dev.extframework.extension:core-mc:1.0-SNAPSHOT"),
                    ExtensionRepositorySettings.local()
                ),
                DevWorkflow(),
                object : ApplicationTarget {
                    override val node: ClassLoadedArchiveNode<ApplicationDescriptor> =
                        object : ClassLoadedArchiveNode<ApplicationDescriptor> {
                            private val appDesc = ApplicationDescriptor.parseDescription("test:app:1")!!
                            override val access: ArchiveAccessTree = object : ArchiveAccessTree {
                                override val descriptor: ArtifactMetadata.Descriptor = appDesc
                                override val targets: List<ArchiveTarget> = listOf()

                            }
                            override val descriptor: ApplicationDescriptor = appDesc
                            override val handle: ArchiveHandle? = null
                        }
                }
            )().merge()
        }
    }
}

fun setupBoot(path: Path): Pair<ArchiveGraph, DependencyTypeContainer> {
//    val dependencies = TweakerTest::class.java.getResource("/dependencies.txt")?.openStream()?.use {
//        val fileStr = String(it.readInputStream())
//        fileStr.split("\n").toSet()
//    }?.filterNot { it.isBlank() }?.mapTo(HashSet()) { SimpleMavenDescriptor.parseDescription(it)!! }
//        ?: throw IllegalStateException("Cant load dependencies?")

    val archiveGraph = DefaultArchiveGraph(
        path,

//        dependencies.associateByTo(HashMap()) {
//            BasicDependencyNode(it, null,
//                object : ArchiveAccessTree {
//                    override val descriptor: ArtifactMetadata.Descriptor = it
//                    override val targets: List<ArchiveTarget> = listOf()
//                }
//            )
//        } as MutableMap<ArtifactMetadata.Descriptor, ArchiveNode<*>>
    )

    val negotiator = MavenConstraintNegotiator()

//    val alreadyLoaded = dependencies.map {
//        negotiator.classify(it)
//    }

    val maven = object : MavenDependencyResolver(
        parentClassLoader = TweakerTest::class.java.classLoader,
    ) {
        override val auditors: Auditors
            get() = super.auditors.replace(
                ConstraintArchiveAuditor(
                    listOf(MavenConstraintNegotiator()),
                ).chain(object : ArchiveTreeAuditor {
                    override fun audit(event: ArchiveTreeAuditContext): Job<ArchiveTreeAuditContext> = job {
                        event.copy(tree = event.tree.removeIf {
                            false
//                            alreadyLoaded.contains(negotiator.classify(it.value.descriptor as SimpleMavenDescriptor))
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