package net.yakclient.components.extloader.api.tweaker

public data class TweakerRuntimeModel(
    val name: String,
    val entrypoint: String
)

public const val TRM_LOCATION: String = "trm.json"