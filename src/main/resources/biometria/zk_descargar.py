#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Descarga TODOS los usuarios de un biométrico ZKTeco con sus templates (pyzk).

Uso: zk_descargar.py <ip> <puerto> <timeout_ms> <clave>
  <clave> vacía = sin contraseña de comunicación.

Imprime UN json por stdout (puede ser grande: miles de templates en Base64):
  {"estado": "OK", "usuarios": [
     {"pin": "1001", "uid": 1, "nombre": "...", "version": "10",
      "templates": {"0": "<b64>", "1": "<b64>"}}]}
En error: {"estado": "ERROR", "mensaje": "..."} (exit 2).

El PIN del equipo es el RU: el backend lo cruza con el sistema.
"""
import base64
import json
import sys
import traceback


def error(mensaje):
    print(json.dumps({"estado": "ERROR", "mensaje": mensaje}))
    sys.exit(2)


def main():
    if len(sys.argv) != 5:
        error("Argumentos: <ip> <puerto> <timeout_ms> <clave>")
    ip, puerto, timeout_ms, clave = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]

    try:
        puerto = int(puerto)
        timeout_s = max(5, int(timeout_ms) // 1000)
    except ValueError:
        error("Puerto/timeout inválidos")
    if clave and not clave.isdigit():
        error("La clave de comunicación debe ser numérica")
    password = int(clave) if clave else 0

    try:
        from zk import ZK
    except ImportError:
        error("Falta la librería pyzk (pip install pyzk)")

    try:
        zk = ZK(ip, puerto, timeout=timeout_s, password=password, ommit_ping=True)
        conn = zk.connect()
        try:
            conn.disable_device()  # lectura consistente
            try:
                version = conn.get_fp_version()
            except Exception:
                version = None
            usuarios = conn.get_users()
            por_uid = {}
            try:
                for f in conn.get_templates():
                    if f.template and len(f.template) >= 64 and 0 <= f.fid <= 9:
                        por_uid.setdefault(f.uid, {})[str(f.fid)] = base64.b64encode(f.template).decode("ascii")
            except Exception as e:
                # Sin bloque de templates: se informa igual (cada usuario quedará SIN_HUELLA).
                print("WARN: no se pudo bajar el bloque de templates: %s" % e, file=sys.stderr)
            salida = []
            for u in usuarios:
                pin = (u.user_id or "").strip()
                if not pin:
                    continue
                salida.append({
                    "pin": pin,
                    "uid": u.uid,
                    "nombre": (u.name or "").strip(),
                    "version": str(version) if version else None,
                    "templates": por_uid.get(u.uid, {}),
                })
            print(json.dumps({"estado": "OK", "usuarios": salida}))
        finally:
            try:
                conn.enable_device()
            except Exception:
                pass
            try:
                conn.disconnect()
            except Exception:
                pass
    except Exception as e:
        traceback.print_exc(file=sys.stderr)
        error("%s: %s" % (type(e).__name__, e))


if __name__ == "__main__":
    main()
