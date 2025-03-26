package com.example.viewtest;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private static final String CHANNEL_ID = "アプリ通知";
    private FusedLocationProviderClient fusedLocationClient;
    private GoogleMap mMap;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateTask;
    private double referenceLatitude = Double.NaN;
    private double referenceLongitude = Double.NaN;
    private TextView currentlocationText;
    private TextView referencePointText;
    private TextView distanceFromReferenceText;
    private int selectedRange = 100; // シークバーで選択された範囲（初期値100メートル）
    private boolean isWithinRange = false; // 範囲内かどうかを記録

    private final String clientId = " ";       // TuyaのクライアントID(不正利用防止のため削除)
    private final String clientSecret = " "; // Tuyaのクライアントシークレット

    private final String tokenUrl = "https://openapi.tuyaus.com/v1.0/token?grant_type=1";
    private final String sceneTriggerUrlTemplate = "https://openapi.tuyaus.com/v2.0/cloud/scene/rule/%s/actions/trigger";
    private final String sceneTriggerUrlTemplate2 = "/v2.0/cloud/scene/rule/%s/actions/trigger";

    public static String getSHA256Hash(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button buttonOn = findViewById(R.id.buttonOn);
        Button buttonOff = findViewById(R.id.buttonOff);

        buttonOn.setOnClickListener(v -> triggerSceneRule("xRzlO8piVH6JDjae"));
        buttonOff.setOnClickListener(v -> triggerSceneRule("RN2841BhOvzRjL7Q"));


        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        currentlocationText = findViewById(R.id.locationText);
        referencePointText = findViewById(R.id.referencePointText);
        distanceFromReferenceText = findViewById(R.id.distanceFromReferenceText);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2000);
            }
        }

        sendNotification("アプリ起動", "アプリケーションが起動されました");

        Button setReferencePointButton = findViewById(R.id.setReferencePointButton);
        setReferencePointButton.setOnClickListener(v -> {
            setReferencePoint(); // 基準点を設定
        });

        // 位置情報を取得
        Button updateLocationButton = findViewById(R.id.updateLocationButton);
        updateLocationButton.setOnClickListener(v -> {
            fetchLocation(); // ボタン押下時に位置情報を取得
        });

        SeekBar rangeSeekBar = findViewById(R.id.rangeSeekBar);
        TextView rangeText = findViewById(R.id.rangeText);

        rangeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selectedRange = progress; // 選択された範囲を更新
                rangeText.setText("範囲: " + selectedRange + "メートル");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        CheckBox autoUpdateCheckbox = findViewById(R.id.autoUpdateCheckbox);
        autoUpdateCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startAutoUpdate(); // 自動更新開始
            } else {
                stopAutoUpdate(); // 自動更新停止
            }
        });
    }

    private void fetchLocation() {
        // パーミッションのチェック
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            // パーミッションが許可されていない場合はリクエスト
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
            return;
        }

        // 現在の位置情報を取得
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        // 緯度と経度を取得して表示
                        double currentLatitude = location.getLatitude();
                        double currentLongitude = location.getLongitude();
                        currentlocationText.setText("現在地: 緯度 " + currentLatitude + ", 経度 " + currentLongitude);

                        // マップの現在地を更新
                        updateMapLocation(currentLatitude, currentLongitude);

                        if (!Double.isNaN(referenceLatitude) && !Double.isNaN(referenceLongitude)) {
                            float[] results = new float[1];
                            Location.distanceBetween(
                                    referenceLatitude,
                                    referenceLongitude,
                                    currentLatitude,
                                    currentLongitude,
                                    results
                            );

                            // 距離を取得
                            float distance = results[0];
                            distanceFromReferenceText.setText("基準点からの距離: " + distance + " メートル");

                            // 範囲内か範囲外かを判定
                            if (distance <= selectedRange && !isWithinRange) {
                                sendNotification("範囲内に入りました", "現在地が指定された範囲内に入りました");
                                isWithinRange = true; // 範囲内と記録
                                triggerSceneRule("xRzlO8piVH6JDjae");
                            } else if (distance > selectedRange && isWithinRange) {
                                sendNotification("範囲外に出ました", "現在地が指定された範囲外に出ました");
                                isWithinRange = false; // 範囲外と記録
                                triggerSceneRule("RN2841BhOvzRjL7Q");
                            }
                        } else {
                            distanceFromReferenceText.setText("基準点からの距離: 基準点が未設定です");
                        }
                    } else {
                        currentlocationText.setText("現在地を取得できませんでした");
                    }
                })
                .addOnFailureListener(this, e -> {
                    Log.e("LocationError", "Failed to get location", e);
                    currentlocationText.setText("現在地の取得に失敗しました");
                });
    }

    private void updateMapLocation(double latitude, double longitude) {
        if (mMap != null) {
            LatLng currentLocation = new LatLng(latitude, longitude);
            mMap.clear();
            mMap.addMarker(new MarkerOptions().position(currentLocation).title("現在地"));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 17)); // 地図を拡大して現在地を表示
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        fetchLocation();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLocation(); // パーミッションが許可された場合、位置情報を再取得
            } else {
                currentlocationText.setText("位置情報の権限が必要です");
            }
        }
    }

    // 自動更新を開始するメソッド
    private void startAutoUpdate() {
        if (updateTask == null) {
            updateTask = new Runnable() {
                @Override
                public void run() {
                    fetchLocation(); // 位置情報を更新
                    handler.postDelayed(this, 5000); // 5秒後に再実行
                }
            };
        }
        handler.post(updateTask); // 初回実行
    }

    // 自動更新を停止するメソッド
    private void stopAutoUpdate() {
        if (updateTask != null) {
            handler.removeCallbacks(updateTask); // タスクの削除
        }
    }

    // 基準点を設定するメソッド
    private void setReferencePoint() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            // 現在地を基準点として設定
                            referenceLatitude = location.getLatitude();
                            referenceLongitude = location.getLongitude();

                            // 基準点の座標を表示
                            referencePointText.setText("基準点: 緯度 " + referenceLatitude + ", 経度 " + referenceLongitude);
                        } else {
                            referencePointText.setText("基準点: 現在地を取得できませんでした");
                        }
                    })
                    .addOnFailureListener(e -> {
                        referencePointText.setText("基準点: 設定に失敗しました");
                    });
        } else {
            referencePointText.setText("基準点: 権限がありません");
        }
    }

    private void sendNotification(String title, String message) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // 通知チャネルの作成（Android 8.0以上が対象）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "アプリ通知",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }

        // 通知の作成
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        // タップ時にアプリを開くPendingIntent
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.setContentIntent(pendingIntent);

        // 通知を表示
        notificationManager.notify(1, builder.build());
    }

    private void triggerSceneRule(String ruleId) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executorService.execute(() -> {
            try {
                long timestamp = System.currentTimeMillis();

                String sceneTriggerUrl = String.format(sceneTriggerUrlTemplate, ruleId);
                String sceneTriggerUrl2 = String.format(sceneTriggerUrlTemplate2, ruleId);

                String accessToken = retrieveAccessToken(timestamp);

                String jsonRequestBody = new JSONObject().toString();

                String hashedValue = getSHA256Hash(jsonRequestBody);

                String sign = generateSignWithToken(clientId, accessToken, clientSecret, sceneTriggerUrl2, hashedValue, timestamp);

                URL url = new URL(sceneTriggerUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("client_id", clientId);
                conn.setRequestProperty("t", String.valueOf(timestamp));
                conn.setRequestProperty("sign_method", "HMAC-SHA256");
                conn.setRequestProperty("sign", sign);
                conn.setRequestProperty("access_token", accessToken);
                conn.setRequestProperty("Content-Type", "application/json");

                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonRequestBody.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();

                handler.post(() -> {
                });

            } catch (Exception e) {
                handler.post(() -> {
                });
            }
        });
    }

    private String retrieveAccessToken(long timestamp) throws Exception {
        String sign = generateSign(clientId, clientSecret, timestamp);

        URL url = new URL(tokenUrl);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("client_id", clientId);
        conn.setRequestProperty("sign", sign);
        conn.setRequestProperty("sign_method", "HMAC-SHA256");
        conn.setRequestProperty("t", String.valueOf(timestamp));

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            response.append(line);
        }
        in.close();

        JSONObject jsonResponse = new JSONObject(response.toString());

        return jsonResponse.getJSONObject("result").getString("access_token");
    }

    private String generateSign(String clientId, String clientSecret, long timestamp) throws Exception {
        String message = clientId + timestamp + "GET" + "\n" + "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" + "\n" + "\n" + "/v1.0/token?grant_type=1";

        return hmacSha256(clientSecret, message);
    }

    private String generateSignWithToken(String clientId, String accessToken, String clientSecret, String triggerurl, String hashedValue, long timestamp) throws Exception {
        String message = clientId + accessToken + timestamp + "POST" + "\n" + hashedValue + "\n" + "\n" + triggerurl;

        return hmacSha256(clientSecret, message);
    }

    private String hmacSha256(String key, String message) throws Exception {
        Mac hmacSha256 = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(), "HmacSHA256");

        hmacSha256.init(secretKeySpec);
        byte[] hash = hmacSha256.doFinal(message.getBytes());

        StringBuilder result = new StringBuilder();
        for (byte b : hash) {
            result.append(String.format("%02x", b & 0xff));
        }
        return result.toString().toUpperCase();
    }
}
