package net.yakclient.components.extloader.util

import com.durganmcbroom.artifact.resolver.*

public class EmptyResolutionContext<R : ArtifactRequest<*>, S : ArtifactStub<R, *>, T : ArtifactReference<*, S>> :
    ResolutionContext<R, S, T>(
        object : ArtifactRepositoryContext<R, S, T> {
            override val artifactRepository: ArtifactRepository<R, S, T>
                get() = throw UnsupportedOperationException("Illegal activity here.")
        },
        object : StubResolverContext<S, T> {
            override val stubResolver: ArtifactStubResolver<*, S, T>
                get() = throw UnsupportedOperationException("Illegal activity here.")
        },
        object : ArtifactComposerContext {
            override val artifactComposer: ArtifactComposer
                get() = throw UnsupportedOperationException("Illegal activity here.")
        }
    )