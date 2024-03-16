package net.yakclient.components.extloader.extension

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.job
import com.durganmcbroom.resources.openStream
import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.MappingNodeContainerImpl
import net.yakclient.archive.mapper.MappingValueContainerImpl
import net.yakclient.archive.mapper.transform.*
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.ArchiveTree
import net.yakclient.archives.Archives
import net.yakclient.archives.transform.TransformerConfig
import net.yakclient.boot.archive.ArchiveAccessTree
import net.yakclient.boot.loader.ArchiveSourceProvider
import net.yakclient.client.api.Extension
import net.yakclient.common.util.LazyMap
import net.yakclient.common.util.runCatching
import net.yakclient.components.extloader.api.environment.*
import net.yakclient.components.extloader.api.extension.ExtensionClassLoaderProvider
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel
import net.yakclient.components.extloader.api.extension.ExtensionVersionPartition
import net.yakclient.components.extloader.api.extension.archive.ExtensionArchiveReference
import net.yakclient.components.extloader.api.target.ApplicationTarget
import net.yakclient.components.extloader.extension.versioning.VersionedExtArchiveHandle
import net.yakclient.components.extloader.mapping.findShortest
import net.yakclient.components.extloader.mapping.newMappingsGraph
import net.yakclient.components.extloader.util.parseNode
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

public class ExtensionContainerLoader(
    private val parentClassloader: ClassLoader,
    private val environment: ExtLoaderEnvironment
) : EnvironmentAttribute {
    private val minecraftRef = environment[ApplicationTarget].extract().reference.reference
    private val mcVersion = environment[ApplicationTarget].extract().reference.descriptor.version
    private val appInheritanceTree = createFakeInheritanceTree(minecraftRef.reader)
    override val key: EnvironmentAttributeKey<*> = ExtensionContainerLoader

    public companion object : EnvironmentAttributeKey<ExtensionContainerLoader>

    private fun transformEachEntry(
        archiveReference: ExtensionArchiveReference,
        // Dependencies should already be mapped
        dependencies: List<ArchiveTree>,
        mappings: (ExtensionVersionPartition) -> ArchiveMapping
    ): Job<ClassInheritanceTree> = job(JobName("Transform all entries in archive reference.")) {
        // Gets all the loaded mixins and map them to their actual location in the archive reference.

        fun inheritancePathFor(
            node: ClassNode
        ): Job<ClassInheritancePath> = job {
            fun ClassNode.getParent(name: String?): ClassInheritancePath? {
                if (name == null) return null

                val treeFromApp = run {
                    val entry = archiveReference.reader["${this.name}.class"] ?: return@run null

                    val part = archiveReference.reader.determinePartition(entry).first()

                    if (part is ExtensionVersionPartition) {
                        appInheritanceTree[mappings(part).mapClassName(
                            name,
                            part.mappingNamespace,
                            environment[ApplicationMappingTarget].extract().namespace
                        ) ?: name]
                    } else null
                }

                val treeFromRef = archiveReference.reader["$name.class"]?.let { e ->
                    inheritancePathFor(
                        e.resource.openStream().parseNode()
                    )().merge()
                }

                val treeFromDependencies = dependencies.firstNotNullOfOrNull {
                    it.getResource("$name.class")?.parseNode()?.let(::inheritancePathFor)?.invoke()?.merge()
                }

                return treeFromApp ?: treeFromRef ?: treeFromDependencies
            }

            ClassInheritancePath(
                node.name,
                node.getParent(node.superName),
                node.interfaces.mapNotNull { node.getParent(it) }
            )
        }

        val treeInternal = (archiveReference.reader.entries())
            .filterNot(ArchiveReference.Entry::isDirectory)
            .filter { it.name.endsWith(".class") }
            .associate { e ->
                val path = inheritancePathFor(
                    e.resource.openStream()
                        .parseNode()
                )().merge()
                path.name to path
            }

        val tree = object : Map<String, ClassInheritancePath> by treeInternal {
            override fun get(key: String): ClassInheritancePath? {
                return treeInternal[key] ?: appInheritanceTree[key]
            }
        }

        // Map each entry based on its respective mapper
        archiveReference.reader.entries()
            .filter { it.name.endsWith(".class") }
            .forEach { entry ->
            val part = archiveReference.reader.determinePartition(entry).first()
            val config = mapperFor(
                archiveReference,
                dependencies,
                if (part is ExtensionVersionPartition) mappings(part) else ArchiveMapping(
                    setOf(), MappingValueContainerImpl(
                        mapOf()
                    ), MappingNodeContainerImpl(setOf())
                ),
                tree,
                if (part is ExtensionVersionPartition) part.mappingNamespace else environment[ApplicationMappingTarget].extract().namespace
            )

            // TODO, this will recompute frames, see if we need to do that or not.
            Archives.resolve(
                ClassReader(entry.resource.openStream()),
                config,
            )

            archiveReference.writer.put(
                entry.transform(
                    config, dependencies
                )
            )
        }

        tree
    }

    public fun load(
        ref: ExtensionArchiveReference,
        parents: List<ExtensionNode>,
        dependencies: List<ArchiveHandle>,
        erm: ExtensionRuntimeModel,
        access: ArchiveAccessTree,
    ): Job<ExtensionContainer> = job {
        val mappingGraph = newMappingsGraph(environment[mappingProvidersAttrKey].extract())

        // From, (to is implicitly the application target), and identifier (version)
        val mappings = LazyMap<Pair<String, String>, ArchiveMapping> { (fromNS, identifier) ->
            mappingGraph.findShortest(fromNS, environment[ApplicationMappingTarget].extract().namespace)
                .forIdentifier(identifier)
        }

        val partitionsToMappings = ref.enabledPartitions.associateWith {
            mappings[it.mappingNamespace to mcVersion]
        }

        val tree = transformEachEntry(ref, (dependencies + minecraftRef)) {
            partitionsToMappings[it]!!
        }().merge()


        ExtensionContainer(
            environment,
            ref,
            tree,
            {
                partitionsToMappings[it]!!
            }
        ) { minecraft ->
            val archives: List<ArchiveHandle> =
                parents.mapNotNull { it.archive } + dependencies

            val handle = VersionedExtArchiveHandle(
                ref,
                environment[ExtensionClassLoaderProvider].extract().createFor(
                    ref,
                    access,
                    parentClassloader,
                ),
                erm.name,
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
        }
    }

    private fun mapperFor(
        archive: ArchiveReference,
        dependencies: List<ArchiveTree>,
        mappings: ArchiveMapping,
        tree: ClassInheritanceTree,
        fromNS: String,
    ): TransformerConfig {
        val toNamespace = environment[ApplicationMappingTarget].extract().namespace

        fun ClassInheritancePath.fromTreeInternal(): ClassInheritancePath {
            val mappedName = mappings.mapClassName(name, fromNS, toNamespace) ?: name

            return ClassInheritancePath(
                mappedName,
                superClass?.fromTreeInternal(),
                interfaces.map { it.fromTreeInternal() }
            )
        }

        val lazilyMappedTree = LazyMap<String, ClassInheritancePath> {
            tree[mappings.mapClassName(it, fromNS, toNamespace)]?.fromTreeInternal()
        }

        return mappingTransformConfigFor(
            ArchiveTransformerContext(
                archive,
                dependencies,
                mappings,
                fromNS,
                toNamespace,
                lazilyMappedTree,
            )
        )
    }
}