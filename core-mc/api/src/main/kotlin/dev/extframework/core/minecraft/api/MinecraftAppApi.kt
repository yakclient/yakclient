package dev.extframework.core.minecraft.api

import dev.extframework.tooling.api.target.ApplicationTarget
import java.nio.file.Path

public abstract class MinecraftAppApi(
    public val classpath: List<Path>,
) : ApplicationTarget {
    public abstract val gameDir: Path
    public abstract val gameJar: Path
}

