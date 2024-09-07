# Android Media Player

ExoPlayer を利用したビデオ再生用コンポーネントです。
次のような機能が利用できます。
1. ピンチ操作による拡大・縮小
2. 回転
3. スナップショット（PNG）の保存
4. プレイリストによる連続再生
5. チャプターの設定、および、無効チャプターの読み飛ばし

[android-media-processor](https://github.com/toyota-m2k/android-media-processor)
を組み合わせて使うことで、回転した動画の保存や、無効チャプターのトリミングなどの編集も可能になります。

## インストール

### root build.gradle.ktx
```groovy
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
  }
}
```
### module build.gradle
```groovy
dependencies {
  implementation 'com.github.toyota-m2k:android-media-player:Tag'
}
```

## 依存ライブラリ
- [android-utilities](https://github.com/toyota-m2k/android-utilities)<br>
雑多な便利機能を詰め込んだライブラリ
- [android-binding](https://github.com/toyota-m2k/android-binding)<br>
View-ViewModelバインディング用ライブラリ。宣言的なリアクティブプログラミングが可能。
- [android-viewex](https://github.com/toyota-m2k/android-viewex)<br>
.NETのViewbox相当のViewや円グラフ的なプログレスバーなど、汎用的ビューを入れていくライブラリ。

## サンプルプログラム
[android-camera](https://github.com/toyota-m2k/android-camera) の `secureCamera` アプリで利用しています。

## License
Apache License 2.0
