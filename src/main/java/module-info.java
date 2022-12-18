module yakclient {
    requires kotlin.stdlib;
    requires yakclient.boot;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.kotlin;
    requires durganmcbroom.artifact.resolver;
    requires durganmcbroom.artifact.resolver.simple.maven;
    requires yakclient.common.util;
    requires transitive yakclient.archives;
    requires arrow.core.jvm;
    requires yakclient.minecraft.bootstrapper;
    requires transitive yakclient.archives.mixin;
    requires java.logging;
    requires yakclient.archive.mapper;

    exports net.yakclient.components.yak.extension;
    exports net.yakclient.components.yak;
    exports net.yakclient.components.yak.mixin;
    opens net.yakclient.components.yak.extension to com.fasterxml.jackson.databind, kotlin.reflect;
}