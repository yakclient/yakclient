package dev.extframework.extloader

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.extloader.environment.registerBasicSerializers
import dev.extframework.extloader.exception.BasicExceptionPrinter
import dev.extframework.extloader.exception.handleException
import dev.extframework.extloader.workflow.Workflow
import dev.extframework.extloader.workflow.WorkflowContext
import dev.extframework.internal.api.environment.*
import dev.extframework.internal.api.exception.StackTracePrinter
import dev.extframework.internal.api.exception.StructuredException
import dev.extframework.internal.api.target.ApplicationTarget
import java.nio.file.Path
import kotlin.system.exitProcess

public const val EXT_LOADER_GROUP: String = "dev.extframework.components"
public const val EXT_LOADER_ARTIFACT: String = "ext-loader"
public const val EXT_LOADER_VERSION: String = "1.1.1-SNAPSHOT"

public fun <T: WorkflowContext> work(
    path: Path,

    archiveGraph: ArchiveGraph,
    dependencyTypes: DependencyTypeContainer,

    context: T,
    workflow: Workflow<T>,

    app: ApplicationTarget,

    environment: ExtensionEnvironment = ExtensionEnvironment(),
): Job<Unit> = job {
    environment += MutableObjectSetAttribute(
        exceptionCxtSerializersAttrKey,
    ).registerBasicSerializers()
    environment += BasicExceptionPrinter()

    environment += ArchiveGraphAttribute(archiveGraph)
    environment += DependencyTypeContainerAttribute(
        dependencyTypes
    )

    environment += app
    environment += ValueAttribute(path, wrkDirAttrKey)

    environment += ValueAttribute(ClassLoader.getSystemClassLoader(), parentCLAttrKey)

    workflow.work(context, environment)().handleStructuredException(environment)
}

private fun <T> Result<T>.handleStructuredException(
    env: ExtensionEnvironment
) {
    exceptionOrNull()?.run {
        if (this !is StructuredException) {
            throw this
        } else {
            handleException(env[exceptionCxtSerializersAttrKey].extract(), env[StackTracePrinter].extract(), this)
            exitProcess(-1)
        }
    }
}