package com.example.tunas.iot;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Real BLE transport, not a simulator. All work and Listener callbacks are serialized
 * on the main thread. The vendor UUIDs/schema below are PROVISIONAL; see IOT_PROTOCOL.md.
 * The caller owns permission prompts, device selection and physical-action confirmation.
 */
@SuppressLint("MissingPermission")
@SuppressWarnings("deprecation")
public final class BleDeviceClient implements AutoCloseable {
    public interface Listener {
        void onStatus(String message);
        void onDeviceFound(String address, String name, int rssi);
        void onConnected(boolean connected);
        void onTelemetry(Telemetry telemetry);
        void onCommandResult(boolean success, String message);
    }

    // Proposed Tunas vendor protocol, NOT confirmed firmware assignments.
    public static final UUID PROVISIONAL_SERVICE_UUID = UUID.fromString("6f8a0001-7c2d-4e9f-a6b1-32d7c05e9140");
    public static final UUID PROVISIONAL_TELEMETRY_UUID = UUID.fromString("6f8a0002-7c2d-4e9f-a6b1-32d7c05e9140");
    public static final UUID PROVISIONAL_COMMAND_UUID = UUID.fromString("6f8a0003-7c2d-4e9f-a6b1-32d7c05e9140");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final long SCAN_MILLIS = 12_000;
    private static final long SETUP_MILLIS = 20_000;
    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final BluetoothAdapter adapter;
    private final GattHandshake handshake = new GattHandshake();
    private final CommandTracker commands = new CommandTracker();
    private volatile boolean connected;
    private boolean closed;
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private Runnable scanTimeout;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic commandCharacteristic;
    private BluetoothGattCharacteristic telemetryCharacteristic;
    private Runnable setupTimeout;
    private Runnable commandTimeout;
    private LineDecoder decoder = new LineDecoder();
    private byte[] outgoing;
    private int sentOffset;
    private boolean writeInFlight;
    private String writingId;
    private IncomingMessage deferredAck;

    public BleDeviceClient(Context context, Listener listener) {
        Context supplied = Objects.requireNonNull(context, "context");
        Context app = supplied.getApplicationContext();
        this.context = app == null ? supplied : app;
        this.listener = Objects.requireNonNull(listener, "listener");
        BluetoothManager manager = this.context.getSystemService(BluetoothManager.class);
        this.adapter = manager == null ? null : manager.getAdapter();
    }

    public void startScan() { onMain(this::startScanOnMain); }
    public void stopScan() { onMain(() -> stopScanOnMain(true)); }
    public void connect(String address) { onMain(() -> connectOnMain(address)); }
    public void disconnect() {
        onMain(() -> {
            stopScanOnMain(false);
            disconnectOnMain("Disconnected; previous sensor data is stale", null);
        });
    }
    @Override public void close() {
        onMain(() -> {
            if (closed) return;
            closed = true;
            stopScanOnMain(false);
            disconnectOnMain("Client closed; previous sensor data is stale", null);
        });
    }
    public boolean isConnected() { return connected; }
    public void command(String action, int value) { onMain(() -> commandOnMain(action, value)); }

    private void onMain(Runnable action) {
        if (Looper.myLooper() == main.getLooper()) action.run();
        else main.post(action);
    }

    private boolean hasPermission(String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean canUseBluetooth(String permission) {
        if (closed) { listener.onStatus("BLE client is closed"); return false; }
        if (!hasPermission(permission)) { listener.onStatus("Bluetooth permission required"); return false; }
        if (adapter == null) { listener.onStatus("BLE is not supported on this device"); return false; }
        if (!adapter.isEnabled()) { listener.onStatus("Turn on Bluetooth first"); return false; }
        return true;
    }

    private void startScanOnMain() {
        try {
            if (!canUseBluetooth(Manifest.permission.BLUETOOTH_SCAN)
                    || !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return;
            if (gatt != null) { listener.onStatus("Disconnect before scanning"); return; }
            stopScanOnMain(false);
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) { listener.onStatus("BLE scanner is unavailable"); return; }
            ScanCallback callback = new ScanCallback() {
                @Override public void onScanResult(int callbackType, ScanResult result) {
                    main.post(() -> deliverScan(this, result));
                }
                @Override public void onBatchScanResults(List<ScanResult> results) {
                    for (ScanResult result : results) main.post(() -> deliverScan(this, result));
                }
                @Override public void onScanFailed(int errorCode) {
                    main.post(() -> {
                        if (scanCallback != this || closed) return;
                        stopScanOnMain(false);
                        listener.onStatus("BLE scan failed (" + errorCode + ")");
                    });
                }
            };
            scanCallback = callback;
            // No name/UUID inference: only actual advertisements, selected by the user.
            scanner.startScan(Collections.emptyList(), new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback);
            scanTimeout = () -> {
                if (scanCallback == callback) stopScanOnMain(true);
            };
            main.postDelayed(scanTimeout, SCAN_MILLIS);
            listener.onStatus("Scanning for nearby BLE devices");
        } catch (SecurityException denied) {
            stopScanOnMain(false);
            listener.onStatus("Bluetooth permission was denied or revoked");
        } catch (IllegalStateException unavailable) {
            stopScanOnMain(false);
            listener.onStatus("Bluetooth scanner is unavailable");
        }
    }

    private void deliverScan(ScanCallback source, ScanResult result) {
        if (closed || source != scanCallback || result == null) return;
        try {
            BluetoothDevice device = result.getDevice();
            if (device == null) return;
            String name = result.getScanRecord() == null ? null : result.getScanRecord().getDeviceName();
            listener.onDeviceFound(device.getAddress(), name == null ? "Unnamed BLE device" : name, result.getRssi());
        } catch (SecurityException denied) {
            stopScanOnMain(false);
            listener.onStatus("Bluetooth permission was revoked");
        }
    }

    private void stopScanOnMain(boolean report) {
        if (scanTimeout != null) main.removeCallbacks(scanTimeout);
        scanTimeout = null;
        ScanCallback callback = scanCallback;
        BluetoothLeScanner activeScanner = scanner;
        scanCallback = null;
        scanner = null;
        if (callback != null && activeScanner != null) {
            try { activeScanner.stopScan(callback); }
            catch (SecurityException | IllegalStateException ignored) { /* local state still cleared */ }
        }
        if (report && callback != null && !closed) listener.onStatus("BLE scan stopped");
    }

    private void connectOnMain(String address) {
        try {
            if (!canUseBluetooth(Manifest.permission.BLUETOOTH_CONNECT)) return;
            if (address == null || !BluetoothAdapter.checkBluetoothAddress(address)) {
                listener.onStatus("Invalid Bluetooth address");
                return;
            }
            stopScanOnMain(false);
            disconnectOnMain("Previous connection closed; previous sensor data is stale", null);
            if (closed) return;
            handshake.begin();
            BluetoothDevice device = adapter.getRemoteDevice(address);
            // autoConnect=false: there is no background reconnect or command replay.
            gatt = device.connectGatt(context, false, callbacks, BluetoothDevice.TRANSPORT_LE);
            if (gatt == null) {
                disconnectOnMain("Could not start GATT connection", null);
                return;
            }
            BluetoothGatt attempt = gatt;
            setupTimeout = () -> {
                if (gatt == attempt && !connected) disconnectOnMain("BLE setup timed out", null);
            };
            main.postDelayed(setupTimeout, SETUP_MILLIS);
            listener.onStatus("Connecting; waiting for services and notification subscription");
        } catch (SecurityException denied) {
            disconnectOnMain("Bluetooth permission was denied or revoked", null);
        } catch (IllegalArgumentException | IllegalStateException unavailable) {
            disconnectOnMain("Could not start GATT connection", null);
        }
    }

    private final BluetoothGattCallback callbacks = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt source, int status, int newState) {
            main.post(() -> {
                if (!isCurrent(source)) return;
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                    disconnectOnMain("BLE disconnected (" + status + "); previous sensor data is stale", null);
                } else if (newState == BluetoothProfile.STATE_CONNECTED && handshake.linkConnected()) {
                    try {
                        if (!source.discoverServices()) disconnectOnMain("Service discovery could not start", null);
                    } catch (SecurityException | IllegalStateException failure) {
                        disconnectOnMain("Service discovery failed", null);
                    }
                }
            });
        }
        @Override public void onServicesDiscovered(BluetoothGatt source, int status) {
            main.post(() -> configureServices(source, status));
        }
        @Override public void onDescriptorWrite(BluetoothGatt source, BluetoothGattDescriptor descriptor, int status) {
            main.post(() -> {
                if (!isCurrent(source) || telemetryCharacteristic == null
                        || !CCCD_UUID.equals(descriptor.getUuid())
                        || !PROVISIONAL_TELEMETRY_UUID.equals(descriptor.getCharacteristic().getUuid())) return;
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    disconnectOnMain("Notification subscription failed", null);
                    return;
                }
                if (!handshake.subscriptionConfirmed()) return;
                if (setupTimeout != null) main.removeCallbacks(setupTimeout);
                setupTimeout = null;
                connected = true;
                listener.onConnected(true);
                if (isCurrent(source) && connected) listener.onStatus("BLE ready; waiting for real sensor readings");
            });
        }
        @Override public void onCharacteristicChanged(BluetoothGatt source, BluetoothGattCharacteristic characteristic) {
            // API 31-32 values are mutable; snapshot on the callback thread before posting.
            byte[] value = characteristic.getValue();
            receive(source, characteristic.getUuid(), value == null ? null : value.clone());
        }
        @Override public void onCharacteristicChanged(BluetoothGatt source, BluetoothGattCharacteristic characteristic, byte[] value) {
            receive(source, characteristic.getUuid(), value == null ? null : value.clone());
        }
        @Override public void onCharacteristicWrite(BluetoothGatt source, BluetoothGattCharacteristic characteristic, int status) {
            main.post(() -> {
                if (!isCurrent(source) || !writeInFlight || outgoing == null
                        || !PROVISIONAL_COMMAND_UUID.equals(characteristic.getUuid())) return;
                writeInFlight = false;
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    disconnectOnMain("Command write failed; device outcome unknown", null);
                } else if (sentOffset < outgoing.length) {
                    writeNextChunk();
                } else {
                    outgoing = null;
                    IncomingMessage ack = deferredAck;
                    deferredAck = null;
                    if (ack != null) handleAcknowledgement(ack);
                    // A successful GATT write is NOT an application acknowledgement.
                }
            });
        }
    };

    private boolean isCurrent(BluetoothGatt source) { return !closed && source != null && source == gatt; }

    private void configureServices(BluetoothGatt source, int status) {
        if (!isCurrent(source)) return;
        if (status != BluetoothGatt.GATT_SUCCESS) {
            disconnectOnMain("Service discovery failed", null);
            return;
        }
        try {
            BluetoothGattService service = source.getService(PROVISIONAL_SERVICE_UUID);
            BluetoothGattCharacteristic tx = service == null ? null : service.getCharacteristic(PROVISIONAL_COMMAND_UUID);
            BluetoothGattCharacteristic rx = service == null ? null : service.getCharacteristic(PROVISIONAL_TELEMETRY_UUID);
            int rxProperties = rx == null ? 0 : rx.getProperties();
            boolean notify = (rxProperties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
            boolean indicate = (rxProperties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;
            BluetoothGattDescriptor cccd = rx == null ? null : rx.getDescriptor(CCCD_UUID);
            if (tx == null || (tx.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0
                    || (!notify && !indicate) || cccd == null) {
                disconnectOnMain("Device does not expose the provisional Tunas GATT protocol", null);
                return;
            }
            if (!handshake.servicesResolved()) return;
            commandCharacteristic = tx;
            telemetryCharacteristic = rx;
            if (!source.setCharacteristicNotification(rx, true)) {
                disconnectOnMain("Local notification registration failed", null);
                return;
            }
            byte[] enable = notify ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
            boolean started;
            if (Build.VERSION.SDK_INT >= 33) started = source.writeDescriptor(cccd, enable) == BluetoothStatusCodes.SUCCESS;
            else { cccd.setValue(enable); started = source.writeDescriptor(cccd); }
            if (!started) disconnectOnMain("CCCD write could not start", null);
        } catch (SecurityException | IllegalStateException failure) {
            disconnectOnMain("Notification setup failed", null);
        }
    }

    private void receive(BluetoothGatt source, UUID characteristic, byte[] value) {
        main.post(() -> {
            if (!isCurrent(source) || !connected || !PROVISIONAL_TELEMETRY_UUID.equals(characteristic) || value == null) return;
            decoder.accept(value, this::handleLine, () -> listener.onStatus("Discarded invalid or oversized BLE frame"));
        });
    }

    private void handleLine(String line) {
        if (!connected || closed) return;
        IncomingMessage message;
        try {
            message = IncomingMessage.decode(FlatJson.fields(line, FlatJson.KEYS), System.currentTimeMillis());
        } catch (JSONException | IllegalArgumentException invalid) {
            listener.onStatus("Discarded malformed BLE protocol frame");
            return;
        }
        if (message.telemetry != null) listener.onTelemetry(message.telemetry);
        else if (writingId != null && writingId.equals(message.commandId)) {
            if (outgoing != null) deferredAck = message;
            else handleAcknowledgement(message);
        }
    }

    private void handleAcknowledgement(IncomingMessage ack) {
        long now = SystemClock.elapsedRealtime();
        CommandTracker.Result expired = commands.expire(now);
        if (expired != null) {
            disconnectOnMain("Command acknowledgement timed out; device outcome unknown", expired);
            return;
        }
        CommandTracker.Result result = commands.acknowledge(ack.commandId, ack.success, now);
        if (result != null) finishCommand(result);
    }

    private void commandOnMain(String action, int value) {
        String id = UUID.randomUUID().toString();
        try {
            commands.begin(!closed && connected && gatt != null, id, action, value, SystemClock.elapsedRealtime());
        } catch (IllegalArgumentException | IllegalStateException rejected) {
            listener.onCommandResult(false, rejected.getMessage());
            return;
        }
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            disconnectOnMain("Bluetooth permission was revoked; command not sent", null);
            return;
        }
        try {
            outgoing = (CommandJson.encode(id, action, value) + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException encodingFailure) {
            finishCommand(commands.cancel("Could not encode command; command not sent"));
            return;
        }
        writingId = id;
        deferredAck = null;
        sentOffset = 0;
        commandTimeout = () -> {
            CommandTracker.Result expired = commands.expire(SystemClock.elapsedRealtime());
            if (expired != null) disconnectOnMain("Command acknowledgement timed out; device outcome unknown", expired);
        };
        main.postDelayed(commandTimeout, CommandTracker.TIMEOUT_MILLIS);
        writeNextChunk();
    }

    private void writeNextChunk() {
        if (!connected || gatt == null || commandCharacteristic == null || outgoing == null || writeInFlight) return;
        // ATT MTU 23 is guaranteed: 20 payload bytes, no MTU negotiation assumptions.
        int end = Math.min(sentOffset + 20, outgoing.length);
        byte[] chunk = Arrays.copyOfRange(outgoing, sentOffset, end);
        try {
            boolean started;
            if (Build.VERSION.SDK_INT >= 33) {
                started = gatt.writeCharacteristic(commandCharacteristic, chunk,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS;
            } else {
                commandCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                commandCharacteristic.setValue(chunk);
                started = gatt.writeCharacteristic(commandCharacteristic);
            }
            if (!started) {
                disconnectOnMain("Command write could not start; device outcome unknown", null);
                return;
            }
            sentOffset = end;
            writeInFlight = true;
        } catch (SecurityException | IllegalStateException failure) {
            disconnectOnMain("Command transport failed; device outcome unknown", null);
        }
    }

    private void finishCommand(CommandTracker.Result result) {
        if (result == null) return;
        if (commandTimeout != null) main.removeCallbacks(commandTimeout);
        commandTimeout = null;
        outgoing = null;
        sentOffset = 0;
        writeInFlight = false;
        writingId = null;
        deferredAck = null;
        listener.onCommandResult(result.success, result.message);
    }

    private void disconnectOnMain(String reason, CommandTracker.Result alreadyFailed) {
        BluetoothGatt previous = gatt;
        boolean hadConnection = previous != null || connected;
        gatt = null; // Ignore callbacks from this session before releasing the native object.
        connected = false;
        handshake.reset();
        commandCharacteristic = null;
        telemetryCharacteristic = null;
        decoder = new LineDecoder();
        if (setupTimeout != null) main.removeCallbacks(setupTimeout);
        if (commandTimeout != null) main.removeCallbacks(commandTimeout);
        setupTimeout = null;
        commandTimeout = null;
        CommandTracker.Result result = commands.cancel("Connection closed; command outcome unknown, do not retry automatically");
        if (alreadyFailed != null) result = alreadyFailed;
        outgoing = null;
        sentOffset = 0;
        writeInFlight = false;
        writingId = null;
        deferredAck = null;
        if (previous != null) {
            try { previous.disconnect(); }
            catch (SecurityException | IllegalStateException ignored) { /* still close the client handle */ }
            try { previous.close(); }
            catch (SecurityException | IllegalStateException ignored) { /* permissions may have been revoked */ }
        }
        if (hadConnection) listener.onConnected(false);
        listener.onStatus(reason);
        if (result != null) listener.onCommandResult(result.success, result.message);
    }
}
