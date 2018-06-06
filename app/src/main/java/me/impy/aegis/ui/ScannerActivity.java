package me.impy.aegis.ui;

import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;

import java.util.Collections;

import me.dm7.barcodescanner.core.IViewFinder;
import me.dm7.barcodescanner.zxing.ZXingScannerView;
import me.impy.aegis.R;
import me.impy.aegis.db.DatabaseEntry;
import me.impy.aegis.helpers.SquareFinderView;
import me.impy.aegis.otp.GoogleAuthInfo;
import me.impy.aegis.otp.GoogleAuthInfoException;

import static android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK;
import static android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT;

public class ScannerActivity extends AegisActivity implements ZXingScannerView.ResultHandler {
    private ZXingScannerView _scannerView;
    private Menu _menu;
    private int _facing = CAMERA_FACING_BACK;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        _scannerView = new ZXingScannerView(this) {
            @Override
            protected IViewFinder createViewFinderView(Context context) {
                return new SquareFinderView(context);
            }
        };
        _scannerView.setResultHandler(this);
        _scannerView.setFormats(Collections.singletonList(BarcodeFormat.QR_CODE));

        int camera = getRearCameraId();
        if (camera == -1) {
            camera = getFrontCameraId();
            if (camera == -1) {
                Toast.makeText(this, "No cameras available", Toast.LENGTH_LONG).show();
                finish();
            }
            _facing = CAMERA_FACING_FRONT;
        }
        _scannerView.startCamera(camera);

        setContentView(_scannerView);
    }

    @Override
    protected void setPreferredTheme(boolean darkMode) {
        setTheme(R.style.AppTheme_Fullscreen);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        _menu = menu;
        getMenuInflater().inflate(R.menu.menu_scanner, menu);
        updateCameraIcon();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_camera:
                _scannerView.stopCamera();
                switch (_facing) {
                    case CAMERA_FACING_BACK:
                        _facing = CAMERA_FACING_FRONT;
                        break;
                    case CAMERA_FACING_FRONT:
                        _facing = CAMERA_FACING_BACK;
                        break;
                }
                updateCameraIcon();
                _scannerView.startCamera(getCameraId(_facing));
                return true;
            case R.id.action_lock:
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        _scannerView.startCamera(getCameraId(_facing));
    }

    @Override
    public void onPause() {
        super.onPause();
        _scannerView.stopCamera();
    }

    @Override
    public void handleResult(Result rawResult) {
        try {
            // parse google auth uri
            Uri uri = Uri.parse(rawResult.getText());
            GoogleAuthInfo info = GoogleAuthInfo.parseUri(uri);

            DatabaseEntry entry = new DatabaseEntry(info.getOtpInfo());
            entry.setIssuer(info.getIssuer());
            entry.setName(info.getAccountName());

            Intent intent = new Intent();
            intent.putExtra("entry", entry);
            setResult(RESULT_OK, intent);
            finish();
        } catch (GoogleAuthInfoException e) {
            Toast.makeText(this, "An error occurred while trying to parse the QR code contents", Toast.LENGTH_SHORT).show();
        }

        _scannerView.resumeCameraPreview(this);
    }

    private void updateCameraIcon() {
        if (_menu != null) {
            MenuItem item = _menu.findItem(R.id.action_camera);
            boolean dual = getFrontCameraId() != -1 && getRearCameraId() != -1;
            if (dual) {
                switch (_facing) {
                    case CAMERA_FACING_BACK:
                        item.setIcon(R.drawable.ic_camera_front_24dp);
                        break;
                    case CAMERA_FACING_FRONT:
                        item.setIcon(R.drawable.ic_camera_rear_24dp);
                        break;
                }
            }
            item.setVisible(dual);
        }
    }

    private static int getCameraId(int facing) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); ++i) {
            Camera.getCameraInfo(i, info);
            if (info.facing == facing) {
                return i;
            }
        }

        return -1;
    }

    private static int getRearCameraId() {
        return getCameraId(CAMERA_FACING_BACK);
    }

    private static int getFrontCameraId() {
        return getCameraId(CAMERA_FACING_FRONT);
    }
}