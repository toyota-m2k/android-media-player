package io.github.toyota32k.lib.player.gesture

import io.github.toyota32k.shared.gesture.Direction
import io.github.toyota32k.shared.gesture.IUtManipulationTarget
import io.github.toyota32k.shared.gesture.Orientation
import java.util.EnumSet

/**
 * 最小限（ページングなし）のIUtManipulationTargetの実装
 */
abstract class AbstractManipulationTarget : IUtManipulationTarget {
    override val overScrollX: Float = 0f
    override val overScrollY: Float = 0f
    override val pageOrientation: EnumSet<Orientation> = EnumSet.noneOf(Orientation::class.java)

    override fun changePage(
        orientation: Orientation,
        dir: Direction
    ): Boolean {
        return false
    }

    override fun hasNextPage(
        orientation: Orientation,
        dir: Direction
    ): Boolean {
        return false
    }
}
