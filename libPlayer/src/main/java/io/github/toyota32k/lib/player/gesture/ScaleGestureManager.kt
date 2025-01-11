package io.github.toyota32k.lib.player.gesture

import android.content.Context
import android.view.View
import androidx.lifecycle.LifecycleOwner
import io.github.toyota32k.shared.gesture.IUtManipulationTarget
import io.github.toyota32k.shared.gesture.UtGestureInterpreter
import io.github.toyota32k.shared.gesture.UtGestureInterpreter.IListenerBuilder
import io.github.toyota32k.shared.gesture.UtManipulationAgent

/**
 * ジェスチャー（UtGestureInterpreter）と対象ビュー（IUtManipulationTarget）、アクション(UtManipulationAgent) をまとめて管理するクラス
 *
 * usage
 * Activity
 *   private lateinit var scaleGestureManager: ScaleGestureManager(this.applicationContext, true)
 *   override fun onCreate(savedInstanceState: Bundle?) {
 *      ...
 *      scaleGestureManager = scaleGestureManager.setup(this) {
 *          onTap {
 *              ...
 *          }
 *          onDoubleTap {
 *              ...
 *          }
 *          ...
 *      }
 *   }
 */
class ScaleGestureManager(
    applicationContext: Context,
    enableDoubleTap:Boolean,        // !rapidTap for GestureInterpreter
    val manipulationTarget: IUtManipulationTarget,
    minScale:Float = 0f, maxScale:Float = 10f,
) {
    val gestureInterpreter = UtGestureInterpreter(applicationContext, true, !enableDoubleTap)
    val agent = UtManipulationAgent(manipulationTarget, minScale, maxScale)

    /**
     * Activity#onCreate()から呼び出す
     */
    @Suppress("unused")
    fun setup(owner:LifecycleOwner, view:View? = null, setupMe: IListenerBuilder.()->Unit) : ScaleGestureManager {
        gestureInterpreter.setup(owner, view ?: manipulationTarget.parentView, setupMe)
        gestureInterpreter.scrollListener.add(owner, agent::onScroll)
        gestureInterpreter.scaleListener.add(owner, agent::onScale)
        return this
    }
}