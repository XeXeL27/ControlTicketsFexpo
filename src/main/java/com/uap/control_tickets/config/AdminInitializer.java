package com.uap.control_tickets.config;

import com.uap.control_tickets.models.entity.Persona;
import com.uap.control_tickets.models.entity.Rol;
import com.uap.control_tickets.models.entity.Usuario;
import com.uap.control_tickets.models.entity.UsuarioRol;
import com.uap.control_tickets.models.repository.PersonaDao;
import com.uap.control_tickets.models.repository.RolDao;
import com.uap.control_tickets.models.repository.UsuarioDao;
import com.uap.control_tickets.models.repository.UsuarioRolDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Se ejecuta UNA vez al arrancar la aplicacion (implements ApplicationRunner).
 * Garantiza que existan los roles del sistema y un usuario administrador para
 * poder entrar la primera vez. Es idempotente: si ya existen, no los duplica.
 *
 * El usuario/clave del admin salen de properties (app.admin.username/password),
 * nunca escritos en el codigo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final UsuarioDao usuarioDao;
    private final RolDao rolDao;
    private final PersonaDao personaDao;
    private final UsuarioRolDao usuarioRolDao;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        inicializarRoles();
        inicializarAdmin();
    }

    /**
     * Roles base del sistema:
     * - ADMINISTRADOR: gestiona todo.
     * - CONTROL_CONCIERTO: valida boletos al ingreso del concierto + monitoreo.
     * - CONTROL_FERIA: valida boletos de la feria.
     * - VENTA_FERIA: registra que boletos de su talonario se vendieron (no toca la puerta).
     * (El antiguo rol CONTROL quedó reemplazado por los dos de arriba; si existe
     * en la BD de una instalación previa, se deja pero ya no se usa.)
     */
    private void inicializarRoles() {
        List<String> rolesPorDefecto = List.of("ADMINISTRADOR", "CONTROL_CONCIERTO", "CONTROL_FERIA", "VENTA_FERIA");
        for (String nombreRol : rolesPorDefecto) {
            if (rolDao.findByNombre(nombreRol).isEmpty()) {
                Rol rol = new Rol();
                rol.setNombre(nombreRol);
                rolDao.save(rol);
                log.info("Rol creado: {}", nombreRol);
            }
        }
    }

    private void inicializarAdmin() {
        Optional<Usuario> adminExistente = usuarioDao.findByUsername(adminUsername);
        if (adminExistente.isEmpty()) {
            crearAdmin();
        } else {
            actualizarAdminSiCambio(adminExistente.get());
        }
    }

    private void crearAdmin() {
        // 1. Persona del administrador
        Persona persona = new Persona();
        persona.setNombre("Administrador");
        persona.setPaterno("Sistema");
        persona.setMaterno("");
        persona.setCi("00000000");
        personaDao.save(persona);

        // 2. Cuenta de usuario (password hasheado con BCrypt)
        Usuario admin = new Usuario();
        admin.setUsername(adminUsername);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setPersona(persona);
        usuarioDao.save(admin);

        // 3. Asignar rol ADMINISTRADOR
        Rol rolAdmin = rolDao.findByNombre("ADMINISTRADOR")
                .orElseThrow(() -> new IllegalStateException("Rol ADMINISTRADOR no encontrado"));
        UsuarioRol usuarioRol = new UsuarioRol();
        usuarioRol.setUsuario(admin);
        usuarioRol.setRol(rolAdmin);
        usuarioRolDao.save(usuarioRol);

        log.info("Usuario administrador creado: {}", adminUsername);
    }

    /** Si en properties cambiaron el usuario/clave del admin, se sincroniza. */
    private void actualizarAdminSiCambio(Usuario admin) {
        boolean actualizado = false;

        if (!admin.getUsername().equals(adminUsername)) {
            admin.setUsername(adminUsername);
            actualizado = true;
        }
        if (!passwordEncoder.matches(adminPassword, admin.getPassword())) {
            admin.setPassword(passwordEncoder.encode(adminPassword));
            actualizado = true;
        }
        if (actualizado) {
            usuarioDao.save(admin);
            log.info("Usuario administrador actualizado: {}", adminUsername);
        } else {
            log.info("Usuario administrador sin cambios.");
        }
    }
}
