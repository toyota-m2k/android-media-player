package io.github.toyota32k.android.media.player

import android.os.Bundle
import android.view.View
import io.github.toyota32k.android.media.player.databinding.DialogSnapshotBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.utils.android.RefBitmap
import io.github.toyota32k.utils.android.RefBitmapHolder

class SnapshotDialog : UtDialogEx() {
    class SnapshotViewModel : UtDialogViewModel() {
        var snapshot: RefBitmap? by RefBitmapHolder()
        override fun onCleared() {
            super.onCleared()
            snapshot = null
        }
    }
    override fun preCreateBodyView() {
        heightOption = HeightOption.FULL
        widthOption = WidthOption.FULL
        noHeader = true
        rightButtonType = ButtonType.CLOSE
    }

    private val viewModel: SnapshotViewModel by lazy { getViewModel() }
    private lateinit var controls: DialogSnapshotBinding

    override fun createBodyView(
        savedInstanceState: Bundle?,
        inflater: IViewInflater
    ): View {
        controls = DialogSnapshotBinding.inflate(inflater.layoutInflater, null, false)
        controls.imageView.setImageBitmap(viewModel.snapshot?.bitmapOrNull)
        return controls.root
    }

    companion object {
        fun showBitmap(snapshot: RefBitmap) {
            UtImmortalTask.launchTask(this::class.java.name) {
                createViewModel<SnapshotViewModel> { this.snapshot = snapshot }
                showDialog(taskName) { SnapshotDialog() }
            }
        }
    }
}