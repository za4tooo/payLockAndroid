package com.example.paycheckandroid;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingsActivity extends AppCompatActivity implements SerialInputOutputManager.Listener {

    private UsbManager usbManager;
    private Spinner spinnerDevices, spinnerBaud;
    private EditText etMessageExample;
    private Button btnConnect, btnDisconnect;
    private TextView tvLog;

    private List<UsbSerialDriver> driverList = new ArrayList<>();
    private UsbSerialPort serialPort = null;
    private SerialInputOutputManager ioManager = null;

    private static final String ACTION_USB_PERMISSION = "com.example.serialapp.USB_PERMISSION";
    private PendingIntent permissionIntent;

    // Статический метод для вызова из MainActivity
    public static void sendHexMessage(String hex) {
        if (instance != null && instance.serialPort != null) {
            instance.sendMessageInternal(hex);
        }
    }

    // Для доступа из MainActivity (простой, но рабочий вариант)
    private static SettingsActivity instance;

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        instance = this;

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        spinnerDevices = findViewById(R.id.spinnerDevices);
        spinnerBaud = findViewById(R.id.spinnerBaud);

        btnConnect = findViewById(R.id.btnConnect);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        tvLog = findViewById(R.id.tvLog);

        int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                ? PendingIntent.FLAG_IMMUTABLE
                : 0;
        permissionIntent = PendingIntent.getBroadcast(
                this, 0, new Intent(ACTION_USB_PERMISSION), flags
        );

        BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    log("Разрешение получено");
                    connect();
                } else {
                    log("В доступе отказано");
                }
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(permissionReceiver,
                    new IntentFilter(ACTION_USB_PERMISSION),
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(permissionReceiver, new IntentFilter(ACTION_USB_PERMISSION));
        }

        // Заполняем список скоростей
        List<String> bauds = Arrays.asList("9600", "19200", "38400", "57600", "115200");
        ArrayAdapter<String> adapterBaud = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, bauds);
        spinnerBaud.setAdapter(adapterBaud);
        spinnerBaud.setSelection(4); // по умолчанию 115200

        btnConnect.setOnClickListener(v -> connect());
        btnDisconnect.setOnClickListener(v -> disconnect());

        refreshDeviceList();
    }

    private void refreshDeviceList() {
        UsbSerialProber prober = UsbSerialProber.getDefaultProber();
        driverList.clear();
        driverList.addAll(prober.findAllDrivers(usbManager));

        List<String> names = new ArrayList<>();
        for (UsbSerialDriver driver : driverList) {
            UsbDevice dev = driver.getDevice();
            String name = String.format("VID:%04X PID:%04X (%s)",
                    dev.getVendorId(), dev.getProductId(), driver.getClass().getSimpleName());
            names.add(name);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, names);
        if (names.isEmpty()) {
            adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                    new String[]{"Устройства не найдены"});
        }
        spinnerDevices.setAdapter(adapter);
    }

    private void connect() {
        if (driverList.isEmpty()) {
            log("Нет доступных USB-Serial устройств");
            return;
        }

        int position = spinnerDevices.getSelectedItemPosition();
        if (position < 0 || position >= driverList.size()) return;

        UsbSerialDriver driver = driverList.get(position);
        UsbDevice device = driver.getDevice();

        if (!usbManager.hasPermission(device)) {
            usbManager.requestPermission(device, permissionIntent);
            log("Запрошено разрешение");
            return;
        }

        try {
            var connection = usbManager.openDevice(device);
            if (connection == null) {
                log("Не удалось открыть устройство");
                return;
            }

            serialPort = driver.getPorts().get(0);
            serialPort.open(connection);

            int baud = Integer.parseInt(spinnerBaud.getSelectedItem().toString());
            serialPort.setParameters(baud,
                    UsbSerialPort.DATABITS_8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE);

            ioManager = new SerialInputOutputManager(serialPort, this);
            new Thread(ioManager).start();

            btnConnect.setEnabled(false);
            btnDisconnect.setEnabled(true);
            log("Подключено (baud: " + baud + ")");

            // Обновляем статус на главном экране
            MainActivity.isConnected = true;
        } catch (Exception e) {
            log("Ошибка подключения: " + e.getMessage());
            disconnect();
        }
    }

    private void disconnect() {
        try { if (ioManager != null) ioManager.stop(); } catch (Exception ignored) {}
        ioManager = null;
        try { if (serialPort != null) serialPort.close(); } catch (Exception ignored) {}
        serialPort = null;

        btnConnect.setEnabled(true);
        btnDisconnect.setEnabled(false);
        log("Отключено");

        MainActivity.isConnected = false;
    }

    // Метод для внутреннего использования (вызывается из статического sendHexMessage)
    private void sendMessageInternal(String hexText) {
        if (serialPort == null) {
            log("Порт не открыт");
            return;
        }

        String clean = hexText.replaceAll("[^0-9A-Fa-f]", "");
        if (clean.length() % 2 != 0) {
            log("Некорректный hex (нечётное количество символов)");
            return;
        }

        byte[] bytes = new byte[clean.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
            bytes[i] = (byte) value;
        }

        try {
            serialPort.write(bytes, 1000);
            log("Отправлено: " + bytesToHex(bytes));
        } catch (Exception e) {
            log("Ошибка отправки: " + e.getMessage());
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", bytes[i]));
        }
        return sb.toString();
    }

    private void log(String msg) {
        runOnUiThread(() -> tvLog.append(msg + "\n"));
    }

    @Override
    public void onNewData(byte[] data) {
        log("Получено: " + bytesToHex(data));
    }

    @Override
    public void onRunError(Exception e) {
        log("Ошибка порта: " + e.getMessage());
        runOnUiThread(this::disconnect);
    }
}