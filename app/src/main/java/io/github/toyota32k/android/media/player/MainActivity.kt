package io.github.toyota32k.android.media.player

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.toyota32k.android.media.player.databinding.ActivityMainBinding
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.clickBinding
import io.github.toyota32k.lib.player.model.IMutableChapterList
import io.github.toyota32k.lib.player.model.Range
import io.github.toyota32k.lib.player.model.chapter.Chapter
import io.github.toyota32k.lib.player.model.chapter.MutableChapterList

class MainActivity : AppCompatActivity() {
    private lateinit var controls: ActivityMainBinding
    private lateinit var chapterList: IMutableChapterList

    val binder = Binder()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        controls = ActivityMainBinding.inflate(layoutInflater)
        setContentView(controls.root)
        ViewCompat.setOnApplyWindowInsetsListener(controls.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        controls.playerSlider.setDuration(10000)
        chapterList = MutableChapterList()
        chapterList.initChapters(
            listOf(
                Chapter(1000),
                Chapter(3000, skip=true),
                Chapter(5000, skip=false),
                Chapter(7000, skip=true),
                Chapter(9000, skip=false),
            )
        )
        controls.playerSlider.setChapterList(chapterList)
        controls.playerSlider.setPlayRange(Range(4000L,8000L))

        binder.owner(this)
            .clickBinding(controls.addChapter) {
                chapterList.addChapter(controls.playerSlider.position, "", false)
            }
    }
}