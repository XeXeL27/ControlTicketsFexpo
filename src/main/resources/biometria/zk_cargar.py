#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Carga masiva sistema -> biométrico ZKTeco (pyzk).

Uso: zk_cargar.py <payload.json> <ip> <puerto> <timeout_ms> <clave>
  payload: {"usuarios": [{"pin": "1001", "nombre": "...", "templates": {"0": "<b64>"}}]}
  <clave> vacía = sin contraseña de comunicación.

Por cada RU imprime UNA línea json (progreso en vivo, el backend la lee):
  {"ru": "1001", "estado": "CARGADO", "mensaje": "creado con 2 huellas",
   "procesados": 3, "total": 120}
Al final: {"fin": true} (exit 0). Si no se pudo ni conectar:
  {"estado": "ERROR", "mensaje": "..."} (exit 2).

El PIN del equipo es el RU. Si el PIN ya existe se actualiza (nombre +
templates) y sale ACTUALIZADO en vez de CARGADO.
"""
import base64
import binascii
import json
import sys
import traceback


def linea(obj):
    print(json.dumps(obj), flush=True)


def error(mensaje):
    linea({"estado": "ERROR", "mensaje": mensaje})
    sys.exit(2)


def main():
    if len(sys.argv) != 6:
        error("Argumentos: <payload.json> <ip> <puerto> <timeout_ms> <clave>")
    payload_path, ip, puerto, timeout_ms, clave = sys.argv[1:6]

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
        from zk.user import User
        from zk.finger import Finger
    except ImportError:
        error("Falta la librería pyzk (pip install pyzk)")

    try:
        with open(payload_path, encoding="utf-8") as f:
            usuarios = json.load(f).get("usuarios", [])
    except Exception as e:
        error("Payload ilegible: %s" % e)

    total = len(usuarios)
    try:
        zk = ZK(ip, puerto, timeout=timeout_s, password=password, ommit_ping=True)
        conn = zk.connect()
        try:
            conn.disable_device()
            existentes = {}
            try:
                for u in conn.get_users():
                    pin = (u.user_id or "").strip()
                    if pin:
                        existentes[pin] = u.uid
                siguiente = (max(existentes.values()) + 1) if existentes else 1
            except Exception:
                siguiente = 1
            i = 0
            for u in usuarios:
                i += 1
                pin = str(u.get("pin", "")).strip()
                nombre = str(u.get("nombre", "")).strip() or ("NN-%s" % pin)
                if not pin:
                    linea({"ru": "", "estado": "ERROR", "mensaje": "RU vacío",
                           "procesados": i, "total": total})
                    continue
                try:
                    if pin in existentes:
                        uid = existentes[pin]
                        estado = "ACTUALIZADO"
                    else:
                        uid = siguiente
                        siguiente += 1
                        estado = "CARGADO"
                    conn.set_user(uid=uid, name=nombre, privilege=0, user_id=pin)
                    dedos = 0
                    fingers = []
                    for dedo, b64 in (u.get("templates") or {}).items():
                        try:
                            tpl = base64.b64decode(b64)
                        except (binascii.Error, ValueError):
                            continue
                        if len(tpl) >= 64 and 0 <= int(dedo) <= 9:
                            fingers.append(Finger(uid, int(dedo), 1, tpl))
                    if fingers:
                        conn.save_user_template(
                            User(uid, nombre, 0, "", "", pin, 0), fingers)
                        dedos = len(fingers)
                    detalle = ("creado" if estado == "CARGADO" else "actualizado")
                    detalle += (" con %d huella(s)" % dedos) if dedos else " sin huellas"
                    linea({"ru": pin, "estado": estado, "mensaje": detalle,
                           "procesados": i, "total": total})
                except Exception as e:
                    linea({"ru": pin, "estado": "ERROR",
                           "mensaje": "%s: %s" % (type(e).__name__, e),
                           "procesados": i, "total": total})
            linea({"fin": True})
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
