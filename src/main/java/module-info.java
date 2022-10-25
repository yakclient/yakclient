module yakclient.yakclient.plugin {
    requires kotlin.stdlib;
    requires yakclient.boot;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.kotlin;
    requires kotlin.stdlib.jdk7;
    requires durganmcbroom.artifact.resolver;
    requires durganmcbroom.artifact.resolver.simple.maven;
    requires yakclient.common.util;
    requires yakclient.archives;
    requires arrow.core.jvm;

    exports net.yakclient.plugins.yakclient.extension to com.fasterxml.jackson.databind;
    opens net.yakclient.plugins.yakclient.extension to com.fasterxml.jackson.databind, kotlin.reflect;
}