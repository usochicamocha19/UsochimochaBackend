package com.app.usochicamochabackend.auth.security;

import com.app.usochicamochabackend.auth.application.service.UserDetailsServiceImp;
import com.app.usochicamochabackend.auth.security.filter.JwtTokenValidator;
import com.app.usochicamochabackend.auth.utils.JwtUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtils jwtUtils;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new JwtTokenValidator(jwtUtils), BasicAuthenticationFilter.class)
                .authorizeHttpRequests(http -> {
                    // 1. Acceso Público (Auth, Swagger, Imágenes)
                    http.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    http.requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll();
                    http.requestMatchers(HttpMethod.GET, "/uploads/**").permitAll();
                    http.requestMatchers(HttpMethod.HEAD, "/uploads/**").permitAll();
                    http.requestMatchers(
                            "/swagger-ui/**",
                            "/v3/api-docs/**",
                            "/v3/api-docs.yaml",
                            "/api/v1/auth/**",
                            "/api/v1/moto/documento/imagen/**",
                            "/ws/**").permitAll();

                     // 2a. Cambios de aceite (ESPECÍFICO - debe venir ANTES de las rutas generales de /api/v1/vehicle/**, /api/v1/moto/**)
                    http.requestMatchers(HttpMethod.POST, "/api/oil-changes/motor", "/api/v1/oil-changes/motor").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.POST, "/api/oil-changes/hydraulic", "/api/v1/oil-changes/hydraulic").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.POST, "/api/v1/vehicle/oil-change").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.POST, "/api/v1/moto/*/oil-change").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.POST, "/api/oil-changes/**", "/api/v1/oil-changes/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.GET, "/api/oil-changes/**", "/api/v1/oil-changes/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.PUT, "/api/oil-changes/**", "/api/v1/oil-changes/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.DELETE, "/api/oil-changes/**", "/api/v1/oil-changes/**").hasRole("ADMIN");

                    // 2b. Catálogos y marcas
                    http.requestMatchers(HttpMethod.GET, "/api/v1/catalog/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.POST, "/api/v1/catalog/**").hasAnyRole("ADMIN", "SUPERVISOR_OPERATIVO");
                    http.requestMatchers(HttpMethod.PUT, "/api/v1/catalog/**").hasAnyRole("ADMIN", "SUPERVISOR_OPERATIVO");
                    http.requestMatchers(HttpMethod.DELETE, "/api/v1/catalog/**").hasRole("ADMIN");
                    http.requestMatchers(HttpMethod.GET, "/api/v1/brand/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.POST, "/api/v1/brand/**").hasAnyRole("ADMIN", "SUPERVISOR_OPERATIVO");
                    http.requestMatchers(HttpMethod.PUT, "/api/v1/brand/**").hasAnyRole("ADMIN", "SUPERVISOR_OPERATIVO");
                    http.requestMatchers(HttpMethod.DELETE, "/api/v1/brand/**").hasRole("ADMIN");

                    // 2c. Lecturas generales (vehículos, motos, máquinas)
                    http.requestMatchers(HttpMethod.GET, "/api/v1/machine/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.GET, "/api/v1/vehicle/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.GET, "/api/v1/moto/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");

                    // 4. OPERARIO: inspecciones pre-operativas (vehículos, motos, máquinas)
                    http.requestMatchers(HttpMethod.POST, "/api/v1/vehicle-inspection/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.PUT, "/api/v1/vehicle-inspection/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.POST, "/api/v1/moto/inspeccion").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.PUT, "/api/v1/moto/inspeccion/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.POST, "/api/v1/inspection/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");

                    // Lecturas de inspección (móvil + supervisor + admin)
                    http.requestMatchers(HttpMethod.GET, "/api/v1/inspection/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.GET, "/api/v1/vehicle-inspection/documentos/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.GET, "/api/v1/vehicle-inspection/validar-kilometraje").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.GET, "/api/v1/vehicle-inspection/reports/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");

                    // 6. SUPERVISOR_OPERATIVO + OPERARIO: lectura de reportes y monitoreo web
                    http.requestMatchers(HttpMethod.GET, "/api/v1/order/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.GET, "/api/v1/curriculum/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.GET, "/api/v1/results/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");

                    // 8. Gestión administrativa (crear/editar: ADMIN + SUPERVISOR_OPERATIVO; eliminar: solo ADMIN)
                    http.requestMatchers(HttpMethod.POST, "/api/v1/machine").hasAnyRole("ADMIN", "SUPERVISOR_OPERATIVO");
                    http.requestMatchers(HttpMethod.PUT, "/api/v1/machine/**").hasAnyRole("ADMIN", "SUPERVISOR_OPERATIVO");
                    http.requestMatchers(HttpMethod.DELETE, "/api/v1/machine/**").hasRole("ADMIN");
                    http.requestMatchers(HttpMethod.POST, "/api/v1/vehicle").hasAnyRole("ADMIN", "SUPERVISOR_OPERATIVO");
                    http.requestMatchers(HttpMethod.PUT, "/api/v1/vehicle/**").hasAnyRole("ADMIN", "SUPERVISOR_OPERATIVO");

                    http.requestMatchers(HttpMethod.DELETE, "/api/v1/vehicle/**").hasRole("ADMIN");
                    http.requestMatchers(HttpMethod.POST, "/api/v1/moto").hasAnyRole("ADMIN", "SUPERVISOR_OPERATIVO");
                    http.requestMatchers(HttpMethod.PUT, "/api/v1/moto/**").hasAnyRole("ADMIN", "SUPERVISOR_OPERATIVO");
                    http.requestMatchers(HttpMethod.DELETE, "/api/v1/moto/**").hasRole("ADMIN");
                    http.requestMatchers(HttpMethod.GET, "/api/v1/user/me").authenticated();
                    http.requestMatchers("/api/v1/user/**").hasRole("ADMIN");
                    http.requestMatchers(HttpMethod.POST, "/api/v1/order/**").hasAnyRole("SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.PUT, "/api/v1/order/**").hasAnyRole("SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.DELETE, "/api/v1/order/**").hasRole("ADMIN");
                    http.requestMatchers(HttpMethod.POST, "/api/v1/curriculum/**").hasAnyRole("SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.POST, "/api/v1/results/**").hasAnyRole("SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers("/api/actions/**", "/api/v1/actions/**").hasRole("ADMIN");
                    http.requestMatchers("/new-data/notifications/**").hasRole("ADMIN");
                    http.requestMatchers(HttpMethod.POST, "/api/v1/admin/documents/**").hasAnyRole("ADMIN", "SUPERVISOR_OPERATIVO");
                    http.requestMatchers(HttpMethod.GET, "/api/v1/oil/brand/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.POST, "/api/v1/oil/brand/**").hasAnyRole("ADMIN", "SUPERVISOR_OPERATIVO");
                    http.requestMatchers(HttpMethod.PUT, "/api/v1/oil/brand/**").hasAnyRole("ADMIN", "SUPERVISOR_OPERATIVO");
                    http.requestMatchers(HttpMethod.DELETE, "/api/v1/oil/brand/**").hasRole("ADMIN");

                    // 9. Alertas preventivas (web + móvil: OPERARIO, SUPERVISOR_OPERATIVO y ADMIN pueden verlas
                    http.requestMatchers(HttpMethod.GET, "/api/v1/alerts/debug/**").hasRole("ADMIN");
                    http.requestMatchers(HttpMethod.GET, "/api/v1/alerts/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.POST, "/api/v1/alerts/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.DELETE, "/api/v1/alerts/**").hasAnyRole("OPERARIO", "SUPERVISOR_OPERATIVO", "ADMIN");

                    // 5. Combustibles
                    http.requestMatchers(HttpMethod.POST, "/api/v1/fuel/purchases").hasAnyRole("SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.GET, "/api/v1/fuel/purchases").hasAnyRole( "SUPERVISOR_OPERATIVO", "ADMIN");

                    http.requestMatchers(HttpMethod.POST, "/api/v1/fuel/refueling").hasAnyRole( "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.GET, "/api/v1/fuel/refueling").hasAnyRole(  "SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.PUT, "/api/v1/fuel/refueling/**").hasAnyRole("SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.DELETE, "/api/v1/fuel/refueling/**").hasRole("ADMIN");

                    http.requestMatchers(HttpMethod.GET, "/api/v1/fuel/dashboard/**").hasAnyRole("SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers("/api/v1/fuel/monthly-discount/**").hasAnyRole("SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.GET, "/api/v1/fuel/almacen/**").hasAnyRole("SUPERVISOR_OPERATIVO", "ADMIN");
                    // "/**" cubre también la ruta exacta "/api/v1/fuel/rendimiento" (AntPathMatcher
                    // matchea "/**" con cero segmentos extra) — antes solo cubría esa ruta exacta,
                    // así que /rendimiento/export y el nuevo /rendimiento/export-mensual caían al
                    // catch-all "/api/v1/fuel/**" → authenticated() (cualquier rol autenticado).
                    http.requestMatchers(HttpMethod.GET, "/api/v1/fuel/rendimiento/**").hasAnyRole("SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.GET, "/api/v1/fuel/distribucion").hasAnyRole("SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers(HttpMethod.POST, "/api/v1/fuel/reintegros").hasAnyRole("SUPERVISOR_OPERATIVO", "ADMIN");
                    http.requestMatchers("/api/v1/fuel/config/**").hasRole("ADMIN");
                    // Catálogo de tipos y cualquier otra ruta de fuel no listada arriba: cualquier autenticado.
                    http.requestMatchers("/api/v1/fuel/**").authenticated();

                    // Cualquier otra petición: solo ADMIN
                    http.anyRequest().hasRole("ADMIN");
                })
                .exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler()));

        return httpSecurity.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
                "https://usochicamocha.co",
                "https://web.usochicamocha.co",
                "http://front-test.usochicamocha.co",
                "https://front-test.usochicamocha.co",
                "http://localhost:[*]",
                "http://127.0.0.1:[*]"
        ));
        configuration.addAllowedHeader("*");
        configuration.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider(UserDetailsServiceImp userDetailsService) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\": \"Access denied\"}");
        };
    }

}
