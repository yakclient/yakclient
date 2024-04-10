package net.yakclient.components.extloader.util

import net.yakclient.archives.ArchiveReference
import java.nio.file.Path

public fun ArchiveReference.copy(): ArchiveReference = object : ArchiveReference by this {
    override var isClosed: Boolean = this@copy.isClosed
        private set
    override var modified: Boolean = false
        private set
    override val reader: ArchiveReference.Reader = CopiedReader(this@copy.reader)
    override val writer: ArchiveReference.Writer = CopiedWriter()

    override fun close() {
        isClosed = true
    }

    private val modifiedEntries = HashMap<String, ArchiveReference.Entry>()
    private val removed = HashSet<String>()

    private fun ensureOpen() {
        check(!isClosed) { "This reference is closed!" }
    }

    private inner class CopiedReader(
        private val delegate: ArchiveReference.Reader
    ) : ArchiveReference.Reader {
        override fun entries(): Sequence<ArchiveReference.Entry> {
            val alreadyRead = HashSet<String>()

            return (modifiedEntries.keys.asSequence() + delegate.entries().map { it.name })
                .filter(alreadyRead::add).mapNotNull(::of)
        }

        override fun of(name: String): ArchiveReference.Entry? {
            ensureOpen()

            return (modifiedEntries[name] ?: delegate[name])?.takeUnless { removed.contains(it.name) }
        }
    }

    private inner class CopiedWriter : ArchiveReference.Writer {
        override fun put(entry: ArchiveReference.Entry) {
            ensureOpen()
            modifiedEntries[entry.name] = entry
            removed.remove(entry.name)
        }

        override fun remove(name: String) {
            ensureOpen()
            removed.add(name)
        }
    }
}


public fun ArchiveReference.slice(path: Path): ArchiveReference {
    val superDelegate = this.copy()
    return object : ArchiveReference by superDelegate {
        override val name: String = "'${this@slice.name ?: "unnamed archive"}' sliced along $path"
        override val reader: ArchiveReference.Reader = SlicedReader(superDelegate.reader)
        override val writer: ArchiveReference.Writer = SlicedWriter(superDelegate.writer)

        private val thisRef = this

        private inner class SlicedReader(
            private val delegate: ArchiveReference.Reader
        ) : ArchiveReference.Reader {
            override fun entries(): Sequence<ArchiveReference.Entry> = delegate.entries().filter {
                it.name.startsWith(path.toString())
            }.map {
                it.copy(
                    name = it.name.removePrefix(path.toString()).removePrefix("/"),
                    handle = thisRef
                )
            }

            override fun of(name: String): ArchiveReference.Entry? {
                val newName = path.resolve(name).toString()

                return delegate[newName]?.copy(
                    name = name,
                    handle = thisRef
                )
            }
        }

        private inner class SlicedWriter(
            private val delegate: ArchiveReference.Writer
        ) : ArchiveReference.Writer {
            override fun put(entry: ArchiveReference.Entry) {
                delegate.put(entry.copy(
                    name=path.resolve(entry.name).toString()
                ))
            }

            override fun remove(name: String) {
                delegate.remove(name)
            }
        }
    }
}