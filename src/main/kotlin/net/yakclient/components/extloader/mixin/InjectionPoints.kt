package net.yakclient.components.extloader.mixin

import net.yakclient.archives.mixin.SourceInjectionPoint
import net.yakclient.archives.mixin.SourceInjectors


public class InjectionPoints private constructor() {
    public class BeforeEnd : SourceInjectionPoint by SourceInjectors.BEFORE_END

    public class AfterBegin : SourceInjectionPoint by SourceInjectors.AFTER_BEGIN

    public class BeforeInvoke : SourceInjectionPoint by SourceInjectors.BEFORE_INVOKE

    public class BeforeReturn : SourceInjectionPoint by SourceInjectors.BEFORE_RETURN

    public class Overwrite : SourceInjectionPoint by SourceInjectors.OVERWRITE
}