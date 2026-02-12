テストファイルを
/legacy-stash/test-suites/src/test/java/com/example/test_suites/
から/test-sample/src/main/kotlin/org/example/plugin/
にコピーします。FCS_CKHのように大文字で名前が定義されているクラスは
@SFRアノテーションの一つめのアノテーションを取得しFCS_NAME_CONVERSION.mdに書いて
あるルールをもとにクラス名を変更してください。

utilsに含まれているクラスは/common-utils/src/main/kotlin/以下のAdamUtils.ktが含まれている
ディレクトリに移動しパッケージ名も変更します。
rulesの中は使わないでください

それに合わせて各テストの参照先も変えて、コンパイルを試みてください。

またビルドエラーが出たら適切にコメントアウトして構いません。