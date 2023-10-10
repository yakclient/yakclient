package net.yakclient.components.extloader.extension

import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.transform.*
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.ArchiveTree
import net.yakclient.archives.Archives
import net.yakclient.archives.transform.TransformerConfig
import net.yakclient.boot.container.ProcessLoader
import net.yakclient.boot.loader.ArchiveClassProvider
import net.yakclient.boot.loader.ArchiveSourceProvider
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.client.api.Extension
import net.yakclient.client.api.ExtensionContext
import net.yakclient.common.util.LazyMap
import net.yakclient.common.util.runCatching
import net.yakclient.components.extloader.api.environment.*
import net.yakclient.components.extloader.extension.versioning.VersionedExtArchiveHandle
import net.yakclient.components.extloader.mapping.findShortest
import net.yakclient.components.extloader.mapping.newMappingsGraph
import net.yakclient.components.extloader.api.target.ApplicationTarget
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveReference
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

public class ExtensionProcessLoader(
    private val privilegeManager: PrivilegeManager,
    private val parentClassloader: ClassLoader,
//        private val minecraftRef: ArchiveReference,
//        private val mcVersion: String
    private val environment: ExtLoaderEnvironment
) : ProcessLoader<ExtensionInfo, ExtensionProcess>, EnvironmentAttribute {
    private val minecraftRef = environment[ApplicationTarget]!!.reference.reference
    private val mcVersion = environment[ApplicationTarget]!!.reference.descriptor.version
    private val mcInheritanceTree = createFakeInheritanceTree(minecraftRef.reader)
    override val key: EnvironmentAttributeKey<*> = ExtensionProcessLoader

    public companion object : EnvironmentAttributeKey<ExtensionProcessLoader>

    private fun transformEachEntry(
        erm: ExtensionRuntimeModel,
        archiveReference: ExtensionArchiveReference,
        dependencies: List<ArchiveTree>,
        mappings: ArchiveMapping
    ): ClassInheritanceTree {
        // Gets all the loaded mixins and map them to their actual location in the archive reference.
        val mixinClasses = erm.versionPartitions
            .flatMapTo(HashSet()) { v ->
                v.mixins.map { ("${v.path.removeSuffix("/")}/${it.classname.replace('.', '/')}.class") }
            }

        // Goes through the enabled partitions and main, and group them by same mapping types. Then load that specific mapper
//        val entryToMapping = (archiveReference.enabledPartitions + archiveReference.mainPartition)
//                .map {
//                    it to archiveReference.reader.entriesIn(it.name)
//                            .filterNot(ArchiveReference.Entry::isDirectory)
//                            .filter { entry -> entry.name.endsWith(".class") }
//                }
//                .groupBy { it.first.mappings }
//                .mapValues { (_, entries) -> entries.flatMap { it.second } }
//                .mapValues { (_, entries) ->
//                    entries.filterNot {mixinClasses.contains(it.name) }
//                }.flatMap { (mapping, entries) ->
//                    val mappings = TODO()
////                        (InternalRegistry.extensionMappingContainer.get(mapping.type)?.forIdentifier(mcVersion)
////                            ?: throw IllegalArgumentException("Failed to find mapping type: '${mapping.type}', options are: '${InternalRegistry.extensionMappingContainer.objects().keys}"))
//                    entries.map { it to mappings }
//                }.toMap()


        fun inheritancePathFor(
            entry: ArchiveReference.Entry
        ): ClassInheritancePath {
            val reader = ClassReader(entry.resource.open())
            val node = ClassNode()
            reader.accept(node, 0)

//            val mappings = entryToMapping[entry] ?: throw IllegalArgumentException(
//                    "Unknown class: '${entry.name}' encountered when trying to create inheritance path " +
//                            "for extension archive mapping. This might signify that you either have a illegal " +
//                            "dependency on a partition that is not active when partition: '${archiveReference.reader.determinePartition(entry)}' " +
//                            "is, or this class is a subtype of a mixin class (also illegal).")

            fun getParent(name: String?): ClassInheritancePath? {
                if (name == null) return null
                return mcInheritanceTree[mappings.mapClassName(node.superName, MappingDirection.TO_FAKE)]
                    ?: node.superName?.let { entry.handle.reader["$it.class"] }?.let { inheritancePathFor(it) }
            }

            return ClassInheritancePath(
                node.name,
                getParent(node.superName),
                node.interfaces.mapNotNull(::getParent)
            )
        }

        // Load an inheritance tree based on the mappings of each partition
//        val treeInternal = entryToMapping.map { (e, _) ->
//            inheritancePathFor(e)
//        }.associateBy { it.name }
        val treeInternal = (archiveReference.reader.entries())
            .filterNot(ArchiveReference.Entry::isDirectory)
            .filter { it.name.endsWith(".class") }
            .associate {
                val path = inheritancePathFor(it)
                path.name to path
            }

        val tree = object : Map<String, ClassInheritancePath> by treeInternal {
            override fun get(key: String): ClassInheritancePath? {
                return treeInternal[key] ?: mcInheritanceTree[key]
            }
        }

        // Map each entry based on its respective mapper
        archiveReference.reader.entries().map { entry ->
            val config = mapperFor(mappings, tree)

            Archives.resolve(
                ClassReader(entry.resource.open()),
                config,
            )

            archiveReference.writer.put(
                entry.transform(
                    config, dependencies
                )
            )
        }

        // Not particularly good design here, but its all internal class stuff that can be very easily moved around without disrupting public apis so its good enough for now
        return tree
    }

    override fun load(info: ExtensionInfo): ExtensionProcess {
        val (ref, children, dependencies, erm, containerHandle) = info

        val archives: List<ArchiveHandle> =
            children.map { it.process.archive } + dependencies

        val mappingGraph = newMappingsGraph(environment[mappingProvidersAttrKey]!!)

        val mappings = mappingGraph.findShortest(erm.mappingType, environment[ApplicationMappingType]!!.type)
            .forIdentifier(mcVersion)

        val tree = transformEachEntry(erm, ref, archives + minecraftRef, mappings)

        return ExtensionProcess(
            ExtensionReference(
                environment,
                ref,
                tree,
                mappings
            ) { minecraft ->
                val handle = VersionedExtArchiveHandle(
                    ref,
                    ExtensionClassLoader(
                        ref,
                        archives,
                        privilegeManager,
                        parentClassloader,
                        containerHandle,
                        minecraft
                    ),
                    info.erm.name,
                    archives.toSet(),
                    ArchiveSourceProvider(ref).packages
                )

                val s = "${erm.groupId}:${erm.name}:${erm.version}"

                val extensionClass =
                    runCatching(ClassNotFoundException::class) { handle.classloader.loadClass(erm.extensionClass) }
                        ?: throw IllegalArgumentException("Could not load extension: '$s' because the class: '${erm.extensionClass}' couldnt be found.")
                val extensionConstructor = runCatching(NoSuchMethodException::class) { extensionClass.getConstructor() }
                    ?: throw IllegalArgumentException("Could not find no-arg constructor in class: '${erm.extensionClass}' in extension: '$s'.")

                val instance = extensionConstructor.newInstance() as? Extension
                    ?: throw IllegalArgumentException("Extension class: '${erm.extensionClass}' does not implement: '${Extension::class.qualifiedName} in extension: '$s'.")

                instance to handle
            },
            ExtensionContext()
        )
    }

    private fun mapperFor(
        mappings: ArchiveMapping,
        tree: ClassInheritanceTree,
    ): TransformerConfig {
        val direction = MappingDirection.TO_FAKE

        fun ClassInheritancePath.fromTreeInternal(): ClassInheritancePath {
            val mappedName = mappings.mapClassName(name, MappingDirection.TO_REAL) ?: name

            return ClassInheritancePath(
                mappedName,
                superClass?.fromTreeInternal(),
                interfaces.map { it.fromTreeInternal() }
            )
        }

        val lazilyMappedTree = LazyMap<String, ClassInheritancePath> {
            tree[mappings.mapClassName(it, MappingDirection.TO_FAKE)]?.fromTreeInternal()
        }

        return mappingTransformConfigFor(
            mappings,
            direction,
            lazilyMappedTree
        )
    }
}