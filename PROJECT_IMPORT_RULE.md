指定のプロジェクトを/legac-stash/から探して androidのプロジェクトを取り出して、
apps内にtarget-test-appを参考にビルドできるブロジェクトを作成してください
resフォルダが含まれていない場合は追加して、ソース内で参照されているファイルを追加します。
またactivityのthemeは'@style/Theme.AppCompat'を用いファイルの追加を最小限にします

ビルドができたらエラーが発生して修正した箇所を注意点としてこのファイルに追記してください。

---

**注意点**

*   **`build.gradle.kts` の `kotlinOptions`:** `jvmTarget` の設定は古い形式でエラーになるため、以下のように `kotlin` ブロックで `jvmToolchain` を使用してください。

    ```kotlin
    kotlin {
        jvmToolchain(8) 
    }
    ```

*   **`AndroidManifest.xml` のアイコン指定:** 移行するプロジェクトにアイコンリソースが含まれていない場合、`AndroidManifest.xml` 内の `android:icon` および `android:roundIcon` 属性はビルドエラーの原因となります。これらの属性は削除してください。

*   **`build.gradle.kts` の `BuildConfig`:** `BuildConfig`クラスが見つからない（Unresolved reference）というビルドエラーが発生した場合、`build.gradle.kts`で`buildConfig`の生成を明示的に有効にする必要があります。`android` > `buildFeatures`ブロックに`buildConfig = true`を追記してください。

    ```kotlin
    android {
        buildFeatures {
            buildConfig = true
        }
    }
    ```

---