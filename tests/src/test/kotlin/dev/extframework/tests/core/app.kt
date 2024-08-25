package dev.extframework.tests.core

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.Archives
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ArchiveClassLoader
import dev.extframework.boot.archive.ArchiveTarget
import dev.extframework.boot.archive.ClassLoadedArchiveNode
import dev.extframework.internal.api.target.ApplicationDescriptor
import dev.extframework.internal.api.target.ApplicationTarget
import java.nio.file.Path

fun createEmptyApp() : ApplicationTarget {
    return object : ApplicationTarget {
        override val node: ClassLoadedArchiveNode<ApplicationDescriptor> = object : ClassLoadedArchiveNode<ApplicationDescriptor>  {
            private val appDesc = ApplicationDescriptor.parseDescription("test:app:1")!!
            override val access: ArchiveAccessTree = object : ArchiveAccessTree {
                override val descriptor: ArtifactMetadata.Descriptor = appDesc
                override val targets: List<ArchiveTarget> = listOf()

            }
            override val descriptor: ApplicationDescriptor = appDesc
            override val handle: ArchiveHandle? = null
        }
    }
}

fun createBlackboxApp(path: Path) : ApplicationTarget {
    return object : ApplicationTarget {
        override val node: ClassLoadedArchiveNode<ApplicationDescriptor> = object : ClassLoadedArchiveNode<ApplicationDescriptor>  {
            private val appDesc = ApplicationDescriptor.parseDescription("test:app:1")!!
            override val access: ArchiveAccessTree = object : ArchiveAccessTree {
                override val descriptor: ArtifactMetadata.Descriptor = appDesc
                override val targets: List<ArchiveTarget> = listOf()
            }
            override val descriptor: ApplicationDescriptor = appDesc
            override val handle: ArchiveHandle = run {
                val ref = Archives.find(path, Archives.Finders.ZIP_FINDER)
                Archives.resolve(ref, ArchiveClassLoader(ref, access, ClassLoader.getSystemClassLoader()), Archives.Resolvers.ZIP_RESOLVER).archive
            }
        }
    }
}