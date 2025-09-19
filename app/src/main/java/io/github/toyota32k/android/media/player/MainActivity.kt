package io.github.toyota32k.android.media.player

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.android.media.player.databinding.ActivityMainBinding
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.clickBinding
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.dialog.broker.IUtActivityBrokerStoreProvider
import io.github.toyota32k.dialog.broker.UtActivityBrokerStore
import io.github.toyota32k.dialog.broker.pickers.UtOpenFilePicker
import io.github.toyota32k.dialog.mortal.UtMortalActivity
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.lib.player.model.IMediaSource
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.lib.player.model.Range
import io.github.toyota32k.lib.player.model.chapter.Chapter
import io.github.toyota32k.lib.player.model.chapter.ChapterEditor
import io.github.toyota32k.lib.player.model.chapter.MutableChapterList
import io.github.toyota32k.lib.player.model.chapterAt
import io.github.toyota32k.lib.player.model.skipChapter
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.utils.gesture.UtGestureInterpreter
import io.github.toyota32k.utils.gesture.UtManipulationAgent
import io.github.toyota32k.utils.gesture.UtSimpleManipulationTarget
import java.util.concurrent.atomic.AtomicLong
import kotlin.getValue

class MainActivity : UtMortalActivity(), IUtActivityBrokerStoreProvider {
    companion object {
        val logger = UtLog("MAIN", null, MainActivity::class.java)
    }
    class MainViewModel(application: Application) : AndroidViewModel(application){
        val hasSource: Boolean
            get() = playerModel.currentSource.value != null
        data class FileSource(val fileUri:Uri): IMediaSource {
            override val id: String
                get() = uri
            override val uri: String
                get() = fileUri.toString()
            override val name: String
                get() = fileUri.lastPathSegment ?: "noname"
            override val trimming: Range = Range.empty
            override val type: String get() = "mp4"
            override val startPosition = AtomicLong(0)
        }


        val playerControllerModel = PlayerControllerModel.Builder(application, viewModelScope)
            .supportChapter()
            .supportSnapshot(this::onSnapshot)
            .enableRotateLeft()
            .enableRotateRight()
            .enableSeekSmall(0,0)
            .enableSeekMedium(1000, 3000)
            .enableSeekLarge(5000, 10000)
            .enableSliderLock(true)
            .counterInMs()
            .build()
        val playerModel get() = playerControllerModel.playerModel
        // region Chapter Editor

        lateinit var chapterList : ChapterEditor

        val commandAddChapter = LiteUnitCommand {
            chapterList.addChapter(playerModel.currentPosition, "", null)
        }
        val commandAddSkippingChapter = LiteUnitCommand {
            val neighbor = chapterList.getNeighborChapters(playerModel.currentPosition)
            val prev = neighbor.getPrevChapter(chapterList)
            if(neighbor.hit<0) {
                // 現在位置にチャプターがなければ追加する
                if(!chapterList.addChapter(playerModel.currentPosition, "", null)) {
                    return@LiteUnitCommand
                }
            }
            // ひとつ前のチャプターを無効化する
            if(prev!=null) {
                chapterList.skipChapter(prev, true)
            }
        }
        val commandRemoveChapter = LiteUnitCommand {
            val neighbor = chapterList.getNeighborChapters(playerModel.currentPosition)
            chapterList.removeChapterAt(neighbor.next)
        }
        val commandRemoveChapterPrev = LiteUnitCommand {
            val neighbor = chapterList.getNeighborChapters(playerModel.currentPosition)
            chapterList.removeChapterAt(neighbor.prev)
        }
        val commandToggleSkip = LiteUnitCommand {
            val chapter = chapterList.getChapterAround(playerModel.currentPosition) ?: return@LiteUnitCommand
            chapterList.skipChapter(chapter, !chapter.skip)
        }
        val commandUndo = LiteUnitCommand {
            chapterList.undo()
        }
        val commandRedo = LiteUnitCommand {
            chapterList.redo()
        }
//        val commandSplit = LiteUnitCommand {
//            if(playerModel.naturalDuration.value < SelectRangeDialog.MIN_DURATION) return@LiteUnitCommand
//            UtImmortalTask.launchTask("split") {
//                val current = playerControllerModel.rangePlayModel.value
//                val params = if(current!=null) {
//                    SplitParams.fromModel(current)
//                } else {
//                    SplitParams.create(playerModel.naturalDuration.value)
//                }
//                val result = SelectRangeDialog.show(this, params)
//                if(result!=null) {
//                    playerControllerModel.setRangePlayModel(result.toModel())
//                }
//            }
//        }

        // endregion

        // Source File

        fun setSource(source: Uri) {
            playerModel.setSource(FileSource(source))
            chapterList = ChapterEditor(MutableChapterList())
            if(chapterList.chapterAt(0)?.position!=0L) {
                // 動画先頭位置が暗黙のチャプターとして登録されていることを前提に動作する。
                chapterList.addChapter(0, "", null)
            }
        }

        private fun onSnapshot(pos:Long, bitmap: Bitmap) {
            SnapshotDialog.showBitmap(bitmap)
        }

        override fun onCleared() {
            super.onCleared()
            logger.debug()
            playerControllerModel.close()
        }
    }

    override val activityBrokers = UtActivityBrokerStore(this, UtOpenFilePicker())
    private val viewModel by viewModels<MainViewModel>()
    private lateinit var controls: ActivityMainBinding
    private val binder = Binder()

    private val gestureInterpreter: UtGestureInterpreter by lazy { UtGestureInterpreter(this.applicationContext, enableScaleEvent = true) }
    private val manipulationAgent by lazy { UtManipulationAgent(UtSimpleManipulationTarget(controls.videoViewer,controls.videoViewer.controls.player)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        controls = ActivityMainBinding.inflate(layoutInflater)
        setContentView(controls.root)
        setupWindowInsetsListener(controls.root)

        binder.owner(this)

        // videoPlayerとViewModelを結合
        controls.videoViewer.bindViewModel(viewModel.playerControllerModel, binder)

        // ジェスチャーの設定
        gestureInterpreter.setup(this, manipulationAgent.parentView) {
            onScale(manipulationAgent::onScale)
            onScroll(manipulationAgent::onScroll)
            onTap {
                viewModel.playerControllerModel.playerModel.togglePlay()
            }
            onLongTap {
                selectFile()
            }
            onDoubleTap {
                manipulationAgent.resetScrollAndScale()
            }
        }

        if (!viewModel.hasSource) {
            selectFile()
        }
    }

    private fun selectFile() {
        UtImmortalTask.launchTask("selectFile") {
            val uri = activityBrokers.openFilePicker.selectFile(arrayOf("video/mp4", "video/*"))
            if (uri == null) {
                if (!viewModel.hasSource) {
                    logger.info("cancelled and finish app.")
                    finish()
                }
            } else {
                logger.info("selected: $uri")
                viewModel.setSource(uri)
            }
        }
    }
}