package io.github.toyota32k.lib.player.view

//class ButtonPanel @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
//    : FrameLayout(context, attrs, defStyleAttr) {
//    companion object {
//        val logger by lazy { UtLog("Buttons", ControlPanel.logger) }
//    }
//    val controls: V2ButtonPanelBinding
//
//    init {
//        val sa = context.theme.obtainStyledAttributes(attrs,
//            R.styleable.ControlPanel, defStyleAttr,0)
//        val panelBackground = sa.getColorAsDrawable(R.styleable.ControlPanel_panelBackgroundColor, context.theme, com.google.android.material.R.attr.colorSurface, Color.WHITE)
//        val panelText = sa.getColorAwareOfTheme(R.styleable.ControlPanel_panelTextColor, context.theme, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
//
//        val buttonEnabled = sa.getColorAwareOfTheme(R.styleable.ControlPanel_buttonTintColor, context.theme, com.google.android.material.R.attr.colorPrimaryVariant, Color.WHITE)
//        val disabledDefault = Color.argb(0x80, Color.red(panelText), Color.green(panelText), Color.blue(panelText))
//        val buttonDisabled = sa.getColor(R.styleable.ControlPanel_buttonDisabledTintColor, disabledDefault)
//        val buttonTint = ColorStateList(
//            arrayOf(
//                intArrayOf(android.R.attr.state_enabled),
//                intArrayOf(),
//            ),
//            intArrayOf(buttonEnabled, buttonDisabled)
//        )
//
//        sa.recycle()
//
//        controls = V2ButtonPanelBinding.inflate(LayoutInflater.from(context), this, true).apply {
//            buttonPanelRoot.background = panelBackground
//            controlButtons.children.forEach { (it as? ImageButton)?.imageTintList = buttonTint }
//        }
//
//    }
//
//    private lateinit var model: PlayerControllerModel
//
//    fun bindViewModel(model: PlayerControllerModel, binder: Binder) {
//        this.model = model
//
//        val chapterHandler = model.playerModel as? IChapterHandler
//        val playlistHandler = model.playerModel as? IPlaylistHandler
//
//        binder
//            .visibilityBinding(controls.playButton, model.playerModel.isPlaying, BoolConvert.Inverse, VisibilityBinding.HiddenMode.HideByGone)
//            .visibilityBinding(controls.pauseButton, model.playerModel.isPlaying, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .visibilityBinding(controls.fullscreenButton, model.windowMode.map { it!=PlayerControllerModel.WindowMode.FULLSCREEN }, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .visibilityBinding(controls.collapseButton, model.windowMode.map { it!=PlayerControllerModel.WindowMode.NORMAL }, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .visibilityBinding(controls.pinpButton, ConstantLiveData(model.supportPinP), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .visibilityBinding(controls.fullscreenButton, ConstantLiveData(model.supportFullscreen), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .visibilityBinding(controls.snapshotButton, ConstantLiveData(model.snapshotHandler!=null), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .visibilityBinding(controls.rotateLeft, ConstantLiveData(model.enableRotateLeft), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .visibilityBinding(controls.rotateRight, ConstantLiveData(model.enableRotateRight), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .multiVisibilityBinding(arrayOf(controls.prevChapterButton, controls.nextChapterButton), ConstantLiveData(chapterHandler!=null), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .multiVisibilityBinding(arrayOf(controls.prevVideoButton, controls.nextVideoButton), ConstantLiveData(playlistHandler!=null && model.showNextPreviousButton), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
//            .multiEnableBinding(arrayOf(controls.playButton, controls.pauseButton, controls.seekBackButton, controls.seekForwardButton, controls.fullscreenButton, controls.pinpButton), model.playerModel.isReady)
//
//            .bindCommand(model.commandPlay, controls.playButton)
//            .bindCommand(model.commandPlay, controls.playButton)
//            .bindCommand(model.commandPause, controls.pauseButton)
//            .bindCommand(model.commandSeekBackward, controls.seekBackButton)
//            .bindCommand(model.commandSeekForward, controls.seekForwardButton)
//            .bindCommand(model.commandFullscreen, controls.fullscreenButton)
//            .bindCommand(model.commandSnapshot, controls.snapshotButton)
//            .bindCommand(model.commandPinP, controls.pinpButton)
//            .bindCommand(model.commandCollapse, controls.collapseButton)
//            .bindCommand(model.commandRotate, controls.rotateLeft, Rotation.LEFT)
//            .bindCommand(model.commandRotate, controls.rotateRight, Rotation.RIGHT)
//            .apply {
//                if(playlistHandler!=null ) {
//                    enableBinding(controls.prevVideoButton, playlistHandler.hasPrevious)
//                    enableBinding(controls.nextVideoButton, playlistHandler.hasNext)
//                    bindCommand(playlistHandler.commandNext, controls.nextVideoButton)
//                    bindCommand(playlistHandler.commandPrev, controls.prevVideoButton)
//                }
//                if(chapterHandler!=null) {
//                    bindCommand(chapterHandler.commandNextChapter, controls.nextChapterButton)
//                    bindCommand(chapterHandler.commandPrevChapter, controls.prevChapterButton)
//                }
//            }
//            .add(UtClickRepeater(controls.seekBackButton))
//            .add(UtClickRepeater(controls.seekForwardButton))
//    }
//
//
//}