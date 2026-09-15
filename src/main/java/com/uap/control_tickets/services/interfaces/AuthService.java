package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.dto.login.LoginDto;
import com.uap.control_tickets.dto.login.TokenDto;

/** Contrato del servicio de autenticacion. */
public interface AuthService {
    TokenDto login(LoginDto request);
}
