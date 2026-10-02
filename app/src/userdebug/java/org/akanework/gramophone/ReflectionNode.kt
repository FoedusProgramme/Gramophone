/**
 * This file (ReflectionNode.kt) is dual-licensed (you can choose one):
 * - Project license (as of writing GPL-3.0-or-later)
 * - Apache-2.0
 * Because it's really only useful for debugging, and I hope this will be of use for someone.
 * Copyright (C) 2024 nift4
 */

package org.akanework.gramophone

import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * One page of the reflection browser: [obj] and the entries that can be opened from it. Opening
 * an entry gives the next page, and ".." goes back to [parent].
 */
class ReflectionNode(
    private val obj: Any?,
    val parent: ReflectionNode?,
    listPreview: Boolean,
) {
    /** A row: its label, and the page it opens, or null if it opens nothing. */
    class Entry(val label: String, val open: (() -> ReflectionNode)?)

    val entries: List<Entry> = buildList {
        if (parent != null) {
            add(Entry("..") { parent })
        }
        if (obj != null) {
            if (obj is List<*>) {
                add(Entry(obj::class.qualifiedName + " with entry count " + obj.size) {
                    ReflectionNode(obj, parent, !listPreview)
                })
                for (i in 0..<obj.size) {
                    add(Entry(i.toString() + if (listPreview) (": " + obj[i].toString()) else "") {
                        ReflectionNode(obj[i], this@ReflectionNode, false)
                    })
                }
            } else {
                add(Entry(obj.toString(), null))
                for (m in obj::class.memberProperties) {
                    try {
                        m.isAccessible = true
                        add(Entry(m.name) { ReflectionNode(m.call(obj), this@ReflectionNode, false) })
                    } catch (e: Exception) {
                        add(Entry("INACCESSIBLE: " + m.name, null))
                    } catch (e: Error) {
                        add(Entry("INACCESSIBLE: " + m.name, null))
                    }
                }
                for (m in obj::class.memberFunctions) {
                    try {
                        m.isAccessible = true
                        add(Entry(
                            m.name + '(' + m.toString()
                                .substringAfter('(', missingDelimiterValue = "NOT CALLABLE)")
                        ) { ReflectionNode(m.call(obj), this@ReflectionNode, false) })
                    } catch (e: Exception) {
                        add(Entry("INACCESSIBLE: " + m.name, null))
                    } catch (e: Error) {
                        add(Entry("INACCESSIBLE: " + m.name, null))
                    }
                }
            }
        } else add(Entry("null", null))
    }
}
