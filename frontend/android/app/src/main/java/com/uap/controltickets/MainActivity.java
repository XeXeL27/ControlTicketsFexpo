package com.uap.controltickets;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebSettings;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int intentosMixto = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        permitirMixto();
    }

    @Override
    public void onResume() {
        super.onResume();
        permitirMixto();
    }

    /**
     * Carga mixta: la app corre en https://localhost pero el backend va por
     * http://. Sin esto el WebView bloquea las llamadas al API y al WS
     * (contenido mixto), aunque el manifest permita cleartext.
     * Reintenta hasta que el bridge exista (al arrancar puede no estar listo).
     */
    private void permitirMixto() {
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().getSettings().setMixedContentMode(
                    WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            return;
        }
        if (intentosMixto++ < 20) {
            handler.postDelayed(this::permitirMixto, 500);
        }
    }
}
