package dev.extframework.extension.core.minecraft.mixin

// Source
//            val self = context.definingNode.name.withDots()
//            val point = context.element.annotation.point
//
//            val unmappedTargetClass = context.targetNode.name
//            val unmappedMethodTo = Method(context.element.annotation.methodTo.takeUnless { it == SELF_REF }
//                // TODO using the desc as done below is incorrect because the desc will contain the InjectionContinuation.
//                ?: (context.element.methodNode.name + context.element.methodNode.desc))
//
//            val targetMethod = run {
//                val name = mappingContext.mappings.mapMethodName(
//                    mappingContext.mappings.mapClassName(
//                        unmappedTargetClass,
//                        mappingContext.appNS,
//                        mappingContext.fromNS
//                    ) ?: unmappedTargetClass,
//                    unmappedMethodTo.name,
//                    unmappedMethodTo.descriptor,
//                    mappingContext.fromNS,
//                    mappingContext.toNS
//                ) ?: unmappedMethodTo.name
//                val desc = mappingContext.mappings.mapMethodDesc(
//                    unmappedMethodTo.descriptor,
//                    mappingContext.fromNS,
//                    mappingContext.toNS
//                )
//                Method(name, desc)
//            }
//
//            val targetMethodNode = context.targetNode.methodOf(targetMethod)
//                ?: throw MixinException(message = "Failed to find method: '$targetMethod' in target class: '${context.targetNode.name}'") {
//                    ref.name asContext "Extension name"
//
//                    unmappedTargetClass asContext "Unmapped target class name"
//                    (mappingContext.mappings
//                        .mapClassName(unmappedTargetClass, mappingContext.fromNS, mappingContext.toNS)
//                        ?.withDots() ?: unmappedTargetClass) asContext "Mapped target class name"
//
//                    unmappedMethodTo.descriptor asContext "Unmapped target method signature"
//                    targetMethod.descriptor asContext "Mapped target method signature"
//
//                    mappingContext.fromNS asContext "Unmapped (source) mapping namespace"
//                    mappingContext.toNS asContext "Mapped (target) mapping namespace"
//                }
//            val targetClassname = mappingContext.mappings
//                .mapClassName(unmappedTargetClass, mappingContext.fromNS, mappingContext.toNS)
//                ?.withDots() ?: unmappedTargetClass
//
//            val originStatic = context.element.methodNode.access.and(Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC
//            val descStatic = targetMethodNode.access.and(Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC
//
//            if (originStatic.xor(descStatic))
//                throw MixinException(null, "Method access's dont match! One is static and the other is not.") {
//                    unmappedTargetClass asContext "Unmapped target class name"
//                    targetClassname asContext "Mapped target class name"
//
//                    unmappedMethodTo.descriptor asContext "Unmapped target method signature"
//                    targetMethod.descriptor asContext "Mapped target method signature"
//
//                    self asContext "Mixin classname"
//                    (context.element.methodNode.name + context.element.methodNode.desc) asContext "@SourceInjection method descriptor"
//
//                    mappingContext.fromNS asContext "Unmapped (source) mapping namespace"
//                    mappingContext.toNS asContext "Mapped (target) mapping namespace"
//                }
//
//            val data = RichSourceInjectionData(
//                mappingContext.extensionName,
//                targetClassname,
//                self.withSlashes(),
//                run {
//                    val methodFrom = context.element.methodNode.name + context.element.methodNode.desc
//                    mappingInsnAdapterFor(
//                        mappingContext.tree,
//                        mappingContext.mappings,
//                        self.withSlashes(),
//                        unmappedTargetClass,
//                        ProvidedInstructionReader(
//                            context.definingNode.methods.firstOrNull {
//                                (it.name + it.desc) == methodFrom // Method signature does not get mapped
//                            }?.instructions
//                                ?: throw IllegalArgumentException("Failed to find method: '$methodFrom'.")
//                        )
//                    )
//                },
//                context.element.methodNode.toMethod(),
//                run {
//
//                    targetMethod
//                },
//                checkNotNull(mappingContext.environment[injectionPointsAttrKey].getOrNull()?.container?.get(point)) {
//                    "Illegal injection point: '$point', current registered options are: '${mappingContext.environment[injectionPointsAttrKey].getOrNull()?.container?.objects()?.keys ?: listOf()}'"
//                },
//                originStatic
//            )
//
//            data