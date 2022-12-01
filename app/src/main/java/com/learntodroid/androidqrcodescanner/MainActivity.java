package com.learntodroid.androidqrcodescanner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CAMERA = 0;

    private PreviewView previewView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    private Button qrCodeFoundButton;
    private static String qrCode = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.activity_main_previewView);

        qrCodeFoundButton = findViewById(R.id.activity_main_qrCodeFoundButton);
        qrCodeFoundButton.setVisibility(View.VISIBLE);
        qrCodeFoundButton.setText("RESET");
        qrCodeFoundButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(MainActivity.class.getSimpleName(), "QR Code Found: " + qrCode);
                qrCode = "";
                qrCodeFoundButton.setText("RESET");
                Toast.makeText(getApplicationContext(), "CLEAN MEMORY!", Toast.LENGTH_SHORT).show();
            }
        });
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        requestCamera();
    }

    private void requestCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startCamera() {
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Error starting camera " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraPreview(@NonNull ProcessCameraProvider cameraProvider) {
        previewView.setPreferredImplementationMode(PreviewView.ImplementationMode.SURFACE_VIEW);

        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(previewView.createSurfaceProvider());

        ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), new QRCodeImageAnalyzer(new QRCodeFoundListener() {
            @Override
            public void onQRCodeFound(String _qrCode) {
                if(!qrCode.equals(_qrCode) && _qrCode.contains("https://invitationcap.azurewebsites.net/index.jsp")) {
                    qrCode = _qrCode;
                    /*qrCodeFoundButton.setVisibility(View.VISIBLE); Ya NO SE MUESTRA el botón*/
                    Long idEmployees = getIdEmployees(qrCode);
                    String URL_API_REST = "https://coigoqr.azurewebsites.net/api/employees/findandupdate/" + idEmployees;
                    /*Toast.makeText(getApplicationContext(), URL_API_REST, Toast.LENGTH_SHORT).show();*/
                    StringRequest strRequest = new StringRequest(Request.Method.GET, URL_API_REST, new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            try {
                                JSONObject jsonObject = new JSONObject(response);
                                Long error_idempleado = jsonObject.getLong("idempleado");
                                Long error_idemployees = jsonObject.getLong("idemployees");
                                String nombre = jsonObject.getString("nombre");
                                String sede = jsonObject.getString("sede");
                                if(0L == error_idemployees || 0L == error_idempleado) {
                                    Toast.makeText(getApplicationContext(), "ERROR: " + nombre + " YA ESTA REGISTRADO EN LA SEDE " + sede, Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(getApplicationContext(), "¡Bienvenido " + nombre + "! :) TU SEDE ES " + sede, Toast.LENGTH_SHORT).show();
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }, new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Log.e("error", error.getMessage());
                            Toast.makeText(getApplicationContext(), error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                    Volley.newRequestQueue(getApplicationContext()).add(strRequest);
                } else if(!qrCode.equals(_qrCode)) {
                    qrCode = _qrCode;
                    Toast.makeText(getApplicationContext(), _qrCode, Toast.LENGTH_SHORT).show();
                } else {
                    qrCodeFoundButton.setText("RESET NOW!");
                }
            }

            @Override
            public void qrCodeNotFound() {
                Toast.makeText(getApplicationContext(), qrCode, Toast.LENGTH_SHORT).cancel();
            }
        }));
        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, imageAnalysis, preview);
    }

    private Long getIdEmployees(String qrCode) {
        String[] arrIdEmployeesStr = qrCode.split("&");
        String idEmployeesStr = arrIdEmployeesStr.length > 3 ? arrIdEmployeesStr[3] : null;
        String idEmployees = idEmployeesStr != null ? idEmployeesStr.replace("idEmployees=","") : null;
        return idEmployees != null ? Long.valueOf(idEmployees) : 0L;
    }
}
