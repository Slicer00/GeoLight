# GeoLight

GeoLightは帰宅、外出を検出して照明のONOFFを制御するAndroidアプリケーションです。
具体的には現在地を基準点に対して測定し、特定の範囲内に入ると通知を送信、Tuyaのシーンルールをトリガーします。

ほぼ自分専用に作っているので個人でのビルドは厳しいかもしれません。ご了承ください。UIが非常にお粗末です。

## 概要

このアプリケーションは、Google Mapsを使用して現在地を表示し、基準点からの距離を計算します。ユーザーが設定した範囲内に現在地が入ると、通知を送信し、Tuya社のAPIを使用してシーンルールをトリガーします。

## 主な機能

- 現在地の取得と表示
- 基準点の設定
- 基準点からの距離の計算
- ユーザーが設定した範囲内に入った際の通知送信
- Tuyaのシーンルールをトリガー

## ビルド方法

### 前提条件

- Android Studioがインストールされていること
- Google Maps APIキーを取得していること
- TuyaのクライアントIDとクライアントシークレットを取得していること

### 手順

1. リポジトリをクローンします。  

   ```sh
   git clone https://github.com/Slicer00/GeoLight.git
   ```

2. Android Studioでプロジェクトを開きます。  

3. `local.properties` ファイルにGoogle Maps APIキーを設定します。  

   ```properties
   google.maps.api.key=YOUR_API_KEY
   ```

4. `MainActivity.java` ファイルにTuyaのクライアントIDとクライアントシークレットを設定します。  

   ```java
   private final String clientId = "YOUR_CLIENT_ID";
   private final String clientSecret = "YOUR_CLIENT_SECRET";
   ```

5. プロジェクトをビルドし、エミュレーターまたは実機で実行します。  