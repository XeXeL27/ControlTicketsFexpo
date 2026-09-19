package com.uap.controltickets;

import android.os.Bundle;
import android.webkit.WebSettings;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Carga mixta: la app corre en https://localhost pero el backend va por
        // http://. Sin esto el WebView bloquea las llamadas al API y al WS
        // (contenido mixto), aunque el manifest permita cleartext.
        // Se aplica cuando el WebView ya existe (después del primer resume).
        getWindow().getDecorView().post(() -> {
            if (getBridge() == null || getBridge().getWebView() == null) return;
            getBridge().getWebView().getSettings().setMixedContentMode(
                    WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        });
    }
}

