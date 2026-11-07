package com.example.paycheckandroid;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.io.IOException;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private Button btnGetLink, btnSendToPort, btnSettings;
    private ApiService apiService;
    public static boolean isConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        btnGetLink = findViewById(R.id.btnGetLink);
        btnSendToPort = findViewById(R.id.btnSendToPort);

        OkHttpClient client = new OkHttpClient.Builder().build();

        String baseUrl = "http://192.168.1.208:5000/";

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(ApiService.class);

        Button btnGetLink = findViewById(R.id.btnGetLink);
        btnGetLink.setOnClickListener(v -> fetchLink());
        btnSettings = findViewById(R.id.btnSettings);

        // Кнопка отправки в COM-порт
        btnSendToPort.setOnClickListener(v -> sendData());
        updateUI();

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            }
        });
    }

    private void fetchLink() {
        Call<LinkResponse> call = apiService.getLink();
        String fullUrl = call.request().url().toString();
        Log.d("API_DEBUG", "Запрос к: " + fullUrl);
        call.enqueue(new Callback<LinkResponse>() {
            @Override
            public void onResponse(Call<LinkResponse> call, Response<LinkResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String link = response.body().getLink();
                    try {
                        showQrPopup(link);
                    } catch (WriterException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    // Реальный HTTP-код ошибки
                    int code = response.code();
                    String message = response.message();
                    String errorBody = "";

                    // Тело ответа (часто там есть детали: «endpoint not found», «auth failed» и т. п.)
                    if (response.errorBody() != null) {
                        try {
                            errorBody = response.errorBody().string();
                        } catch (IOException e) {
                            errorBody = e.getMessage();
                        }
                    }

                    Log.e("API_ERROR", "Code: " + code + ", Message: " + message + ", Body: " + errorBody);
                    Toast.makeText(MainActivity.this, "Ошибка сервера: " + code, Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<LinkResponse> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Ошибка сети: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showQrPopup(String text) throws WriterException {
        BarcodeEncoder encoder = new BarcodeEncoder();
        Bitmap bitmap = encoder.encodeBitmap(text, BarcodeFormat.QR_CODE, 400, 400);

        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(bitmap);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        new AlertDialog.Builder(this)
                .setTitle("QR‑код ссылки")
                .setMessage(text)
                .setView(imageView)
                .setPositiveButton("OK", null)
                .show();
    }
    private void sendData() {
        if (!isConnected) {
            tvStatus.setText("Статус: порт не подключён. Откройте настройки и подключитесь.");
            return;
        }
        SettingsActivity.sendHexMessage("49 B0 05");
    }

    public void updateUI() {
        if (isConnected) {
            tvStatus.setText("Статус: подключено");
            btnSendToPort.setEnabled(true);
        } else {
            tvStatus.setText("Статус: не подключено");
            btnSendToPort.setEnabled(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // При возврате из SettingsActivity статус может измениться
        updateUI();
    }
}







