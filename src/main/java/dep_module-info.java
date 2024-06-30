//module yakclient {
//    requires kotlin.stdlib;
//    requires com.fasterxml.jackson.databind;
//    requires com.fasterxml.jackson.kotlin;
//    requires durganmcbroom.artifact.resolver;
//    requires durganmcbroom.artifact.resolver.simple.maven;
//    requires yakclient.common.util;
//    requires transitive yakclient.archives;
//    requires arrow.core.jvm;
//    requires yakclient.minecraft.bootstrapper;
//    requires transitive yakclient.archives.mixin;
//    requires java.logging;
//    requires yakclient.archive.mapper;
//    requires kotlin.reflect;
//    requires yakclient.client.api;
//    requires yakclient.archive.mapper.transform;
//
//    exports dev.extframework.components.yak.extension;
//    exports dev.extframework.components.yak;
//    exports dev.extframework.components.yak.mixin;
//    exports dev.extframework.components.yak.extension.versioning;
//
//    opens dev.extframework.components.yak.extension to com.fasterxml.jackson.databind, kotlin.reflect;
//}