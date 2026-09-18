#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Prueba de conexión a un biométrico ZKTeco (pyzk).

Uso: zk_probar.py <ip> <puerto> <timeout_ms> <clave>
  <clave> vacía = sin contraseña de comunicación.

Siempre imprime UN json por stdout:
  ok:    {"estado": "OK", "plataforma": ..., ...}
  error: {"estado": "ERROR", "mensaje": "..."} (exit 2)
"""
import json
import sys
import time
import traceback


def error(mensaje):
    print(json.dumps({"estado": "ERROR", "mensaje": mensaje}))
    sys.exit(2)


def main():
    t0 = time.time()
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
            info = {"estado": "OK", "equipo": ip}
            try:
                info["plataforma"] = conn.get_platform()
            except Exception:
                info["plataforma"] = "?"
            try:
                info["serie"] = conn.get_serialnumber()
            except Exception:
                info["serie"] = "?"
            try:
                info["nombre"] = conn.get_device_name()
            except Exception:
                info["nombre"] = "?"
            try:
                info["firmware"] = conn.get_firmware_version()
            except Exception:
                info["firmware"] = "?"
            try:
                conn.read_sizes()
                info["usuarios"] = conn.users
                info["huellas"] = conn.fingers
            except Exception:
                info["usuarios"] = "?"
                info["huellas"] = "?"
            try:
                info["versionHuella"] = conn.get_fp_version()
            except Exception:
                info["versionHuella"] = "?"
            info["latenciaMs"] = str(int((time.time() - t0) * 1000))
            print(json.dumps(info))
        finally:
            try:
                conn.disconnect()
            except Exception:
                pass
    except Exception as e:
        traceback.print_exc(file=sys.stderr)
        error("%s: %s" % (type(e).__name__, e))


if __name__ == "__main__":
    main()
