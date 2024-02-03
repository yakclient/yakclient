package net.yakclient.components.extloader.tweaker.archive

import net.yakclient.archives.ArchiveReference

internal class TweakerArchiveReference(
    override val name: String,
    val path: String,
    private val delegate: ArchiveReference
) : ArchiveReference by delegate {
    override val reader: ArchiveReference.Reader = TweakerArchiveReader()

    private inner class TweakerArchiveReader : ArchiveReference.Reader {
        private val reader = delegate.reader
        override fun entries(): Sequence<ArchiveReference.Entry> {
            return reader.entries()
                .filter { it.name.startsWith(path) }
                .map { it.copy(name = it.name.removePrefix(path).removePrefix("/")) }
        }

        override fun of(name: String): ArchiveReference.Entry? {
            return reader.of(path + name)?.copy(name = name)
        }
    }
}